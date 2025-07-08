package com.example.BACK.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Customer;
import com.example.BACK.repository.CustomerRepository;

@Service
public class CustomerService {
    @Autowired
    private CustomerRepository repo;

    public List<Customer> findAll() { return repo.findAll(); }
    public Customer save(Customer c) { return repo.save(c); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<Customer> findById(String id) { return repo.findById(id); }
    public List<Customer> findByProductId(String productCode) {
    return repo.findAll().stream()
        .filter(c -> c.getProductos() != null &&
            c.getProductos().stream().anyMatch(p -> p.getCode().equals(productCode)))
        .collect(Collectors.toList());
}

public List<Customer> findByVendedorId(String vendedorId) {
    return repo.findAll().stream()
        .filter(c -> c.getVendedor() != null && c.getVendedor().getId().equals(vendedorId))
        .collect(Collectors.toList());
}

}
