package com.example.BACK.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.service.ImportDiagnosticService;

@RestController
@RequestMapping("/api/diagnostic")
public class ImportDiagnosticController {

    @Autowired
    private ImportDiagnosticService diagnosticService;

    @GetMapping("/health")
    public Map<String, Object> verificarEstado() {
        Map<String, Object> estado = diagnosticService.verificarConfiguracion();
        estado.put("healthy", true);
        estado.put("totalVentas", diagnosticService.obtenerEstadisticasImportacion().get("totalVentas"));
        estado.put("totalArchivos", diagnosticService.obtenerEstadisticasImportacion().get("totalArchivos"));
        return estado;
    }

    @GetMapping("/stats")
    public Map<String, Object> estadisticas() {
        return diagnosticService.obtenerEstadisticasImportacion();
    }

    @GetMapping("/config")
    public Map<String, Object> configuracion() {
        return diagnosticService.verificarConfiguracion();
    }

    @GetMapping("/test")
    public Map<String, String> pruebaCompleta() {
        return Map.of("resultado", diagnosticService.realizarPruebaCompleta());
    }
}
