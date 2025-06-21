package com.example.BACK.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "families")
public class Family extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "mark_id", nullable = false)
    private Mark mark;

    // Getters y Setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Mark getMark() {
        return mark;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMark(Mark mark) {
        this.mark = mark;
    }
}
