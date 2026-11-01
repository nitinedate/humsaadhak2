package org.humsaadhak.reporting.dto;

/** A CSV download: file name and content. */
public record CsvFile(String fileName, String content) {}
