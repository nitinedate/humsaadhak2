package org.humsaadhak.reporting.dto;

import java.util.*;

public record DetailRow(Map<String, Object> values, DetailChild children) {}
