package cl.rutaexpress.catalog.service;

import cl.rutaexpress.catalog.dto.CreateServiceRequest;
import cl.rutaexpress.catalog.dto.DecreaseCapacityResponse;
import cl.rutaexpress.catalog.dto.ServiceResponse;
import cl.rutaexpress.catalog.dto.UpdateServiceRequest;
import cl.rutaexpress.catalog.entity.ShippingService;
import cl.rutaexpress.catalog.exception.CapacityExceededException;
import cl.rutaexpress.catalog.exception.ServiceNotFoundException;
import cl.rutaexpress.catalog.repository.ShippingServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the shipping-services catalog. Named {@code CatalogService}
 * (not {@code ShippingServiceService}) - deliberately distinct from the JPA
 * entity {@link ShippingService} which represents a catalog "service" offering.
 */
@Service
public class CatalogService {

    private final ShippingServiceRepository repository;

    public CatalogService(ShippingServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(Boolean active) {
        List<ShippingService> services = (active == null) ? repository.findAll() : repository.findByActive(active);
        return services.stream().map(ServiceResponse::from).toList();
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        ShippingService entity = new ShippingService(
                request.name(),
                request.description(),
                request.rate(),
                request.capacity(),
                true
        );
        return ServiceResponse.from(repository.save(entity));
    }

    @Transactional
    public ServiceResponse update(Long id, UpdateServiceRequest request) {
        ShippingService entity = repository.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));

        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.rate() != null) {
            entity.setRate(request.rate());
        }
        if (request.capacity() != null) {
            entity.setCapacity(request.capacity());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        entity.touch();

        return ServiceResponse.from(repository.save(entity));
    }

    /**
     * Atomic decrease: attempts the guarded UPDATE first (no race window).
     * Only when it affects 0 rows do we fall back to an existence check to
     * decide between 404 (no such service) and 409 (insufficient capacity).
     */
    @Transactional
    public DecreaseCapacityResponse decreaseCapacity(Long id, Integer amountOrNull) {
        int amount = (amountOrNull == null) ? 1 : amountOrNull;

        int rowsUpdated = repository.decreaseCapacity(id, amount);
        if (rowsUpdated == 0) {
            if (!repository.existsById(id)) {
                throw new ServiceNotFoundException(id);
            }
            throw new CapacityExceededException(id, amount);
        }

        ShippingService updated = repository.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
        return new DecreaseCapacityResponse(updated.getId(), updated.getCapacity());
    }
}
