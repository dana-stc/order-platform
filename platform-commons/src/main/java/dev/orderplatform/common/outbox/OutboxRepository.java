package dev.orderplatform.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * SKIP LOCKED lets several replicas of a service poll the same table concurrently
     * without blocking each other and without publishing the same row twice at the same time.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :threshold")
    int deletePublishedBefore(@Param("threshold") Instant threshold);
}
