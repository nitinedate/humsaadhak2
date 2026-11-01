import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation } from 'react-router-dom';
import { api, ApiError } from '../shared/api/client';
import type { Me } from '../shared/api/types';
import { useTheme } from './theme';

interface AuthState {
  me: Me | null;
  loading: boolean;
  can: (permission: string) => boolean;
  signIn: (email: string, password: string) => Promise<Me>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try { return await api<Me>('/me'); } catch (e) { if (e instanceof ApiError && e.status === 401) return null; throw e; }
    },
    staleTime: 5 * 60_000,
  });
  const me = data ?? null;

  /**
   * Drops every cached screen (purposes, approvals, documents…) so nothing from the previous person is shown,
   * but keeps the ['me'] entry itself: this provider is subscribed to it, and removing it (as qc.clear() did)
   * left the panel showing the first person who signed in.
   */
  const forgetOtherData = () => qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
  const { adopt } = useTheme();
  useEffect(() => { if (me?.theme) adopt(me.theme); }, [me?.theme, adopt]);
  const value: AuthState = {
    me,
    loading: isLoading,
    can: (p) => !!me?.permissions.includes(p),
    signIn: async (email, password) => {
      const user = await api<Me>('/auth/login', { method: 'POST', json: { email, password } });
      forgetOtherData();
      qc.setQueryData(['me'], user);
      return user;
    },
    signOut: async () => {
      await api('/auth/logout', { method: 'POST' });
      forgetOtherData();
      qc.setQueryData(['me'], null);
    },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

/** Hides a route from people without the permission. The server enforces the same rule on every call. */
export function RequireAuth({ children, permission }: { children: ReactNode; permission?: string }) {
  const { me, loading, can } = useAuth();
  const location = useLocation();
  if (loading) return null;
  if (!me) return <Navigate to="/signin" replace state={{ from: location.pathname }} />;
  if (permission && !can(permission)) return <Navigate to="/" replace />;
  return <>{children}</>;
}
