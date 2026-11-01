package org.humsaadhak.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

public record DocumentTypeUpdateBody(@NotBlank @Size(max = 255) String name, boolean shareable, boolean active, Set<String> mandatoryFor) {}
