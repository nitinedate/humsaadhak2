import { describe, expect, it } from 'vitest';
import { toggleColumn } from './BeneficiaryReports';

describe('custom report columns', () => {
  it('Project and Project (yet to be approved) exclude each other; other columns just toggle', () => {
    expect(toggleColumn(['NEED', 'DONOR_NAME'], 'PENDING_NEED')).toEqual(['DONOR_NAME', 'PENDING_NEED']);
    expect(toggleColumn(['PENDING_NEED'], 'NEED')).toEqual(['NEED']);
    expect(toggleColumn(['NEED', 'DONOR_NAME'], 'DONOR_NAME')).toEqual(['NEED']);
    expect(toggleColumn(['NEED'], 'APPROVED_DATE')).toEqual(['NEED', 'APPROVED_DATE']);
  });
});
