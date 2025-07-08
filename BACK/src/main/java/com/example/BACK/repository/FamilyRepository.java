package com.example.BACK.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Family;

public interface FamilyRepository extends JpaRepository<Family, String> {
    Optional<Family> findByName(String name);
}
