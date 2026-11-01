package org.humsaadhak.reporting.dto;

import java.time.LocalDate;

/** One organisation in the staff CSV export (bank details are never exported). */
public record OrganisationExportRow(String refNo, String name, String type, String city, String state, boolean verified, LocalDate registeredOn) {}
