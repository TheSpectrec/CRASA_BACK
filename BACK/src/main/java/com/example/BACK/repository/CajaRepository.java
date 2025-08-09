package com.example.BACK.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BACK.model.Caja;

@Repository
public interface CajaRepository extends JpaRepository<Caja, String> {
    
    // Buscar por cliente
    List<Caja> findByClienteId(String clienteId);
    
    // Buscar por producto
    List<Caja> findByProductoId(String productoId);
    
    // Buscar por familia
    List<Caja> findByFamiliaId(String familiaId);
    
    // Buscar por marca
    List<Caja> findByMarcaId(String marcaId);
    
    // Buscar por año
    List<Caja> findByAño(int año);
    
    // Buscar por mes y año
    List<Caja> findByMesAndAño(int mes, int año);
    
    // Buscar por tipo de reporte
    List<Caja> findByTipoReporte(String tipoReporte);
    
    // Buscar por archivo (usar navegación por propiedad para el id Long)
    List<Caja> findByArchivo_Id(Long archivoId);
    
    // Consultas agregadas
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.cliente.id = :clienteId AND c.año = :año")
    Optional<Integer> sumCantidadByClienteAndAño(@Param("clienteId") String clienteId, @Param("año") int año);
    
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.producto.id = :productoId AND c.año = :año")
    Optional<Integer> sumCantidadByProductoAndAño(@Param("productoId") String productoId, @Param("año") int año);
    
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.familia.id = :familiaId AND c.año = :año")
    Optional<Integer> sumCantidadByFamiliaAndAño(@Param("familiaId") String familiaId, @Param("año") int año);
    
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.marca.id = :marcaId AND c.año = :año")
    Optional<Integer> sumCantidadByMarcaAndAño(@Param("marcaId") String marcaId, @Param("año") int año);
    
    // Consultas por mes
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.cliente.id = :clienteId AND c.mes = :mes AND c.año = :año")
    Optional<Integer> sumCantidadByClienteAndMesAndAño(@Param("clienteId") String clienteId, @Param("mes") int mes, @Param("año") int año);
    
    @Query("SELECT SUM(c.cantidad) FROM Caja c WHERE c.producto.id = :productoId AND c.mes = :mes AND c.año = :año")
    Optional<Integer> sumCantidadByProductoAndMesAndAño(@Param("productoId") String productoId, @Param("mes") int mes, @Param("año") int año);
    
    // Consultas con filtros múltiples
    @Query("SELECT c FROM Caja c WHERE " +
           "(:clienteId IS NULL OR c.cliente.id = :clienteId) AND " +
           "(:productoId IS NULL OR c.producto.id = :productoId) AND " +
           "(:familiaId IS NULL OR c.familia.id = :familiaId) AND " +
           "(:marcaId IS NULL OR c.marca.id = :marcaId) AND " +
           "(:año IS NULL OR c.año = :año) AND " +
           "(:mes IS NULL OR c.mes = :mes)")
    List<Caja> findByFiltros(@Param("clienteId") String clienteId, 
                             @Param("productoId") String productoId,
                             @Param("familiaId") String familiaId,
                             @Param("marcaId") String marcaId,
                             @Param("año") Integer año,
                             @Param("mes") Integer mes);
    
    // Consultas específicas por tipo de reporte
    @Query("SELECT c FROM Caja c WHERE c.tipoReporte = :tipoReporte AND c.año = :año")
    List<Caja> findByTipoReporteAndAño(@Param("tipoReporte") String tipoReporte, @Param("año") int año);
    
    @Query("SELECT c FROM Caja c WHERE c.tipoReporte = :tipoReporte AND c.mes = :mes AND c.año = :año")
    List<Caja> findByTipoReporteAndMesAndAño(@Param("tipoReporte") String tipoReporte, @Param("mes") int mes, @Param("año") int año);
    
    // Consultas para reportes específicos
    @Query("SELECT c.cliente.name, c.marca.name, SUM(c.cantidad) as totalCajas " +
           "FROM Caja c WHERE c.tipoReporte = 'REP CAJAS' AND c.año = :año " +
           "GROUP BY c.cliente.name, c.marca.name " +
           "ORDER BY c.cliente.name, c.marca.name")
    List<Object[]> getReporteCajasByAño(@Param("año") int año);
    
    @Query("SELECT c.producto.description, SUM(c.cantidad) as totalCajas " +
           "FROM Caja c WHERE c.tipoReporte = 'REP PRODUCTOS' AND c.año = :año " +
           "GROUP BY c.producto.description " +
           "ORDER BY totalCajas DESC")
    List<Object[]> getReporteProductosByAño(@Param("año") int año);
    
    @Query("SELECT c.familia.name, SUM(c.cantidad) as totalCajas " +
           "FROM Caja c WHERE c.tipoReporte = 'FAMILIAS' AND c.año = :año " +
           "GROUP BY c.familia.name " +
           "ORDER BY totalCajas DESC")
    List<Object[]> getReporteFamiliasByAño(@Param("año") int año);
    
    @Query("SELECT c.cliente.name, c.marca.name, SUM(c.cantidad) as totalCajas " +
           "FROM Caja c WHERE c.tipoReporte = 'MARCA' AND c.año = :año " +
           "GROUP BY c.cliente.name, c.marca.name " +
           "ORDER BY c.cliente.name, c.marca.name")
    List<Object[]> getReporteMarcasByAño(@Param("año") int año);
} 