// Mirrors the Spring Boot DTOs (records) one to one.
export type Role = 'SUPER_ADMIN' | 'MANAGER' | 'ASSOCIATE' | 'DONOR_INDIVIDUAL' | 'DONOR_CORPORATE' | 'SCHOOL' | 'NGO' | 'GUEST';

export interface Me {
  id: number;
  email: string;
  fullName: string;
  orgId: number | null;
  /** Account type: decides data scope and dashboard. */
  role: Role;
  /** The assigned role (built-in or custom) and the permissions it grants. */
  roleCode?: string;
  roleName?: string;
  permissions: string[];
  theme?: 'INDIGO' | 'HUMSAADHAK' | 'OCEAN' | 'NIGHT';
}

export interface Kpi { label: string; value: string; note: string }
/** What clicking a chart segment lists (GET /dashboards/details?set=…&filters…). */
export interface Drill { set: 'ORGANISATIONS' | 'NEEDS' | 'DONATIONS' | 'PURPOSES'; filters: Record<string, string> }
export interface Slice { label: string; value: number; colorKey: string; drill?: Drill | null }
export interface DonutDto { title: string; totalLabel: string; slices: Slice[] }
/** drills[i] is what clicking series i of this bar lists. */
export interface BarPoint { label: string; values: number[]; drills?: (Drill | null)[] | null }
export interface BarChartDto { title: string; series: string[]; unit: string; bars: BarPoint[] }
export interface DashboardDto { role: string; title: string; kpis: Kpi[]; donuts: DonutDto[]; bars: BarChartDto[] }

export type SubjectType = 'USER' | 'ORGANISATION' | 'PURPOSE' | 'NEED' | 'DOCUMENT';
export type ApprovalStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
export interface ApprovalRow {
  id: number;
  subjectType: SubjectType;
  subjectId: number;
  title: string | null;
  makerId: number;
  status: ApprovalStatus;
  revision: number;
  lastComment: string | null;
  submittedAt: string;
}
export interface StepRow { from: ApprovalStatus | null; to: ApprovalStatus; actorId: number; comment: string | null; at: string }
export interface PageResponse<T> { items: T[]; page: number; size: number; total: number }

export type Periodicity = 'ONE_TIME' | 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY';
export interface PurposeView {
  id: number; refNo: string; donorOrgId: number; name: string; description: string; sector: string;
  commitmentAmount: number | null; periodicity: Periodicity; approved: boolean;
}
export interface CandidateRow {
  id: number; needId: number; needName: string; kind: 'CAUSE' | 'PROJECT'; sector: string; remaining: number;
  score: number; engineRank: number; explanation: string; decision: 'PENDING' | 'APPROVED' | 'REJECTED';
  remark: string | null; priority: number | null;
}
export interface CandidateDecision { candidateId: number; approve: boolean; remark: string | null; priority: number | null }
export interface DonationResult { donationId: number; refNo: string; paid: number; commitmentLeft: number | null; nextDue: string | null }

export interface NeedView {
  id: number; refNo: string; orgId: number; kind: 'CAUSE' | 'PROJECT'; name: string; description: string; sector: string;
  targetAmount: number; raisedAmount: number; periodicity: Periodicity; approved: boolean; completed: boolean;
}
export interface MatchRow { id: number; refNo: string; needId: number; needName: string; kind: 'CAUSE' | 'PROJECT'; remaining: number; priority: number }
export interface SuggestedBenefactor { purposeId: number; purposeName: string; donorOrgId: number; score: number }
export interface DocRow { id: number; typeId: number; name: string; version: number; state: 'PENDING' | 'REVIEWED' | 'REJECTED'; rejectionReason: string | null }
/** Staff list of every organisation's documents (GET /documents/all); reviewedBy/At are null while it waits. */
export interface DocumentListRow {
  id: number; ownerOrgId: number; ownerName: string; typeId: number; typeName: string; name: string; version: number;
  state: DocRow['state']; rejectionReason: string | null; uploadedBy: string | null; uploadedAt: string;
  reviewedBy: string | null; reviewedAt: string | null;
}
export interface ReviewRow { id: number; ownerOrgId: number; ownerName: string; typeId: number; typeName: string; name: string; version: number; createdBy: string | null }
/** mandatoryFor: the organisation types this document is compulsory for (upload forms highlight it). */
export interface DocumentType { id: number; code: string; name: string; category: string; shareable: boolean; mandatoryFor?: OrganisationType[] }
/** Mirrors ShareRequest.Status on the server. */
export interface ShareRow {
  id: number; needId: number; needName: string; donorOrgId: number; donorName: string; ownerOrgId: number; ownerName: string;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED';
}
export interface Option { code: string; name: string }
export interface OrgSummary { id: number; refNo: string; name: string; type: string; city: string | null; state: string | null; verified: boolean }
export interface ContactRow { role: 'PRIMARY' | 'SECONDARY' | 'SIGNATORY'; name: string; designation: string | null; email: string | null; phone: string | null }
export interface BankRow { id: number; accountName: string; accountNumber: string; ifsc: string; bankName: string | null }
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'DISABLED';
export interface AdminUserRow { id: number; email: string; fullName: string; role: Role; roleCode: string; roleName: string; status: UserStatus; orgId: number | null; hasPassword: boolean; createdAt: string }
export interface ImportError { row: number; column: string; message: string }
export interface ImportJob {
  id: number; type: 'NEEDS' | 'PURPOSES'; fileName: string; status: 'COMPLETED' | 'REJECTED'; rowCount: number; createdCount: number;
  errorCount: number; createdBy: string | null; createdAt: string; errors: ImportError[];
}
export interface HistoryChange { field: string; before: string | null; after: string | null }
export interface HistoryEntry { revision: number; timestamp: number; user: string; kind: 'CREATED' | 'UPDATED' | 'DELETED'; changes: HistoryChange[]; requestId: string | null }

