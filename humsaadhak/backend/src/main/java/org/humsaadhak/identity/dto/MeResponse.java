package org.humsaadhak.identity.dto;

import java.util.Set;
import org.humsaadhak.identity.api.Theme;
import org.humsaadhak.shared.security.Role;
import org.springframework.web.bind.annotation.*;

/** role = account type (data scope, dashboard); roleCode/roleName = the assigned role; permissions = what it grants. */
public record MeResponse(long id, String email, String fullName, Long orgId, Role role, String roleCode, String roleName, Set<String> permissions, Theme theme) {}
