package cl.rutaexpress.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test-only replacement for {@link SecurityConfig}'s jwtDecoder() bean, which
 * is disabled under the "test" profile (see @Profile("!test") there) because
 * it otherwise performs a real OIDC discovery HTTP call to Azure AD at
 * context-startup time. Nothing in the test suite decodes a real JWT
 * (MockMvc auth tests inject a mock JWT authentication directly), so this
 * decoder only needs to exist to satisfy bean wiring.
 */
@Configuration
@Profile("test")
public class TestJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("JWT decoding is disabled in the test profile");
        };
    }
}
