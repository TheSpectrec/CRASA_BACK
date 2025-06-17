package com.example.BACK.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Company;

public interface CompanyRepository extends JpaRepository<Company, String> {}

