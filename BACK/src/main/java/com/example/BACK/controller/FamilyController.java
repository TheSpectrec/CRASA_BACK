package com.example.BACK.controller;

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

import com.example.BACK.model.Family;
import com.example.BACK.service.FamilyService;

@RestController
@RequestMapping("/api/families")
public class FamilyController {

    @Autowired
    private FamilyService service;

    @GetMapping
    public List<Family> all() {
        return service.findAll();
    }

    @PostMapping
    public Family create(@RequestBody Family f) {
        return service.save(f);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Family> get(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
public ResponseEntity<Family> update(@PathVariable String id, @RequestBody Family f) {
    return service.findById(id)
            .map(existing -> {
                existing.setName(f.getName());
                existing.setMark(f.getMark());
                return ResponseEntity.ok(service.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
}



    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
