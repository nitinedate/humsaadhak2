package org.humsaadhak.importer.service;

import org.springframework.data.domain.PageRequest;
import org.humsaadhak.importer.dto.ImportErrorView;
import org.humsaadhak.importer.dto.ImportJobView;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.dto.PageResponse;
import java.util.List;
import org.humsaadhak.importer.entity.ImportJob;
import org.humsaadhak.importer.repository.ImportJobRepository;
import org.humsaadhak.importer.entity.ImportRowError;
import org.humsaadhak.importer.repository.ImportRowErrorRepository;
import org.humsaadhak.importer.model.ImportType;
import org.humsaadhak.importer.util.RowValidator;
import org.humsaadhak.importer.util.WorkbookIO;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.registry.api.Organisations;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.importer.dao.ImportLookupDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validate everything first; create only when every row is valid (all-or-nothing). */
@Service
public class ImportService {
    private final ImportJobRepository jobs;
    private final ImportRowErrorRepository rowErrors;
    private final Organisations organisations;
    private final Catalogue catalogue;
    private final ImportLookupDao lookups;

    public ImportService(ImportJobRepository jobs, ImportRowErrorRepository rowErrors, Organisations organisations,
                  Catalogue catalogue, ImportLookupDao lookups) {
        this.jobs = jobs; this.rowErrors = rowErrors; this.organisations = organisations; this.catalogue = catalogue; this.lookups = lookups;
    }

    @Transactional
    public ImportJob run(ImportType type, String fileName, InputStream in, CurrentUser me) throws IOException {
        List<WorkbookIO.Row> rows = WorkbookIO.read(in, type);
        Set<String> sectors = lookups.sectorNames();
        List<RowValidator.RowError> errors = new ArrayList<>();
        List<Runnable> creations = new ArrayList<>();

        for (WorkbookIO.Row r : rows) {
            if (type == ImportType.NEEDS) {
                RowValidator.need(r.number(), r.values(), sectors, errors).ifPresent(n -> {
                    Optional<Long> org = organisations.idByRefNo(n.orgRef());
                    if (org.isEmpty()) errors.add(new RowValidator.RowError(r.number(), "Organisation ref no", "No organisation " + n.orgRef() + "."));
                    else creations.add(() -> catalogue.createNeed(new Catalogue.NeedDraft(org.get(), n.kind(), n.name(), n.description(),
                            n.sector(), n.target(), n.periodicity()), me.id()));
                });
            } else {
                RowValidator.purpose(r.number(), r.values(), sectors, errors).ifPresent(p -> {
                    Optional<Long> org = organisations.idByRefNo(p.donorRef());
                    if (org.isEmpty()) errors.add(new RowValidator.RowError(r.number(), "Donor ref no", "No donor " + p.donorRef() + "."));
                    else creations.add(() -> catalogue.createPurpose(new Catalogue.PurposeDraft(org.get(), p.name(), p.description(),
                            p.sector(), p.amount(), p.periodicity()), me.id()));
                });
            }
        }

        ImportJob job = jobs.save(new ImportJob(type, fileName));
        job.setRowCount(rows.size());
        if (rows.isEmpty()) errors.add(new RowValidator.RowError(0, "-", "The file has no data rows."));
        if (errors.isEmpty()) {
            creations.forEach(Runnable::run);
            job.setStatus(ImportJob.Status.COMPLETED);
            job.setCreatedCount(creations.size());
        } else {
            job.setStatus(ImportJob.Status.REJECTED);
            job.setErrorCount(errors.size());
            errors.forEach(e -> rowErrors.save(new ImportRowError(job.getId(), e.row(), e.column(), e.message())));
        }
        return job;
    }

    /**
     * Runs an import and returns the job with any row errors. Transactional here because this is the entry point:
     * the call to run() below is inside this class, so it does not pass through Spring's transaction proxy.
     */
    @Transactional
    public ImportJobView upload(ImportType type, String fileName, InputStream in, CurrentUser me) throws IOException {
        return view(run(type, fileName, in, me));
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobView> recent(int page) {
        return PageResponse.of(jobs.findAllByOrderByIdDesc(PageRequest.of(page, 20)), j -> new ImportJobView(j.getId(), j.getType(), j.getFileName(),
                j.getStatus().name(), j.getRowCount(), j.getCreatedCount(), j.getErrorCount(), j.getCreatedBy(), j.getCreatedAt(), List.of()));
    }

    @Transactional(readOnly = true)
    public ImportJobView job(long id) { return view(jobs.findById(id).orElseThrow(() -> new NotFoundException("Import", id))); }

    /** An empty Excel template with the right columns for this kind of import. */
    public byte[] template(ImportType type) throws IOException { return WorkbookIO.template(type); }

    private ImportJobView view(ImportJob j) {
        List<ImportErrorView> errs = rowErrors.findByJobIdOrderByRowNumberAscIdAsc(j.getId()).stream()
                .map(e -> new ImportErrorView(e.getRowNumber(), e.getColumnName(), e.getMessage())).toList();
        return new ImportJobView(j.getId(), j.getType(), j.getFileName(), j.getStatus().name(), j.getRowCount(), j.getCreatedCount(), j.getErrorCount(),
                j.getCreatedBy(), j.getCreatedAt(), errs);
    }
}
