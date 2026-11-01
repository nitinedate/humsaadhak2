-- =====================================================================================================================
-- One-time migration from the old saadhak-app database (51 tables) into the HumSaadhak 2.0 schema.
--
-- How to run (see README "Migrating from the old system"):
--   1. Restore the old database dump into schema "legacy" of the new database:
--        pg_restore --no-owner --schema=public --dbname=humsaadhak old.dump   (then: alter schema public rename to legacy;
--        create schema public;)  -- or restore with a search_path that puts the tables in "legacy".
--   2. Start the application once with SPRING_PROFILES_ACTIVE=legacy-migration. Flyway runs this file after V1-V3,
--      then the LegacyFileCopier copies the old /uploads files into object storage.
--   3. Check the report: select * from legacy_migration_report;
--
-- Decisions encoded here (each one fixes a defect from the review):
--   * Old reference numbers are kept (BN/DB/IB/PU/CA/PR/MA/DN + 100000 + old id); identities are restarted above
--     the largest old id so new numbers never collide.
--   * Only real bcrypt hashes are copied. Unhashed temporary passwords (S3) are dropped: those users use
--     "Forgot password" and receive a single-use link.
--   * donation.amount meant "shortfall" for matched gifts and "paid" for direct gifts (D4). The new paid_amount is
--     the old committed_amount in both cases; pledged_amount is the purpose's commitment.
--   * need.raised_amount is recomputed from donations; a need is complete only when the target is reached (D8)
--     or the old system had explicitly closed it.
--   * Status codes 1/5/2/3 become DRAFT/SUBMITTED/APPROVED/REJECTED approval requests.
-- =====================================================================================================================

-- ---------- helpers ----------
create table if not exists legacy_map (entity varchar(30) not null, legacy_id bigint not null, new_id bigint not null,
                                       primary key (entity, legacy_id));

-- Old benefactors_type ids -> new organisation types, detected from the old names ("Individual Donor", "Corporate Donor").
-- Anything that does not mention corporate/company/CSR/trust/foundation is treated as individual; the mapping is listed
-- in legacy_migration_report so it can be checked.
create table legacy_benefactor_type (type_id bigint primary key, type_name varchar(255), org_type varchar(30) not null);
insert into legacy_benefactor_type
select benefactors_type_id, benefactors_type_name,
       case when benefactors_type_name ~* '(corporat|company|csr|trust|foundation)' then 'DONOR_CORPORATE' else 'DONOR_INDIVIDUAL' end
from legacy.benefactors_type;

create or replace function legacy_status(code integer) returns varchar language sql immutable as $$
  select case code when 2 then 'APPROVED' when 3 then 'REJECTED' when 5 then 'SUBMITTED' else 'DRAFT' end $$;

create or replace function legacy_periodicity(txt varchar) returns varchar language sql immutable as $$
  select case regexp_replace(upper(coalesce(txt, '')), '[^A-Z]', '', 'g')
    when 'MONTHLY' then 'MONTHLY' when 'QUARTERLY' then 'QUARTERLY'
    when 'HALFYEARLY' then 'HALF_YEARLY' when 'SIXMONTHLY' then 'HALF_YEARLY'
    when 'YEARLY' then 'YEARLY' when 'ANNUAL' then 'YEARLY' when 'ANNUALLY' then 'YEARLY'
    else 'ONE_TIME' end $$;

create or replace function legacy_sector(sector_id bigint) returns varchar language sql stable as $$
  select coalesce((select s.name from sector s join legacy.sector ls on lower(trim(ls.sector_name)) = lower(s.name)
                    where ls.sector_id = $1),
                  (select trim(ls.sector_name) from legacy.sector ls where ls.sector_id = $1),
                  'Education') $$;

alter table purpose  add column if not exists legacy_id bigint;
alter table need     add column if not exists legacy_id bigint;
alter table app_user add column if not exists legacy_id bigint;
alter table donation add column if not exists legacy_id bigint;
alter table document add column if not exists legacy_id bigint;
alter table document add column if not exists legacy_path varchar(1000);

-- ---------- organisations (benefactor, db_beneficiary, indirect_beneficiary -> organisation) ----------
insert into organisation (created_at, created_by, ref_no, name, type, verified, legacy_id)
select coalesce(b.entered_date_time, now()), b.entered_by, 'BN' || (100000 + b.benefactor_id), trim(b.benefactor_name),
       coalesce(t.org_type, 'DONOR_INDIVIDUAL'), coalesce(b.status, 1) = 2, b.benefactor_id
