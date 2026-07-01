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
 * Audit base plus a surrogate UUID primary key, for entities that don't have a natural key. The
 * key is a VERSION_7 UUID: time-ordered, so new rows land at the tail of the primary-key B-tree
 * instead of scattering inserts across it the way random v4 UUIDs would.
 *
 * <p>The self-referential type parameter lets the fluent {@link #setUuid(UUID)} return the concrete
 * subtype rather than {@code UuidEntity}, so setter chains keep their real static type. Entities
 * with natural keys ({@code currency_setting}, {@code fx_rate}, {@code allowed_identity}) skip this
 * base and extend {@link AuditableEntity} directly, declaring their own id.
 *
 * @param <T> the concrete entity subtype, bound as a self-type ({@code Foo extends UuidEntity<Foo>})
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

    /**
     * Returns the surrogate VERSION_7 UUID primary key; {@code null} before {@code @UuidGenerator}
     * assigns it at persist.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Sets the UUID and returns {@code this} as the concrete subtype {@code T} for fluent chaining.
     * The {@code (T) this} cast is unchecked but safe: {@code T} is bound to this entity's own type by
     * the self-type generic ({@code Foo extends UuidEntity<Foo>}).
     */
    @SuppressWarnings("unchecked")
    public T setUuid(UUID uuid) {
        this.uuid = uuid;
        return (T) this;
    }
}
