package cl.rutaexpress.catalog.repository;

import cl.rutaexpress.catalog.entity.ShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused test for the atomic decrease-capacity repository method: proves
 * the guarded WHERE clause (capacity >= :amount) prevents the update from
 * ever driving capacity negative under a single-statement UPDATE.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ShippingServiceRepositoryTest {

    @Autowired
    private ShippingServiceRepository repository;

    @Test
    void decreaseCapacity_appliesWhenSufficient_andBlocksWhenInsufficient() {
        ShippingService service = repository.save(
                new ShippingService("Envio Express", "desc", new BigDecimal("10.00"), 5, true));

        int rowsUpdated = repository.decreaseCapacity(service.getId(), 3);
        assertEquals(1, rowsUpdated);

        ShippingService afterFirst = repository.findById(service.getId()).orElseThrow();
        assertEquals(2, afterFirst.getCapacity());

        int rowsUpdatedSecond = repository.decreaseCapacity(service.getId(), 10);
        assertEquals(0, rowsUpdatedSecond);

        ShippingService afterSecond = repository.findById(service.getId()).orElseThrow();
        assertEquals(2, afterSecond.getCapacity());
    }
}
