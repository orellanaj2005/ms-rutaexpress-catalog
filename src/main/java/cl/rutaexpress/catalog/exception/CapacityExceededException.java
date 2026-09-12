package cl.rutaexpress.catalog.exception;

public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(Long id, int requested) {
        super("Not enough capacity on service " + id + " to decrease by " + requested);
    }
}
