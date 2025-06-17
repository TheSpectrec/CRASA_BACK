package com.example.BACK.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, String> {}
