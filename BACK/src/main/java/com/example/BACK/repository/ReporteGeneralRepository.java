package com.example.BACK.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BACK.model.ReporteGeneral;

@Repository
public interface ReporteGeneralRepository extends JpaRepository<ReporteGeneral, String> {
    
    // Buscar por año
    List<ReporteGeneral> findByAño(int año);
    
    // Buscar por mes y año
    List<ReporteGeneral> findByMesAndAño(int mes, int año);
    
    // Buscar por tipo de reporte
    List<ReporteGeneral> findByTipoReporte(String tipoReporte);
    
    // Buscar por archivo
    List<ReporteGeneral> findByArchivo_Id(Long archivoId);
    
    // Consultas agregadas
    @Query("SELECT SUM(r.totalCajas) FROM ReporteGeneral r WHERE r.año = :año")
    Optional<Integer> sumTotalCajasByAño(@Param("año") int año);
    
    @Query("SELECT SUM(r.totalPesos) FROM ReporteGeneral r WHERE r.año = :año")
    Optional<BigDecimal> sumTotalPesosByAño(@Param("año") int año);
    
    @Query("SELECT SUM(r.totalCajas) FROM ReporteGeneral r WHERE r.mes = :mes AND r.año = :año")
    Optional<Integer> sumTotalCajasByMesAndAño(@Param("mes") int mes, @Param("año") int año);
    
    @Query("SELECT SUM(r.totalPesos) FROM ReporteGeneral r WHERE r.mes = :mes AND r.año = :año")
    Optional<BigDecimal> sumTotalPesosByMesAndAño(@Param("mes") int mes, @Param("año") int año);
    
    // Consultas con filtros múltiples
    @Query("SELECT r FROM ReporteGeneral r WHERE " +
           "(:año IS NULL OR r.año = :año) AND " +
           "(:mes IS NULL OR r.mes = :mes) AND " +
           "(:tipoReporte IS NULL OR r.tipoReporte = :tipoReporte)")
    List<ReporteGeneral> findByFiltros(@Param("año") Integer año,
                                      @Param("mes") Integer mes,
                                      @Param("tipoReporte") String tipoReporte);
    
    // Consulta para obtener resumen anual
    @Query("SELECT r.año, SUM(r.totalCajas) as totalCajas, SUM(r.totalPesos) as totalPesos " +
           "FROM ReporteGeneral r " +
           "GROUP BY r.año " +
           "ORDER BY r.año")
    List<Object[]> getResumenAnual();
    
    // Consulta para obtener resumen mensual por año
    @Query("SELECT r.mes, r.año, SUM(r.totalCajas) as totalCajas, SUM(r.totalPesos) as totalPesos " +
           "FROM ReporteGeneral r " +
           "WHERE r.año = :año " +
           "GROUP BY r.mes, r.año " +
           "ORDER BY r.mes")
    List<Object[]> getResumenMensualByAño(@Param("año") int año);
} 