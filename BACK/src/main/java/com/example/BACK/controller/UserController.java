package com.example.BACK.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.model.Role;
import com.example.BACK.model.User;
import com.example.BACK.repository.UserRepository;
import com.example.BACK.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService service;

    @Autowired
    private UserRepository userRepository;


    @GetMapping
    public List<User> all() {
        return service.findAll();
    }

    @PostMapping
    public User create(@RequestBody User u) {
        return service.save(u);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable String id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vendedores")
public List<User> getVendedores() {
    return userRepository.findByRole(Role.Vendedor);
}


    @PutMapping("/{id}")
public ResponseEntity<User> update(@PathVariable String id, @RequestBody User u) {
    return service.getById(id)
            .map(existing -> {
                existing.setName(u.getName());
                existing.setEmail(u.getEmail());
                existing.setRole(u.getRole());
                // No actualizamos contraseña directamente aquí por seguridad
                return ResponseEntity.ok(service.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
}


    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> body) {
        return service.login(body.get("email"), body.get("password"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}

