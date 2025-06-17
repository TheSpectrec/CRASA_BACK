package com.example.BACK.service;

import java.util.List;
import java.util.Optional;

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
}
