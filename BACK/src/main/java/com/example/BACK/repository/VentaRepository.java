package com.example.BACK.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BACK.model.Customer;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, String> {
    boolean existsByClienteAndProductoAndFecha(Customer cliente, Product producto, LocalDateTime fecha);
}
