import express from 'express';
import cookieParser from 'cookie-parser';

// Factory pattern: each test suite calls createTestApp() to get a fresh instance.
// Routes are added by each test file via the returned app.
export function createTestApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  return app;
}
