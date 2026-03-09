import passport from 'passport';
import { Strategy as JwtStrategy, ExtractJwt } from 'passport-jwt';
import { Strategy as LocalStrategy } from 'passport-local';
import { Strategy as GoogleStrategy } from 'passport-google-oauth20';
import { db } from '../../db/index.js';
import { users } from '../../db/schema.js';
import { eq } from 'drizzle-orm';
import bcrypt from 'bcryptjs';
import type { Request, Response, NextFunction } from 'express';

export function configurePassport() {
  // JWT strategy — reads Bearer token from Authorization header
  passport.use(
    new JwtStrategy(
      {
        jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
        secretOrKey: process.env.JWT_SECRET!,
      },
      async (payload, done) => {
        try {
          const user = await db.query.users.findFirst({
            where: eq(users.id, payload.sub),
          });
          return user ? done(null, user) : done(null, false);
        } catch (err) {
          return done(err, false);
        }
      }
    )
  );

  // Local strategy — email/password
  passport.use(
    new LocalStrategy(
      { usernameField: 'email' },
      async (email, password, done) => {
        try {
          const user = await db.query.users.findFirst({
            where: eq(users.email, email),
          });
          if (!user || !user.passwordHash) return done(null, false);
          const valid = await bcrypt.compare(password, user.passwordHash);
          return valid ? done(null, user) : done(null, false);
        } catch (err) {
          return done(err, false);
        }
      }
    )
  );

  // Google OAuth strategy — configured if env vars present
  if (process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET) {
    passport.use(
      new GoogleStrategy(
        {
          clientID: process.env.GOOGLE_CLIENT_ID,
          clientSecret: process.env.GOOGLE_CLIENT_SECRET,
          callbackURL: process.env.GOOGLE_CALLBACK_URL!,
        },
        async (_accessToken, _refreshToken, profile, done) => {
          // _accessToken intentionally unused — never store Google's access token
          try {
            const email = profile.emails?.[0]?.value;
            const googleId = profile.id;
            if (!email) return done(new Error('No email from Google profile'));

            let user = await db.query.users.findFirst({
              where: eq(users.googleId, googleId),
            });

            if (!user) {
              // Try linking by email (user may have registered with email/password first)
              user = await db.query.users.findFirst({
                where: eq(users.email, email),
              });
              if (user) {
                // Link Google account to existing user
                await db.update(users)
                  .set({ googleId, updatedAt: new Date() })
                  .where(eq(users.id, user.id));
              } else {
                // New user via Google
                const [newUser] = await db.insert(users)
                  .values({ email, googleId })
                  .returning();
                user = newUser;
              }
            }

            done(null, user);
          } catch (err) {
            done(err as Error);
          }
        }
      )
    );
  }
}

// Middleware: requires valid JWT — returns 401 if missing/invalid
export function requireAuth(req: Request, res: Response, next: NextFunction): void {
  passport.authenticate('jwt', { session: false }, (err: Error | null, user: Express.User | false) => {
    if (err) return next(err);
    if (!user) {
      res.status(401).json({ error: 'Unauthorized' });
      return;
    }
    req.user = user;
    next();
  })(req, res, next);
}
