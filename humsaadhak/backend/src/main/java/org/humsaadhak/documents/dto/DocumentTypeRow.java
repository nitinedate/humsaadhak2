package org.humsaadhak.documents.dto;

import org.humsaadhak.documents.entity.DocumentType;
import java.util.*;
import org.springframework.web.bind.annotation.*;

public record DocumentTypeRow(long id, String code, String name, DocumentType.Category category, boolean shareable, boolean active, Set<String> mandatoryFor) {}
