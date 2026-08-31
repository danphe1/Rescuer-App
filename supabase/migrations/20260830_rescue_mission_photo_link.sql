alter table public.rescue_mission_photos
  add column if not exists mission_id uuid references public.rescue_missions(id) on delete set null;

create index if not exists rescue_mission_photos_device_mission_idx
  on public.rescue_mission_photos(device_id, mission_id, captured_at desc);
