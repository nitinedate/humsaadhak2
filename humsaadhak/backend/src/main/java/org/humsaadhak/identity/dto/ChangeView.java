package org.humsaadhak.identity.dto;

import java.util.*;

public record ChangeView(java.time.Instant at, String actor, String action, String subject, String details) {}
