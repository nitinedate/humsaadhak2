package org.humsaadhak.identity.dto;

import java.util.*;

public record PermissionView(String code, String name, String description, String category, boolean system, List<String> roles) {}
