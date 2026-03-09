import { describe, it, expect } from 'vitest';
import { renderWithChips } from '../utils/chips';

describe('renderWithChips', () => {
  it('wraps #tag in chip span', () => {
    const result = renderWithChips('buy #milk today');
    expect(result).toContain('data-chip-type="tag"');
    expect(result).toContain('data-chip-value="milk"');
  });
  it('wraps @mention in chip span', () => {
    const result = renderWithChips('ping @alice about this');
    expect(result).toContain('data-chip-type="mention"');
    expect(result).toContain('data-chip-value="alice"');
  });
  it('wraps !![date] in chip span', () => {
    const result = renderWithChips('due !![2026-01-15]');
    expect(result).toContain('data-chip-type="date"');
    expect(result).toContain('data-chip-value="2026-01-15"');
  });
  it('does not chip-ify # inside href attributes', () => {
    const result = renderWithChips('<a href="https://example.com/#section">link</a>');
    // The href must not become a chip
    expect(result).not.toContain('data-chip-type="tag"');
  });
});
