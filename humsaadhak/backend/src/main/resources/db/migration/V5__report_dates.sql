-- Dates the original reports filter and show on: when a cause or project was approved, and when a donor committed
-- (the purpose's first approval). Set by the catalogue module on approval; backfilled here for existing rows.
alter table need add column approved_at timestamptz;
alter table need_aud add column approved_at timestamptz;
alter table purpose add column commitment_date date;
alter table purpose_aud add column commitment_date date;

update need n set approved_at = a.updated_at
from approval_request a where a.subject_type = 'NEED' and a.subject_id = n.id and a.status = 'APPROVED' and n.approved;
update need set approved_at = created_at where approved and approved_at is null;

update purpose p set commitment_date = coalesce(a.updated_at, p.created_at)::date
from approval_request a where a.subject_type = 'PURPOSE' and a.subject_id = p.id and a.status = 'APPROVED' and p.approved;
update purpose set commitment_date = created_at::date where approved and commitment_date is null;

create index ix_need_org_approved_at on need(org_id, approved_at);
