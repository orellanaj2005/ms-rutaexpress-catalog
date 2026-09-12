package cl.rutaexpress.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateServiceRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin("0") BigDecimal rate,
        @NotNull @Min(0) Integer capacity
) {
}
