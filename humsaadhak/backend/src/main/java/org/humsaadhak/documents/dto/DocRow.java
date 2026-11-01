package org.humsaadhak.documents.dto;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record DocRow(long id, long typeId, String name, int version, String state, String rejectionReason) {}
