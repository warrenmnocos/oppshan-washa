package com.oppshan.washa.user;

import com.google.common.base.MoreObjects;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

/**
 * A linked Google sign-in identity: the one concrete {@link IdpAccount} subtype. The stable Google
 * {@code sub} lives in the inherited {@code providerId}; this subclass adds the profile snapshot
 * taken from the Google ID token (display name, email, photo). Under JOINED inheritance the
 * {@code google_account} row shares its {@code uuid} PK with its parent {@code idp_account} row.
 *
 * <p>Only {@code email} is required and holds the identity's verified address; {@code name} and
 * {@code photoUrl} are best-effort, since a token needn't carry them. The stored values are a
 * snapshot from link time, not a live mirror of the Google profile.
 */
@Entity
@Table(name = "google_account",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_google_account_name",
                        columnList = "name"
                ),
                @Index(
                        name = "idx_google_account_email",
                        columnList = "email"
                ),
        })
public class GoogleAccount extends IdpAccount {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "name")
    private String name;

    @Basic(optional = false)
    @Column(name = "email",
            nullable = false)
    @NotEmpty
    private String email;

    @Column(name = "photo_url",
            length = 2048)
    private String photoUrl;

    /** Covariant override so the fluent {@code uuid} setter returns {@code GoogleAccount}. */
    @Override
    public GoogleAccount setUuid(UUID uuid) {
        super.setUuid(uuid);
        return this;
    }

    /** Covariant override so the fluent {@code providerId} setter returns {@code GoogleAccount}. */
    @Override
    public GoogleAccount setProviderId(String providerId) {
        super.setProviderId(providerId);
        return this;
    }

    /** Covariant override so the fluent {@code providerName} setter returns {@code GoogleAccount}. */
    @Override
    public GoogleAccount setProviderName(String providerName) {
        super.setProviderName(providerName);
        return this;
    }

    /** Covariant override so the fluent {@code userAccount} setter returns {@code GoogleAccount}. */
    @Override
    public GoogleAccount setUserAccount(UserAccount userAccount) {
        super.setUserAccount(userAccount);
        return this;
    }

    /** The display name from the Google profile snapshot; may be null. */
    public String getName() {
        return name;
    }

    /** Sets the profile display name; returns this for chaining. */
    public GoogleAccount setName(String name) {
        this.name = name;
        return this;
    }

    /** The verified email from the Google profile; required. */
    public String getEmail() {
        return email;
    }

    /** Sets the email; returns this for chaining. */
    public GoogleAccount setEmail(String email) {
        this.email = email;
        return this;
    }

    /**
     * The profile-photo URL from the snapshot; may be null. Stored at length 2048 because
     * Google profile-photo URLs, sizing suffixes and all, run long.
     */
    public String getPhotoUrl() {
        return photoUrl;
    }

    /** Sets the profile-photo URL; returns this for chaining. */
    public GoogleAccount setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
        return this;
    }

    /** Value equality over the UUID, provider key, profile fields, and audit timestamps. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final GoogleAccount that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               Objects.equals(getProviderId(), that.getProviderId()) &&
               Objects.equals(getProviderName(), that.getProviderName()) &&
               Objects.equals(name, that.name) &&
               Objects.equals(email, that.email) &&
               Objects.equals(photoUrl, that.photoUrl) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /** Hash of the fields {@code equals} uses. */
    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                getProviderId(),
                getProviderName(),
                name,
                email,
                photoUrl,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /** Diagnostic dump of the UUID, provider key, profile fields, and audit timestamps. */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("providerId", getProviderId())
                .add("providerName", getProviderName())
                .add("name", name)
                .add("email", email)
                .add("photoUrl", photoUrl)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
