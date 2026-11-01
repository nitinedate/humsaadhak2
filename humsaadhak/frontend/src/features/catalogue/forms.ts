import type { Periodicity } from '../../shared/api/types';

/** Same limits as the server (CatalogueController): checked here first so people see problems before saving. */
export const MIN_DESCRIPTION = 40;

export interface CatalogueDraft {
  name: string; description: string; sector: string; amount: string; periodicity: Periodicity; kind?: 'CAUSE' | 'PROJECT';
  /** Staff create records on behalf of an organisation (sent as donorOrgId / orgId); null for donors and beneficiaries. */
  orgId?: number | null;
}

export function validateDraft(d: CatalogueDraft, amountRequired: boolean, orgRequired = false): Record<string, string> {
  const errors: Record<string, string> = {};
  if (orgRequired && !d.orgId) errors.org = 'Choose the organisation this belongs to.';
  if (!d.name.trim()) errors.name = 'Give it a short name.';
  if (d.description.trim().length < MIN_DESCRIPTION) errors.description = `Write at least ${MIN_DESCRIPTION} characters: who benefits, where, and what the money buys.`;
  if (!d.sector) errors.sector = 'Choose a sector.';
  const amount = parseRupees(d.amount);
  if (amountRequired ? !(amount && amount > 0) : d.amount.trim() !== '' && !(amount && amount > 0)) errors.amount = 'Enter an amount above zero.';
  return errors;
}

/** "4,50,000" or "₹ 450000" -> 450000; null when not a number. */
export function parseRupees(text: string): number | null {
  const clean = text.replace(/[₹,\s]/g, '');
  if (!clean) return null;
  const n = Number(clean);
  return Number.isFinite(n) ? n : null;
}
