package com.example.BACK.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "marks")
public class Mark extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    @ManyToMany(mappedBy = "marks")
    @JsonIgnoreProperties("marks")
    private List<Company> companies;

    @OneToMany(mappedBy = "mark")
@JsonIgnoreProperties("mark")
private List<Family> families;

public Mark() {}

public Mark(String name) {
    this.name = name;
}

// Getters y Setters

public List<Family> getFamilies() {
    return families;
}

public void setFamilies(List<Family> families) {
    this.families = families;
}

    public List<Company> getCompanies() {
        return companies;
    }
    public void setCompanies(List<Company> companies) {
        this.companies = companies;
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

