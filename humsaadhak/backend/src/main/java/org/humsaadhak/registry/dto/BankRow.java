package org.humsaadhak.registry.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

public record BankRow(long id, String accountName, String accountNumber, String ifsc, String bankName) {}
