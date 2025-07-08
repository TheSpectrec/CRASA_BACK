package com.example.BACK.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

@ManyToOne(optional = false)
@JoinColumn(name = "customer_id") // apunta a id
private Customer cliente;

@ManyToOne(optional = false)
@JoinColumn(name = "product_id") // apunta a id
private Product producto;


    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal total;

    private LocalDateTime fecha;

    public Venta() {}

    public Venta(Customer cliente, Product producto, int cantidad, BigDecimal precioUnitario, BigDecimal total, LocalDateTime fecha) {
        this.cliente = cliente;
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.total = total;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public Customer getCliente() { return cliente; }
    public void setCliente(Customer cliente) { this.cliente = cliente; }
    public Product getProducto() { return producto; }
    public void setProducto(Product producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
