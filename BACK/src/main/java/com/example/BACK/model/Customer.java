package com.example.BACK.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String customerCode;

    private String name;

    @ManyToOne
    @JoinColumn(name = "vendedor_id", nullable = true) 
    private User vendedor;

    // Getters y Setters
    public String getId() {
        return id;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public String getName() {
        return name;
    }

    public User getVendedor() {
        return vendedor;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVendedor(User vendedor) {
        this.vendedor = vendedor;
    }
}
