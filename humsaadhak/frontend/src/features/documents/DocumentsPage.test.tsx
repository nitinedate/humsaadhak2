import { describe, expect, it } from 'vitest';
import { organisationTypeOf } from './DocumentsPage';

describe('which compulsory documents apply', () => {
  it('follows the organisation type of the account (as AccountService.organisationTypeFor on the server)', () => {
    expect(organisationTypeOf('SCHOOL')).toBe('SCHOOL');
    expect(organisationTypeOf('NGO')).toBe('NGO');
    expect(organisationTypeOf('DONOR_CORPORATE')).toBe('DONOR_CORPORATE');
    expect(organisationTypeOf('DONOR_INDIVIDUAL')).toBe('DONOR_INDIVIDUAL');
    expect(organisationTypeOf('MANAGER')).toBeNull();        // staff: no organisation, no compulsory list
    expect(organisationTypeOf(undefined)).toBeNull();
  });
});
