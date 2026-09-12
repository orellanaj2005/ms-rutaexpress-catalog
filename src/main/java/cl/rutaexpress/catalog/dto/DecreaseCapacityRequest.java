package cl.rutaexpress.catalog.dto;

import jakarta.validation.constraints.Min;

/**
 * {@code amount} is optional; when omitted, the service layer defaults it to 1.
 */
public record DecreaseCapacityRequest(
        @Min(1) Integer amount
) {
}
