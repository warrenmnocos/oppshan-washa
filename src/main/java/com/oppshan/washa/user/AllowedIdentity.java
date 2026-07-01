package com.oppshan.washa.user;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.AuditableEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the sign-in allowlist: a single permitted Google email mapped to the household person
 * who owns it. The email is the natural key (one entry per address), so this extends
 * {@link AuditableEntity} for the audit columns rather than
 * {@link com.oppshan.washa.common.UuidEntity}.
 *
 * <p>The owning person is held as a raw {@code user_account_uuid}, not a mapped {@code @ManyToOne}:
 * this is a lean lookup table, so it points at the person by id rather than mapping the association.
 */
@Entity
@Table(name = "allowed_identity", schema = "washa")
public class AllowedIdentity extends AuditableEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @Column(name = "email",
            nullable = false,
            updatable = false)
    @NotEmpty
    private String email;

    @Basic(optional = false)
    @Column(name = "user_account_uuid",
            nullable = false)
    @NotNull
    private UUID userAccountUuid;

    /** The permitted email address, this row's natural key. */
    public String getEmail() {
        return email;
    }

    /** Sets the permitted email; returns this for chaining. */
    public AllowedIdentity setEmail(String email) {
        this.email = email;
        return this;
    }

    /** The UUID of the household person this email may sign in as. */
    public UUID getUserAccountUuid() {
        return userAccountUuid;
    }

    /** Sets the owning person's UUID; returns this for chaining. */
    public AllowedIdentity setUserAccountUuid(UUID userAccountUuid) {
        this.userAccountUuid = userAccountUuid;
        return this;
    }

    /** Value equality over the email, owning-person UUID, and audit timestamps. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final AllowedIdentity that)) {
            return false;
        }

        return Objects.equals(email, that.email) &&
               Objects.equals(userAccountUuid, that.userAccountUuid) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /** Hash of the fields {@code equals} uses. */
    @Override
    public int hashCode() {
        return Objects.hash(
                email,
                userAccountUuid,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /** Diagnostic dump of the email, owning-person UUID, and audit timestamps. */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("email", email)
                .add("userAccountUuid", userAccountUuid)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
