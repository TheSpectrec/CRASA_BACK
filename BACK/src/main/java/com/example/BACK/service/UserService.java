package com.example.BACK.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.User;
import com.example.BACK.repository.UserRepository;

@Service
public class UserService {
    @Autowired
    private UserRepository repo;

    public List<User> findAll() { return repo.findAll(); }
    public User save(User u) { return repo.save(u); }
    public void delete(String id) { repo.deleteById(id); }
    public Optional<User> getById(String id) { return repo.findById(id); }
    public Optional<User> login(String email, String pass) {
        return repo.findByEmail(email).filter(u -> u.getPassword().equals(pass));
    }
}
