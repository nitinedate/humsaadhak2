package org.humsaadhak.reporting.dto;

import org.springframework.web.bind.annotation.*;

public record OrganisationOption(long id, String refNo, String name, String type, String group, boolean verified) {}
