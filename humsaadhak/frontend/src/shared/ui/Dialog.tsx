import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/**
 * Centred modal dialog (as in the original project's chart details). Escape, the Close button or a click outside closes it;
 * focus moves into the dialog and returns to the chart afterwards; Tab stays inside while it is open.
 */
export function Dialog({ title, subtitle, onClose, children, actions }: {
  title: string; subtitle?: string; onClose: () => void; children: ReactNode; actions?: ReactNode;
}) {
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    box.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'Tab' && box.current) {
        const items = box.current.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (items.length === 0) return;
        const first = items[0]!, last = items[items.length - 1]!;
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = previousOverflow; opener?.focus(); };
  }, [onClose]);

  return createPortal(
    <div className="dialog-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title" tabIndex={-1} ref={box}>
        <div className="dialog-head">
          <div style={{ minWidth: 0 }}>
            <h2 id="dialog-title" className="title" style={{ fontSize: 22 }}>{title}</h2>
            {subtitle && <span className="small muted">{subtitle}</span>}
          </div>
          <div className="segmented" style={{ flexShrink: 0 }}>
            {actions}
            <button type="button" className="btn btn-outline-secondary btn-sm" onClick={onClose}>Close</button>
          </div>
        </div>
        <div className="dialog-body">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
