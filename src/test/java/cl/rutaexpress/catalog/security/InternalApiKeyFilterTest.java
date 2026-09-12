package cl.rutaexpress.catalog.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies InternalApiKeyFilter rejects the internal decrease-capacity path
 * without (or with the wrong) X-Internal-Api-Key header, accepts it with the
 * correct header, and leaves every other /api/catalog/services path
 * completely unaffected regardless of the header.
 */
class InternalApiKeyFilterTest {

    private static final String CORRECT_KEY = "expected-key";

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(CORRECT_KEY);

    @Test
    void rejectsDecreaseCapacityWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/catalog/services/5/decrease-capacity");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void rejectsDecreaseCapacityWithWrongHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/catalog/services/5/decrease-capacity");
        request.addHeader(InternalApiKeyFilter.HEADER_NAME, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void allowsDecreaseCapacityWithCorrectHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/catalog/services/5/decrease-capacity");
        request.addHeader(InternalApiKeyFilter.HEADER_NAME, CORRECT_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void otherCatalogPathsAreUnaffectedRegardlessOfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalog/services");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // shouldNotFilter() short-circuits: chain runs untouched, no 401 written.
        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void postToOtherPathIsUnaffected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/catalog/services");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }
}
