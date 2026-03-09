import { describe, it, expect } from 'vitest';
import { shouldShowEditMode } from '../utils/bulletViewMode';

describe('bulletViewMode', () => {
  it('returns true when isEditing is true', () => {
    expect(shouldShowEditMode(true)).toBe(true);
  });
  it('returns false when isEditing is false', () => {
    expect(shouldShowEditMode(false)).toBe(false);
  });
});
