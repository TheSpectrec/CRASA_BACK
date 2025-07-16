package com.example.BACK.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "archivo_procesado")
public class ArchivoProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    @Column(name = "tipo", nullable = false)
    private String tipo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @OneToMany(mappedBy = "archivo", cascade = CascadeType.ALL)
    private List<Venta> ventas;

    public ArchivoProcesado() {}

    public ArchivoProcesado(String nombre, String tipo, LocalDateTime fecha) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.fecha = fecha;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public List<Venta> getVentas() { return ventas; }
    public void setVentas(List<Venta> ventas) { this.ventas = ventas; }
}
