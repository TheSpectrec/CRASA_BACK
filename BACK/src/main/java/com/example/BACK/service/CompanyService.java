package com.example.BACK.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Company;
import com.example.BACK.repository.CompanyRepository;

@Service
public class CompanyService {
    @Autowired
    private CompanyRepository repo;

    public List<Company> findAll() { return repo.findAll(); }
    public Company save(Company c) { return repo.save(c); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<Company> findById(String id) { return repo.findById(id); }
}

