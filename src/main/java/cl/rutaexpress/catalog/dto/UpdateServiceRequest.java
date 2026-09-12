package cl.rutaexpress.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Partial-update payload: all fields are optional, only non-null fields are
 * applied. This is the endpoint used to edit "tarifa/capacidad" per spec.
 */
public record UpdateServiceRequest(
        String name,
        String description,
        @DecimalMin("0") BigDecimal rate,
        @Min(0) Integer capacity,
        Boolean active
) {
}
