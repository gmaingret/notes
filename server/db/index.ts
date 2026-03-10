import 'dotenv/config';
import { drizzle } from 'drizzle-orm/node-postgres';
import { PgDatabase } from 'drizzle-orm/pg-core';
import { Pool } from 'pg';
import * as schema from './schema.js';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

export const db = drizzle(pool, { schema });
// DB accepts both NodePgDatabase and PgTransaction (transaction callbacks receive the latter)
export type DB = PgDatabase<any, typeof schema>;
