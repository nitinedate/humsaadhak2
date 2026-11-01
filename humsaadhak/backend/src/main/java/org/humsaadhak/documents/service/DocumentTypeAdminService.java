package org.humsaadhak.documents.service;

import java.util.*;
import java.util.stream.Collectors;
import org.humsaadhak.documents.api.MandatoryRulesChanged;
import org.humsaadhak.documents.dto.DocumentTypeCreateBody;
import org.humsaadhak.documents.dto.DocumentTypeRow;
import org.humsaadhak.documents.dto.DocumentTypeUpdateBody;
import org.humsaadhak.documents.entity.DocumentType;
import org.humsaadhak.documents.entity.DocumentTypeRule;
import org.humsaadhak.documents.repository.DocumentTypeRepository;
import org.humsaadhak.documents.repository.DocumentTypeRuleRepository;
import org.humsaadhak.documents.util.MandatoryRuleChange;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.util.Codes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Super admins: document types and which organisation types must provide each. A change to the mandatory rules
 * publishes MandatoryRulesChanged, so the registry re-checks the organisations of the affected types.
 */
@Service
@Transactional
public class DocumentTypeAdminService {
    private final DocumentTypeRepository types;
    private final DocumentTypeRuleRepository rules;
    private final ApplicationEventPublisher events;

    public DocumentTypeAdminService(DocumentTypeRepository types, DocumentTypeRuleRepository rules, ApplicationEventPublisher events) {
        this.types = types; this.rules = rules; this.events = events;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeRow> list() {
        return types.findAll().stream().sorted(Comparator.comparing(DocumentType::getName)).map(this::row).toList();
    }

    public DocumentTypeRow create(DocumentTypeCreateBody b) {
        String code = Codes.fromName(b.name());
        if (types.existsByCode(code)) throw new DomainException("duplicate", "A document type called “" + b.name().trim() + "” already exists.");
        DocumentType t = types.save(new DocumentType(code, b.name(), b.category(), b.shareable()));
        applyMandatory(t.getId(), b.mandatoryFor() == null ? Set.of() : b.mandatoryFor());
        return row(t);
    }

    public DocumentTypeRow update(long id, DocumentTypeUpdateBody b) {
        DocumentType t = types.findById(id).orElseThrow(() -> new NotFoundException("Document type", id));
        Set<String> wanted = b.mandatoryFor() == null ? Set.of() : b.mandatoryFor();
        if (!b.active() && !wanted.isEmpty())
            throw new DomainException("inactive-mandatory", "An inactive document type cannot be mandatory. Clear “Mandatory for” first.");
        t.update(b.name(), b.shareable(), b.active());
        applyMandatory(id, wanted);
        return row(t);
    }

    private void applyMandatory(long typeId, Set<String> wanted) {
        MandatoryRuleChange.validated(wanted);
        Map<String, DocumentTypeRule> existing = rules.findByDocumentTypeId(typeId).stream()
                .collect(Collectors.toMap(DocumentTypeRule::getOrganisationType, r -> r));
        Set<String> before = existing.values().stream().filter(DocumentTypeRule::isMandatory)
                .map(DocumentTypeRule::getOrganisationType).collect(Collectors.toSet());
        for (String orgType : MandatoryRuleChange.ORGANISATION_TYPES) {
            boolean mandatory = wanted.contains(orgType);
            DocumentTypeRule r = existing.get(orgType);
            if (r != null) r.setMandatory(mandatory);
            else if (mandatory) rules.save(new DocumentTypeRule(typeId, orgType, true));
        }
        Set<String> affected = MandatoryRuleChange.affected(before, wanted);
        if (!affected.isEmpty()) events.publishEvent(new MandatoryRulesChanged(affected));
    }

    private DocumentTypeRow row(DocumentType t) {
        Set<String> mandatory = rules.findByDocumentTypeId(t.getId()).stream().filter(DocumentTypeRule::isMandatory)
                .map(DocumentTypeRule::getOrganisationType).collect(Collectors.toCollection(TreeSet::new));
        return new DocumentTypeRow(t.getId(), t.getCode(), t.getName(), t.getCategory(), t.isShareable(), t.isActive(), mandatory);
    }
}
