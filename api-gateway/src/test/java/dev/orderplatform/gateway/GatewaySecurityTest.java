package dev.orderplatform.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.otlp.tracing.endpoint=http://localhost:1/v1/traces",
        "security.jwt.secret=test-secret-test-secret-test-secret-12",
        "ORDER_SERVICE_URL=http://localhost:1"      // nothing listens there: authenticated calls end in the fallback
})
class GatewaySecurityTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-12";

    @LocalServerPort int port;

    WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void apiWithoutToken_is401() {
        client().get().uri("/api/orders/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void apiWithGarbageToken_is401() {
        client().get().uri("/api/orders/1").headers(h -> h.setBearerAuth("not-a-jwt")).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiWithTokenSignedByAnotherKey_is401() throws Exception {
        String token = token("another-secret-another-secret-12345", 3600);
        client().get().uri("/api/orders/1").headers(h -> h.setBearerAuth(token)).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiWithExpiredToken_is401() throws Exception {
        String token = token(SECRET, -60);
        client().get().uri("/api/orders/1").headers(h -> h.setBearerAuth(token)).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiWithValidToken_passesAuthentication() throws Exception {
        String token = token(SECRET, 3600);
        client().get().uri("/api/orders/1").headers(h -> h.setBearerAuth(token)).exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));   // 503 fallback: no backend
    }

    @Test
    void healthProbe_isOpen() {
        client().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    }

    private static String token(String secret, int ttlSeconds) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("alice").expirationTime(new Date(System.currentTimeMillis() + ttlSeconds * 1000L)).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
