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

## Git Workflow
- `main` is the stable branch — never commit directly to it.
- For each phase, create a dedicated branch named `phase-N/short-description` (e.g. `phase-3/rich-content`).
- All commits for that phase go on the phase branch.
- When the phase is approved by the user, open a PR and merge the phase branch into `main`.

## Deployment Workflow

**STRICT ORDER — never push to git before the user confirms it works on the server.**

1. **Deploy to server first via scp** — copy changed files directly to `/root/notes` on `192.168.1.50`, then rebuild Docker:
   ```bash
   scp -r <local-file-or-dir> root@192.168.1.50:/root/notes/<path>
   ssh root@192.168.1.50 "cd /root/notes && docker compose up -d --build"
   ```
2. **Wait for user confirmation** — do not proceed until the user says it works at `https://notes.gregorymaingret.fr`.
3. **Only after confirmation**: commit, push to the phase branch, create PR, wait for CI, merge to `main`.
4. After merge, sync the server from `main` (no rebuild needed — Docker image already has the code from step 1):
   ```bash
   ssh root@192.168.1.50 "cd /root/notes && git checkout main && git pull origin main"
   ```
