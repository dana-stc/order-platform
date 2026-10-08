package dev.orderplatform.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Edge authentication: every /api/** call needs a valid Bearer JWT. Probes, Prometheus and the circuit-breaker
 * fallback stay open (protect /actuator/prometheus with a NetworkPolicy / separate management port in production).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)   // stateless API, no cookies
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.issuer-uri:}") String issuerUri,
                                  @Value("${security.jwt.secret}") String secret) {
        if (!issuerUri.isBlank()) {
            return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);   // keys fetched from the IdP's JWKS
        }
        return NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
