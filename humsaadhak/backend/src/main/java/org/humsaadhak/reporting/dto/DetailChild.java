package org.humsaadhak.reporting.dto;

import java.util.*;

public record DetailChild(String title, List<DetailColumn> columns, List<Map<String, Object>> rows) {}
