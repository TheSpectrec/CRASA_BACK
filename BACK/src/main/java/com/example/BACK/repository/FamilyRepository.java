package com.example.BACK.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Family;

public interface FamilyRepository extends JpaRepository<Family, String> {}