from legacy.benefactor b left join legacy_benefactor_type t on t.type_id = b.benefactors_type_id;
insert into legacy_map select 'BENEFACTOR', legacy_id, id from organisation where type like 'DONOR_%' and legacy_id is not null;

insert into organisation (created_at, created_by, ref_no, name, type, verified, legacy_id)
select coalesce(d.entered_date_time, now()), d.entered_by, 'DB' || (100000 + d.db_beneficiary_id), trim(d.name), 'SCHOOL',
       coalesce(d.status, 1) = 2, d.db_beneficiary_id
from legacy.db_beneficiary d;
insert into legacy_map select 'SCHOOL', legacy_id, id from organisation where type = 'SCHOOL' and legacy_id is not null;

insert into organisation (created_at, created_by, ref_no, name, type, verified, legacy_id)
select coalesce(i.entered_date_time, now()), i.entered_by, 'IB' || (100000 + i.indirect_beneficiary_id), trim(i.ib_name), 'NGO',
       coalesce(i.status, 1) = 2, i.indirect_beneficiary_id
from legacy.indirect_beneficiary i;
insert into legacy_map select 'NGO', legacy_id, id from organisation where type = 'NGO' and legacy_id is not null;

-- ---------- users ----------
-- Highest-privilege role wins when an old user had several.
insert into app_user (created_at, created_by, email, full_name, password_hash, org_id, role, status, legacy_id)
select distinct on (lower(u.email))
       coalesce(u.entered_date_time, now()), u.entered_by, lower(trim(u.email)),
       coalesce(nullif(trim(concat_ws(' ', u.first_name, u.middle_name, u.last_name)), ''), u.username, u.email),
       case when u.encrypted_password ~ '^\$2[aby]\$' then '{bcrypt}' || u.encrypted_password end,
       coalesce(mb.new_id, ms.new_id, mn.new_id),
       coalesce((select case r.role_name
                          when 'ROLE_SUPER_ADMIN' then 'SUPER_ADMIN' when 'ROLE_USER_MANGER' then 'MANAGER'
                          when 'ROLE_USER_OPERATION' then 'ASSOCIATE' when 'ROLE_NGO' then 'NGO'
                          when 'ROLE_INSTITUTION_EDUCATION' then 'SCHOOL' when 'ROLE_CORPORATE_DONOR' then 'DONOR_CORPORATE'
                          when 'ROLE_INDIVIDUAL_DONOR' then 'DONOR_INDIVIDUAL' else 'GUEST' end
                 from legacy.users_roles ur join legacy.roles r on r.roles_id = ur.roles_roles_id
                 where ur.users_users_id = u.users_id
                 order by case r.role_name when 'ROLE_SUPER_ADMIN' then 1 when 'ROLE_USER_MANGER' then 2
                          when 'ROLE_USER_OPERATION' then 3 else 4 end
                 limit 1), 'GUEST'),
       case when u.deleted_date_time is not null or coalesce(u.active, true) = false then 'DISABLED'
            when u.status = 2 then 'ACTIVE' when u.status = 3 then 'REJECTED' else 'PENDING' end,
       u.users_id
from legacy.users u
left join legacy_map mb on mb.entity = 'BENEFACTOR' and mb.legacy_id = u.benefactor_id
left join legacy_map ms on ms.entity = 'SCHOOL' and ms.legacy_id = u.db_beneficiary_id
left join legacy_map mn on mn.entity = 'NGO' and mn.legacy_id = u.indirect_beneficiary_id
where u.email is not null and trim(u.email) <> ''
order by lower(u.email), u.users_id desc
on conflict (email) do nothing;

-- ---------- purposes ----------
insert into purpose (created_at, created_by, ref_no, donor_org_id, name, description, sector, commitment_amount, periodicity,
                     approved, matched, completed, legacy_id, commitment_date)
