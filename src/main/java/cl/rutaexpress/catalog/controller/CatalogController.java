package cl.rutaexpress.catalog.controller;

import cl.rutaexpress.catalog.dto.CreateServiceRequest;
import cl.rutaexpress.catalog.dto.DecreaseCapacityRequest;
import cl.rutaexpress.catalog.dto.DecreaseCapacityResponse;
import cl.rutaexpress.catalog.dto.ServiceResponse;
import cl.rutaexpress.catalog.dto.UpdateServiceRequest;
import cl.rutaexpress.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/services")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> list(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(catalogService.list(active));
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = catalogService.create(request);
        return ResponseEntity.created(URI.create("/api/catalog/services/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(catalogService.update(id, request));
    }

    /**
     * Internal, service-to-service endpoint (called synchronously by
     * ms-rutaexpress-shipments). Auth for this path is NOT the usual JWT +
     * role check - it's the shared API key validated by
     * {@link cl.rutaexpress.catalog.security.InternalApiKeyFilter}, and this
     * path is permitAll() in {@link cl.rutaexpress.catalog.config.SecurityConfig}.
     */
    @PostMapping("/{id}/decrease-capacity")
    public ResponseEntity<DecreaseCapacityResponse> decreaseCapacity(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DecreaseCapacityRequest request) {
        Integer amount = (request == null) ? null : request.amount();
        return ResponseEntity.ok(catalogService.decreaseCapacity(id, amount));
    }
}
