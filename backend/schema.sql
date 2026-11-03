-- LINDA backend schema (Supabase / Postgres). Run once in: Supabase > SQL Editor > New query > Run.
-- Safe to re-run.


-- ---------- staff allowlist (people allowed to use the Fraud console) ----------
create table if not exists public.staff (
  user_id uuid primary key,
  note text,
  created_at timestamptz not null default now()
);
alter table public.staff enable row level security;

create or replace function public.is_staff() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.staff where user_id = auth.uid());
$$;

-- ---------- number normaliser ----------
-- Phone numbers -> local form 07xxxxxxxx. Sender names (MPESA etc.) -> upper-case letters/digits.
create or replace function public.norm_number(n text) returns text
language plpgsql immutable as $$
declare d text;
begin
  if n is null then return ''; end if;
  d := regexp_replace(n, '[^0-9]', '', 'g');
  if n ~ '[A-Za-z]' then return upper(regexp_replace(n, '[^A-Za-z0-9]', '', 'g')); end if;
  if d like '254%' and length(d) >= 12 then d := '0' || substr(d, 4); end if;
  if length(d) = 9 and d !~ '^0' then d := '0' || d; end if;
  return d;
end $$;

-- ---------- tables ----------
create table if not exists public.reports (
  id bigint generated always as identity primary key,
  device uuid not null default auth.uid(),
  kind text not null default 'report' check (kind in ('report','case','stopped')),
  number text not null default '',
  number_norm text not null default '',
  type text not null default 'unknown',
  region text not null default '',
  sample text not null default '' check (char_length(sample) <= 300),
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists reports_norm_idx on public.reports (number_norm, created_at desc);
create index if not exists reports_kind_idx on public.reports (kind, created_at desc);
create index if not exists reports_device_idx on public.reports (device, created_at desc);

create table if not exists public.numbers (
  number_norm text primary key,
  display text not null default '',
  status text not null default 'pending' check (status in ('pending','confirmed','verified')),
  type text not null default 'unknown',
  reporters int not null default 0,
  name text not null default '',
  sample text not null default '',
  last_report_at timestamptz,
  updated_at timestamptz not null default now()
);
create index if not exists numbers_upd_idx on public.numbers (updated_at);

create table if not exists public.broadcasts (
  id bigint generated always as identity primary key,
  text text not null check (char_length(text) between 1 and 300),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid()
);

-- ---------- triggers on reports ----------
create or replace function public.reports_before() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.device := auth.uid();
  if new.device is null then raise exception 'not signed in'; end if;
  new.number_norm := public.norm_number(new.number);
  new.sample := left(coalesce(new.sample,''), 300);
  new.created_at := now();
  if (select count(*) from public.reports where device = new.device and created_at > now() - interval '1 hour') >= 40 then
    raise exception 'rate limit';
  end if;
  if new.kind = 'report' and new.number_norm <> '' and exists (
      select 1 from public.reports where device = new.device and kind = 'report'
        and number_norm = new.number_norm and created_at > now() - interval '30 days') then
    return null; -- silently ignore a repeat report from the same phone for the same number within 30 days
  end if;
  return new;
end $$;
drop trigger if exists trg_reports_before on public.reports;
create trigger trg_reports_before before insert on public.reports
  for each row execute function public.reports_before();

create or replace function public.reports_after() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.kind = 'report' and new.number_norm <> '' then
    insert into public.numbers (number_norm, display, status, type, reporters, sample, last_report_at, updated_at)
    values (new.number_norm, new.number, 'pending', new.type, 1, new.sample, now(), now())
    on conflict (number_norm) do update set
      reporters = (select count(distinct device) from public.reports where number_norm = new.number_norm and kind = 'report'),
      type = case when public.numbers.status = 'pending' then new.type else public.numbers.type end,
      sample = case when new.sample <> '' then new.sample else public.numbers.sample end,
      last_report_at = now(), updated_at = now();
  end if;
  return new;
end $$;
drop trigger if exists trg_reports_after on public.reports;
create trigger trg_reports_after after insert on public.reports
  for each row execute function public.reports_after();

-- ---------- row level security ----------
alter table public.reports enable row level security;
alter table public.numbers enable row level security;
alter table public.broadcasts enable row level security;

drop policy if exists reports_insert on public.reports;
create policy reports_insert on public.reports for insert to authenticated with check (device = auth.uid());
drop policy if exists reports_select on public.reports;
create policy reports_select on public.reports for select to authenticated using (device = auth.uid() or public.is_staff());
drop policy if exists reports_delete on public.reports;
create policy reports_delete on public.reports for delete to authenticated using (device = auth.uid());

drop policy if exists numbers_select on public.numbers;
create policy numbers_select on public.numbers for select to authenticated using (true);
drop policy if exists broadcasts_select on public.broadcasts;
create policy broadcasts_select on public.broadcasts for select to authenticated using (true);
drop policy if exists staff_self on public.staff;
create policy staff_self on public.staff for select to authenticated using (user_id = auth.uid());

-- ---------- staff-only actions ----------
create or replace function public.confirm_number(p_number text, p_type text default null)
returns void language plpgsql security definer set search_path = public as $$
declare k text := public.norm_number(p_number);
begin
  if not public.is_staff() then raise exception 'staff only'; end if;
  insert into public.numbers (number_norm, display, status, type, updated_at)
  values (k, p_number, 'confirmed', coalesce(p_type,'unknown'), now())
  on conflict (number_norm) do update set status = 'confirmed',
    type = coalesce(p_type, public.numbers.type), updated_at = now();
end $$;

create or replace function public.verify_number(p_number text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
declare k text := public.norm_number(p_number);
begin
  if not public.is_staff() then raise exception 'staff only'; end if;
  insert into public.numbers (number_norm, display, status, name, updated_at)
  values (k, p_number, 'verified', coalesce(p_name,''), now())
  on conflict (number_norm) do update set status = 'verified', name = coalesce(p_name,''), updated_at = now();
end $$;

create or replace function public.dismiss_number(p_number text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_staff() then raise exception 'staff only'; end if;
  delete from public.numbers where number_norm = public.norm_number(p_number) and status = 'pending';
end $$;

create or replace function public.set_broadcast(p_text text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_staff() then raise exception 'staff only'; end if;
  update public.broadcasts set active = false where active;
  if p_text is not null and length(trim(p_text)) > 0 then
    insert into public.broadcasts (text) values (left(trim(p_text), 300));
  end if;
end $$;

-- ---------- permissions ----------
revoke all on all tables in schema public from anon;
revoke all on all tables in schema public from authenticated;
grant select, insert, delete on public.reports to authenticated;
grant select on public.numbers, public.broadcasts, public.staff to authenticated;
grant usage, select on all sequences in schema public to authenticated;
revoke execute on all functions in schema public from public, anon;
grant execute on function public.confirm_number(text,text), public.verify_number(text,text),
  public.dismiss_number(text), public.set_broadcast(text), public.is_staff(), public.norm_number(text) to authenticated;

-- ---------- starter data: genuine Safaricom numbers ----------
insert into public.numbers (number_norm, display, status, name) values
  ('100', '100', 'verified', 'Safaricom Customer Care'),
  ('200', '200', 'verified', 'Safaricom Customer Care'),
  ('0722000000', '0722 000 000', 'verified', 'Safaricom Customer Care')
on conflict (number_norm) do nothing;
