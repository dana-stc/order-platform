package dev.orderplatform.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    /** Locks in a stable (sku) order so two orders touching the same SKUs cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.sku IN :skus ORDER BY p.sku")
    List<Product> lockBySkus(@Param("skus") Collection<String> skus);
}
