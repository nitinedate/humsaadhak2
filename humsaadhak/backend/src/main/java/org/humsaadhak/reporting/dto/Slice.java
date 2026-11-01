package org.humsaadhak.reporting.dto;

public record Slice(String label, double value, String colorKey, Drill drill) {
    public Slice(String label, double value, String colorKey) { this(label, value, colorKey, null); }
}
