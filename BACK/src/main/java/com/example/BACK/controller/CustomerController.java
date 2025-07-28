package com.example.BACK.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.model.Customer;
import com.example.BACK.model.Product;
import com.example.BACK.model.Role;
import com.example.BACK.model.User;
import com.example.BACK.repository.UserRepository;
import com.example.BACK.service.CustomerService;
import com.example.BACK.service.ProductService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductService productService;

    @GetMapping
    public List<Customer> all() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Customer newCustomer) {
        try {
            boolean codigoDuplicado = service.findAll().stream()
                .anyMatch(c -> c.getCustomerCode() != null &&
                               c.getCustomerCode().equalsIgnoreCase(newCustomer.getCustomerCode()));
            if (codigoDuplicado) return ResponseEntity.badRequest().body(null);

            if (newCustomer.getVendedor() != null && newCustomer.getVendedor().getId() != null) {
                var optionalVendedor = userRepository.findById(newCustomer.getVendedor().getId());
                if (optionalVendedor.isEmpty() || optionalVendedor.get().getRole() != Role.Vendedor) {
                    return ResponseEntity.badRequest().build();
                }
                newCustomer.setVendedor(optionalVendedor.get());
            }

            // Validar y asignar productos
            if (newCustomer.getProductos() != null && !newCustomer.getProductos().isEmpty()) {
                List<Product> productosValidados = new ArrayList<>();
                for (Product p : newCustomer.getProductos()) {
                    productService.findByProductCode(p.getProductCode()).ifPresent(productosValidados::add);
                }
                newCustomer.setProductos(productosValidados);
            }

            return ResponseEntity.ok(service.save(newCustomer));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> get(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable String id, @RequestBody Customer updatedCustomer) {
        var optional = service.findById(id);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();

        Customer existing = optional.get();
        existing.setCustomerCode(updatedCustomer.getCustomerCode());
        existing.setName(updatedCustomer.getName());

        if (updatedCustomer.getVendedor() != null && updatedCustomer.getVendedor().getId() != null) {
            var optionalVendedor = userRepository.findById(updatedCustomer.getVendedor().getId());
            if (optionalVendedor.isEmpty() || optionalVendedor.get().getRole() != Role.Vendedor) {
                return ResponseEntity.badRequest().build();
            }
            existing.setVendedor(optionalVendedor.get());
        }

        if (updatedCustomer.getProductos() != null) {
            List<Product> productosValidados = new ArrayList<>();
            for (Product p : updatedCustomer.getProductos()) {
                productService.findByProductCode(p.getProductCode()).ifPresent(productosValidados::add);
            }
            existing.setProductos(productosValidados);
        }

        return ResponseEntity.ok(service.save(existing));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PutMapping("/{id}/transferir")
    public ResponseEntity<Customer> transferirVendedor(@PathVariable String id, @RequestBody User nuevoVendedor) {
        var optional = service.findById(id);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();

        var c = optional.get();
        var vendedor = userRepository.findById(nuevoVendedor.getId());
        if (vendedor.isEmpty() || vendedor.get().getRole() != Role.Vendedor) {
            return ResponseEntity.badRequest().build();
        }

        c.setVendedor(vendedor.get());
        return ResponseEntity.ok(service.save(c));
    }
}
