package org.humsaadhak.referencedata.dto;

import org.springframework.web.bind.annotation.*;

public record SectorRow(String code, String name, boolean active, long usedBy) {}
