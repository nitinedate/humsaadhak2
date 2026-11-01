package org.humsaadhak.identity.dto;

import org.humsaadhak.shared.security.Role;

public record Registration(String email, String fullName, String mobile, Role role,
                    String organisationName, String city, String state, boolean consent) {}
