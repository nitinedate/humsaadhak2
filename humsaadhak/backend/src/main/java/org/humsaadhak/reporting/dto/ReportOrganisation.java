package org.humsaadhak.reporting.dto;

import java.util.*;

public record ReportOrganisation(long id, String name, String refNo, String type, String group) {
    public boolean school() { return "SCHOOL".equals(type); }
}
