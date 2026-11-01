package org.humsaadhak.referencedata.controller;

import java.util.List;
import org.humsaadhak.referencedata.dto.DocumentTypeOption;
import org.humsaadhak.referencedata.dto.Option;
import org.humsaadhak.referencedata.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public lookups for forms. Cached by the browser for a day. */
@RestController
@RequestMapping("/api/v2/reference")
public class ReferenceController {
    private final ReferenceDataService reference;

    public ReferenceController(ReferenceDataService reference) { this.reference = reference; }

    @GetMapping("/periodicities") public List<Option> periodicities() { return reference.periodicities(); }
    @GetMapping("/sectors") public List<Option> sectors() { return reference.sectors(); }
    @GetMapping("/states") public List<Option> states() { return reference.states(); }
    @GetMapping("/document-types") public List<DocumentTypeOption> documentTypes() { return reference.documentTypes(); }
}
