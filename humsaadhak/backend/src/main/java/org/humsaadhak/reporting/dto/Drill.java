package org.humsaadhak.reporting.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** What to list when a chart segment is clicked: a detail set plus allow-listed filters (see DetailQuery). */
public record Drill(String set, Map<String, String> filters) {
    public static Drill of(String set, String... keyValues) {
        Map<String, String> f = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) f.put(keyValues[i], keyValues[i + 1]);
        return new Drill(set, f);
    }
}
