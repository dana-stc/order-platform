package dev.orderplatform.common;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Picked up by component scanning of "dev.orderplatform" (every service uses scanBasePackages).
 * Makes entities/repositories of this library (outbox) and of the service itself visible to JPA.
 */
@Configuration
@EnableScheduling
@EntityScan("dev.orderplatform")
@EnableJpaRepositories("dev.orderplatform")
public class PlatformCommonsConfig {
}
