package com.example.BACK.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Family;
import com.example.BACK.repository.FamilyRepository;

@Service
public class FamilyService {
    @Autowired
    private FamilyRepository repo;

    public List<Family> findAll() { return repo.findAll(); }
    public Family save(Family f) { return repo.save(f); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<Family> findById(String id) { return repo.findById(id); }
    public List<Family> findByCompanyId(String companyId) {
    return repo.findAll().stream()
            .filter(f -> f.getMark() != null && f.getMark().getCompany() != null && f.getMark().getCompany().getId().equals(companyId))
            .collect(Collectors.toList());
}

public List<Family> findByMarkId(String markId) {
    return repo.findAll().stream()
            .filter(f -> f.getMark() != null && f.getMark().getId().equals(markId))
            .collect(Collectors.toList());
}

public List<Family> findByProductId(String productCode) {
    return findAll().stream()
        .filter(f -> f.getProducts() != null &&
                     f.getProducts().stream().anyMatch(p -> p.getCode().equals(productCode)))
        .collect(Collectors.toList());
}

}
