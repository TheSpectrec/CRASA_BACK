package com.example.BACK.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public List<Product> findByCompanyId(String companyId) {
    return repo.findAll().stream()
        .filter(p -> p.getFamily() != null && p.getFamily().getMark() != null
              && p.getFamily().getMark().getCompany() != null
              && p.getFamily().getMark().getCompany().getId().equals(companyId))
        .collect(Collectors.toList());
}

public List<Product> findByMarkId(String markId) {
    return repo.findAll().stream()
        .filter(p -> p.getFamily() != null && p.getFamily().getMark() != null
              && p.getFamily().getMark().getId().equals(markId))
        .collect(Collectors.toList());
}

public List<Product> findByFamilyId(String familyId) {
    return repo.findAll().stream()
        .filter(p -> p.getFamily() != null && p.getFamily().getId().equals(familyId))
        .collect(Collectors.toList());
}

public List<Product> findByCustomerId(String customerId) {
    return findAll().stream()
        .filter(p -> p.getCustomers() != null &&
                     p.getCustomers().stream().anyMatch(c -> c.getId().equals(customerId)))
        .collect(Collectors.toList());
}

}
