package com.oppshan.washa.user;

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
 * Natural-key entity (PK = email). Extends {@link AuditableEntity} for the audit columns;
 * not {@link com.oppshan.washa.common.UuidEntity} because the email is the key.
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

    public String getEmail() {
        return email;
    }

    public AllowedIdentity setEmail(String email) {
        this.email = email;
        return this;
    }

    public UUID getUserAccountUuid() {
        return userAccountUuid;
    }

    public AllowedIdentity setUserAccountUuid(UUID userAccountUuid) {
        this.userAccountUuid = userAccountUuid;
        return this;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(
                email,
                userAccountUuid,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    @Override
    public String toString() {
        return "AllowedIdentity{" +
               "email=" + email +
               ", userAccountUuid=" + userAccountUuid +
               ", createdAt=" + getCreatedAt() +
               ", lastModifiedAt=" + getLastModifiedAt() +
               '}';
    }
}
