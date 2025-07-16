package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.*;
import com.example.BACK.repository.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;

@Service
public class ExcelImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private Drive drive;

    @Scheduled(cron = "0 */1 * * * *")
    public List<ImportResultDTO> importarDesdeDriveConVentas() throws Exception {
        List<ImportResultDTO> resultados = new ArrayList<>();
        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) return resultados;

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents and mimeType contains 'spreadsheet'")
            .setFields("files(id, name)")
            .execute();

        for (File archivo : archivos.getFiles()) {
            String nombreArchivo = archivo.getName();
            if (archivoRepo.existsByNombre(nombreArchivo)) {
                System.out.println("⚠️ Ya procesado: " + nombreArchivo);
                continue;
            }

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                List<Venta> ventas = procesarReporteVentas(inputStream, nombreArchivo);
                resultados.add(new ImportResultDTO(nombreArchivo, "Excel", ventas));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return resultados;
    }

    public List<Venta> procesarReporteVentas(InputStream inputStream, String nombreArchivo) throws Exception {
        List<Venta> ventas = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        ArchivoProcesado archivo = archivoRepo.save(
            new ArchivoProcesado(nombreArchivo, "Excel", LocalDateTime.now())
        );

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String customerCode = getCellString(row.getCell(0));
            String customerName = getCellString(row.getCell(1));
            String productDescription = getCellString(row.getCell(3));
            int cantidad = (int) row.getCell(4).getNumericCellValue();
            BigDecimal precio = new BigDecimal(row.getCell(5).getNumericCellValue());
            BigDecimal total = new BigDecimal(row.getCell(6).getNumericCellValue());
            LocalDateTime fechaHora = row.getCell(7).getLocalDateTimeCellValue().toLocalDate().atStartOfDay();

            Customer cliente = customerRepo.findByCustomerCode(customerCode).orElseGet(() -> {
                Customer nuevo = new Customer();
                nuevo.setCustomerCode(customerCode);
                nuevo.setName(customerName);
                return customerRepo.save(nuevo);
            });

            Product producto = productRepo.findByDescription(productDescription).orElseGet(() -> {
                Product nuevo = new Product();
                nuevo.setCode("AUTO-" + System.currentTimeMillis());
                nuevo.setDescription(productDescription);
                nuevo.setPrice(precio);
                return productRepo.save(nuevo);
            });

            if (!ventaRepo.existsByClienteAndProductoAndFecha(cliente, producto, fechaHora)) {
                Venta venta = new Venta(cliente, producto, cantidad, precio, total, fechaHora, archivo);
                ventaRepo.save(venta);
                ventas.add(venta);
            }
        }

        workbook.close();
        return ventas;
    }

    public String importarDesdeCarpetaCRASAVentasConResumen() throws Exception {
        StringBuilder resumen = new StringBuilder();
        int excelProcesados = 0;
        int errores = 0;

        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) return "❌ Carpeta no encontrada.";

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents and mimeType contains 'spreadsheet'")
            .setFields("files(id, name)")
            .execute();

        for (File archivo : archivos.getFiles()) {
            String nombreArchivo = archivo.getName();
            if (archivoRepo.existsByNombre(nombreArchivo)) {
                System.out.println("⚠️ Ya procesado (resumen): " + nombreArchivo);
                continue;
            }

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                procesarReporteVentas(inputStream, nombreArchivo);
                excelProcesados++;
            } catch (Exception e) {
                resumen.append("❌ Error al procesar: ").append(nombreArchivo).append("\n");
                errores++;
            }
        }

        resumen.append("✅ Excel procesados: ").append(excelProcesados).append("\n");
        resumen.append("❌ Con errores: ").append(errores).append("\n");
        return resumen.toString();
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles().isEmpty()) return null;
        return result.getFiles().get(0).getId();
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }
}
