insert into sector(code, name) values
 ('EDUCATION','Education'), ('NUTRITION','Nutrition'), ('HEALTH_SANITATION','Health and sanitation'),
 ('WOMEN_GIRLS','Women and girls'), ('LIVELIHOOD','Livelihood'), ('ENVIRONMENT','Environment');

insert into state_region(code, name) values ('MH','Maharashtra'), ('GJ','Gujarat'), ('KA','Karnataka'), ('GA','Goa'), ('OTHER','Other');

insert into document_type(created_at, code, name, category, shareable) values
 (now(),'REGISTRATION_CERT','Registration certificate','REGISTRATION',true),
 (now(),'PAN','PAN card','REGISTRATION',false),
 (now(),'CERT_12A','12A certificate','REGISTRATION',true),
 (now(),'CERT_80G','80G certificate','REGISTRATION',true),
 (now(),'CSR_1','CSR-1 registration','REGISTRATION',true),
 (now(),'AUDITED_ACCOUNTS','Latest audited accounts','REGISTRATION',true),
 (now(),'CANCELLED_CHEQUE','Cancelled cheque','REGISTRATION',false),
 (now(),'BOARD_RESOLUTION','Board resolution for CSR','REGISTRATION',false),
 (now(),'SCHOOL_UDISE','UDISE+ school certificate','REGISTRATION',true),
 (now(),'NEED_BUDGET','Budget or quotation','NEED',true),
 (now(),'NEED_PROPOSAL','Proposal','NEED',true),
 (now(),'DONATION_RECEIPT','Payment receipt','DONATION',false),
 (now(),'DONOR_ACK','Acknowledgement to donor','ACKNOWLEDGEMENT',false),
 (now(),'PROGRESS_PHOTO','Progress photo','MEDIA',false),
 (now(),'PROGRESS_VIDEO','Progress video','MEDIA',false);

-- Mandatory documents per organisation type (Q7 in the blueprint: confirm with operations).
insert into document_type_rule(created_at, document_type_id, organisation_type, mandatory)
select now(), id, 'NGO', true from document_type where code in ('REGISTRATION_CERT','PAN','CERT_12A','CERT_80G','AUDITED_ACCOUNTS','CANCELLED_CHEQUE');
insert into document_type_rule(created_at, document_type_id, organisation_type, mandatory)
select now(), id, 'SCHOOL', true from document_type where code in ('SCHOOL_UDISE','PAN','CANCELLED_CHEQUE');
insert into document_type_rule(created_at, document_type_id, organisation_type, mandatory)
select now(), id, 'DONOR_CORPORATE', true from document_type where code in ('PAN','CSR_1','BOARD_RESOLUTION');
insert into document_type_rule(created_at, document_type_id, organisation_type, mandatory)
select now(), id, 'DONOR_INDIVIDUAL', true from document_type where code in ('PAN');
