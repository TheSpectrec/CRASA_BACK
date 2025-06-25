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

import com.example.BACK.model.Mark;
import com.example.BACK.service.MarkService;

@RestController
@RequestMapping("/api/marks")
public class MarkController {

    @Autowired
    private MarkService service;

    @GetMapping
    public List<Mark> all() {
        return service.findAll();
    }

    @PostMapping
    public Mark create(@RequestBody Mark m) {
        return service.save(m);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mark> get(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
public ResponseEntity<Mark> update(@PathVariable String id, @RequestBody Mark m) {
    return service.findById(id)
            .map(existing -> {
                existing.setName(m.getName());
                existing.setCompany(m.getCompany());
                return ResponseEntity.ok(service.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
}



    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
