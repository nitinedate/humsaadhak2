package org.humsaadhak.referencedata.dto;

import java.util.List;

/** A document type for forms; mandatoryFor lists the organisation types it is compulsory for (document_type_rule). */
public record DocumentTypeOption(long id, String code, String name, String category, boolean shareable, List<String> mandatoryFor) {}
