import { describe, it, expect } from 'vitest';
import { validatePasswordPolicy } from '../src/services/passwordPolicy.js';

describe('validatePasswordPolicy', () => {
  // Length check
  it('returns error for passwords shorter than 8 characters', () => {
    const result = validatePasswordPolicy('short');
    expect(result).toBeTruthy();
    expect(result).toContain('8');
  });

  // Character diversity
  it('returns error when no uppercase letter present', () => {
    const result = validatePasswordPolicy('alllowercase1');
    expect(result).toBeTruthy();
    expect(result).toContain('uppercase');
  });

  it('returns error when no lowercase letter present', () => {
    const result = validatePasswordPolicy('ALLUPPERCASE1');
    expect(result).toBeTruthy();
    expect(result).toContain('lowercase');
  });

  it('returns error when no digit present', () => {
    const result = validatePasswordPolicy('NoDigitsHere!');
    expect(result).toBeTruthy();
    expect(result).toContain('digit');
  });

  // Common password checks
  it('returns error for commonly breached password "Password1"', () => {
    const result = validatePasswordPolicy('Password1');
    expect(result).toBeTruthy();
    expect(result).toContain('common');
  });

  it('returns error for commonly breached password "password123"', () => {
    const result = validatePasswordPolicy('password123');
    expect(result).toBeTruthy();
    expect(result).toContain('common');
  });

  it('returns error for commonly breached password "Qwerty123"', () => {
    const result = validatePasswordPolicy('Qwerty123');
    expect(result).toBeTruthy();
    expect(result).toContain('common');
  });

  // Valid passwords
  it('returns null for valid password "V4l!dPass"', () => {
    expect(validatePasswordPolicy('V4l!dPass')).toBeNull();
  });

  it('returns null for valid password "C0mpl3x!Pass"', () => {
    expect(validatePasswordPolicy('C0mpl3x!Pass')).toBeNull();
  });
});
