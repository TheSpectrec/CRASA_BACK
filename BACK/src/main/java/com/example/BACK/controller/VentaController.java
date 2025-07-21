package com.example.BACK.controller;

import java.util.List;
import java.util.Optional;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.model.Venta;
import com.example.BACK.repository.VentaRepository;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    @Autowired
    private VentaRepository ventaRepository;

    @GetMapping
    public List<Venta> obtenerVentas() {
        return ventaRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venta> obtenerVentaPorId(@PathVariable String id) {
        Optional<Venta> venta = ventaRepository.findById(id);
        if (venta.isPresent()) {
            return ResponseEntity.ok(venta.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Venta> crearVenta(@RequestBody Venta venta) {
        try {
            Venta nuevaVenta = ventaRepository.save(venta);
            return ResponseEntity.ok(nuevaVenta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Venta> actualizarVenta(@PathVariable String id, @RequestBody Venta ventaActualizada) {
        Optional<Venta> ventaExistente = ventaRepository.findById(id);
        if (ventaExistente.isPresent()) {
            Venta venta = ventaExistente.get();
            venta.setCliente(ventaActualizada.getCliente());
            venta.setProducto(ventaActualizada.getProducto());
            venta.setCantidad(ventaActualizada.getCantidad());
            venta.setPrecioUnitario(ventaActualizada.getPrecioUnitario());
            venta.setTotal(ventaActualizada.getTotal());
            venta.setFecha(ventaActualizada.getFecha());
            venta.setArchivo(ventaActualizada.getArchivo());
            
            Venta ventaGuardada = ventaRepository.save(venta);
            return ResponseEntity.ok(ventaGuardada);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVenta(@PathVariable String id) {
        if (ventaRepository.existsById(id)) {
            ventaRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/count")
    public ResponseEntity<Long> contarVentas() {
        long count = ventaRepository.count();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Venta>> buscarVentasPorCliente(@RequestParam String customerCode) {
        try {
            List<Venta> ventas = ventaRepository.findByCustomerCode(customerCode);
            return ResponseEntity.ok(ventas);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }
} 