select coalesce(p.entered_date_time, now()), p.entered_by, 'PU' || (100000 + p.purpose_id), m.new_id, trim(p.purpose_name),
       coalesce(nullif(trim(p.purpose_description), ''), trim(p.purpose_name)), legacy_sector(p.sector_id),
       nullif(p.commitment_amount, 0),
       case when coalesce(p.is_periodic, false) then legacy_periodicity(per.periodicity) else 'ONE_TIME' end,
       coalesce(p.status, 1) = 2, coalesce(p.is_matched, false), coalesce(p.is_completed, false), p.purpose_id,
       coalesce(p.commitment_date, case when p.status = 2 then p.entered_date_time end)::date
from legacy.purpose p
join legacy_map m on m.entity = 'BENEFACTOR' and m.legacy_id = p.benefactor_id
left join legacy.periodicity per on per.periodicity_id = p.periodicity;
insert into legacy_map select 'PURPOSE', legacy_id, id from purpose where legacy_id is not null;

-- ---------- needs (causes + projects) ----------
insert into need (created_at, created_by, ref_no, org_id, kind, name, description, sector, target_amount, periodicity,
                  approved, completed, legacy_id, approved_at)
select coalesce(c.entered_date_time, now()), c.entered_by, 'CA' || (100000 + c.db_cause_id), m.new_id, 'CAUSE', trim(c.db_cause_name),
       coalesce(nullif(trim(c.db_cause_description), ''), trim(c.db_cause_name)), legacy_sector(c.sector_id),
       greatest(coalesce(c.amount, 1), 1), legacy_periodicity(per.periodicity),
       coalesce(c.status, 1) = 2, coalesce(c.is_completed, false), c.db_cause_id,
       case when c.status = 2 then coalesce(c.approved_date_time, c.entered_date_time) end
from legacy.db_causes c
join legacy_map m on m.entity = 'SCHOOL' and m.legacy_id = c.db_beneficiary_id
left join legacy.periodicity per on per.periodicity_id = c.periodicity;
insert into legacy_map select 'CAUSE', legacy_id, id from need where kind = 'CAUSE' and legacy_id is not null;

insert into need (created_at, created_by, ref_no, org_id, kind, name, description, sector, target_amount, periodicity,
                  approved, completed, legacy_id, approved_at)
select coalesce(p.entered_date_time, now()), p.entered_by, 'PR' || (100000 + p.ib_projects_id), m.new_id, 'PROJECT', trim(p.ib_project_name),
       coalesce(nullif(trim(p.ib_project_description), ''), trim(p.ib_project_name)), legacy_sector(p.sector_id),
       greatest(coalesce(p.amount, 1), 1), legacy_periodicity(per.periodicity),
       coalesce(p.status, 1) = 2, coalesce(p.is_completed, false), p.ib_projects_id,
       case when p.status = 2 then coalesce(p.approved_date_time, p.entered_date_time) end
from legacy.ib_projects p
join legacy_map m on m.entity = 'NGO' and m.legacy_id = p.indirect_beneficiary_id
left join legacy.periodicity per on per.periodicity_id = p.periodicity;
insert into legacy_map select 'PROJECT', legacy_id, id from need where kind = 'PROJECT' and legacy_id is not null;

-- ---------- approval requests (maker 0 = "migrated from the old system") ----------
insert into approval_request (created_at, subject_type, subject_id, maker_id, status, last_comment, subject_title)
select now(), 'ORGANISATION', o.id, coalesce((select id from app_user where email = lower(o.created_by)), 0),
       legacy_status(coalesce(b.status, d.status, i.status)),
       coalesce(b.rejection_comment, d.rejection_comment, i.rejection_comment), o.name
from organisation o
left join legacy.benefactor b on o.type like 'DONOR_%' and b.benefactor_id = o.legacy_id
left join legacy.db_beneficiary d on o.type = 'SCHOOL' and d.db_beneficiary_id = o.legacy_id
left join legacy.indirect_beneficiary i on o.type = 'NGO' and i.indirect_beneficiary_id = o.legacy_id
where o.legacy_id is not null;

insert into approval_request (created_at, subject_type, subject_id, maker_id, status, subject_title)
select now(), 'PURPOSE', p.id, coalesce((select id from app_user where email = lower(p.created_by)), 0),
       legacy_status(lp.status), p.name
from purpose p join legacy.purpose lp on lp.purpose_id = p.legacy_id;

insert into approval_request (created_at, subject_type, subject_id, maker_id, status, subject_title)
select now(), 'NEED', n.id, coalesce((select id from app_user where email = lower(n.created_by)), 0),
       legacy_status(coalesce(c.status, pr.status)), n.name
