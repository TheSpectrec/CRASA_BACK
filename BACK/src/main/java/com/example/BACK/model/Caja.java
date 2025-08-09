package com.example.BACK.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cajas")
public class Caja extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer cliente;
    
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product producto;
    
    @ManyToOne
    @JoinColumn(name = "family_id")
    private Family familia;
    
    @ManyToOne
    @JoinColumn(name = "mark_id")
    private Mark marca;
    
    private int cantidad; // Total de cajas compradas
    private int mes;
    private int año;
    private LocalDateTime fecha;
    
    @ManyToOne
    @JoinColumn(name = "archivo_id")
    private ArchivoProcesado archivo;
    
    private String tipoReporte; // "REP CAJAS", "REP PRODUCTOS", "FAMILIAS", "MARCA", "REP GENERAL"
    
    // Campos adicionales para mejor organización
    private String nombreCliente; // Nombre del cliente para búsquedas rápidas
    private String nombreProducto; // Nombre del producto
    private String nombreFamilia; // Nombre de la familia
    private String nombreMarca; // Nombre de la marca
    
    public Caja() {}
    
    public Caja(Customer cliente, Product producto, Family familia, Mark marca, 
                int cantidad, int mes, int año, LocalDateTime fecha, 
                ArchivoProcesado archivo, String tipoReporte) {
        this.cliente = cliente;
        this.producto = producto;
        this.familia = familia;
        this.marca = marca;
        this.cantidad = cantidad;
        this.mes = mes;
        this.año = año;
        this.fecha = fecha;
        this.archivo = archivo;
        this.tipoReporte = tipoReporte;
        
        // Llenar campos de nombres para búsquedas rápidas
        this.nombreCliente = cliente != null ? cliente.getName() : "";
        this.nombreProducto = producto != null ? producto.getDescription() : "";
        this.nombreFamilia = familia != null ? familia.getName() : "";
        this.nombreMarca = marca != null ? marca.getName() : "";
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public Customer getCliente() { return cliente; }
    public void setCliente(Customer cliente) { 
        this.cliente = cliente; 
        this.nombreCliente = cliente != null ? cliente.getName() : "";
    }
    
    public Product getProducto() { return producto; }
    public void setProducto(Product producto) { 
        this.producto = producto; 
        this.nombreProducto = producto != null ? producto.getDescription() : "";
    }
    
    public Family getFamilia() { return familia; }
    public void setFamilia(Family familia) { 
        this.familia = familia; 
        this.nombreFamilia = familia != null ? familia.getName() : "";
    }
    
    public Mark getMarca() { return marca; }
    public void setMarca(Mark marca) { 
        this.marca = marca; 
        this.nombreMarca = marca != null ? marca.getName() : "";
    }
    
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    
    public int getAño() { return año; }
    public void setAño(int año) { this.año = año; }
    
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    
    public ArchivoProcesado getArchivo() { return archivo; }
    public void setArchivo(ArchivoProcesado archivo) { this.archivo = archivo; }
    
    public String getTipoReporte() { return tipoReporte; }
    public void setTipoReporte(String tipoReporte) { this.tipoReporte = tipoReporte; }
    
    // Getters para nombres
    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }
    
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
    
    public String getNombreFamilia() { return nombreFamilia; }
    public void setNombreFamilia(String nombreFamilia) { this.nombreFamilia = nombreFamilia; }
    
    public String getNombreMarca() { return nombreMarca; }
    public void setNombreMarca(String nombreMarca) { this.nombreMarca = nombreMarca; }
} 