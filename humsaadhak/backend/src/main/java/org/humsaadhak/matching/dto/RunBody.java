package org.humsaadhak.matching.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.web.bind.annotation.*;

public record RunBody(@NotEmpty List<Long> purposeIds) {}
