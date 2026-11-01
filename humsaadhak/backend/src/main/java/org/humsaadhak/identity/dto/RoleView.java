package org.humsaadhak.identity.dto;

import java.util.*;
import org.humsaadhak.shared.security.Role;

public record RoleView(String code, String name, String description, Role kind, String kindLabel, boolean system, long users, List<String> permissions) {}
