package com.oppshan.washa.common;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Audit base for every entity: two Hibernate-managed timestamps and nothing else.
 * {@code created_at} is stamped once on insert (Hibernate {@link CreationTimestamp}) and is
 * never rewritten. {@code last_modified_at} pulls double duty as the temporal optimistic-lock
 * {@link Version}: Hibernate bumps it on every flush, so the one column both guards against
 * concurrent updates and records when the row was last touched (which is why there's no numeric
 * version column and no separate {@code updated_at}).
 *
 * <p>Never set either field by hand, and don't add a second {@code @UpdateTimestamp}: it would
 * fight the {@link Version} over {@code last_modified_at}.
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

    /**
     * Returns the insert timestamp Hibernate stamped via {@code @CreationTimestamp}; never rewritten.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the last-flush timestamp, which doubles as the optimistic-lock {@code @Version}.
     */
    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }
}
