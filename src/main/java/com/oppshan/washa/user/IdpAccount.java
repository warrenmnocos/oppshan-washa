package com.oppshan.washa.user;

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

@Entity
@Table(name = "idp_account",
        schema = "oppshan",
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

    public String getProviderId() {
        return providerId;
    }

    public IdpAccount setProviderId(String providerId) {
        this.providerId = providerId;
        return this;
    }

    public String getProviderName() {
        return providerName;
    }

    public IdpAccount setProviderName(String providerName) {
        this.providerName = providerName;
        return this;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public IdpAccount setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
        return this;
    }

    public Optional<GoogleAccount> asGoogleAccount() {
        return Optional.of(this)
                .filter(GoogleAccount.class::isInstance)
                .map(GoogleAccount.class::cast);
    }

    @Override
    public int compareTo(IdpAccount other) {
        return IdpAccountComparator.PROVIDER_NAME.compare(this, other);
    }

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
               Objects.equals(providerName, that.providerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUuid(), providerId, providerName);
    }

    public enum IdpAccountComparator implements Comparator<IdpAccount> {
        PROVIDER_NAME(Comparator
                .comparing(IdpAccount::getProviderName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IdpAccount::getProviderId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(IdpAccount::getUuid, Comparator.nullsLast(Comparator.naturalOrder()))),
        ;

        private final Comparator<IdpAccount> comparator;

        IdpAccountComparator(Comparator<IdpAccount> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(IdpAccount a, IdpAccount b) {
            return comparator.compare(a, b);
        }
    }
}
