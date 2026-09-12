package cl.rutaexpress.catalog.config;

import cl.rutaexpress.catalog.security.InternalApiKeyFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Two-tier security:
 * 1) Everything under /api/catalog/** requires a valid Azure AD JWT with
 *    Admin/Operador authority - same pattern as ms-rutaexpress-bff.
 * 2) The single internal endpoint (POST .../decrease-capacity) is permitAll()
 *    here because it is instead guarded ahead of this chain by
 *    {@link InternalApiKeyFilter}, which checks the shared X-Internal-Api-Key
 *    header and short-circuits with 401 if it's missing/wrong.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String DECREASE_CAPACITY_PATH = "/api/catalog/services/*/decrease-capacity";

    @Value("${security.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${security.jwt.audience}")
    private String audience;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(internalApiKeyFilter(), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, DECREASE_CAPACITY_PATH).permitAll()
                .requestMatchers("/api/catalog/**").hasAnyAuthority("Admin", "Operador")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter() {
        return new InternalApiKeyFilter(internalApiKey);
    }

    // Excluded from the "test" profile: fromOidcIssuerLocation performs a real
    // OIDC discovery HTTP call at bean-creation time, which would make every
    // context-loading test depend on network access to Azure AD. The "test"
    // profile supplies its own no-network JwtDecoder (see test sources).
    @Bean
    @Profile("!test")
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = JwtDecoders.fromOidcIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
            "aud", aud -> aud != null && aud.contains(audience)
        );
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
