import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './app/App';
import { AuthProvider } from './app/auth';
import { applyTheme, storedTheme, ThemeProvider } from './app/theme';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/themes.css';
import './styles/bootstrap-theme.css';
import './styles/global.css';

// Apply the last-used theme before React renders, so the page never flashes in the wrong colours.
applyTheme(storedTheme());

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
