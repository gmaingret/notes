/**
 * Password policy validation for registration.
 * Enforces character diversity and rejects commonly breached passwords.
 */

// A curated list of commonly breached passwords, checked case-insensitively.
const COMMON_PASSWORDS = new Set([
  'password',
  'password1',
  'password123',
  'password1234',
  '123456',
  '1234567',
  '12345678',
  '123456789',
  '1234567890',
  'qwerty',
  'qwerty123',
  'qwerty1',
  'qwerty12',
  'letmein',
  'letmein1',
  'welcome',
  'welcome1',
  'welcome123',
  'admin',
  'admin123',
  'admin1234',
  'adminadmin',
  'iloveyou',
  'sunshine',
  'monkey',
  'master',
  'dragon',
  'batman',
  'superman',
  'starwars',
  'football',
  'baseball',
  'basketball',
  'soccer',
  'charlie',
  'donald',
  'michael',
  'jessica',
  'jennifer',
  'trustno1',
  'pass',
  'pass1',
  'pass123',
  'passw0rd',
  'p@ssword',
  'p@ssw0rd',
  '111111',
  '111111111',
  '000000',
  'aaaaaa',
  'abcdef',
  'abcdefg',
  'abcdefgh',
  'abc123',
  'abc1234',
  'test',
  'test123',
  'test1234',
  'login',
  'login123',
  'secret',
  'secret1',
  'secret123',
  'hello',
  'hello123',
  'hello1234',
  'changeme',
  'changeme1',
  'temp',
  'temp123',
  'guest',
  'guest123',
  'user',
  'user123',
  'root',
  'root123',
  'toor',
  'shadow',
  'shadow1',
  'shadow123',
  'mustang',
  'princess',
  'access',
  'access1',
  'access123',
  'matrix',
  'michael1',
  'daniel',
  'daniel1',
  'andrew',
  'joshua',
  'thomas',
  'george',
  'computer',
  'computer1',
  'internet',
  'internet1',
  'summer',
  'summer1',
  'summer123',
  'winter',
  'winter1',
  'spring',
  'spring1',
  'passpass',
  'pass@word',
  'passw0rd1',
]);

/**
 * Validates a password against the application's security policy.
 *
 * Rules (all three must be satisfied):
 * 1. Minimum 8 characters
 * 2. At least one uppercase letter
 * 3. At least one lowercase letter
 * 4. At least one digit
 * 5. Not in the common passwords list (case-insensitive)
 *
 * @param password - The plaintext password to validate
 * @returns An error message string if invalid, or null if the password is acceptable
 */
export function validatePasswordPolicy(password: string): string | null {
  if (password.length < 8) {
    return 'Password must be at least 8 characters';
  }

  // Check common passwords before character rules — gives a better error message
  // for well-known breached passwords (e.g. "password123") that also happen to
  // fail a character rule.
  if (COMMON_PASSWORDS.has(password.toLowerCase())) {
    return 'This password is too common. Please choose a more unique password.';
  }

  if (!/[A-Z]/.test(password)) {
    return 'Password must include at least one uppercase letter';
  }

  if (!/[a-z]/.test(password)) {
    return 'Password must include at least one lowercase letter';
  }

  if (!/[0-9]/.test(password)) {
    return 'Password must include at least one digit';
  }

  return null;
}
