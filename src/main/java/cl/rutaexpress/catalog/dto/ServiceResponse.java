package cl.rutaexpress.catalog.dto;

import cl.rutaexpress.catalog.entity.ShippingService;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal rate,
        Integer capacity,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceResponse from(ShippingService entity) {
        return new ServiceResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRate(),
                entity.getCapacity(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
