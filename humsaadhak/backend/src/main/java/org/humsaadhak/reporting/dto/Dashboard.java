package org.humsaadhak.reporting.dto;

import java.util.List;

public record Dashboard(String role, String title, List<Kpi> kpis, List<Donut> donuts, List<BarChart> bars) {}
