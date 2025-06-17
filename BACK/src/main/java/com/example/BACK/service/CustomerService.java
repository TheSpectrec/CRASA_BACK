package com.example.BACK.service;

import java.util.List;
import java.util.Optional;

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
}
