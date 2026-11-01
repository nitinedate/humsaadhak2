package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.Email;
import org.springframework.web.bind.annotation.*;

public record EmailBody(@Email String email) {}
