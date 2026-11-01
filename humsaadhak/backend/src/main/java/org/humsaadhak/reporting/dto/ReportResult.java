package org.humsaadhak.reporting.dto;

import java.util.*;

public record ReportResult(String title, String entityLine, String filterLine, List<ReportColumn> columns, List<Map<String, Object>> rows,
              long total, int page, int size) {}
