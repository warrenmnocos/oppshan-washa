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
 * Repository mixin for stateful-session writes — the {@code @UuidGenerator} and association
 * cascades that Jakarta Data's {@code CrudRepository.save} (a StatelessSession upsert) won't run.
 * Encapsulates the {@link EntityManager} inside the repository layer so services never touch it
 * (mirrors {@code oppshan-files}'s {@code StatefulWriteRepository}).
 *
 * @param <T> the repository's entity type
 */
public interface StatefulWriteRepository<T> {

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

    default <S extends T> S attachWithSession(@Nonnull S entity) {
        requireNonNull(entity, "Null entity");
        return CDI.current().select(EntityManager.class).get().merge(entity);
    }

    default void flushWithSession() {
        CDI.current().select(EntityManager.class).get().flush();
    }
}
