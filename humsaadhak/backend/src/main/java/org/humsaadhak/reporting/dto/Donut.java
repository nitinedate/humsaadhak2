package org.humsaadhak.reporting.dto;

import java.util.List;

public record Donut(String title, String totalLabel, List<Slice> slices) {}
