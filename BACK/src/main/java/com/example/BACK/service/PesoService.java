package com.example.BACK.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Peso;
import com.example.BACK.repository.PesoRepository;

@Service
public class PesoService {
    
    @Autowired
    private PesoRepository pesoRepository;
    
    // Métodos CRUD básicos
    public List<Peso> findAll() {
        return pesoRepository.findAll();
    }
    
    public Optional<Peso> findById(String id) {
        return pesoRepository.findById(id);
    }
    
    public Peso save(Peso peso) {
        return pesoRepository.save(peso);
    }
    
    public void delete(String id) {
        pesoRepository.deleteById(id);
    }
    
    // Métodos de búsqueda específicos
    public List<Peso> findByClienteId(String clienteId) {
        return pesoRepository.findByClienteId(clienteId);
    }
    
    public List<Peso> findByProductoId(String productoId) {
        return pesoRepository.findByProductoId(productoId);
    }
    
    public List<Peso> findByFamiliaId(String familiaId) {
        return pesoRepository.findByFamiliaId(familiaId);
    }
    
    public List<Peso> findByMarcaId(String marcaId) {
        return pesoRepository.findByMarcaId(marcaId);
    }
    
    public List<Peso> findByAño(int año) {
        return pesoRepository.findByAño(año);
    }
    
    public List<Peso> findByMesAndAño(int mes, int año) {
        return pesoRepository.findByMesAndAño(mes, año);
    }
    
    public List<Peso> findByTipoReporte(String tipoReporte) {
        return pesoRepository.findByTipoReporte(tipoReporte);
    }
    
    public List<Peso> findByArchivoId(Long archivoId) {
        return pesoRepository.findByArchivo_Id(archivoId);
    }
    
    // Métodos de consultas agregadas
    public Optional<BigDecimal> sumValorByClienteAndAño(String clienteId, int año) {
        return pesoRepository.sumValorByClienteAndAño(clienteId, año);
    }
    
    public Optional<BigDecimal> sumValorByProductoAndAño(String productoId, int año) {
        return pesoRepository.sumValorByProductoAndAño(productoId, año);
    }
    
    public Optional<BigDecimal> sumValorByFamiliaAndAño(String familiaId, int año) {
        return pesoRepository.sumValorByFamiliaAndAño(familiaId, año);
    }
    
    public Optional<BigDecimal> sumValorByMarcaAndAño(String marcaId, int año) {
        return pesoRepository.sumValorByMarcaAndAño(marcaId, año);
    }
    
    public Optional<BigDecimal> sumValorByClienteAndMesAndAño(String clienteId, int mes, int año) {
        return pesoRepository.sumValorByClienteAndMesAndAño(clienteId, mes, año);
    }
    
    public Optional<BigDecimal> sumValorByProductoAndMesAndAño(String productoId, int mes, int año) {
        return pesoRepository.sumValorByProductoAndMesAndAño(productoId, mes, año);
    }
    
    // Método con filtros múltiples
    public List<Peso> findByFiltros(String clienteId, String productoId, String familiaId, 
                                   String marcaId, Integer año, Integer mes) {
        return pesoRepository.findByFiltros(clienteId, productoId, familiaId, marcaId, año, mes);
    }
    
    // Métodos específicos por tipo de reporte
    public List<Peso> findByTipoReporteAndAño(String tipoReporte, int año) {
        return pesoRepository.findByTipoReporteAndAño(tipoReporte, año);
    }
    
    public List<Peso> findByTipoReporteAndMesAndAño(String tipoReporte, int mes, int año) {
        return pesoRepository.findByTipoReporteAndMesAndAño(tipoReporte, mes, año);
    }
    
    // Métodos para reportes específicos
    public List<Map<String, Object>> getReportePesosByAño(int año) {
        return pesoRepository.getReportePesosByAño(año).stream()
                .map(row -> Map.of(
                    "cliente", row[0],
                    "marca", row[1],
                    "totalPesos", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getReportePesosByMesAndAño(int mes, int año) {
        return pesoRepository.getReportePesosByMesAndAño(mes, año).stream()
                .map(row -> Map.of(
                    "cliente", row[0],
                    "marca", row[1],
                    "totalPesos", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    // Métodos de negocio
    public BigDecimal getTotalPesosByAño(int año) {
        return pesoRepository.findByAño(año).stream()
                .map(Peso::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalPesosByClienteAndAño(String clienteId, int año) {
        return sumValorByClienteAndAño(clienteId, año).orElse(BigDecimal.ZERO);
    }
    
    public BigDecimal getTotalPesosByProductoAndAño(String productoId, int año) {
        return sumValorByProductoAndAño(productoId, año).orElse(BigDecimal.ZERO);
    }
    
    public BigDecimal getTotalPesosByMesAndAño(int mes, int año) {
        return pesoRepository.findByMesAndAño(mes, año).stream()
                .map(Peso::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
} 