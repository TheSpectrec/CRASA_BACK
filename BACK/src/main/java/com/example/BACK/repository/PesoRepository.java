package com.example.BACK.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BACK.model.Peso;

@Repository
public interface PesoRepository extends JpaRepository<Peso, String> {
    
    // Buscar por cliente
    List<Peso> findByClienteId(String clienteId);
    
    // Buscar por producto
    List<Peso> findByProductoId(String productoId);
    
    // Buscar por familia
    List<Peso> findByFamiliaId(String familiaId);
    
    // Buscar por marca
    List<Peso> findByMarcaId(String marcaId);
    
    // Buscar por año
    List<Peso> findByAño(int año);
    
    // Buscar por mes y año
    List<Peso> findByMesAndAño(int mes, int año);
    
    // Buscar por tipo de reporte
    List<Peso> findByTipoReporte(String tipoReporte);
    
    // Buscar por archivo (usar navegación por propiedad para el id Long)
    List<Peso> findByArchivo_Id(Long archivoId);
    
    // Consultas agregadas
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.cliente.id = :clienteId AND p.año = :año")
    Optional<BigDecimal> sumValorByClienteAndAño(@Param("clienteId") String clienteId, @Param("año") int año);
    
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.producto.id = :productoId AND p.año = :año")
    Optional<BigDecimal> sumValorByProductoAndAño(@Param("productoId") String productoId, @Param("año") int año);
    
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.familia.id = :familiaId AND p.año = :año")
    Optional<BigDecimal> sumValorByFamiliaAndAño(@Param("familiaId") String familiaId, @Param("año") int año);
    
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.marca.id = :marcaId AND p.año = :año")
    Optional<BigDecimal> sumValorByMarcaAndAño(@Param("marcaId") String marcaId, @Param("año") int año);
    
    // Consultas por mes
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.cliente.id = :clienteId AND p.mes = :mes AND p.año = :año")
    Optional<BigDecimal> sumValorByClienteAndMesAndAño(@Param("clienteId") String clienteId, @Param("mes") int mes, @Param("año") int año);
    
    @Query("SELECT SUM(p.valor) FROM Peso p WHERE p.producto.id = :productoId AND p.mes = :mes AND p.año = :año")
    Optional<BigDecimal> sumValorByProductoAndMesAndAño(@Param("productoId") String productoId, @Param("mes") int mes, @Param("año") int año);
    
    // Consultas con filtros múltiples
    @Query("SELECT p FROM Peso p WHERE " +
           "(:clienteId IS NULL OR p.cliente.id = :clienteId) AND " +
           "(:productoId IS NULL OR p.producto.id = :productoId) AND " +
           "(:familiaId IS NULL OR p.familia.id = :familiaId) AND " +
           "(:marcaId IS NULL OR p.marca.id = :marcaId) AND " +
           "(:año IS NULL OR p.año = :año) AND " +
           "(:mes IS NULL OR p.mes = :mes)")
    List<Peso> findByFiltros(@Param("clienteId") String clienteId, 
                             @Param("productoId") String productoId,
                             @Param("familiaId") String familiaId,
                             @Param("marcaId") String marcaId,
                             @Param("año") Integer año,
                             @Param("mes") Integer mes);
    
    // Consultas específicas por tipo de reporte
    @Query("SELECT p FROM Peso p WHERE p.tipoReporte = :tipoReporte AND p.año = :año")
    List<Peso> findByTipoReporteAndAño(@Param("tipoReporte") String tipoReporte, @Param("año") int año);
    
    @Query("SELECT p FROM Peso p WHERE p.tipoReporte = :tipoReporte AND p.mes = :mes AND p.año = :año")
    List<Peso> findByTipoReporteAndMesAndAño(@Param("tipoReporte") String tipoReporte, @Param("mes") int mes, @Param("año") int año);
    
    // Consultas para reportes específicos
    @Query("SELECT p.cliente.name, p.marca.name, SUM(p.valor) as totalPesos " +
           "FROM Peso p WHERE p.tipoReporte = 'REP PESOS' AND p.año = :año " +
           "GROUP BY p.cliente.name, p.marca.name " +
           "ORDER BY p.cliente.name, p.marca.name")
    List<Object[]> getReportePesosByAño(@Param("año") int año);
    
    @Query("SELECT p.cliente.name, p.marca.name, SUM(p.valor) as totalPesos " +
           "FROM Peso p WHERE p.tipoReporte = 'REP PESOS' AND p.mes = :mes AND p.año = :año " +
           "GROUP BY p.cliente.name, p.marca.name " +
           "ORDER BY p.cliente.name, p.marca.name")
    List<Object[]> getReportePesosByMesAndAño(@Param("mes") int mes, @Param("año") int año);
} 