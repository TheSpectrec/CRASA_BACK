package com.example.BACK.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Mark;
import com.example.BACK.repository.MarkRepository;

@Service
public class MarkService {
    @Autowired
    private MarkRepository repo;

    public List<Mark> findAll() { return repo.findAll(); }
    public Mark save(Mark m) { return repo.save(m); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<Mark> findById(String id) { return repo.findById(id); }
    public List<Mark> findByCompanyId(String companyId) {
    return repo.findAll().stream()
        .filter(m -> m.getCompany() != null && m.getCompany().getId().equals(companyId))
        .collect(Collectors.toList());
}
public List<Mark> findByProductId(String productCode) {
    return findAll().stream()
        .filter(m -> m.getFamilies() != null &&
                     m.getFamilies().stream().anyMatch(f ->
                         f.getProducts() != null &&
                         f.getProducts().stream().anyMatch(p -> p.getCode().equals(productCode))))
        .collect(Collectors.toList());
}


}

