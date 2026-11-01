// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

function reducedMotion(on: boolean) {
  window.matchMedia = vi.fn().mockImplementation((q: string) => ({
    matches: on && q.includes('prefers-reduced-motion'), media: q, addEventListener() {}, removeEventListener() {},
  })) as unknown as typeof window.matchMedia;
}
async function freshModules() {     // the typing runs once per visit: start each test as a new visit
  vi.resetModules();
  return { ...(await import('./TypedText')), ...(await import('./LoginPictures')), ...(await import('./AuthLayout')), ...(await import('./flip')) };
}


type Modules = Awaited<ReturnType<typeof freshModules>>;
/** The sign-in layout as a route layout, with two small pages and an app page, like App.tsx. */
function renderAuth(m: Modules, start = '/signin') {
  const { AuthLayout, FlipLink, useFlip } = m;
  function SignIn() {
    const { flipTo } = useFlip();
    return <><h2>Sign in page</h2><FlipLink to="/signup">Create an account</FlipLink><button onClick={() => flipTo('/app', { intoApp: true })}>Sign in</button></>;
  }
  function AppPage() { const { state } = useLocation(); return <p>App {(state as { flipIn?: boolean } | null)?.flipIn ? 'flipping in' : 'still'}</p>; }
  return render(
    <MemoryRouter initialEntries={[start]}>
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/signin" element={<SignIn />} />
          <Route path="/signup" element={<><h2>Sign up page</h2><FlipLink to="/signin">Back to sign in</FlipLink></>} />
        </Route>
        <Route path="/app" element={<AppPage />} />
      </Routes>
    </MemoryRouter>);
}

