package com.example.BACK.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.ArchivoProcesado;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.ArchivoProcesadoRepository;
import com.example.BACK.repository.VentaRepository;
import com.example.BACK.service.ExcelImportService;
import com.example.BACK.service.GoogleDriveService;
import com.example.BACK.service.PdfImportService;

@RestController
@RequestMapping("/api/drive")
public class DriveImportController {

    @Autowired private GoogleDriveService driveService;
    @Autowired private PdfImportService pdfImportService;
    @Autowired private ExcelImportService excelImportService;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;

    @PostMapping("/carpeta")
    public ResponseEntity<String> crearCarpeta(@RequestParam("nombre") String nombre) {
        try {
            String id = driveService.crearCarpeta(nombre);
            return ResponseEntity.ok("✅ Carpeta creada con ID: " + id);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("❌ Error al crear carpeta: " + e.getMessage());
        }
    }

    @PostMapping("/subir")
    public ResponseEntity<String> subirArchivo(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "carpetaId", required = false) String carpetaId) {
        try {
            String mimeType = file.getContentType();
            String id = driveService.subirArchivo(file.getOriginalFilename(), mimeType, file.getInputStream(), carpetaId);
            return ResponseEntity.ok("✅ Archivo subido con ID: " + id);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("❌ Error al subir archivo: " + e.getMessage());
        }
    }

    @PostMapping("/importar")
    public ResponseEntity<String> importarArchivo(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "carpetaId", required = false) String carpetaId) {
        try {
            String mimeType = file.getContentType();
            String fileName = file.getOriginalFilename();
            String fileId = driveService.subirArchivo(fileName, mimeType, file.getInputStream(), carpetaId);

            if (mimeType != null && mimeType.contains("pdf")) {
                pdfImportService.procesarFacturaPDF(file.getInputStream(), fileName);
            } else if (mimeType != null && (mimeType.contains("spreadsheet") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls"))) {
                excelImportService.procesarReporteVentas(file.getInputStream(), fileName);
            } else {
                return ResponseEntity.badRequest().body("❌ Tipo de archivo no soportado: " + mimeType);
            }

            return ResponseEntity.ok("✅ Archivo importado y procesado correctamente. ID en Drive: " + fileId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error al procesar archivo: " + e.getMessage());
        }
    }

    @PostMapping("/importar-carpeta")
    public ResponseEntity<String> importarDesdeCarpetaDrive() {
        try {
            pdfImportService.importarDesdeDriveConVentas();
            excelImportService.importarDesdeDriveConVentas();
            return ResponseEntity.ok("✅ Archivos importados exitosamente desde la carpeta CRASA_VENTAS.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error al importar desde carpeta: " + e.getMessage());
        }
    }

    @GetMapping("/importar-carpeta")
    public List<ImportResultDTO> listarImportaciones() {
        List<ImportResultDTO> resultado = new ArrayList<>();

        List<ArchivoProcesado> archivos = archivoRepo.findAll();

        for (ArchivoProcesado archivo : archivos) {
            List<Venta> ventas = ventaRepo.findByArchivo(archivo);
            resultado.add(new ImportResultDTO(archivo.getNombre(), archivo.getTipo(), ventas));
        }

        return resultado;
    }

    @GetMapping("/ventas")
    public List<Venta> obtenerTodasLasVentas() {
        return ventaRepo.findAll();
    }

    @GetMapping("/ventas/por-archivo")
    public List<Venta> obtenerVentasPorArchivo(@RequestParam String nombre) {
    ArchivoProcesado archivo = archivoRepo.findByNombre(nombre).orElse(null);
        if (archivo == null) return Collections.emptyList();
            return ventaRepo.findByArchivo(archivo);
    }
    
    @GetMapping("/diagnostico")
    public ResponseEntity<String> diagnosticarEstado() {
        StringBuilder diagnostico = new StringBuilder();
        diagnostico.append("=== DIAGNÓSTICO DEL SISTEMA ===\n\n");
        
        try {
            long totalVentas = ventaRepo.count();
            diagnostico.append("📊 Total de ventas en BD: ").append(totalVentas).append("\n");
            
            long totalArchivos = archivoRepo.count();
            diagnostico.append("📁 Total de archivos procesados: ").append(totalArchivos).append("\n");
            
            List<ArchivoProcesado> archivos = archivoRepo.findAll();
            List<ArchivoProcesado> archivosConError = archivos.stream()
                .filter(a -> a.getTipo().contains("Error"))
                .toList();
            
            diagnostico.append("\n📋 Archivos procesados exitosamente: ").append(archivos.size() - archivosConError.size()).append("\n");
            diagnostico.append("⚠️ Archivos con errores: ").append(archivosConError.size()).append("\n");
            
            if (!archivosConError.isEmpty()) {
                diagnostico.append("\n🚨 ARCHIVOS CON ERRORES:\n");
                for (ArchivoProcesado archivo : archivosConError) {
                    diagnostico.append("  - ").append(archivo.getNombre())
                        .append(" (").append(archivo.getTipo()).append(") - ")
                        .append(archivo.getFecha()).append("\n");
                }
            }
            
            diagnostico.append("\n📈 ARCHIVOS PROCESADOS EXITOSAMENTE:\n");
            for (ArchivoProcesado archivo : archivos) {
                if (!archivo.getTipo().contains("Error")) {
                    long ventasArchivo = ventaRepo.countByArchivo(archivo);
                    diagnostico.append("  ✅ ").append(archivo.getNombre())
                              .append(" (").append(archivo.getTipo()).append("): ")
                              .append(ventasArchivo).append(" ventas\n");
                }
            }
            
            diagnostico.append("\n✅ Conexión a base de datos: OK");
            diagnostico.append("\n💡 IOUtils límite configurado: 200MB para archivos Excel grandes");
            
        } catch (Exception e) {
            diagnostico.append("❌ Error en diagnóstico: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(diagnostico.toString());
    }
    
    @PostMapping("/test-save")
    public ResponseEntity<String> probarGuardadoVenta() {
        try {
            // Crear datos de prueba
            ArchivoProcesado archivoTest = new ArchivoProcesado("TEST_FILE.pdf", "PDF", LocalDateTime.now());
            archivoTest = archivoRepo.save(archivoTest);
            
            // Crear cliente de prueba
            Customer clienteTest = new Customer();
            clienteTest.setCustomerCode("TEST001");
            clienteTest.setName("Cliente de Prueba");
            // No guardar aún para verificar si causa problemas
            
            return ResponseEntity.ok("✅ Prueba completada - revisar logs para detalles");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error en prueba: " + e.getMessage());
        }
    }
    
}
