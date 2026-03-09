# CLAUDE.md - Operational Instructions

## Project Repository
- **GitHub**: `https://github.com/gmaingret/notes.git`
- All code changes must be committed and pushed to this repository.

## Infrastructure

### Application Server
- **Host**: `ssh root@192.168.1.50`
- **Project folder**: `/root/notes`
- **App port**: `3000`
- The application runs inside Docker on this server.

### Reverse Proxy
- **Nginx host**: `192.168.1.206`
- **Public URL**: `https://notes.gregorymaingret.fr`
- Nginx proxies to `192.168.1.50:3000`.

## Environment Configuration
- A `.env.example` file must be provided at the project root with all required variables (Google OAuth credentials, database credentials, JWT secret, etc.).
- The actual `.env` file with real credentials will be provided separately and must never be committed to the repository.

## Testing
- Once authentication is implemented, Claude must create its own test account on the running server using email/password registration.
- Claude uses this account to perform end-to-end testing of features.
- Google SSO cannot be tested by Claude — only email/password auth is used for automated testing.

## Deployment Workflow
1. Develop and commit code to the GitHub repository.
2. On the application server (`192.168.1.50`), pull changes and rebuild/restart Docker containers.
3. Verify the application is accessible at `https://notes.gregorymaingret.fr`.
