package org.humsaadhak.identity.dto;

import org.humsaadhak.identity.model.UserStatus;
import java.time.Instant;
import org.humsaadhak.shared.security.Role;
import org.springframework.web.bind.annotation.*;

public record UserRow(long id, String email, String fullName, Role role, String roleCode, String roleName, UserStatus status, Long orgId,
               boolean hasPassword, Instant createdAt) {}
