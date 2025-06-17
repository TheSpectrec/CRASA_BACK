package com.example.BACK.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Product;

public interface ProductRepository extends JpaRepository<Product, String> {}
