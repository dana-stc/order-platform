package dev.orderplatform.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    /** Reached when the circuit breaker is open or the downstream call timed out. */
    @RequestMapping("/fallback/orders")
    public ResponseEntity<ProblemDetail> ordersFallback() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The order service is temporarily unavailable, please retry shortly.");
        problem.setTitle("Order service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "10").body(problem);
    }
}
