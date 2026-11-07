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


-- =====================================================================
-- FAMILY GUARDIAN (safe to re-run)
-- A protected person makes a 6-character code; a guardian types it in; from then on the guardian is told
-- (never the message text) when a scam reaches the protected person. Either side can end it at any time.
-- =====================================================================
create table if not exists public.guardian_links (
  id bigint generated always as identity primary key,
  protected uuid not null,
  guardian uuid,
  code text unique,
  protected_name text not null default '' check (char_length(protected_name) <= 60),
  protected_phone text not null default '' check (char_length(protected_phone) <= 20),
  guardian_name text not null default '' check (char_length(guardian_name) <= 60),
  guardian_phone text not null default '' check (char_length(guardian_phone) <= 20),
  status text not null default 'pending' check (status in ('pending','active','ended')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours'
);
create index if not exists gl_protected_idx on public.guardian_links (protected);
create index if not exists gl_guardian_idx on public.guardian_links (guardian);

-- secret keys let the phone's background jobs act for a link without sharing the app's sign-in
create table if not exists public.guardian_secrets (
  link_id bigint primary key references public.guardian_links(id) on delete cascade,
  protect_key text not null unique,
  guardian_key text unique
);
create table if not exists public.guardian_alerts (
  id bigint generated always as identity primary key,
  link_id bigint not null references public.guardian_links(id) on delete cascade,
  kind text not null check (kind in ('sms','imp','call','payblock','pay')),
  number text not null default '' check (char_length(number) <= 40),
  amount int not null default 0,
  level text not null default 'red' check (level in ('red','amber')),
  created_at timestamptz not null default now()
);
create index if not exists ga_link_idx on public.guardian_alerts (link_id, created_at desc);
create table if not exists public.guardian_tries (device uuid not null, at timestamptz not null default now());

alter table public.guardian_links enable row level security;
alter table public.guardian_secrets enable row level security;
alter table public.guardian_alerts enable row level security;
alter table public.guardian_tries enable row level security;

drop policy if exists gl_select on public.guardian_links;
create policy gl_select on public.guardian_links for select to authenticated
  using (protected = auth.uid() or guardian = auth.uid());
drop policy if exists ga_select on public.guardian_alerts;
create policy ga_select on public.guardian_alerts for select to authenticated
  using (exists (select 1 from public.guardian_links l where l.id = link_id and (l.protected = auth.uid() or l.guardian = auth.uid())));

create or replace function public.rand_hex() returns text language sql volatile as $$
  select replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
$$;
create or replace function public.rand_code() returns text language plpgsql volatile as $$
declare a text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; b bytea := decode(substr(replace(gen_random_uuid()::text,'-',''),1,12),'hex'); r text := ''; i int;
begin
  for i in 0..5 loop r := r || substr(a, (get_byte(b, i) % 32) + 1, 1); end loop;
  return r;
end $$;

create or replace function public.create_guardian_link(p_name text, p_phone text default '')
returns table (link_id bigint, code text, protect_key text)
language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text; id bigint; k text;
begin
  if me is null then raise exception 'not signed in'; end if;
  update public.guardian_links set status = 'ended', code = null where protected = me and status = 'pending' and expires_at < now();
  if (select count(*) from public.guardian_links where protected = me and status in ('pending','active')) >= 5 then
    raise exception 'too many guardians';
  end if;
  if (select count(*) from public.guardian_links where protected = me and created_at > now() - interval '1 day') >= 10 then
    raise exception 'rate limit';
  end if;
  loop
    c := public.rand_code();
    begin
      insert into public.guardian_links (protected, code, protected_name, protected_phone)
      values (me, c, left(coalesce(p_name,''), 60), left(coalesce(p_phone,''), 20)) returning guardian_links.id into id;
      exit;
    exception when unique_violation then null;
    end;
  end loop;
  k := public.rand_hex();
  insert into public.guardian_secrets (link_id, protect_key) values (id, k);
  return query select id, c, k;
end $$;

create or replace function public.join_guardian_link(p_code text, p_name text, p_phone text default '')
returns table (link_id bigint, protected_name text, protected_phone text, guardian_key text)
language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); l public.guardian_links; k text;
begin
  if me is null then raise exception 'not signed in'; end if;
  if (select count(*) from public.guardian_tries where device = me and at > now() - interval '1 hour') >= 10 then
    raise exception 'too many tries';
  end if;
  insert into public.guardian_tries (device) values (me);
  select * into l from public.guardian_links
    where code = upper(trim(coalesce(p_code,''))) and status = 'pending' and expires_at > now() and protected <> me;
  -- A wrong code returns no rows (not an error) so the failed try stays counted and guessing is rate limited.
  if not found then return; end if;
  k := public.rand_hex();
  update public.guardian_links set guardian = me, status = 'active', code = null,
    guardian_name = left(coalesce(p_name,''), 60), guardian_phone = left(coalesce(p_phone,''), 20) where id = l.id;
  update public.guardian_secrets set guardian_key = k where public.guardian_secrets.link_id = l.id;
  return query select l.id, l.protected_name, l.protected_phone, k;
