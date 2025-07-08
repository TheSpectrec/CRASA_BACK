package com.example.BACK.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
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

    @ManyToOne
    @JoinColumn(name = "mark_id")
    private Mark mark;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToMany(mappedBy = "productos")
    @JsonIgnoreProperties("productos")
    private List<Customer> customers;


    @Column(name = "created_at")
private Timestamp createdAt;

public Timestamp getCreatedAt() { return createdAt; }
public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

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

    public List<Customer> getCustomers() {
        return customers;
    }

    public Mark getMark() {
        return mark;
    }

    public Company getCompany() {
        return company;
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

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public void setMark(Mark mark) {
        this.mark = mark;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
