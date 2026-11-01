package org.humsaadhak.reporting.dto;

import java.time.LocalDate;
import java.util.*;

public record ReportRequest(Long orgId, Long needId, String year, LocalDate approvedFrom, LocalDate approvedTo, List<String> columns,
               String header, Integer page, Integer size, Boolean all) {}
