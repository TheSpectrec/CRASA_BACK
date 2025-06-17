package com.example.BACK.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Product;
import com.example.BACK.repository.ProductRepository;

@Service
public class ProductService {
    @Autowired
    private ProductRepository repo;

    public List<Product> findAll() { return repo.findAll(); }
    public Product save(Product p) { return repo.save(p); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<Product> findById(String id) { return repo.findById(id); }
}
