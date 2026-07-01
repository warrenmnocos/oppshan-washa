package com.oppshan.washa.user;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Abstract base for one external identity linked to a household {@link UserAccount}. A person can
 * link several of these: the household is closed, but each member may sign in from more than one
 * Google account. Internally the row is keyed by a surrogate UUID; externally an identity is pinned
 * by ({@code providerName}, {@code providerId}), where {@code providerId} is the provider's stable
 * subject. washa keys on that subject rather than the email because the subject never changes, while
 * a Google account's email can.
 *
 * <p>JOINED inheritance: each provider subtype ({@link GoogleAccount} today) gets its own table
 * sharing this row's PK. The unique constraint on ({@code provider_id}, {@code provider_name},
 * {@code user_account_uuid}) stops the same identity being linked to a person twice.
 */
@Entity
@Table(name = "idp_account",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_idp_account_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_idp_account_user_account_uuid",
                        columnList = "user_account_uuid"
                ),
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uc_idp_account_provider",
                        columnNames = {
                                "provider_id",
                                "provider_name",
                                "user_account_uuid"
                        }
                ),
        })
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class IdpAccount
        extends UuidEntity<IdpAccount>
        implements Comparable<IdpAccount> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Basic(optional = false)
    @Column(name = "provider_id",
            nullable = false,
            updatable = false)
    @NotEmpty
    private String providerId;

    @Basic(optional = false)
    @Column(name = "provider_name",
            nullable = false,
            updatable = false)
    @NotEmpty
    private String providerName;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false,
            targetEntity = UserAccount.class
    )
    @JoinColumn(
            name = "user_account_uuid",
            nullable = false,
            updatable = false
    )
    @NotNull
    private UserAccount userAccount;

    /**
     * The provider's stable subject (Google's {@code sub}); the durable external identity key,
     * set once and never updated.
     */
    public String getProviderId() {
        return providerId;
    }

    /** Sets the provider subject; returns this for chaining. */
    public IdpAccount setProviderId(String providerId) {
        this.providerId = providerId;
        return this;
    }

    /** The provider label (e.g. {@code google}), the other half of the external identity key. */
    public String getProviderName() {
        return providerName;
    }

    /** Sets the provider label; returns this for chaining. */
    public IdpAccount setProviderName(String providerName) {
        this.providerName = providerName;
        return this;
    }

    /** The household person this identity is linked to. */
    public UserAccount getUserAccount() {
        return userAccount;
    }

    /** Links this identity to a person; returns this for chaining. */
    public IdpAccount setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
        return this;
    }

    /**
     * Narrows this identity to a {@link GoogleAccount} when it is one, empty otherwise, so the
     * Google-only profile fields are reachable without an {@code instanceof}-and-cast.
     */
    public Optional<GoogleAccount> asGoogleAccount() {
        return Optional.of(this)
                .filter(GoogleAccount.class::isInstance)
                .map(GoogleAccount.class::cast);
    }

    /** Orders by the {@code PROVIDER_NAME} strategy: provider name, then provider id, then uuid. */
    @Override
    public int compareTo(IdpAccount other) {
        return IdpAccountComparator.PROVIDER_NAME.compare(this, other);
    }

    /** Value equality over the UUID, provider key, and audit timestamps. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final IdpAccount that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               Objects.equals(providerId, that.providerId) &&
               Objects.equals(providerName, that.providerName) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /** Hash of the fields {@code equals} uses. */
    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                providerId,
                providerName,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /** Diagnostic dump of the UUID, provider key, and audit timestamps. */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("providerId", providerId)
                .add("providerName", providerName)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }

    /**
     * Ordering for {@code IdpAccount}, as a named-comparator enum. Every step wraps its extractor in
     * {@code nullsLast(naturalOrder())}: a not-yet-persisted account can carry a null {@code uuid}
     * (and {@code TreeSet} compares an element against itself on first insert), which would NPE a
     * bare comparator. The chain ends on {@code uuid} as a tie-breaker so the order stays consistent
     * with {@code equals}.
     */
    public enum IdpAccountComparator implements Comparator<IdpAccount> {
        PROVIDER_NAME(Comparator
                .comparing(IdpAccount::getProviderName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IdpAccount::getProviderId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IdpAccount::getUuid, Comparator.nullsLast(Comparator.naturalOrder()))),
        ;

        private final Comparator<IdpAccount> comparator;

        /** Wraps the named comparison strategy. */
        IdpAccountComparator(Comparator<IdpAccount> comparator) {
            this.comparator = comparator;
        }

        /** Delegates to the wrapped comparator. */
        @Override
        public int compare(IdpAccount a,
                           IdpAccount b) {
            return comparator.compare(a, b);
        }
    }
}