describe('sign-in page animations', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => { cleanup(); vi.useRealTimers(); });

  it('types the headline and the text character by character, and screen readers get the full text at once', async () => {
    reducedMotion(false);
    const m = await freshModules();
    const { container } = renderAuth(m);
    const live = () => [...container.querySelectorAll('.typed-live')].map((e) => e.textContent).join(' | ');
    expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('Every rupee, matched to a real need.');
    expect(live()).toBe(' | ');                                            // nothing typed yet
    for (let i = 0; i < 5; i++) await act(async () => { vi.advanceTimersByTime(16); });   // each character schedules the next
    expect(live().startsWith('Every')).toBe(true);
    expect(container.querySelectorAll('.typed-caret').length).toBe(1);   // one caret, where the typing is
    for (let i = 0; i < 200; i++) await act(async () => { vi.advanceTimersByTime(20); });
    expect(live()).toBe('Every rupee, matched to a real need. | HumSaadhak connects donors with verified schools and NGOs, and shows where every contribution goes.');
    expect(container.querySelectorAll('.typed-caret').length).toBe(0);
  });

  it('shows the whole text at once when the system asks for reduced motion', async () => {
    reducedMotion(true);
    const m = await freshModules();
    const { container } = renderAuth(m);
    expect(container.querySelector('.typed-live')?.textContent).toBe('Every rupee, matched to a real need.');
  });

  it('plays once: each picture arrives, whitens and bursts; then all of them show together in a shining frame', async () => {
    reducedMotion(false);
    const { LoginPictures, LOGIN_PICTURES } = await freshModules();
    const { container } = render(<LoginPictures />);
    expect(LOGIN_PICTURES).toHaveLength(5);
    for (const [i, p] of LOGIN_PICTURES.entries()) {
      expect(screen.getByRole('img', { name: p.alt })).toBeTruthy();                  // in the circle
      expect(screen.getByText(p.caption)).toBeTruthy();
      expect(container.querySelector('.lp-picture.arrive')).toBeTruthy();
      await act(async () => { vi.advanceTimersByTime(1400); });
      expect(container.querySelector('.lp-picture.hold .lp-shade')).toBeTruthy();      // the white shade
      await act(async () => { vi.advanceTimersByTime(2600); });
      expect(container.querySelectorAll('.lp-burst .lp-piece').length).toBe(36);        // bursts into 6 x 6 pieces
      await act(async () => { vi.advanceTimersByTime(900); });
      if (i < LOGIN_PICTURES.length - 1) expect(screen.getByRole('img', { name: LOGIN_PICTURES[i + 1].alt })).toBeTruthy();
    }
    // all five together, at their own proportions, in the diamond frame
    expect(container.querySelector('.lp-finale .lp-diamond')).toBeTruthy();
    const tiles = [...container.querySelectorAll('.lp-tile')];
    expect(tiles).toHaveLength(5);
    for (const p of LOGIN_PICTURES) expect(screen.getByRole('img', { name: p.alt }).getAttribute('src')).toBe(p.full);
    expect(container.querySelectorAll('.lp-glint').length).toBe(6);
    // no rotation: it stays on the final frame
    for (let i = 0; i < 12; i++) await act(async () => { vi.advanceTimersByTime(5000); });
    expect(container.querySelector('.lp-finale')).toBeTruthy();
    expect(container.querySelector('.lp-picture, .lp-burst')).toBeNull();
  });

  it('later in the same visit (for example after signing out) the sign-in page opens on the final frame', async () => {
    reducedMotion(false);
    const { LoginPictures, LOGIN_PICTURES } = await freshModules();
    const first = render(<LoginPictures />);
    for (let i = 0; i < LOGIN_PICTURES.length; i++) {
      for (const ms of [1400, 2600, 900]) await act(async () => { vi.advanceTimersByTime(ms); });
    }
    first.unmount();
    const { container } = render(<LoginPictures />);
    expect(container.querySelector('.lp-finale')).toBeTruthy();
  });

  it('with reduced motion the final frame shows at once, without the sequence', async () => {
    reducedMotion(true);
    const { LoginPictures } = await freshModules();
    const { container } = render(<LoginPictures />);
    expect(container.querySelector('.lp-finale')).toBeTruthy();
    expect(container.querySelectorAll('.lp-tile')).toHaveLength(5);
  });

  it('a link flips the card: the current page turns away, then the next page turns into view', async () => {
    reducedMotion(false);
    const m = await freshModules();
    const { container } = renderAuth(m);
    fireEvent.click(screen.getByRole('link', { name: 'Create an account' }));
    expect(container.querySelector('.auth-card.flip-out')).toBeTruthy();        // still the sign-in page, turning away
    expect(screen.getByRole('heading', { name: 'Sign in page' })).toBeTruthy();
    await act(async () => { vi.advanceTimersByTime(m.FLIP_MS); });
    expect(screen.getByRole('heading', { name: 'Sign up page' })).toBeTruthy();
    expect(container.querySelector('.auth-card.flip-in')).toBeTruthy();         // the next page turns into view
    fireEvent.click(screen.getByRole('link', { name: 'Back to sign in' }));       // and back, the same way
    await act(async () => { vi.advanceTimersByTime(m.FLIP_MS); });
    expect(screen.getByRole('heading', { name: 'Sign in page' })).toBeTruthy();
  });

  it('signing in flips the card away and the app flips in', async () => {
    reducedMotion(false);
    const m = await freshModules();
    const { container } = renderAuth(m);
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(container.querySelector('.auth-card.flip-out')).toBeTruthy();
    await act(async () => { vi.advanceTimersByTime(m.FLIP_MS); });
    expect(screen.getByText('App flipping in')).toBeTruthy();
  });

  it('with reduced motion links go straight to the page, without flipping', async () => {
    reducedMotion(true);
    const m = await freshModules();
    const { container } = renderAuth(m);
    fireEvent.click(screen.getByRole('link', { name: 'Create an account' }));
    expect(screen.getByRole('heading', { name: 'Sign up page' })).toBeTruthy();
    expect(container.querySelector('.auth-card.flip-out')).toBeNull();
  });

  it('a Ctrl-click is left to the browser (open in a new tab) instead of flipping', async () => {
    reducedMotion(false);
    const m = await freshModules();
    const { container } = renderAuth(m);
    fireEvent.click(screen.getByRole('link', { name: 'Create an account' }), { ctrlKey: true });
    expect(container.querySelector('.auth-card.flip-out')).toBeNull();
  });

  it('the pictures sit in the brand panel, below the text; the form column holds only the page card', async () => {
    reducedMotion(true);
    const m = await freshModules();
    const { container } = renderAuth(m);
    const brand = screen.getByRole('region', { name: 'About HumSaadhak' });
    const pictures = brand.querySelector('.login-pictures');
    expect(pictures).toBeTruthy();
    expect(brand.querySelector('.login-brand-text')!.compareDocumentPosition(pictures!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(container.querySelector('.login-form .login-pictures')).toBeNull();
    expect(container.querySelector('.login-aurora')).toBeTruthy();                           // the colour mesh behind (no photo)
    expect((container.querySelector('.login') as HTMLElement).style.getPropertyValue('--login-bg')).toBe('');
  });
});

