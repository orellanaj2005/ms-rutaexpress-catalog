package cl.rutaexpress.catalog.repository;

import cl.rutaexpress.catalog.entity.ShippingService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ShippingServiceRepository extends JpaRepository<ShippingService, Long> {

    List<ShippingService> findByActive(boolean active);

    /**
     * Atomically decreases capacity in a single UPDATE statement, guarded by
     * {@code capacity >= :amount} so concurrent requests can never drive it
     * negative. Returns the number of rows affected: 1 if the decrease was
     * applied, 0 if the service doesn't exist OR capacity was insufficient
     * (the caller distinguishes the two with a follow-up existence check).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE ShippingService s SET s.capacity = s.capacity - :amount, s.updatedAt = CURRENT_INSTANT "
            + "WHERE s.id = :id AND s.capacity >= :amount")
    int decreaseCapacity(@Param("id") Long id, @Param("amount") int amount);
}
