package org.humsaadhak.reporting.dto;

import java.util.*;

public record DetailTable(long total, boolean truncated, List<DetailColumn> columns, List<DetailRow> rows) {}
