package com.example.BACK.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Role;
import com.example.BACK.model.User;
import com.example.BACK.repository.UserRepository;

@Service
public class UserService {
    @Autowired
    private UserRepository repo;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public List<User> findAll() { return repo.findAll(); }
public User save(User user) {
        // Encriptar solo si es nuevo o la contraseña no está encriptada aún
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        return repo.save(user);
    }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<User> getById(String id) { return repo.findById(id); }
    public Optional<User> login(String email, String rawPassword) {
        return repo.findByEmail(email)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .filter(user -> user.getRole() == Role.Administrador); // Solo Administrador puede loguearse en web
    }
    public List<User> findByCustomerId(String customerId) {
    return repo.findAll().stream()
        .filter(u -> u.getCustomers() != null && u.getCustomers().stream()
            .anyMatch(c -> c.getId().equals(customerId)))
        .collect(Collectors.toList());
}

}
