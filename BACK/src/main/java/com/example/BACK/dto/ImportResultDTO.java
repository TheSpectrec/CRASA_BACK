package com.example.BACK.dto;

import java.util.List;

import com.example.BACK.model.Venta;

public class ImportResultDTO {
    private String archivo;
    private String tipo;
    private List<Venta> ventas;

    public ImportResultDTO() {}

    public ImportResultDTO(String archivo, String tipo, List<Venta> ventas) {
        this.archivo = archivo;
        this.tipo = tipo;
        this.ventas = ventas;
    }

    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public List<Venta> getVentas() { return ventas; }
    public void setVentas(List<Venta> ventas) { this.ventas = ventas; }
}
