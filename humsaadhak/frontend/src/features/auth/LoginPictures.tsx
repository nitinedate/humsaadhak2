import { useEffect, useMemo, useState } from 'react';
import booksRound from '../../assets/login/books_donate-round.jpg';
import books from '../../assets/login/books_donate.jpg';
import girlsRound from '../../assets/login/girls_education-round.jpg';
import girls from '../../assets/login/girls_education.jpg';
import mealRound from '../../assets/login/mid_day_meal-round.jpg';
import meal from '../../assets/login/mid_day_meal.jpg';
import schoolRound from '../../assets/login/village_school-round.jpg';
import school from '../../assets/login/village_school.jpg';
import rebuildRound from '../../assets/login/village_school_constr-round.jpg';
import rebuild from '../../assets/login/village_school_constr.jpg';

/**
 * The sign-in page pictures, in the order they play. Each has a square crop for the circle ("round") and the full
 * photo ("full", with its width / height as "ratio") for the final frame that shows them all together.
 * To change them, replace the files in src/assets/login (keep a square "-round" crop with the subject in the middle).
 */
export const LOGIN_PICTURES = [
  { round: schoolRound, full: school, ratio: 694 / 392, caption: 'Village schools',
    alt: 'Children in pink uniforms lined up outside a village school, holding a banner' },
  { round: mealRound, full: meal, ratio: 738 / 414, caption: 'Mid-day meals',
    alt: 'Children in blue uniforms seated in a school corridor, being served a mid-day meal' },
  { round: girlsRound, full: girls, ratio: 547 / 365, caption: "Girls' education",
    alt: 'Three schoolgirls in blue uniforms smiling and holding a slate that says Education' },
  { round: booksRound, full: books, ratio: 425 / 470, caption: 'Books and uniforms',
    alt: 'A smiling girl in a green headscarf writing at her desk, on a poster asking for donations for books and uniforms' },
  { round: rebuildRound, full: rebuild, ratio: 638 / 480, caption: 'Rebuilding schools',
    alt: 'A government higher secondary school building before its renovation' },
];
type Picture = (typeof LOGIN_PICTURES)[number];

type Phase = 'arrive' | 'hold' | 'burst';
const DURATION: Record<Phase, number> = { arrive: 1400, hold: 2600, burst: 900 };   // milliseconds
const GRID = 6;                                                                       // the burst: 6 x 6 pieces
let playedOnce = false;     // after the first time in a visit, the sign-in page opens on the final frame

const calm = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * Plays once: each picture comes from far away into a circle, catches a white shine, whitens and bursts apart, and
 * the next one follows. After the last, all of them appear together in one frame that shines like a diamond.
 */
export function LoginPictures({ pictures = LOGIN_PICTURES }: { pictures?: Picture[] }) {
  const reduced = useMemo(calm, []);
  const [index, setIndex] = useState(() => (reduced || playedOnce ? pictures.length : 0));
  const [phase, setPhase] = useState<Phase>('arrive');
  const done = index >= pictures.length;

  useEffect(() => {
    if (done) { playedOnce = true; return; }
    const t = window.setTimeout(() => {
      if (phase === 'arrive') setPhase('hold');
      else if (phase === 'hold') setPhase('burst');
      else { setIndex((i) => i + 1); setPhase('arrive'); }
    }, DURATION[phase]);
    return () => window.clearTimeout(t);
  }, [phase, index, done]);

  const current = pictures[Math.min(index, pictures.length - 1)];
  const pieces = useMemo(() => burst(current.round), [current.round]);
  if (done) return <AllPictures pictures={pictures} />;
  return (
    <figure className="login-pictures">
      <div className="lp-stage">
        {phase !== 'burst' ? (
          <div key={index} className={`lp-picture ${phase}`}>
            <img src={current.round} alt={current.alt} />
            <span className="lp-sheen" aria-hidden="true" />
            <span className="lp-shade" aria-hidden="true" />
          </div>
        ) : (
          <div className="lp-burst" aria-hidden="true">{pieces}</div>
        )}
      </div>
      <figcaption key={index} className="lp-caption">{current.caption}</figcaption>
    </figure>
  );
}

/** All the pictures at their own proportions in one frame: the two widest on top, the other three below. */
function AllPictures({ pictures }: { pictures: Picture[] }) {
  const rows = pictures.length === 5 ? [[1, 2], [3, 0, 4]] : [pictures.map((_, i) => i)];
  let n = 0;
  return (
    <figure className="login-pictures lp-finale">
      <div className="lp-diamond">
        <div className="lp-mosaic">
          {rows.map((row, r) => (
            <div key={r} className="lp-row">
              {row.map((i) => {
                const p = pictures[i];
                return (
                  <div key={i} className="lp-tile" style={{ '--r': p.ratio, animationDelay: `${120 * n++}ms` } as React.CSSProperties}>
                    <img src={p.full} alt={p.alt} />
                  </div>
                );
              })}
            </div>
          ))}
        </div>
        {[1, 2, 3, 4, 5, 6].map((g) => <span key={g} className={`lp-glint g${g}`} aria-hidden="true" />)}
      </div>
      <figcaption className="lp-caption">Schools and NGOs, matched with donors</figcaption>
    </figure>
  );
}

/** The circle cut into GRID x GRID pieces, each flying outward and spinning as it fades. */
function burst(src: string) {
  const step = 100 / GRID, at = (i: number) => (i * 100) / (GRID - 1);
  const pieces = [];
  for (let row = 0; row < GRID; row++) {
    for (let col = 0; col < GRID; col++) {
      const x = col + 0.5 - GRID / 2, y = row + 0.5 - GRID / 2;      // from the centre, in pieces
      const length = Math.hypot(x, y) || 1;
      const reach = 110 + Math.random() * 90;                         // pixels
      const style = {
        left: `${col * step}%`, top: `${row * step}%`, width: `${step}%`, height: `${step}%`,
        backgroundImage: `linear-gradient(rgba(255,255,255,.55), rgba(255,255,255,.55)), url("${src}")`,
        backgroundSize: `100% 100%, ${GRID * 100}% ${GRID * 100}%`,
        backgroundPosition: `0 0, ${at(col)}% ${at(row)}%`,
        maskPosition: `${at(col)}% ${at(row)}%`,
        WebkitMaskPosition: `${at(col)}% ${at(row)}%`,
        '--dx': `${(x / length) * reach}px`, '--dy': `${(y / length) * reach}px`,
        '--rot': `${Math.round((Math.random() - 0.5) * 360)}deg`, animationDelay: `${Math.round(Math.random() * 120)}ms`,
      } as React.CSSProperties;
      pieces.push(<span key={`${row}-${col}`} className="lp-piece" style={style} />);
    }
  }
  return pieces;
}
