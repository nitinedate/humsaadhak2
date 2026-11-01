package org.humsaadhak.reporting.dto;

import java.util.*;

public record ReportOptions(ReportOrganisation organisation, List<NeedOption> needs, List<String> years, List<ColumnOption> customColumns) {}
