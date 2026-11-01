import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { OrganisationOption } from '../../shared/api/types';
import { SearchSelect } from '../../shared/ui/SearchSelect';
import { useSectors } from '../../shared/api/reference';
import type { Periodicity } from '../../shared/api/types';
import { PERIODICITY_LABEL } from '../../shared/format';
import { type CatalogueDraft, MIN_DESCRIPTION, validateDraft } from './forms';

/** Shared form for purposes (donor side) and causes/projects (beneficiary side). */
export function CatalogueForm({ initial, kind, amountLabel, amountRequired, busy, serverError, onSave, orgGroup }: {
  initial: CatalogueDraft; kind?: boolean; amountLabel: string; amountRequired: boolean; busy: boolean; serverError: string | null;
  /** Set for staff: which organisations they may create this record for (from /lookups/organisations). */
  orgGroup?: 'DONORS' | 'BENEFICIARIES';
  onSave: (draft: CatalogueDraft, submit: boolean) => void;
}) {
  const sectors = useSectors();
  const orgs = useQuery({ queryKey: ['lookups', orgGroup], enabled: !!orgGroup,
    queryFn: () => api<OrganisationOption[]>(`/lookups/organisations?group=${orgGroup}`) });
  const [d, setD] = useState<CatalogueDraft>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (patch: Partial<CatalogueDraft>) => setD((x) => ({ ...x, ...patch }));

  function save(e: FormEvent, submit: boolean) {
    e.preventDefault();
    const found = validateDraft(d, amountRequired, !!orgGroup);
    setErrors(found);
    if (Object.keys(found).length === 0) onSave(d, submit);
  }

  const err = (k: string) => errors[k] && <span className="error" id={`${k}-err`}>{errors[k]}</span>;
  return (
    <form onSubmit={(e) => save(e, false)} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {serverError && <div className="alert alert-danger" role="alert">{serverError}</div>}
      {orgGroup && (
        <SearchSelect label={orgGroup === 'DONORS' ? 'Donor' : 'School or NGO'} loading={orgs.isLoading} value={d.orgId ?? null}
                      onChange={(v) => {
                        const o = orgs.data?.find((x) => x.id === v);
                        // a school's need is a cause and an NGO's a project, so the type follows the organisation
                        set({ orgId: v, ...(o && kind ? { kind: o.type === 'NGO' ? 'PROJECT' : 'CAUSE' } : {}) });
                      }}
                      error={errors.org ?? null}
                      options={(orgs.data ?? []).map((o) => ({ value: o.id, label: o.name, detail: `${o.group}, ${o.refNo}${o.verified ? '' : ', not verified yet'}` }))} />
      )}
      {kind && (
        <div className="field">
          <span className="label" id="kind-l">Type</span>
          <div className="segmented" role="radiogroup" aria-labelledby="kind-l">
            {(['CAUSE', 'PROJECT'] as const).map((k) => (
              <button key={k} type="button" role="radio" aria-checked={d.kind === k} className="chip-btn" onClick={() => set({ kind: k })}>
                {k === 'CAUSE' ? 'School cause' : 'NGO project'}
              </button>
            ))}
          </div>
        </div>
      )}
      <div className="field">
        <label htmlFor="f-name">Name</label>
        <input id="f-name" className="form-control" value={d.name} onChange={(e) => set({ name: e.target.value })} aria-invalid={!!errors.name} aria-describedby="name-err" />
        {err('name')}
      </div>
      <div className="field">
        <label htmlFor="f-desc">Description</label>
        <textarea id="f-desc" className="form-control" rows={5} value={d.description} onChange={(e) => set({ description: e.target.value })}
                  aria-invalid={!!errors.description} aria-describedby="desc-hint description-err" />
        <span className="hint" id="desc-hint">{d.description.trim().length} / {MIN_DESCRIPTION}+ characters. The matching engine reads this, so be specific.</span>
        {err('description')}
      </div>
      <div className="field-row">
        <div className="field">
          <label htmlFor="f-sector">Sector</label>
          <select id="f-sector" className="form-select" value={d.sector} onChange={(e) => set({ sector: e.target.value })} aria-invalid={!!errors.sector}>
            <option value="">Choose…</option>
            {sectors.data?.map((s) => <option key={s.code} value={s.name}>{s.name}</option>)}
          </select>
          {err('sector')}
        </div>
        <div className="field">
          <label htmlFor="f-amount">{amountLabel}</label>
          <input id="f-amount" className="form-control num" inputMode="numeric" value={d.amount} onChange={(e) => set({ amount: e.target.value })} aria-invalid={!!errors.amount} />
          {err('amount')}
        </div>
        <div className="field">
          <label htmlFor="f-per">How often</label>
          <select id="f-per" className="form-select" value={d.periodicity} onChange={(e) => set({ periodicity: e.target.value as Periodicity })}>
            {Object.entries(PERIODICITY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
      </div>
      <div className="segmented">
        <button type="button" className="btn btn-primary" disabled={busy} onClick={(e) => save(e, true)}>Save and submit for approval</button>
        <button type="submit" className="btn btn-outline-secondary" disabled={busy}>Save as draft</button>
      </div>
    </form>
  );
}
