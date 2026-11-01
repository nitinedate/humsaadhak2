package org.humsaadhak.documents.dto;

import org.humsaadhak.documents.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

public record DocumentTypeCreateBody(@NotBlank @Size(max = 255) String name, @NotNull DocumentType.Category category, boolean shareable, Set<String> mandatoryFor) {}
