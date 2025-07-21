package com.example.BACK.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.BACK.model.ArchivoProcesado;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, String> {
    boolean existsByClienteAndProductoAndFecha(Customer cliente, Product producto, LocalDateTime fecha);
    List<Venta> findByArchivo(ArchivoProcesado archivo);
    
    // Nuevos métodos para mejor consulta de datos
    List<Venta> findByCliente(Customer cliente);
    List<Venta> findByProducto(Product producto);
    List<Venta> findByFechaBetween(LocalDateTime fechaInicio, LocalDateTime fechaFin);
    
    @Query("SELECT v FROM Venta v WHERE v.cliente.customerCode = :customerCode")
    List<Venta> findByCustomerCode(@Param("customerCode") String customerCode);
    
    @Query("SELECT v FROM Venta v WHERE v.producto.code = :productCode")
    List<Venta> findByProductCode(@Param("productCode") String productCode);
    
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.archivo = :archivo")
    long countByArchivo(@Param("archivo") ArchivoProcesado archivo);
    
    @Query("SELECT v FROM Venta v WHERE v.cliente.name LIKE %:nombreCliente%")
    List<Venta> findByClienteNameContaining(@Param("nombreCliente") String nombreCliente);
    
    @Query("SELECT v FROM Venta v WHERE v.producto.description LIKE %:descripcion%")
    List<Venta> findByProductoDescriptionContaining(@Param("descripcion") String descripcion);
}
