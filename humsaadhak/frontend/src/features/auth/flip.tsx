import { createContext, useCallback, useContext, useRef, useState, type MouseEvent, type ReactNode } from 'react';
import { Link, useNavigate, type LinkProps } from 'react-router-dom';

/** The sign-in pages turn like a card: the current one flips away, the next one flips into view. */
export const FLIP_MS = 280;
type FlipOptions = { replace?: boolean; intoApp?: boolean };
type Flip = { leaving: boolean; flipTo: (to: string, options?: FlipOptions) => void };
const FlipContext = createContext<Flip | null>(null);

const calm = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

export function FlipProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);
  const busy = useRef(false);
  const flipTo = useCallback((to: string, options: FlipOptions = {}) => {
    // flip: the next sign-in page flips in; flipIn: the app's first page flips in after signing in
    const state = options.intoApp ? { flipIn: true } : { flip: true };
    if (calm()) { navigate(to, { replace: options.replace, state }); return; }
    if (busy.current) return;
    busy.current = true;
    setLeaving(true);
    window.setTimeout(() => { busy.current = false; setLeaving(false); navigate(to, { replace: options.replace, state }); }, FLIP_MS);
  }, [navigate]);
  return <FlipContext.Provider value={{ leaving, flipTo }}>{children}</FlipContext.Provider>;
}

/** Inside the sign-in layout: flips. Anywhere else (a page shown on its own): plain navigation, no flip. */
export function useFlip(): Flip {
  const flip = useContext(FlipContext);
  const navigate = useNavigate();
  return flip ?? {
    leaving: false,
    flipTo: (to, options = {}) => navigate(to, { replace: options.replace, state: options.intoApp ? { flipIn: true } : undefined }),
  };
}

/** A link that turns the card to its page. Ctrl-, Shift- or middle-click still open a new tab as usual. */
export function FlipLink({ to, onClick, ...rest }: Omit<LinkProps, 'to'> & { to: string }) {
  const { flipTo } = useFlip();
  return (
    <Link to={to} {...rest} onClick={(e: MouseEvent<HTMLAnchorElement>) => {
      onClick?.(e);
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      e.preventDefault();
      flipTo(to);
    }} />
  );
}