/** Kinds of record an audited action acts on (AuditTarget.java). */
export type AuditTargetType = 'USER' | 'ORGANISATION' | 'PURPOSE' | 'NEED' | 'DOCUMENT' | 'SHARE_REQUEST' | 'DONATION' | 'MATCH_RUN' | 'IMPORT_JOB'
  | 'ROLE' | 'PERMISSION' | 'SECTOR' | 'DOCUMENT_TYPE';
export type AuditOutcome = 'SUCCESS' | 'DENIED' | 'FAILED';
/** One line of an audit trail (AuditEntry.java); ip and userAgent only for people with audit.view. */
export interface AuditEntry {
  id: number; at: string; actorName: string | null; actorEmail: string | null; actorRole: string | null; action: string; label: string;
  category: string; targetType: AuditTargetType | null; targetId: string | null; outcome: AuditOutcome; errorCode: string | null; detail: string | null;
  ip: string | null; userAgent: string | null; requestId: string | null; changes: HistoryChange[];
}

export type OrganisationType = 'DONOR_INDIVIDUAL' | 'DONOR_CORPORATE' | 'SCHOOL' | 'NGO';
export interface SectorAdminRow { code: string; name: string; active: boolean; usedBy: number }
export type DocumentCategory = 'REGISTRATION' | 'DONATION' | 'NEED' | 'MEDIA' | 'ACKNOWLEDGEMENT';
export interface DocumentTypeAdminRow {
  id: number; code: string; name: string; category: DocumentCategory; shareable: boolean; active: boolean; mandatoryFor: OrganisationType[];
}

export interface DetailColumn { key: string; label: string; type: 'text' | 'money' | 'date' | 'count' | 'yesno' }
export type DetailValues = Record<string, string | number | boolean | null>;
export interface DetailChild { title: string; columns: DetailColumn[]; rows: DetailValues[] }
export interface DetailRow { values: DetailValues; children: DetailChild | null }
export interface DetailTable { total: number; truncated: boolean; columns: DetailColumn[]; rows: DetailRow[] }
export interface OpenNeed {
  id: number; refNo: string; name: string; kind: 'CAUSE' | 'PROJECT'; sector: string; orgId: number; orgName: string; orgGroup: string;
  targetAmount: number; raisedAmount: number; remaining: number;
}
export interface OrganisationOption { id: number; refNo: string; name: string; type: OrganisationType; group: string; verified: boolean }
export interface Counters { approvalsWaiting: number; documentsToReview: number; shareRequestsWaiting: number }

/** Beneficiary reports from the original project (/beneficiary-reports). */
export type BeneficiaryReportType = 'donations' | 'matches' | 'needs' | 'custom';
export interface ReportColumn { key: string; label: string; type: 'text' | 'money' | 'date' }
export interface ReportResult {
  title: string; entityLine: string; filterLine: string; columns: ReportColumn[];
  rows: Record<string, string | number | null>[]; total: number; page: number; size: number;
}
export interface ReportOptions {
  organisation: { id: number; name: string; refNo: string; type: 'SCHOOL' | 'NGO'; group: string };
  needs: { id: number; refNo: string; name: string; approved: boolean }[];
  years: string[];
  customColumns: { key: string; label: string }[];
}
export interface ReportRequest {
  orgId?: number | null; needId?: number | null; year?: string | null; approvedFrom?: string | null; approvedTo?: string | null;
  columns?: string[]; header?: string | null; page?: number; size?: number; all?: boolean;
}

/** Roles and permissions screen (/admin/access). */
export interface AccessRole {
  code: string; name: string; description: string | null; kind: Role; kindLabel: string; system: boolean; users: number; permissions: string[];
}
export interface AccessPermission { code: string; name: string; description: string | null; category: string; system: boolean; roles: string[] }
export interface RoleKindOption { kind: Role; label: string }
export interface AccessChange { at: string; actor: string; action: string; subject: string; details: string | null }
