package com.example.BACK.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BACK.service.ImportDiagnosticService;

@RestController
@RequestMapping("/api/diagnostic")
public class ImportDiagnosticController {

    @Autowired
    private ImportDiagnosticService diagnosticService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticas() {
        try {
            Map<String, Object> stats = diagnosticService.obtenerEstadisticasImportacion();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error obteniendo estadísticas: " + e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> verificarConfiguracion() {
        try {
            Map<String, Object> config = diagnosticService.verificarConfiguracion();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error verificando configuración: " + e.getMessage()));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> realizarPruebaCompleta() {
        try {
            String resultado = diagnosticService.realizarPruebaCompleta();
            return ResponseEntity.ok(Map.of("resultado", resultado));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error en prueba completa: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> verificarSalud() {
        try {
            Map<String, Object> stats = diagnosticService.obtenerEstadisticasImportacion();
            Map<String, Object> config = diagnosticService.verificarConfiguracion();
            
            boolean healthy = (Boolean) config.getOrDefault("baseDatosConectada", false) &&
                            (Boolean) stats.getOrDefault("googleDriveConectado", false);
            
            return ResponseEntity.ok(Map.of(
                "healthy", healthy,
                "database", config.getOrDefault("baseDatosConectada", false),
                "googleDrive", stats.getOrDefault("googleDriveConectado", false),
                "totalVentas", stats.getOrDefault("totalVentas", 0),
                "totalArchivos", stats.getOrDefault("totalArchivos", 0)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("healthy", false, "error", e.getMessage()));
        }
    }
}