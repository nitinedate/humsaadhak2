-- Reference data managed from the admin screens: sectors and document types can be switched off instead of deleted
-- (existing records keep them; new forms stop offering them).
alter table sector add column active boolean not null default true;
alter table document_type add column active boolean not null default true;
