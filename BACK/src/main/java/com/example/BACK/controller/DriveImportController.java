package com.example.BACK.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.BACK.service.ExcelImportService;
import com.example.BACK.service.GoogleDriveService;
import com.example.BACK.service.PdfImportService;

@RestController
@RequestMapping("/api/drive")
public class DriveImportController {

    @Autowired private GoogleDriveService driveService;
    @Autowired private PdfImportService pdfImportService;
    @Autowired private ExcelImportService excelImportService;

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
                pdfImportService.procesarFacturaPDF(file.getInputStream());
            } else if (mimeType != null && (mimeType.contains("spreadsheet") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls"))) {
                excelImportService.procesarReporteVentas(file.getInputStream());
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
            pdfImportService.importarDesdeCarpetaCRASAVentas();
            excelImportService.importarDesdeCarpetaCRASAVentas();
            return ResponseEntity.ok("✅ Archivos importados exitosamente desde la carpeta CRASA_VENTAS.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error al importar desde carpeta: " + e.getMessage());
        }
    }
}
