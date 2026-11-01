package org.humsaadhak.audit.dto;

/** Who acted, as recorded (a snapshot: later renames do not change past records). */
public record Actor(Long id, String email, String name, String role, Long orgId) {}
