/** ₹ with Indian digit grouping: 1240000 -> ₹12,40,000. */
export function inr(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const v = Math.round(value);
  const s = Math.abs(v).toString();
  const last3 = s.slice(-3);
  const rest = s.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
  return `${v < 0 ? '-' : ''}₹${rest ? `${rest},${last3}` : last3}`;
}

/** Compact lakh form for chart labels: 410000 -> ₹4.1 L. */
export function lakh(value: number): string {
  return `₹${(value / 100000).toFixed(1)} L`;
}

export const PERIODICITY_LABEL: Record<string, string> = {
  ONE_TIME: 'One-time', MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly',
};

export const ROLE_LABEL: Record<string, string> = {
  SUPER_ADMIN: 'Super admin', MANAGER: 'Manager', ASSOCIATE: 'Operations associate', DONOR_INDIVIDUAL: 'Individual donor',
  DONOR_CORPORATE: 'Corporate donor', SCHOOL: 'Direct beneficiary (school)', NGO: 'Indirect beneficiary (NGO)', GUEST: 'Guest',
};

export function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
}

/** Human "waited" text from an ISO timestamp. */
export function waited(iso: string, now: Date = new Date()): string {
  const mins = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins} min`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'}`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? '' : 's'}`;
}

/** Parses the candidate explanation JSON produced by the search service. */
/** How to read a match percentage (the engine shows candidates from 50% up). */
export function matchLabel(score: number): string {
  return score >= 85 ? 'Strong match' : score >= 70 ? 'Good match' : score >= 50 ? 'Possible match' : 'Weak match';
}

export function whyChips(json: string): string[] {
  try {
    const e = JSON.parse(json) as Record<string, unknown>;
    const chips: string[] = [];
    if (e.sameSector === true || e.sameSector === 'true') chips.push('Same sector');
    if (typeof e.sharedTerms === 'string' && e.sharedTerms) e.sharedTerms.split(',').map((t) => t.trim()).filter(Boolean).slice(0, 3).forEach((t) => chips.push(`“${t}”`));
    return chips;
  } catch {
    return [];
  }
}

export function bestSentence(json: string): string | undefined {
  try { return (JSON.parse(json) as Record<string, string>).bestSentence; } catch { return undefined; }
}
