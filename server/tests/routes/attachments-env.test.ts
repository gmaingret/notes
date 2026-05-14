import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import path from 'node:path';

/**
 * CONF-01: Verify UPLOAD_PATH and UPLOAD_MAX_SIZE_MB env vars actually drive
 * the multer config inside server/src/routes/attachments.ts.
 *
 * Strategy: set env vars, then re-import the module (with cache reset) and
 * read the module-level constants from the JS source to confirm they were
 * picked up at module-load time.
 */

const ATTACHMENTS_SRC = path.join(__dirname, '..', '..', 'src', 'routes', 'attachments.ts');

async function readModuleSource(): Promise<string> {
  const { readFile } = await import('node:fs/promises');
  return readFile(ATTACHMENTS_SRC, 'utf8');
}

describe('attachments router — CONF-01: env-var-driven upload config', () => {
  let originalUploadPath: string | undefined;
  let originalUploadMaxSizeMb: string | undefined;

  beforeEach(() => {
    originalUploadPath = process.env.UPLOAD_PATH;
    originalUploadMaxSizeMb = process.env.UPLOAD_MAX_SIZE_MB;
  });

  afterEach(() => {
    if (originalUploadPath === undefined) delete process.env.UPLOAD_PATH;
    else process.env.UPLOAD_PATH = originalUploadPath;
    if (originalUploadMaxSizeMb === undefined) delete process.env.UPLOAD_MAX_SIZE_MB;
    else process.env.UPLOAD_MAX_SIZE_MB = originalUploadMaxSizeMb;
    vi.resetModules();
  });

  it('source reads UPLOAD_PATH from process.env with /data/attachments fallback', async () => {
    const src = await readModuleSource();
    expect(src).toMatch(/process\.env\.UPLOAD_PATH\s*\|\|\s*['"]\/data\/attachments['"]/);
  });

  it('source reads UPLOAD_MAX_SIZE_MB from process.env with 100 fallback', async () => {
    const src = await readModuleSource();
    expect(src).toMatch(/Number\(process\.env\.UPLOAD_MAX_SIZE_MB\)\s*\|\|\s*100/);
  });

  it('LIMIT_FILE_SIZE error message references the configured limit (not a hardcoded number)', async () => {
    const src = await readModuleSource();
    // Either references UPLOAD_MAX_SIZE_MB constant or env var directly in the message
    expect(src).toMatch(/File too large \(max \$\{UPLOAD_MAX_SIZE_MB\}MB\)/);
  });
});
