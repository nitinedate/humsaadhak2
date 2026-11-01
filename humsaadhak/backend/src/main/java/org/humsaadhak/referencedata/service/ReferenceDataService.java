package org.humsaadhak.referencedata.service;

import java.util.Arrays;
import java.util.List;
import org.humsaadhak.referencedata.dao.ReferenceDataDao;
import org.humsaadhak.referencedata.dto.DocumentTypeOption;
import org.humsaadhak.referencedata.dto.Option;
import org.humsaadhak.shared.model.Periodicity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Choices offered by forms: periodicities, active sectors, states and active document types. */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {
    private final ReferenceDataDao reference;

    public ReferenceDataService(ReferenceDataDao reference) { this.reference = reference; }

    public List<Option> periodicities() { return Arrays.stream(Periodicity.values()).map(p -> new Option(p.name(), label(p))).toList(); }
    public List<Option> sectors() { return reference.activeSectors(); }
    public List<Option> states() { return reference.states(); }
    public List<DocumentTypeOption> documentTypes() { return reference.activeDocumentTypes(); }

    static String label(Periodicity p) {
        return switch (p) {
            case ONE_TIME -> "One-time"; case MONTHLY -> "Monthly"; case QUARTERLY -> "Quarterly";
            case HALF_YEARLY -> "Half-yearly"; case YEARLY -> "Yearly";
        };
    }
}
