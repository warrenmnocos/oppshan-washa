package com.oppshan.washa.user;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@Entity
@Table(name = "user_account",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_user_account_created_at",
                        columnList = "created_at"
                ),
        })
public class UserAccount
        extends UuidEntity<UserAccount>
        implements Comparable<UserAccount> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @OneToMany(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            mappedBy = "userAccount",
            fetch = FetchType.LAZY,
            targetEntity = IdpAccount.class
    )
    @NotNull
    private SortedSet<@NotNull IdpAccount> idpAccounts;

    public String getFirstName() {
        return firstName;
    }

    public UserAccount setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public UserAccount setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public Set<IdpAccount> getIdpAccounts() {
        idpAccounts = Objects.requireNonNullElseGet(idpAccounts, TreeSet::new);
        return idpAccounts;
    }

    @Override
    public int compareTo(UserAccount other) {
        return UserAccountComparator.FIRST_NAME.compare(this, other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final UserAccount that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                firstName,
                lastName,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("firstName", firstName)
                .add("lastName", lastName)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }

    @PrePersist
    private void onPrePersist() {
        initialize();
    }

    @PostLoad
    private void onPostLoad() {
        initialize();
    }

    private void initialize() {
        idpAccounts = Objects.requireNonNullElseGet(idpAccounts, TreeSet::new);
    }

    public enum UserAccountComparator implements Comparator<UserAccount> {
        FIRST_NAME(Comparator
                .comparing(UserAccount::getFirstName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserAccount::getLastName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserAccount::getUuid, Comparator.nullsLast(Comparator.naturalOrder()))),
        ;

        private final Comparator<UserAccount> comparator;

        UserAccountComparator(Comparator<UserAccount> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(UserAccount a,
                           UserAccount b) {
            return comparator.compare(a, b);
        }
    }
}
