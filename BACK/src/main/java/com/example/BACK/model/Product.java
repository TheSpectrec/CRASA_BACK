package com.example.BACK.model;

import java.sql.Timestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String description;

    private boolean  price;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne
    @JoinColumn(name = "mark_id", nullable = false)
    private Mark mark;

    private Timestamp createdAt;

    // Getters y Setters
    public String getId() {
        return id;
    }
    public String getDescription() {
        return description;
    }

    public boolean  getPrice() {
        return price;
    }

    public Family getFamily() {
        return family;
    }

    public Mark getMark() {
        return mark;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(boolean  price) {
        this.price = price;
    }

    public void setFamily(Family family) {
        this.family = family;
    }

    public void setMark(Mark mark) {
        this.mark = mark;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
