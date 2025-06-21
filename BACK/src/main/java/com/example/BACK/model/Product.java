package com.example.BACK.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String code;

    private String description;

    private BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    // Getters y Setters
    public String getCode() {
        return code;
    }
    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Family getFamily() {
        return family;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setFamily(Family family) {
        this.family = family;
    } 
}
