package com.example.BACK.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    Optional<Product> findByDescription(String description);
    Optional<Product> findByProductCode(String productCode);
}
