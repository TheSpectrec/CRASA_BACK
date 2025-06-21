package com.example.BACK.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.model.Company;
import com.example.BACK.service.CompanyService;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    @Autowired
    private CompanyService service;

    @GetMapping
    public List<Company> all() {
        return service.findAll();
    }

    @PostMapping
    public Company create(@RequestBody Company c) {
        return service.save(c);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> get(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}")
    public ResponseEntity<Company> update(@PathVariable String id, @RequestBody Company c) {
        return service.findById(id)
                .map(existingCompany -> {
                    existingCompany.setName(c.getName());
                    return ResponseEntity.ok(service.save(existingCompany));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}

