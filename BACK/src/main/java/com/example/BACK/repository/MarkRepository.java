package com.example.BACK.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Mark;

public interface MarkRepository extends JpaRepository<Mark, String> {
    Optional<Mark> findByName(String name);
}
