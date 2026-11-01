import { describe, expect, it } from 'vitest';
import { parseRupees, validateDraft } from './forms';

const ok = { name: 'Tablet learning', description: 'Shared tablets with offline lessons for six rural schools.', sector: 'Education', amount: '3,80,000', periodicity: 'ONE_TIME' as const };

describe('catalogue form rules', () => {
  it('parses rupees as people type them', () => {
    expect(parseRupees('4,50,000')).toBe(450000);
    expect(parseRupees('₹ 1200')).toBe(1200);
    expect(parseRupees('abc')).toBeNull();
    expect(parseRupees('')).toBeNull();
  });
  it('accepts a complete draft', () => expect(validateDraft(ok, true)).toEqual({}));
  it('names every problem', () => {
    expect(Object.keys(validateDraft({ ...ok, name: ' ', description: 'short', sector: '', amount: '0' }, true)))
      .toEqual(['name', 'description', 'sector', 'amount']);
  });
  it('asks staff which organisation a record belongs to', () => {
    expect(validateDraft(ok, true, true)).toEqual({ org: 'Choose the organisation this belongs to.' });
    expect(validateDraft({ ...ok, orgId: 31 }, true, true)).toEqual({});
  });
  it('lets a purpose leave the amount blank', () => {
    expect(validateDraft({ ...ok, amount: '' }, false)).toEqual({});
    expect(validateDraft({ ...ok, amount: '-1' }, false)).toHaveProperty('amount');
  });
});
