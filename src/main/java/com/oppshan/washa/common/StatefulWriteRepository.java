package com.oppshan.washa.common;

import jakarta.annotation.Nonnull;
import jakarta.data.exceptions.DataException;
import jakarta.data.exceptions.EntityExistsException;
import jakarta.data.exceptions.OptimisticLockingFailureException;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;

import static java.util.Objects.requireNonNull;

/**
 * Repository mixin for writes that need a stateful Hibernate session. Jakarta Data's
 * {@code CrudRepository.save} runs as a {@code StatelessSession} upsert, which skips the
 * {@code @UuidGenerator} id assignment and the association cascades our entities rely on; routing
 * writes through the request-scoped {@link EntityManager} instead makes both run. Keeping that
 * {@code EntityManager} lookup inside this mixin encapsulates it in the repository layer (mirrors
 * oppshan-files' {@code StatefulWriteRepository}).
 *
 * <p>Each method looks the {@code EntityManager} up inline via {@code CDI.current()} on purpose.
 * Pulling that into a shared {@code private} helper, or adding a {@code (Class, Object)}-style
 * generic method to this interface, makes the Hibernate Data annotation processor reject every
 * repository that mixes this in with "repository must be backed by a 'StatelessSession'". So the
 * lookup stays duplicated and every mixin method takes an entity argument only.
 *
 * @param <T> the repository's entity type
 */
public interface StatefulWriteRepository<T> {

    /**
     * Merges a detached entity into the persistence context and returns the managed copy. Always
     * use that return value: dirty-checking, cascades, and orphan removal apply to the managed copy,
     * not to the instance passed in. A {@link StaleStateException} (the row's temporal {@code @Version}
     * moved on under a concurrent update) becomes a Jakarta Data {@link OptimisticLockingFailureException};
     * any other {@link PersistenceException} becomes a {@link DataException}.
     */
    default <S extends T> S updateWithSession(S entity) {
        requireNonNull(entity, "Null entity");
        try {
            return CDI.current().select(EntityManager.class).get().merge(entity);
        } catch (StaleStateException ex) {
            throw new OptimisticLockingFailureException(ex.getMessage(), ex);
        } catch (PersistenceException ex) {
            throw new DataException(ex.getMessage(), ex);
        }
    }

    /**
     * Persists a new entity and returns it (persist manages the instance in place, so unlike
     * {@link #updateWithSession} there's no separate managed copy to swap to). A database
     * {@link ConstraintViolationException} (Hibernate's, e.g. a unique or primary-key collision, not
     * Bean Validation) becomes a Jakarta Data {@link EntityExistsException}; any other
     * {@link PersistenceException} becomes a {@link DataException}.
     */
    default <S extends T> S insertWithSession(@Nonnull S entity) {
        requireNonNull(entity, "Null entity");
        try {
            CDI.current().select(EntityManager.class).get().persist(entity);
        } catch (ConstraintViolationException ex) {
            throw new EntityExistsException(ex.getMessage(), ex);
        } catch (PersistenceException ex) {
            throw new DataException(ex.getMessage(), ex);
        }
        return entity;
    }

    /**
     * Removes an entity. {@code EntityManager.remove} only works on a managed instance, so a
     * detached one has to go through {@link #attachWithSession} first. Same exception translation as
     * {@link #updateWithSession}: {@link StaleStateException} to {@link OptimisticLockingFailureException},
     * any other {@link PersistenceException} to {@link DataException}.
     */
    default <S extends T> void deleteWithSession(@Nonnull S entity) {
        requireNonNull(entity, "Null entity");
        try {
            CDI.current().select(EntityManager.class).get().remove(entity);
        } catch (StaleStateException ex) {
            throw new OptimisticLockingFailureException(ex.getMessage(), ex);
        } catch (PersistenceException ex) {
            throw new DataException(ex.getMessage(), ex);
        }
    }

    /**
     * Re-attaches a detached entity and returns the managed copy, so its lazy associations can be
     * traversed inside the current transaction or it can be handed to {@link #deleteWithSession}.
     * Same {@code merge} as {@link #updateWithSession} but without the exception translation: reach
     * for this when the intent is "give me a managed handle," not "record an update." Use the
     * returned copy, never the argument.
     */
    default <S extends T> S attachWithSession(@Nonnull S entity) {
        requireNonNull(entity, "Null entity");
        return CDI.current().select(EntityManager.class).get().merge(entity);
    }

    /**
     * Flushes pending SQL to the database now instead of at transaction commit. Useful to order a
     * delete ahead of a re-insert on the same key within one transaction so the two don't collide.
     */
    default void flushWithSession() {
        CDI.current().select(EntityManager.class).get().flush();
    }
}
