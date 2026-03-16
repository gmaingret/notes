import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import morgan from 'morgan';
import cookieParser from 'cookie-parser';
import rateLimit from 'express-rate-limit';
import passport from 'passport';
import { configurePassport } from './middleware/auth.js';

export function createApp() {
  const app = express();

  // Security headers — allow blob: for images (needed for attachment preview via URL.createObjectURL)
  app.use(helmet({
    contentSecurityPolicy: {
      directives: {
        ...helmet.contentSecurityPolicy.getDefaultDirectives(),
        'img-src': ["'self'", 'data:', 'blob:'],
      },
    },
  }));

  // CORS — dev: Vite on :5173; production: same-origin (Nginx serves both)
  app.use(cors({
    origin: process.env.NODE_ENV === 'development' ? 'http://localhost:5173' : false,
    credentials: true,
  }));

  // Logging, body parsing, cookies
  app.use(morgan('dev'));
  app.use(express.json());
  app.use(cookieParser());

  // Passport (no sessions — JWT only)
  configurePassport();
  app.use(passport.initialize());

  // Rate limit login/register (brute-force protection)
  const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 20,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use('/api/auth/login', authLimiter);
  app.use('/api/auth/register', authLimiter);
  app.use('/api/auth/google/token', authLimiter);

  // Separate higher limit for refresh (called on every app cold start)
  const refreshLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 60,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use('/api/auth/refresh', refreshLimiter);

  // Rate limit data endpoints (abuse / exfiltration protection)
  const dataLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 600,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use('/api/documents', dataLimiter);
  app.use('/api/bullets', dataLimiter);
  app.use('/api/undo', dataLimiter);
  app.use('/api/bookmarks', dataLimiter);
  app.use('/api/tags', dataLimiter);
  app.use('/api/search', dataLimiter);
  app.use('/api/attachments', dataLimiter);

  // CSRF note: No CSRF token middleware needed. All data endpoints require
  // Bearer token auth via Authorization header, which browsers never attach
  // automatically to cross-origin requests. See REQUIREMENTS.md API-02.

  // Health check
  app.get('/health', (_req, res) => res.json({ status: 'ok' }));

  // Routes mounted in index.ts after this factory
  return app;
}
