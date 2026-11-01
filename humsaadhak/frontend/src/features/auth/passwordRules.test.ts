import { describe, expect, it } from 'vitest';
import { passwordProblems } from './passwordRules';

describe('password rules', () => {
  it('accepts a long mixed password', () => expect(passwordProblems('river-lamp-42-mango')).toEqual([]));
  it('explains every problem', () => {
    expect(passwordProblems('short')).toEqual(['Use at least 12 characters.', 'Add a number or a symbol.']);
    expect(passwordProblems('meera.iyer-2026!', 'meera.iyer@asha.org')).toEqual(['Do not use your email name.']);
  });
});
