import { useEffect, useMemo, useState } from 'react';

const SPEED = 16;          // milliseconds per character: fast
let typedOnce = false;     // type once per visit, not again when moving between sign-in, sign-up and password pages

const calm = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * Types the given texts one after another, character by character. The full text keeps its place from the start
 * (no jumping layout) and is what screen readers read; the typed copy is decoration.
 */
export function useTyping(texts: string[]) {
  const total = texts.reduce((n, t) => n + t.length, 0);
  const instant = useMemo(() => typedOnce || calm(), []);
  const [count, setCount] = useState(instant ? total : 0);
  useEffect(() => {
    if (count >= total) { typedOnce = true; return; }
    const t = window.setTimeout(() => setCount((c) => c + 1), SPEED);
    return () => window.clearTimeout(t);
  }, [count, total]);
  let left = count, caretPlaced = count >= total;     // the caret sits in the first text that is not finished
  return texts.map((text) => {
    const typed = Math.max(0, Math.min(left, text.length));
    left -= text.length;
    const typing = !caretPlaced && typed < text.length;
    if (typing) caretPlaced = true;
    return { shown: text.slice(0, typed), typing };
  });
}

export function Typed({ text, shown, typing }: { text: string; shown: string; typing: boolean }) {
  return (
    <span className="typed">
      <span className="typed-ghost" aria-hidden="true">{text}</span>
      <span className="typed-live" aria-hidden="true">{shown}{typing && <span className="typed-caret" />}</span>
      <span className="visually-hidden">{text}</span>
    </span>
  );
}
