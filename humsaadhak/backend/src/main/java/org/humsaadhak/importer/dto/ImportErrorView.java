package org.humsaadhak.importer.dto;

import org.springframework.web.bind.annotation.*;

public record ImportErrorView(int row, String column, String message) {}
