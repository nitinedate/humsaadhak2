/** Mirrors PasswordPolicy.java: at least 12 characters, not only letters, not the email's name part. */
export function passwordProblems(password: string, email = ''): string[] {
  const p: string[] = [];
  if (password.length < 12) p.push('Use at least 12 characters.');
  if (/^\p{L}*$/u.test(password)) p.push('Add a number or a symbol.');
  const name = email.split('@')[0]!.toLowerCase();
  if (email.includes('@') && name.length >= 3 && password.toLowerCase().includes(name)) p.push('Do not use your email name.');
  return p;
}
