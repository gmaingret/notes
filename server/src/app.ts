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

  // Rate limit auth endpoints
  const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 20,
    standardHeaders: true,
    legacyHeaders: false,
  });
  app.use('/api/auth', authLimiter);

  // Health check
  app.get('/health', (_req, res) => res.json({ status: 'ok' }));

  // Routes mounted in index.ts after this factory
  return app;
}
