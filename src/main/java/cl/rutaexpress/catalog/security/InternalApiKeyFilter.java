package cl.rutaexpress.catalog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards the single internal, service-to-service endpoint
 * (POST /api/catalog/services/{id}/decrease-capacity), called synchronously
 * by ms-rutaexpress-shipments. This path does NOT require a user JWT; instead
 * it must present a valid {@code X-Internal-Api-Key} header.
 * <p>
 * {@link #shouldNotFilter(HttpServletRequest)} restricts this filter to that
 * one path/method so every other request passes straight through to Spring
 * Security's normal OAuth2-resource-server JWT + role checks.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private static final RequestMatcher MATCHER = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/catalog/services/*/decrease-capacity");

    private final String expectedApiKey;

    public InternalApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);
        if (provided == null || !provided.equals(expectedApiKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid " + HEADER_NAME + " header\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
