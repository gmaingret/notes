import { describe, it } from 'vitest';

describe('AUTH-01: Register with email/password', () => {
  it.todo('POST /api/auth/register with valid email+password returns 201 + accessToken');
  it.todo('POST /api/auth/register with duplicate email returns 409 with field:email');
  it.todo('POST /api/auth/register with invalid email returns 400');
  it.todo('POST /api/auth/register with password < 8 chars returns 400');
});

describe('AUTH-02: Login with email/password', () => {
  it.todo('POST /api/auth/login with correct credentials returns 200 + accessToken + sets refreshToken cookie');
  it.todo('POST /api/auth/login with wrong password returns 401');
  it.todo('POST /api/auth/login with unknown email returns 401');
  it.todo('POST /api/auth/refresh with valid cookie issues new accessToken');
  it.todo('POST /api/auth/refresh with missing cookie returns 401');
});

describe('AUTH-03: Google OAuth (server-side)', () => {
  it.todo('OAuth callback with valid profile finds existing user by googleId');
  it.todo('OAuth callback creates new user when email not found');
  it.todo('OAuth callback links googleId to existing email/password account');
  it.todo('OAuth callback does not store Google access token in DB');
});

describe('AUTH-04: Logout', () => {
  it.todo('POST /api/auth/logout clears the refreshToken cookie');
  it.todo('POST /api/auth/logout returns 200 even if no cookie present');
});

describe('AUTH-05: Inbox document on first login', () => {
  it.todo('New user created via register gets exactly one document titled "Inbox"');
  it.todo('Second register with same user does not create a second Inbox (idempotent)');
  it.todo('Google OAuth new user also gets Inbox document');
});
