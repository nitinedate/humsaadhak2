import { Outlet, useLocation } from 'react-router-dom';
import logoTagline from '../../assets/logo-tagline.png';
import { FlipProvider, useFlip } from './flip';
import { LoginPictures } from './LoginPictures';
import { Typed, useTyping } from './TypedText';

const HEADLINE = 'Every rupee, matched to a real need.';
const ABOUT = 'HumSaadhak connects donors with verified schools and NGOs, and shows where every contribution goes.';

/**
 * The frame of the sign-in, sign-up and password pages (a layout route): the brand panel stays in place while the
 * page card in the middle flips from one page to the next.
 */
export function AuthLayout() {
  return <FlipProvider><AuthFrame /></FlipProvider>;
}

function AuthFrame() {
  const location = useLocation();
  const { leaving } = useFlip();
  const [headline, about] = useTyping([HEADLINE, ABOUT]);
  const arriving = !!(location.state as { flip?: boolean } | null)?.flip;
  return (
    <div className="login">
      {/* The background: a still mesh of soft colour made from the theme's own colours, behind frosted glass */}
      <div className="login-aurora" aria-hidden="true" />
      <section className="login-brand" aria-label="About HumSaadhak">
        <div className="plate"><img src={logoTagline} alt="HumSaadhak, where social sector needs find a suitable match" /></div>
        <div className="login-brand-text">
          <h1><Typed text={HEADLINE} {...headline} /></h1>
          <p><Typed text={ABOUT} {...about} /></p>
        </div>
        <LoginPictures />
      </section>
      <div className="login-form">
        <div className="auth-flip">
          <div key={location.pathname} className={`auth-card${leaving ? ' flip-out' : arriving ? ' flip-in' : ''}`}>
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  );
}
