import { describe, it, expect } from 'vitest';
import { renderBulletMarkdown } from '../utils/markdown';

describe('renderBulletMarkdown', () => {
  it('renders **bold** as <strong>', () => {
    expect(renderBulletMarkdown('**bold**')).toContain('<strong>');
  });
  it('renders *italic* as <em>', () => {
    expect(renderBulletMarkdown('*italic*')).toContain('<em>');
  });
  it('renders ~~strike~~ as <del>', () => {
    expect(renderBulletMarkdown('~~strike~~')).toContain('<del>');
  });
  it('renders [link](url) as <a', () => {
    expect(renderBulletMarkdown('[link](https://example.com)')).toContain('<a');
  });
  it('does not wrap plain text in <p>', () => {
    const result = renderBulletMarkdown('hello');
    expect(result).not.toContain('<p>');
    expect(result).toContain('hello');
  });
});
