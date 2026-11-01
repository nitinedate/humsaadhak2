package org.humsaadhak.reporting.dto;

import java.util.List;

/** drills[i] is what clicking series i of this bar lists (null = not clickable). */
public record Bar(String label, List<Double> values, List<Drill> drills) {
    public Bar(String label, List<Double> values) { this(label, values, null); }
}
