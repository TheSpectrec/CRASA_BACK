package com.example.BACK.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Caja;
import com.example.BACK.repository.CajaRepository;

@Service
public class CajaService {
    
    @Autowired
    private CajaRepository cajaRepository;
    
    // Métodos CRUD básicos
    public List<Caja> findAll() {
        return cajaRepository.findAll();
    }
    
    public Optional<Caja> findById(String id) {
        return cajaRepository.findById(id);
    }
    
    public Caja save(Caja caja) {
        return cajaRepository.save(caja);
    }
    
    public void delete(String id) {
        cajaRepository.deleteById(id);
    }
    
    // Métodos de búsqueda específicos
    public List<Caja> findByClienteId(String clienteId) {
        return cajaRepository.findByClienteId(clienteId);
    }
    
    public List<Caja> findByProductoId(String productoId) {
        return cajaRepository.findByProductoId(productoId);
    }
    
    public List<Caja> findByFamiliaId(String familiaId) {
        return cajaRepository.findByFamiliaId(familiaId);
    }
    
    public List<Caja> findByMarcaId(String marcaId) {
        return cajaRepository.findByMarcaId(marcaId);
    }
    
    public List<Caja> findByAño(int año) {
        return cajaRepository.findByAño(año);
    }
    
    public List<Caja> findByMesAndAño(int mes, int año) {
        return cajaRepository.findByMesAndAño(mes, año);
    }
    
    public List<Caja> findByTipoReporte(String tipoReporte) {
        return cajaRepository.findByTipoReporte(tipoReporte);
    }
    
    public List<Caja> findByArchivoId(Long archivoId) {
        return cajaRepository.findByArchivo_Id(archivoId);
    }
    
    // Métodos de consultas agregadas
    public Optional<Integer> sumCantidadByClienteAndAño(String clienteId, int año) {
        return cajaRepository.sumCantidadByClienteAndAño(clienteId, año);
    }
    
    public Optional<Integer> sumCantidadByProductoAndAño(String productoId, int año) {
        return cajaRepository.sumCantidadByProductoAndAño(productoId, año);
    }
    
    public Optional<Integer> sumCantidadByFamiliaAndAño(String familiaId, int año) {
        return cajaRepository.sumCantidadByFamiliaAndAño(familiaId, año);
    }
    
    public Optional<Integer> sumCantidadByMarcaAndAño(String marcaId, int año) {
        return cajaRepository.sumCantidadByMarcaAndAño(marcaId, año);
    }
    
    public Optional<Integer> sumCantidadByClienteAndMesAndAño(String clienteId, int mes, int año) {
        return cajaRepository.sumCantidadByClienteAndMesAndAño(clienteId, mes, año);
    }
    
    public Optional<Integer> sumCantidadByProductoAndMesAndAño(String productoId, int mes, int año) {
        return cajaRepository.sumCantidadByProductoAndMesAndAño(productoId, mes, año);
    }
    
    // Método con filtros múltiples
    public List<Caja> findByFiltros(String clienteId, String productoId, String familiaId, 
                                   String marcaId, Integer año, Integer mes) {
        return cajaRepository.findByFiltros(clienteId, productoId, familiaId, marcaId, año, mes);
    }
    
    // Métodos específicos por tipo de reporte
    public List<Caja> findByTipoReporteAndAño(String tipoReporte, int año) {
        return cajaRepository.findByTipoReporteAndAño(tipoReporte, año);
    }
    
    public List<Caja> findByTipoReporteAndMesAndAño(String tipoReporte, int mes, int año) {
        return cajaRepository.findByTipoReporteAndMesAndAño(tipoReporte, mes, año);
    }
    
    // Métodos para reportes específicos
    public List<Map<String, Object>> getReporteCajasByAño(int año) {
        return cajaRepository.getReporteCajasByAño(año).stream()
                .map(row -> Map.of(
                    "cliente", row[0],
                    "marca", row[1],
                    "totalCajas", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getReporteProductosByAño(int año) {
        return cajaRepository.getReporteProductosByAño(año).stream()
                .map(row -> Map.of(
                    "producto", row[0],
                    "totalCajas", row[1]
                ))
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getReporteFamiliasByAño(int año) {
        return cajaRepository.getReporteFamiliasByAño(año).stream()
                .map(row -> Map.of(
                    "familia", row[0],
                    "totalCajas", row[1]
                ))
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getReporteMarcasByAño(int año) {
        return cajaRepository.getReporteMarcasByAño(año).stream()
                .map(row -> Map.of(
                    "cliente", row[0],
                    "marca", row[1],
                    "totalCajas", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    // Métodos de negocio
    public int getTotalCajasByAño(int año) {
        return cajaRepository.findByAño(año).stream()
                .mapToInt(Caja::getCantidad)
                .sum();
    }
    
    public int getTotalCajasByClienteAndAño(String clienteId, int año) {
        return sumCantidadByClienteAndAño(clienteId, año).orElse(0);
    }
    
    public int getTotalCajasByProductoAndAño(String productoId, int año) {
        return sumCantidadByProductoAndAño(productoId, año).orElse(0);
    }
    
    public int getTotalCajasByMesAndAño(int mes, int año) {
        return cajaRepository.findByMesAndAño(mes, año).stream()
                .mapToInt(Caja::getCantidad)
                .sum();
    }
} 