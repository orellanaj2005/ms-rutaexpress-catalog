package cl.rutaexpress.catalog.exception;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(Long id) {
        super("Shipping service not found: " + id);
    }
}
