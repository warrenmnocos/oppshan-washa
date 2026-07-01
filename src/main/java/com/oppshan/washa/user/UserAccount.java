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

/**
 * A person in the closed two-person household: the stable owner of budget data, identified by a
 * surrogate UUID rather than by any login. The linked sign-in identities hang off
 * {@link #getIdpAccounts()} (a person can have several). First and last name are optional and may
 * be null until seeded.
 */
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

    /** The person's given name; may be null until seeded. */
    public String getFirstName() {
        return firstName;
    }

    /** Sets the given name; returns this for chaining. */
    public UserAccount setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    /** The person's family name; may be null until seeded. */
    public String getLastName() {
        return lastName;
    }

    /** Sets the family name; returns this for chaining. */
    public UserAccount setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    /**
     * The linked sign-in identities, lazily initialized to an empty sorted set so it's never null.
     * Backed by a {@code TreeSet}, so iteration follows {@link IdpAccount}'s comparator.
     */
    public Set<IdpAccount> getIdpAccounts() {
        idpAccounts = Objects.requireNonNullElseGet(idpAccounts, TreeSet::new);
        return idpAccounts;
    }

    /** Orders by the {@code FIRST_NAME} strategy: first name, then last name, then uuid. */
    @Override
    public int compareTo(UserAccount other) {
        return UserAccountComparator.FIRST_NAME.compare(this, other);
    }

    /** Value equality over the UUID, names, and audit timestamps. */
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

    /** Hash of the fields {@code equals} uses. */
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

    /** Diagnostic dump of the UUID, names, and audit timestamps. */
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

    /** {@code @PrePersist} hook: initializes the identities collection before an insert. */
    @PrePersist
    private void onPrePersist() {
        initialize();
    }

    /** {@code @PostLoad} hook: initializes the identities collection after a load. */
    @PostLoad
    private void onPostLoad() {
        initialize();
    }

    /**
     * Guarantees {@code idpAccounts} is a non-null {@code TreeSet} after a load and before a
     * persist, so the field is never null even when reached directly. The getter lazily inits it
     * too; these hooks cover the paths that touch the field without going through the getter.
     */
    private void initialize() {
        idpAccounts = Objects.requireNonNullElseGet(idpAccounts, TreeSet::new);
    }

    /**
     * Ordering for {@code UserAccount}, as a named-comparator enum. Each step wraps its extractor in
     * {@code nullsLast(naturalOrder())} so a null name or a pre-persist null {@code uuid} can't NPE
     * the comparison ({@code TreeSet} compares an element against itself on first insert). The
     * {@code uuid} tie-breaker keeps the order consistent with {@code equals}.
     */
    public enum UserAccountComparator implements Comparator<UserAccount> {
        FIRST_NAME(Comparator
                .comparing(UserAccount::getFirstName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserAccount::getLastName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserAccount::getUuid, Comparator.nullsLast(Comparator.naturalOrder()))),
        ;

        private final Comparator<UserAccount> comparator;

        /** Wraps the named comparison strategy. */
        UserAccountComparator(Comparator<UserAccount> comparator) {
            this.comparator = comparator;
        }

        /** Delegates to the wrapped comparator. */
        @Override
        public int compare(UserAccount a,
                           UserAccount b) {
            return comparator.compare(a, b);
        }
    }
}
