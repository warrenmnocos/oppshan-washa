package com.oppshan.washa.common;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UuidGenerator.Style;

import java.util.UUID;

/**
 * Audit base plus a VERSION_7 (time-sortable) UUID primary key, for entities that use a
 * surrogate key. Generic self-type so the fluent {@link #setUuid(UUID)} returns the concrete
 * entity. Entities with natural keys (e.g. {@code currency_setting}, {@code fx_rate},
 * {@code allowed_identity}) extend {@link AuditableEntity} directly and declare their own id.
 *
 * @param <T> the concrete entity type
 */
@MappedSuperclass
public abstract class UuidEntity<T extends UuidEntity<T>> extends AuditableEntity {

    @Id
    @Basic(optional = false)
    @Column(name = "uuid",
            nullable = false,
            updatable = false)
    @UuidGenerator(style = Style.VERSION_7)
    @NotNull
    private UUID uuid;

    public UUID getUuid() {
        return uuid;
    }

    @SuppressWarnings("unchecked")
    public T setUuid(UUID uuid) {
        this.uuid = uuid;
        return (T) this;
    }
}
