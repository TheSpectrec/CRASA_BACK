package com.example.BACK.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Company;

public interface CompanyRepository extends JpaRepository<Company, String> {
    Optional<Company> findByName(String name);
}

