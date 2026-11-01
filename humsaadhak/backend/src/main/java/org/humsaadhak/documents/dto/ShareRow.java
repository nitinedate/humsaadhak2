package org.humsaadhak.documents.dto;

import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Names are included so screens never show bare numbers. */
public record ShareRow(long id, long needId, String needName, long donorOrgId, String donorName, long ownerOrgId, String ownerName, String status) {}
