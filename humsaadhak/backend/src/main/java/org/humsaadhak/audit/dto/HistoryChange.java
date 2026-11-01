package org.humsaadhak.audit.dto;

import java.util.*;

public record HistoryChange(String field, String before, String after) {}
