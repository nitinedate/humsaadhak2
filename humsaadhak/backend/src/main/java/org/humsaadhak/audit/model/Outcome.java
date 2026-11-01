package org.humsaadhak.audit.model;

/** How an audited action ended: done, refused (no permission or not their record), or failed (a rule or an error). */
public enum Outcome { SUCCESS, DENIED, FAILED }
