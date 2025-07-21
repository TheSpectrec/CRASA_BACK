package com.example.BACK.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

@OneToMany(mappedBy = "company")
@JsonIgnoreProperties("company")
private List<Mark> marks;

public Company() {}

public Company(String name) {
    this.name = name;
}

    // Getters y Setters

public List<Mark> getMarks() {
    return marks;
}

public void setMarks(List<Mark> marks) {
    this.marks = marks;
}

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