from need n
left join legacy.db_causes c on n.kind = 'CAUSE' and c.db_cause_id = n.legacy_id
left join legacy.ib_projects pr on n.kind = 'PROJECT' and pr.ib_projects_id = n.legacy_id
where n.legacy_id is not null;

insert into approval_request (created_at, subject_type, subject_id, maker_id, status, last_comment, subject_title)
select now(), 'USER', a.id, a.id, legacy_status(u.status), u.rejection_comment, a.full_name || ' (' || a.role || ')'
from app_user a join legacy.users u on u.users_id = a.legacy_id;

-- ---------- matching history ----------
insert into match_run (created_at, started_by, model_version, purpose_count, candidate_count)
select now(), 0, 'legacy', (select count(distinct purpose_id) from legacy.cause_project_purpose_similarity),
       (select count(*) from legacy.cause_project_purpose_similarity);

insert into match_candidate (created_at, run_id, purpose_id, need_id, final_score, engine_rank, model_version, decision, remark, priority)
select distinct on (mp.new_id, coalesce(mc.new_id, mpr.new_id))
       coalesce(s.entered_date_time, now()), (select max(id) from match_run where model_version = 'legacy'),
       mp.new_id, coalesce(mc.new_id, mpr.new_id),
       least(100, greatest(0, round(case when s.similarity_percentage <= 1 then s.similarity_percentage * 100
                                         else s.similarity_percentage end)))::int,
       row_number() over (partition by mp.new_id order by s.similarity_percentage desc nulls last)::int,
       'legacy',
       case when s.approved then 'APPROVED' when s.rejected_by is not null or s.status = 3 then 'REJECTED' else 'PENDING' end,
       s.remark, case when s.approved then s.order_match::int end
from legacy.cause_project_purpose_similarity s
join legacy_map mp on mp.entity = 'PURPOSE' and mp.legacy_id = s.purpose_id
left join legacy_map mc on mc.entity = 'CAUSE' and mc.legacy_id = s.db_cause_id
left join legacy_map mpr on mpr.entity = 'PROJECT' and mpr.legacy_id = s.ib_projects_id
where coalesce(mc.new_id, mpr.new_id) is not null
order by mp.new_id, coalesce(mc.new_id, mpr.new_id), s.cause_project_purpose_similarity_id desc;

-- Confirmed matches: one batch per purpose; a candidate row is created if the old data had none.
insert into match_candidate (created_at, run_id, purpose_id, need_id, final_score, engine_rank, model_version, decision, priority)
select now(), (select max(id) from match_run where model_version = 'legacy'), mp.new_id, coalesce(mc.new_id, mpr.new_id),
       0, 0, 'legacy', 'APPROVED', coalesce(m.order_match, 1)::int
from legacy.match m
join legacy_map mp on mp.entity = 'PURPOSE' and mp.legacy_id = m.purpose_id
left join legacy_map mc on mc.entity = 'CAUSE' and mc.legacy_id = m.cause_id
left join legacy_map mpr on mpr.entity = 'PROJECT' and mpr.legacy_id = m.project_id
where coalesce(mc.new_id, mpr.new_id) is not null
on conflict (purpose_id, need_id) do nothing;

insert into match_batch (created_at, purpose_id, confirmed_by)
select now(), mp.new_id, 0 from (select distinct purpose_id from legacy.match) lm
join legacy_map mp on mp.entity = 'PURPOSE' and mp.legacy_id = lm.purpose_id;

insert into match (created_at, ref_no, batch_id, candidate_id, purpose_id, need_id, donor_org_id, priority)
select distinct on (p.id, n.id)
       now(), 'MA' || (100000 + m.match_id), b.id, c.id, p.id, n.id, p.donor_org_id, coalesce(m.order_match, 1)::int
from legacy.match m
join legacy_map mp on mp.entity = 'PURPOSE' and mp.legacy_id = m.purpose_id
join purpose p on p.id = mp.new_id
left join legacy_map mc on mc.entity = 'CAUSE' and mc.legacy_id = m.cause_id
left join legacy_map mpr on mpr.entity = 'PROJECT' and mpr.legacy_id = m.project_id
join need n on n.id = coalesce(mc.new_id, mpr.new_id)
join match_batch b on b.purpose_id = p.id
join match_candidate c on c.purpose_id = p.id and c.need_id = n.id
order by p.id, n.id, m.match_id;
insert into legacy_map select 'MATCH', substring(ref_no from 3)::bigint - 100000, id from match where ref_no like 'MA%';

