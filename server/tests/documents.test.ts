import { describe, it } from 'vitest';

describe('DOC-01: Create document', () => {
  it.todo('POST /api/documents creates document with correct userId and default position');
  it.todo('POST /api/documents requires authentication (401 without token)');
});

describe('DOC-02: Rename document', () => {
  it.todo('PATCH /api/documents/:id updates title field');
  it.todo("PATCH /api/documents/:id with another user's document returns 403");
});

describe('DOC-03: Delete document', () => {
  it.todo('DELETE /api/documents/:id removes document from DB');
  it.todo('DELETE /api/documents/:id cascades to bullets');
  it.todo("DELETE /api/documents/:id with another user's document returns 403");
});

describe('DOC-04: Reorder documents (FLOAT8 midpoint)', () => {
  it.todo('PATCH /api/documents/:id/position computes midpoint between neighbors');
  it.todo('Position is computed server-side — client does not pass the final float value');
});

describe('DOC-05: Navigate between documents (last_opened_at)', () => {
  it.todo('POST /api/documents/:id/open updates last_opened_at timestamp');
  it.todo('GET /api/documents returns documents sorted by position asc');
});

describe('DOC-06: Export single document as Markdown', () => {
  it.todo('GET /api/documents/:id/export returns Content-Type text/markdown');
  it.todo('Response has Content-Disposition attachment with .md filename');
  it.todo('Bullets rendered as indented dashes (2-space per level)');
});

describe('DOC-07: Export all documents as ZIP', () => {
  it.todo('GET /api/documents/export-all returns Content-Type application/zip');
  it.todo('ZIP contains one .md file per document');
  it.todo('ZIP is streamed (not buffered) via archiver');
});
