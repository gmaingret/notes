import 'dotenv/config';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { db } from './index.js';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

await migrate(db, { migrationsFolder: path.join(__dirname, 'migrations') });
console.log('Migrations applied');
process.exit(0);
