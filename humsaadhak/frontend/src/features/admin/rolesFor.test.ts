import { describe, expect, it } from 'vitest';
import type { AccessRole, Role } from '../../shared/api/types';
import { rolesFor } from './UsersPage';

const role = (code: string, kind: Role): AccessRole => ({ code, name: code, description: null, kind, kindLabel: '', system: true, users: 0, permissions: [] });
const roles = [role('SUPER_ADMIN', 'SUPER_ADMIN'), role('MANAGER', 'MANAGER'), role('COORD', 'ASSOCIATE'), role('NGO', 'NGO'),
  role('NGO_LEAD', 'NGO'), role('DONOR_CORPORATE', 'DONOR_CORPORATE'), role('GUEST', 'GUEST')];

describe('roles offered on the Users screen', () => {
  it('staff get staff roles; others get roles of their account type; never Guest', () => {
    expect(rolesFor({ role: 'ASSOCIATE' }, roles).map((r) => r.code)).toEqual(['SUPER_ADMIN', 'MANAGER', 'COORD']);
    expect(rolesFor({ role: 'NGO' }, roles).map((r) => r.code)).toEqual(['NGO', 'NGO_LEAD']);
    expect(rolesFor({ role: 'DONOR_CORPORATE' }, roles).map((r) => r.code)).toEqual(['DONOR_CORPORATE']);
  });
});
