package org.humsaadhak.reporting.dto;

import java.util.List;

public record BarChart(String title, List<String> series, String unit, List<Bar> bars) {}
