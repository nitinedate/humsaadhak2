package org.humsaadhak.registry.dto;

import org.humsaadhak.registry.entity.OrgContact;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

public record ContactRow(OrgContact.ContactRole role, String name, String designation, String email, String phone) {}
