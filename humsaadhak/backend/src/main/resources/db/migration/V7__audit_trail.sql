-- Audit trail: one row per user action (who, what, which record, when, from where, how it ended). Append-only.

create table audit_event (
  id bigint generated always as identity primary key,
  at timestamptz not null default now(),
  request_id varchar(36),                       -- shared with the Envers revisions the request wrote
  actor_id bigint,                              -- null for anonymous requests (sign-in, registration, password links)
  actor_email varchar(320),
  actor_name varchar(255),
  actor_role varchar(120),                      -- role name at the time (a snapshot)
  actor_org_id bigint,
  action varchar(80) not null,                  -- e.g. purpose.update
  category varchar(40) not null,                -- e.g. purpose: the page area that shows it
  target_type varchar(40),                      -- e.g. PURPOSE
  target_id varchar(64),                        -- text: roles, permissions and sectors use codes
  org_ids bigint[] not null default '{}',       -- organisations whose people may see this record's trail
  outcome varchar(10) not null check (outcome in ('SUCCESS', 'DENIED', 'FAILED')),
  error_code varchar(80),
  detail varchar(1000),
  ip varchar(64),
  user_agent varchar(400)
);

create index audit_event_target on audit_event (target_type, target_id, at desc);
create index audit_event_category on audit_event (category, at desc);
create index audit_event_actor on audit_event (actor_id, at desc);
create index audit_event_actor_org on audit_event (actor_org_id, at desc);
create index audit_event_org_ids on audit_event using gin (org_ids);
create index audit_event_at on audit_event (at desc);
create index audit_event_request on audit_event (request_id);

-- Nobody, including the application, can change or remove what was recorded.
create function audit_event_append_only() returns trigger language plpgsql as $$
begin
  raise exception 'audit_event is append-only: % is not allowed', tg_op;
end
$$;
create trigger audit_event_no_change before update or delete on audit_event
  for each row execute function audit_event_append_only();
create trigger audit_event_no_truncate before truncate on audit_event
  for each statement execute function audit_event_append_only();

-- Links each Envers revision to the request (and so the audited action) that made it.
alter table revinfo add column request_id varchar(36);
create index revinfo_request on revinfo (request_id);

insert into app_permission (code, name, description, category, system) values
 ('audit.view', 'View the audit log', 'See every action in the audit log, including refused and failed attempts, with IP addresses and browsers.',
  'Administration', true);

insert into app_role_permission (role_code, permission_code) values ('SUPER_ADMIN', 'audit.view'), ('MANAGER', 'audit.view');
