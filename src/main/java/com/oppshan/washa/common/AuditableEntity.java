package com.oppshan.washa.common;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Audit base for every entity. {@code created_at} is stamped once on insert
 * (Hibernate {@link CreationTimestamp}); {@code last_modified_at} is the temporal
 * optimistic-lock {@link Version} — Hibernate stamps it on every flush, so it also
 * serves as the "last touched" timestamp. Do not set either field manually, and do
 * not add a separate {@code @UpdateTimestamp} (it would conflict with the version).
 */
@MappedSuperclass
public abstract class AuditableEntity implements Serializable {

    @CreationTimestamp
    @Basic(optional = false)
    @Column(name = "created_at",
            nullable = false,
            updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "last_modified_at",
            nullable = false)
    private Instant lastModifiedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }
}