-- ---------- donations (D4: paid = committed_amount for both matched and direct gifts) ----------
insert into donation (created_at, ref_no, donor_org_id, need_id, purpose_id, match_id, pledged_amount, paid_amount, currency,
                      payment_ref, paid_on, idempotency_key, confirmed, legacy_id)
select coalesce(d.date_of_payment, now()),
       case when d.direct_donation_id is not null then 'DDN' else 'DN' end || (100000 + d.donation_id),
       mb.new_id, coalesce(mc.new_id, mpr.new_id), mp.new_id, mm.new_id,
       p.commitment_amount, d.committed_amount, 'INR',
       coalesce(nullif(trim(d.acknowledgment), ''), 'LEGACY-' || d.donation_id),
       coalesce(d.date_of_payment::date, current_date), 'legacy-dn-' || d.donation_id, true, d.donation_id
from legacy.donation d
join legacy_map mb on mb.entity = 'BENEFACTOR' and mb.legacy_id = d.benefactor_id
left join legacy_map mc on mc.entity = 'CAUSE' and mc.legacy_id = d.db_cause_id
left join legacy_map mpr on mpr.entity = 'PROJECT' and mpr.legacy_id = d.ib_projects_id
left join legacy_map mp on mp.entity = 'PURPOSE' and mp.legacy_id = d.purpose_id
left join legacy_map mm on mm.entity = 'MATCH' and mm.legacy_id = d.match_id
left join purpose p on p.id = mp.new_id
where coalesce(mc.new_id, mpr.new_id) is not null and coalesce(d.committed_amount, 0) > 0;

-- D8: raised amount from real payments; complete only when the target is met or the old system closed it.
update need n set raised_amount = coalesce((select sum(paid_amount) from donation where need_id = n.id), 0)
where n.legacy_id is not null;
update need set completed = true where legacy_id is not null and raised_amount >= target_amount;

-- ---------- documents (files are copied by LegacyFileCopier) ----------
insert into document_type (created_at, code, name, category, shareable)
values (now(), 'LEGACY_OTHER', 'Other document (migrated)', 'REGISTRATION', false) on conflict (code) do nothing;

insert into document (created_at, created_by, owner_org_id, document_type_id, need_id, storage_key, original_name, content_type,
                      size_bytes, doc_version, state, superseded, legacy_id, legacy_path)
select coalesce(d.entered_date_time, now()), d.entered_by,
       coalesce(mb.new_id, ms.new_id, mn.new_id),
       (select id from document_type where code = case
            when t.document_type_name ilike '%udise%' then 'SCHOOL_UDISE'
            when t.document_type_name ilike '%12a%' then 'CERT_12A'
            when t.document_type_name ilike '%80g%' then 'CERT_80G'
            when t.document_type_name ilike '%csr%' then 'CSR_1'
            when t.document_type_name ilike '%pan%' then 'PAN'
            when t.document_type_name ilike '%registration%' then 'REGISTRATION_CERT'
            when t.document_type_name ilike '%audit%' then 'AUDITED_ACCOUNTS'
            when t.document_type_name ilike '%cheque%' then 'CANCELLED_CHEQUE'
            when t.document_type_name ilike '%resolution%' then 'BOARD_RESOLUTION'
            when t.document_type_name ilike any (array['%budget%', '%quotation%']) then 'NEED_BUDGET'
            when t.document_type_name ilike '%proposal%' then 'NEED_PROPOSAL'
            when t.document_type_name ilike '%receipt%' then 'DONATION_RECEIPT'
            when t.document_type_name ilike '%ack%' then 'DONOR_ACK'
            when t.document_type_name ilike any (array['%photo%', '%image%']) then 'PROGRESS_PHOTO'
            when t.document_type_name ilike '%video%' then 'PROGRESS_VIDEO'
            else 'LEGACY_OTHER' end),
       coalesce(mca.new_id, mpj.new_id),
       'legacy/' || d.document_id || '/' || coalesce(d.document_name, d.document_id::text),
       coalesce(d.original_document_name, d.document_name, 'document-' || d.document_id),
       case lower(coalesce(d.document_extension, '')) when 'pdf' then 'application/pdf' when '.pdf' then 'application/pdf'
            when 'png' then 'image/png' when '.png' then 'image/png' when 'mp4' then 'video/mp4' when '.mp4' then 'video/mp4'
            when 'jpg' then 'image/jpeg' when '.jpg' then 'image/jpeg' when 'jpeg' then 'image/jpeg' when '.jpeg' then 'image/jpeg'
            else 'application/octet-stream' end,
       0, 1,
       case when d.document_status then 'REVIEWED' else 'PENDING' end,
       not coalesce(d.is_active, true) or d.deleted_date_time is not null,
       d.document_id,
       concat_ws('/', nullif(trim(trailing '/' from d.document_path), ''), d.document_name)
