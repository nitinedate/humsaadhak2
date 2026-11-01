package org.humsaadhak.audit.entity;

import jakarta.persistence.*;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/** Envers revision with the acting user's email and the request that made it, so history shows who changed what and why. */
@Entity
@Table(name = "revinfo")
@RevisionEntity(UsernameRevisionListener.class)
public class AuditRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(name = "revinfo_seq", sequenceName = "revinfo_seq", allocationSize = 50)
    @RevisionNumber
    @Column(name = "rev")
    private int id;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    @Column(name = "username")
    private String username;
    /** The request that made this revision: links field changes to the audited action. */
    @Column(name = "request_id")
    private String requestId;

    public int getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
