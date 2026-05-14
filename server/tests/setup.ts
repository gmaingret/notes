import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

// Route attachments writes to a writable temp dir so tests never touch /data
// (which only exists inside the production Docker container).
// Set BEFORE any test imports routes/attachments.ts, so multer.diskStorage's
// module-load mkdir hits a path the test user can create.
const tmpUploads = mkdtempSync(path.join(tmpdir(), 'notes-test-attachments-'));
process.env.UPLOAD_PATH = tmpUploads;
process.env.UPLOAD_MAX_SIZE_MB = process.env.UPLOAD_MAX_SIZE_MB ?? '100';