from legacy.document d
left join legacy.document_type t on t.document_type_id = d.document_type_id
left join legacy_map mb on mb.entity = 'BENEFACTOR' and mb.legacy_id = coalesce(d.document_benefactor_id, d.benefactor_id)
left join legacy_map ms on ms.entity = 'SCHOOL' and ms.legacy_id = coalesce(d.document_db_beneficiary_id, d.db_beneficiary_id)
left join legacy_map mn on mn.entity = 'NGO' and mn.legacy_id = coalesce(d.document_indirect_beneficiary_id, d.indirect_beneficiary_id)
left join legacy_map mca on mca.entity = 'CAUSE' and mca.legacy_id = d.document_cause_id
left join legacy_map mpj on mpj.entity = 'PROJECT' and mpj.legacy_id = d.document_project_id
where coalesce(mb.new_id, ms.new_id, mn.new_id) is not null;

-- ---------- restart identities above every migrated old id so new reference numbers never collide ----------
do $$
declare r record; v bigint;
begin
  for r in select * from (values
      ('organisation', greatest((select max(benefactor_id) from legacy.benefactor), (select max(db_beneficiary_id) from legacy.db_beneficiary),
                                (select max(indirect_beneficiary_id) from legacy.indirect_beneficiary))),
      ('purpose', (select max(purpose_id) from legacy.purpose)),
      ('need', greatest((select max(db_cause_id) from legacy.db_causes), (select max(ib_projects_id) from legacy.ib_projects))),
      ('match', (select max(match_id) from legacy.match)),
      ('donation', (select max(donation_id) from legacy.donation))) as t(tbl, legacy_max)
  loop
    execute format('select greatest(coalesce(max(id), 0), %s) + 1 from %I', coalesce(r.legacy_max, 0), r.tbl) into v;
    execute format('alter table %I alter column id restart with %s', r.tbl, v);
  end loop;
end $$;

-- ---------- reconciliation report ----------
create view legacy_migration_report as
select 'organisations' as item, (select count(*) from legacy.benefactor) + (select count(*) from legacy.db_beneficiary)
         + (select count(*) from legacy.indirect_beneficiary) as old_rows, (select count(*) from organisation where legacy_id is not null) as new_rows
union all select 'users', (select count(*) from legacy.users), (select count(*) from app_user where legacy_id is not null)
union all select 'users without a usable password', null, (select count(*) from app_user where legacy_id is not null and password_hash is null)
union all select 'purposes', (select count(*) from legacy.purpose), (select count(*) from purpose where legacy_id is not null)
union all select 'causes and projects', (select count(*) from legacy.db_causes) + (select count(*) from legacy.ib_projects),
                 (select count(*) from need where legacy_id is not null)
union all select 'confirmed matches', (select count(*) from legacy.match), (select count(*) from match where ref_no like 'MA%')
union all select 'donations', (select count(*) from legacy.donation), (select count(*) from donation where legacy_id is not null)
union all select 'paid amount (INR)', (select sum(committed_amount)::bigint from legacy.donation where committed_amount > 0),
                 (select sum(paid_amount)::bigint from donation where legacy_id is not null)
union all select 'documents', (select count(*) from legacy.document), (select count(*) from document where legacy_id is not null)
union all select 'benefactor type "' || coalesce(type_name, '?') || '" became ' || org_type, null, null from legacy_benefactor_type;

-- Users migrated above get the system role matching their account type (V6 introduced assignable roles).
update app_user set role_code = role where role_code is null;
