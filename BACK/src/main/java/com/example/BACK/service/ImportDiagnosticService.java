package com.example.BACK.service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.*;
import com.example.BACK.repository.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;

@Service
public class ImportDiagnosticService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private FamilyRepository familyRepo;
    @Autowired private MarkRepository markRepo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private Drive drive;

    public Map<String, Object> obtenerEstadisticasImportacion() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Estadísticas básicas
            long totalClientes = customerRepo.count();
            long totalProductos = productRepo.count();
            long totalVentas = ventaRepo.count();
            long totalArchivos = archivoRepo.count();
            long totalFamilias = familyRepo.count();
            long totalMarcas = markRepo.count();
            long totalCompanias = companyRepo.count();

            stats.put("totalClientes", totalClientes);
            stats.put("totalProductos", totalProductos);
            stats.put("totalVentas", totalVentas);
            stats.put("totalArchivos", totalArchivos);
            stats.put("totalFamilias", totalFamilias);
            stats.put("totalMarcas", totalMarcas);
            stats.put("totalCompanias", totalCompanias);

            // Archivos procesados por tipo
            List<ArchivoProcesado> archivosPDF = archivoRepo.findByTipo("PDF");
            List<ArchivoProcesado> archivosExcel = archivoRepo.findByTipo("Excel");
            
            stats.put("archivosPDF", archivosPDF.size());
            stats.put("archivosExcel", archivosExcel.size());

            // Estado de conexión a Google Drive
            try {
                String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
                if (folderId != null) {
                    FileList archivos = drive.files().list()
                        .setQ("'" + folderId + "' in parents")
                        .setFields("files(id, name, mimeType)")
                        .execute();
                    
                    stats.put("googleDriveConectado", true);
                    stats.put("archivosEnDrive", archivos.getFiles().size());
                    
                    // Contar por tipo
                    long pdfEnDrive = archivos.getFiles().stream()
                        .filter(f -> f.getName().toLowerCase().endsWith(".pdf") || 
                                   (f.getMimeType() != null && f.getMimeType().contains("pdf")))
                        .count();
                    
                    long excelEnDrive = archivos.getFiles().stream()
                        .filter(f -> f.getName().toLowerCase().endsWith(".xlsx") || 
                                   f.getName().toLowerCase().endsWith(".xls") ||
                                   (f.getMimeType() != null && f.getMimeType().contains("spreadsheet")))
                        .count();
                    
                    stats.put("pdfEnDrive", pdfEnDrive);
                    stats.put("excelEnDrive", excelEnDrive);
                } else {
                    stats.put("googleDriveConectado", false);
                    stats.put("error", "Carpeta CRASA_VENTAS no encontrada");
                }
            } catch (Exception e) {
                stats.put("googleDriveConectado", false);
                stats.put("error", "Error conectando a Google Drive: " + e.getMessage());
            }

            stats.put("status", "success");
            
        } catch (Exception e) {
            stats.put("status", "error");
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    public Map<String, Object> verificarConfiguracion() {
        Map<String, Object> config = new HashMap<>();
        
        try {
            // Verificar inyección de dependencias
            config.put("customerRepoInyectado", customerRepo != null);
            config.put("productRepoInyectado", productRepo != null);
            config.put("ventaRepoInyectado", ventaRepo != null);
            config.put("archivoRepoInyectado", archivoRepo != null);
            config.put("driveInyectado", drive != null);

            // Verificar conectividad de base de datos
            try {
                customerRepo.count();
                config.put("baseDatosConectada", true);
            } catch (Exception e) {
                config.put("baseDatosConectada", false);
                config.put("errorBaseDatos", e.getMessage());
            }

            // Verificar estructura de tablas
            try {
                List<Customer> customers = customerRepo.findAll();
                List<Product> products = productRepo.findAll();
                List<Family> families = familyRepo.findAll();
                List<Mark> marks = markRepo.findAll();
                List<Company> companies = companyRepo.findAll();
                
                config.put("tablasExisten", true);
                config.put("customersSample", customers.size() > 0 ? customers.get(0).getName() : "Sin datos");
                config.put("productsSample", products.size() > 0 ? products.get(0).getDescription() : "Sin datos");
            } catch (Exception e) {
                config.put("tablasExisten", false);
                config.put("errorTablas", e.getMessage());
            }

            config.put("status", "success");
            
        } catch (Exception e) {
            config.put("status", "error");
            config.put("error", e.getMessage());
        }
        
        return config;
    }

    public String realizarPruebaCompleta() {
        StringBuilder resultado = new StringBuilder();
        
        resultado.append("🔍 DIAGNÓSTICO COMPLETO DE IMPORTACIÓN\n");
        resultado.append("=" .repeat(50)).append("\n\n");

        // 1. Verificar configuración
        resultado.append("1️⃣ CONFIGURACIÓN:\n");
        Map<String, Object> config = verificarConfiguracion();
        config.forEach((key, value) -> 
            resultado.append("   ").append(key).append(": ").append(value).append("\n"));
        resultado.append("\n");

        // 2. Estadísticas
        resultado.append("2️⃣ ESTADÍSTICAS:\n");
        Map<String, Object> stats = obtenerEstadisticasImportacion();
        stats.forEach((key, value) -> 
            resultado.append("   ").append(key).append(": ").append(value).append("\n"));
        resultado.append("\n");

        // 3. Verificaciones específicas
        resultado.append("3️⃣ VERIFICACIONES ESPECÍFICAS:\n");
        
        try {
            // Verificar si existe al menos una compañía
            List<Company> companies = companyRepo.findAll();
            if (companies.isEmpty()) {
                resultado.append("   ⚠️ No hay compañías en la base de datos\n");
                // Crear compañía por defecto
                Company defaultCompany = new Company();
                defaultCompany.setName("Importaciones Automáticas");
                companyRepo.save(defaultCompany);
                resultado.append("   ✅ Compañía por defecto creada\n");
            } else {
                resultado.append("   ✅ Compañías disponibles: ").append(companies.size()).append("\n");
            }

            // Verificar si existe al menos una marca
            List<Mark> marks = markRepo.findAll();
            if (marks.isEmpty()) {
                resultado.append("   ⚠️ No hay marcas en la base de datos\n");
                // Crear marca por defecto si hay compañías
                if (!companies.isEmpty() || companyRepo.count() > 0) {
                    Mark defaultMark = new Mark();
                    defaultMark.setName("General");
                    defaultMark.setCompany(companyRepo.findAll().get(0));
                    markRepo.save(defaultMark);
                    resultado.append("   ✅ Marca por defecto creada\n");
                }
            } else {
                resultado.append("   ✅ Marcas disponibles: ").append(marks.size()).append("\n");
            }

            // Verificar si existe al menos una familia
            List<Family> families = familyRepo.findAll();
            if (families.isEmpty()) {
                resultado.append("   ⚠️ No hay familias en la base de datos\n");
                // Crear familia por defecto si hay marcas
                if (!marks.isEmpty() || markRepo.count() > 0) {
                    Family defaultFamily = new Family();
                    defaultFamily.setName("General");
                    defaultFamily.setMark(markRepo.findAll().get(0));
                    familyRepo.save(defaultFamily);
                    resultado.append("   ✅ Familia por defecto creada\n");
                }
            } else {
                resultado.append("   ✅ Familias disponibles: ").append(families.size()).append("\n");
            }

        } catch (Exception e) {
            resultado.append("   ❌ Error en verificaciones: ").append(e.getMessage()).append("\n");
        }

        // 4. Recomendaciones
        resultado.append("\n4️⃣ RECOMENDACIONES:\n");
        
        if ((Boolean) config.getOrDefault("googleDriveConectado", false)) {
            resultado.append("   ✅ Google Drive conectado correctamente\n");
        } else {
            resultado.append("   ❌ Google Drive no conectado. Verificar:\n");
            resultado.append("      - Archivo credentials.json en src/main/resources/\n");
            resultado.append("      - Permisos de la aplicación en Google Cloud Console\n");
            resultado.append("      - Carpeta CRASA_VENTAS existe en Google Drive\n");
        }

        if ((Boolean) config.getOrDefault("baseDatosConectada", false)) {
            resultado.append("   ✅ Base de datos conectada correctamente\n");
        } else {
            resultado.append("   ❌ Base de datos no conectada. Verificar application.properties\n");
        }

        long totalVentas = (Long) stats.getOrDefault("totalVentas", 0L);
        if (totalVentas > 0) {
            resultado.append("   ✅ Sistema procesando datos: ").append(totalVentas).append(" ventas registradas\n");
        } else {
            resultado.append("   ⚠️ No hay ventas registradas. El sistema está listo para procesar archivos\n");
        }

        resultado.append("\n🎯 ESTADO GENERAL: ");
        if ((Boolean) config.getOrDefault("googleDriveConectado", false) && 
            (Boolean) config.getOrDefault("baseDatosConectada", false)) {
            resultado.append("SISTEMA OPERATIVO ✅\n");
        } else {
            resultado.append("REQUIERE CONFIGURACIÓN ⚠️\n");
        }

        return resultado.toString();
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();
            
        if (result.getFiles().isEmpty()) {
            return null;
        }
        
        return result.getFiles().get(0).getId();
    }
}