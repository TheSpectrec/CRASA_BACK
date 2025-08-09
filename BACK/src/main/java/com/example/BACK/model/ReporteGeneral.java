package com.example.BACK.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reportes_generales")
public class ReporteGeneral extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private int mes;
    private int año;
    private LocalDateTime fecha;
    
    // Totales por mes y año
    private int totalCajas; // Total de cajas vendidas
    private BigDecimal totalPesos; // Total de dinero vendido
    
    @ManyToOne
    @JoinColumn(name = "archivo_id")
    private ArchivoProcesado archivo;
    
    private String tipoReporte; // "REP GENERAL"
    
    // Campos adicionales para mejor organización
    private String nombreMes; // Nombre del mes (Enero, Febrero, etc.)
    
    public ReporteGeneral() {}
    
    public ReporteGeneral(int mes, int año, LocalDateTime fecha, int totalCajas, 
                         BigDecimal totalPesos, ArchivoProcesado archivo, String tipoReporte) {
        this.mes = mes;
        this.año = año;
        this.fecha = fecha;
        this.totalCajas = totalCajas;
        this.totalPesos = totalPesos;
        this.archivo = archivo;
        this.tipoReporte = tipoReporte;
        this.nombreMes = obtenerNombreMes(mes);
    }
    
    private String obtenerNombreMes(int mes) {
        String[] meses = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        };
        return (mes >= 1 && mes <= 12) ? meses[mes - 1] : "Desconocido";
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public int getMes() { return mes; }
    public void setMes(int mes) { 
        this.mes = mes; 
        this.nombreMes = obtenerNombreMes(mes);
    }
    
    public int getAño() { return año; }
    public void setAño(int año) { this.año = año; }
    
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    
    public int getTotalCajas() { return totalCajas; }
    public void setTotalCajas(int totalCajas) { this.totalCajas = totalCajas; }
    
    public BigDecimal getTotalPesos() { return totalPesos; }
    public void setTotalPesos(BigDecimal totalPesos) { this.totalPesos = totalPesos; }
    
    public ArchivoProcesado getArchivo() { return archivo; }
    public void setArchivo(ArchivoProcesado archivo) { this.archivo = archivo; }
    
    public String getTipoReporte() { return tipoReporte; }
    public void setTipoReporte(String tipoReporte) { this.tipoReporte = tipoReporte; }
    
    public String getNombreMes() { return nombreMes; }
    public void setNombreMes(String nombreMes) { this.nombreMes = nombreMes; }
} 