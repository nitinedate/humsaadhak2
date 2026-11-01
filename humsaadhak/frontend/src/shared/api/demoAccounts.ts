import type { Role } from './types';

/** Sign-in shortcuts shown only in demo mode (no data, so it is safe in production bundles). */
export const DEMO_ACCOUNTS: { key: string; role: Role; email: string }[] = [
  { key: 'manager', role: 'MANAGER', email: 'manager@demo.humsaadhak.org' },
  { key: 'associate', role: 'ASSOCIATE', email: 'associate@demo.humsaadhak.org' },
  { key: 'donor', role: 'DONOR_CORPORATE', email: 'donor@demo.humsaadhak.org' },
  { key: 'individual', role: 'DONOR_INDIVIDUAL', email: 'individual@demo.humsaadhak.org' },
  { key: 'school', role: 'SCHOOL', email: 'school@demo.humsaadhak.org' },
  { key: 'ngo', role: 'NGO', email: 'ngo@demo.humsaadhak.org' },
  { key: 'admin', role: 'SUPER_ADMIN', email: 'admin@demo.humsaadhak.org' },
];
