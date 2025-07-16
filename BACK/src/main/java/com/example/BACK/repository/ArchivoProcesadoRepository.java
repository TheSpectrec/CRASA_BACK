package com.example.BACK.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.ArchivoProcesado;

public interface ArchivoProcesadoRepository extends JpaRepository<ArchivoProcesado, Long> {
    boolean existsByNombre(String nombre);
    Optional<ArchivoProcesado> findByNombre(String nombre);
}
