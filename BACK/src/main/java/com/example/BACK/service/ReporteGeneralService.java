package com.example.BACK.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.ReporteGeneral;
import com.example.BACK.repository.ReporteGeneralRepository;

@Service
public class ReporteGeneralService {
    
    @Autowired
    private ReporteGeneralRepository reporteGeneralRepository;
    
    // Métodos CRUD básicos
    public List<ReporteGeneral> findAll() {
        return reporteGeneralRepository.findAll();
    }
    
    public Optional<ReporteGeneral> findById(String id) {
        return reporteGeneralRepository.findById(id);
    }
    
    public ReporteGeneral save(ReporteGeneral reporteGeneral) {
        return reporteGeneralRepository.save(reporteGeneral);
    }
    
    public void delete(String id) {
        reporteGeneralRepository.deleteById(id);
    }
    
    // Métodos de búsqueda específicos
    public List<ReporteGeneral> findByAño(int año) {
        return reporteGeneralRepository.findByAño(año);
    }
    
    public List<ReporteGeneral> findByMesAndAño(int mes, int año) {
        return reporteGeneralRepository.findByMesAndAño(mes, año);
    }
    
    public List<ReporteGeneral> findByTipoReporte(String tipoReporte) {
        return reporteGeneralRepository.findByTipoReporte(tipoReporte);
    }
    
    public List<ReporteGeneral> findByArchivoId(Long archivoId) {
        return reporteGeneralRepository.findByArchivo_Id(archivoId);
    }
    
    // Métodos de consultas agregadas
    public Optional<Integer> sumTotalCajasByAño(int año) {
        return reporteGeneralRepository.sumTotalCajasByAño(año);
    }
    
    public Optional<BigDecimal> sumTotalPesosByAño(int año) {
        return reporteGeneralRepository.sumTotalPesosByAño(año);
    }
    
    public Optional<Integer> sumTotalCajasByMesAndAño(int mes, int año) {
        return reporteGeneralRepository.sumTotalCajasByMesAndAño(mes, año);
    }
    
    public Optional<BigDecimal> sumTotalPesosByMesAndAño(int mes, int año) {
        return reporteGeneralRepository.sumTotalPesosByMesAndAño(mes, año);
    }
    
    // Método con filtros múltiples
    public List<ReporteGeneral> findByFiltros(Integer año, Integer mes, String tipoReporte) {
        return reporteGeneralRepository.findByFiltros(año, mes, tipoReporte);
    }
    
    // Métodos de negocio
    public int getTotalCajasByAño(int año) {
        return sumTotalCajasByAño(año).orElse(0);
    }
    
    public BigDecimal getTotalPesosByAño(int año) {
        return sumTotalPesosByAño(año).orElse(BigDecimal.ZERO);
    }
    
    public int getTotalCajasByMesAndAño(int mes, int año) {
        return sumTotalCajasByMesAndAño(mes, año).orElse(0);
    }
    
    public BigDecimal getTotalPesosByMesAndAño(int mes, int año) {
        return sumTotalPesosByMesAndAño(mes, año).orElse(BigDecimal.ZERO);
    }
    
    // Métodos para obtener resúmenes
    public List<Map<String, Object>> getResumenAnual() {
        return reporteGeneralRepository.getResumenAnual().stream()
                .map(row -> Map.of(
                    "año", row[0],
                    "totalCajas", row[1],
                    "totalPesos", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getResumenMensualByAño(int año) {
        return reporteGeneralRepository.getResumenMensualByAño(año).stream()
                .map(row -> Map.of(
                    "mes", row[0],
                    "año", row[1],
                    "totalCajas", row[2],
                    "totalPesos", row[3]
                ))
                .collect(Collectors.toList());
    }
    
    // Método para obtener datos del dashboard
    public Map<String, Object> getDashboardData(int año) {
        List<ReporteGeneral> reportes = findByAño(año);
        
        int totalCajas = reportes.stream()
                .mapToInt(ReporteGeneral::getTotalCajas)
                .sum();
        
        BigDecimal totalPesos = reportes.stream()
                .map(ReporteGeneral::getTotalPesos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return Map.of(
            "año", año,
            "totalCajas", totalCajas,
            "totalPesos", totalPesos,
            "promedioCajasMensual", totalCajas / 12.0,
            "promedioPesosMensual", totalPesos.divide(BigDecimal.valueOf(12), 2, BigDecimal.ROUND_HALF_UP)
        );
    }
} 