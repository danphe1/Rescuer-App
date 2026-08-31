# DMT mobile source import — 2026-08-31

Source repository: `danphe1/DMT_rescue`
Source branch reviewed: `feature/rescue-grade-preview-2026-08-31`
Target repository: `danphe1/Rescuer-App`

## Copied mobile-critical source
- `supabase/functions/rescue-field/index.ts` — current rescuer field UI/behavior.
- `supabase/functions/rescue-tracker-api/index.ts` — registration, login, approval status, messages, mission lifecycle, GPS points, offline-delayed GPS flagging, SOS, reports, profile updates, and mission photos.
- `supabase/functions/rescue-register-link/index.ts` — external-browser registration handoff.
- `supabase/migrations/20260830_rescue_mission_tracking.sql` — durable mission/session and GPS linkage.
- `supabase/migrations/20260830_rescue_mission_photo_link.sql` — mission-photo linkage.

## Live dependencies referenced by the rescuer field source
The working production rescuer currently calls these Supabase Edge Functions:
- `rescue-tracker-api`
- `rescue-deployment-api`
- `rescue-tracking-consent-api`
- `rescue-team-leader-roster`
- `scout-logo`

The DMT repository currently contains source for `rescue-tracker-api`, but source files for `rescue-deployment-api`, `rescue-tracking-consent-api`, `rescue-team-leader-roster`, and `scout-logo` are not present in the Git repository. Their deployed production versions are recorded in the DMT production baseline and must be recovered/exported separately before this repository can be considered fully self-contained.

## Production connection recorded in DMT
Supabase project: `fkxbohbrfotbwmqalzyw`
Vercel project: `rawusa-rapid`

Do not commit service-role keys, passwords, raw device tokens, or other secrets to this repository.
