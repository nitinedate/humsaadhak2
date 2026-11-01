package org.humsaadhak.documents.dto;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record ReviewRow(long id, long ownerOrgId, String ownerName, long typeId, String typeName, String name, int version, String createdBy) {}