end $$;

create or replace function public.end_guardian_link(p_id bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.guardian_links set status = 'ended', code = null
    where id = p_id and (protected = auth.uid() or guardian = auth.uid());
  if found then
    delete from public.guardian_alerts where link_id = p_id;
    delete from public.guardian_secrets where link_id = p_id;
  end if;
end $$;

create or replace function public.add_guardian_alert(p_link bigint, p_kind text, p_number text, p_amount int, p_level text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_kind not in ('sms','imp','call','payblock','pay') then return; end if;
  if exists (select 1 from public.guardian_alerts where link_id = p_link and kind = p_kind and number = left(coalesce(p_number,''),40)
             and created_at > now() - interval '10 minutes') then return; end if;
  if (select count(*) from public.guardian_alerts where link_id = p_link and created_at > now() - interval '1 hour') >= 60 then return; end if;
  insert into public.guardian_alerts (link_id, kind, number, amount, level)
  values (p_link, p_kind, left(coalesce(p_number,''),40), greatest(coalesce(p_amount,0),0), case when p_level = 'amber' then 'amber' else 'red' end);
end $$;

-- from the protected person's app
create or replace function public.send_guardian_alert(p_kind text, p_number text default '', p_amount int default 0, p_level text default 'red')
returns void language plpgsql security definer set search_path = public as $$
declare r record;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  for r in select id from public.guardian_links where protected = auth.uid() and status = 'active' loop
    perform public.add_guardian_alert(r.id, p_kind, p_number, p_amount, p_level);
  end loop;
end $$;

-- from the protected phone's background scanner (uses the link's secret key)
create or replace function public.guardian_notify(p_key text, p_kind text, p_number text default '', p_amount int default 0, p_level text default 'red')
returns void language plpgsql security definer set search_path = public as $$
declare lid bigint;
begin
  select s.link_id into lid from public.guardian_secrets s join public.guardian_links l on l.id = s.link_id
    where s.protect_key = p_key and l.status = 'active';
  if lid is not null then perform public.add_guardian_alert(lid, p_kind, p_number, p_amount, p_level); end if;
end $$;

-- from the guardian phone's background job (uses the guardian's secret key)
create or replace function public.guardian_poll(p_key text, p_since timestamptz)
returns table (id bigint, protected_name text, protected_phone text, kind text, number text, amount int, level text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select a.id, l.protected_name, l.protected_phone, a.kind, a.number, a.amount, a.level, a.created_at
  from public.guardian_secrets s
  join public.guardian_links l on l.id = s.link_id and l.status = 'active'
  join public.guardian_alerts a on a.link_id = l.id
  where s.guardian_key = p_key and a.created_at > p_since
  order by a.created_at asc limit 50;
$$;

-- each phone asks for its own keys so it can hand them to its background jobs
create or replace function public.my_guardian_keys()
returns table (link_id bigint, role text, key text)
language sql stable security definer set search_path = public as $$
  select l.id, 'protected', s.protect_key from public.guardian_links l join public.guardian_secrets s on s.link_id = l.id
    where l.protected = auth.uid() and l.status = 'active'
  union all
  select l.id, 'guardian', s.guardian_key from public.guardian_links l join public.guardian_secrets s on s.link_id = l.id
    where l.guardian = auth.uid() and l.status = 'active';
$$;

revoke all on public.guardian_links, public.guardian_secrets, public.guardian_alerts, public.guardian_tries from anon, authenticated;
grant select on public.guardian_links, public.guardian_alerts to authenticated;
revoke execute on all functions in schema public from public, anon;
grant execute on function public.create_guardian_link(text,text), public.join_guardian_link(text,text,text),
  public.end_guardian_link(bigint), public.send_guardian_alert(text,text,int,text),
  public.guardian_notify(text,text,text,int,text), public.guardian_poll(text,timestamptz), public.my_guardian_keys() to authenticated;
