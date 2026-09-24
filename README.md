# Rescuer App

Mobile rescuer application repository for Nepal Scouts rescue operations.

## Local application

```bash
npm install
npm run dev
```

The rescuer UI is a mobile-first React client. See [docs/RESCUER_APP_ARCHITECTURE.md](docs/RESCUER_APP_ARCHITECTURE.md) for the state model, offline/SOS architecture, backend gaps, and native Android path. Copy `.env.example` to `.env.local` only when connecting an approved public Edge Function endpoint. Never add service-role credentials.

## Repository policy
- `main` is kept as the stable mobile-app baseline.
- DMT rescue backend/shared files are imported through dated feature branches before merging.
- Do not commit passwords, service-role keys, device tokens, or other secrets.

Bootstrapped from the DMT rescue recovery project on 2026-08-31.
