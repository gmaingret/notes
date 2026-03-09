import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.use({ breaks: false, gfm: true });

export function renderBulletMarkdown(content: string): string {
  // parseInline avoids wrapping <p> tags — CRITICAL for inline bullet display
  const html = marked.parseInline(content) as string;
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['strong', 'em', 'del', 'a', 'img', 'code', 'span'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'data-chip-type', 'data-chip-value', 'target'],
  });
}
