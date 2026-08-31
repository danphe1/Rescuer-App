create table if not exists public.rescue_missions (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.rescue_devices(id) on delete cascade,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.rescue_devices
  add column if not exists active_mission_id uuid references public.rescue_missions(id) on delete set null;

alter table public.rescue_locations
  add column if not exists mission_id uuid references public.rescue_missions(id) on delete set null,
  add column if not exists uploaded_from_offline boolean not null default false;

alter table public.rescue_activity_events
  add column if not exists mission_id uuid references public.rescue_missions(id) on delete set null;

alter table public.rescue_mission_reports
  add column if not exists mission_id uuid references public.rescue_missions(id) on delete set null;

create index if not exists rescue_missions_device_started_idx
  on public.rescue_missions(device_id, started_at desc);
create index if not exists rescue_locations_device_mission_recorded_idx
  on public.rescue_locations(device_id, mission_id, recorded_at);
create index if not exists rescue_locations_device_recorded_idx
  on public.rescue_locations(device_id, recorded_at desc);

alter table public.rescue_missions enable row level security;

comment on table public.rescue_missions is 'One durable mission/session record per rescuer deployment. GPS points reference this mission so separate missions never mix.';
comment on column public.rescue_locations.uploaded_from_offline is 'True when a GPS point was captured offline or materially delayed before server upload.';
