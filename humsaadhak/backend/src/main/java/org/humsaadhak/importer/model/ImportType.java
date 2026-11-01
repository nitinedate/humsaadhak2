package org.humsaadhak.importer.model;

import java.util.List;

/** What can be imported, with the exact column headers of each template. */
public enum ImportType {
    NEEDS(List.of("Organisation ref no", "Kind", "Name", "Description", "Sector", "Target amount", "Periodicity"),
          List.of("IB100412", "Project", "Tablet learning programme, classes 6-8",
                  "Shared tablets with offline lessons in Marathi for six rural schools, classes 6 to 8.", "Education", "3,80,000", "One-time")),
    PURPOSES(List.of("Donor ref no", "Name", "Description", "Sector", "Commitment amount", "Periodicity"),
             List.of("BN100011", "Digital classrooms for rural schools",
                     "Smart classrooms and computer labs in rural zilla parishad schools, classes 5 to 10, with teacher training.", "Education", "5,00,000", "Yearly"));

    public static final int MAX_ROWS = 2000;
    private final List<String> columns;
    private final List<String> example;

    ImportType(List<String> columns, List<String> example) { this.columns = columns; this.example = example; }

    public List<String> columns() { return columns; }
    public List<String> example() { return example; }
}
