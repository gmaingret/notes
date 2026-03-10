import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.use({ breaks: false, gfm: true });

function decodeHtmlEntities(text: string): string {
  const ta = document.createElement('textarea');
  ta.innerHTML = text;
  return ta.value;
}

export function renderBulletMarkdown(content: string): string {
  // Decode any HTML entities stored literally in content (e.g. &#39; → ')
  // before passing to marked, so they aren't double-encoded on render.
  const decoded = decodeHtmlEntities(content);
  // parseInline avoids wrapping <p> tags — CRITICAL for inline bullet display
  const html = marked.parseInline(decoded) as string;
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['strong', 'em', 'del', 'a', 'img', 'code', 'span'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'data-chip-type', 'data-chip-value', 'target'],
  });
}
