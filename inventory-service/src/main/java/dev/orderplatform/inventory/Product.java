package dev.orderplatform.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    @Id
    private String sku;

    @Column(nullable = false)
    private int available;

    protected Product() {
    }

    public Product(String sku, int available) {
        this.sku = sku;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public void reserve(int quantity) {
        if (quantity > available) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        available -= quantity;
    }

    public void release(int quantity) {
        available += quantity;
    }
}
