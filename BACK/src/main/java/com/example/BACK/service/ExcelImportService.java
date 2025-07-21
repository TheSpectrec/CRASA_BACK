package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.*;
import com.example.BACK.repository.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
public class ExcelImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private FamilyRepository familyRepo;
    @Autowired private MarkRepository markRepo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private Drive drive;

    @Scheduled(cron = "0 */1 * * * *")
    public List<ImportResultDTO> importarDesdeDriveConVentas() throws Exception {
        List<ImportResultDTO> resultados = new ArrayList<>();
        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) return resultados;

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents and (mimeType contains 'spreadsheet' or name contains '.xls')")
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
        Workbook workbook = nombreArchivo.toLowerCase().endsWith(".xls")
            ? new HSSFWorkbook(inputStream)
            : new XSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheet("FINAL");
        if (sheet == null) return ventas;

        ArchivoProcesado archivo = archivoRepo.save(
            new ArchivoProcesado(nombreArchivo, "Excel", LocalDateTime.now())
        );

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String customerCode = getCellString(row.getCell(0));
            String productCode = getCellString(row.getCell(1));
            String description = getCellString(row.getCell(2));
            int cantidad = extractInt(row.getCell(3));
            BigDecimal precioUnitario = extractDecimal(row.getCell(4));
            BigDecimal precioTotal = extractDecimal(row.getCell(6));
            String customerName = getCellString(row.getCell(9));
            String vendedorName = getCellString(row.getCell(10));
            int year = extractInt(row.getCell(11));
            String month = getCellString(row.getCell(12));
            String markName = getCellString(row.getCell(13));
            String companyName = getCellString(row.getCell(14));
            String familyName = getCellString(row.getCell(15));

            if (customerCode == null || productCode == null) {
                System.out.println("⚠️ Fila omitida - CustomerCode o ProductCode es null: Fila " + row.getRowNum());
                continue;
            }

            User vendedor = userRepo.findByNameIgnoreCase(vendedorName).orElse(null);
            if (vendedor == null) {
                System.out.println("⚠️ Vendedor no encontrado: " + vendedorName + " - Fila " + row.getRowNum());
                continue;
            }

            Customer cliente = customerRepo.findByCustomerCode(customerCode).orElseGet(() -> {
                try {
                    Customer nuevo = new Customer();
                    nuevo.setCustomerCode(customerCode);
                    nuevo.setName(customerName);
                    nuevo.setVendedor(vendedor);
                    Customer clienteGuardado = customerRepo.save(nuevo);
                    System.out.println("✅ Cliente creado: " + clienteGuardado.getCustomerCode());
                    return clienteGuardado;
                } catch (Exception e) {
                    System.err.println("❌ Error al crear cliente: " + customerCode + " - " + e.getMessage());
                    return null;
                }
            });

            if (cliente == null) {
                System.out.println("⚠️ No se pudo crear/obtener cliente: " + customerCode);
                continue;
            }

            Mark marca = markRepo.findById(markName).orElseGet(() -> markRepo.save(new Mark(markName)));
            Company company = companyRepo.findById(companyName).orElseGet(() -> companyRepo.save(new Company(companyName)));

            Family familia = familyRepo.findByName(familyName).orElseGet(() -> {
                Family nueva = new Family();
                nueva.setName(familyName);
                nueva.setMark(marca);
                return familyRepo.save(nueva);
            });

            Product producto = productRepo.findById(productCode).orElseGet(() -> {
                try {
                    Product nuevo = new Product();
                    nuevo.setCode(productCode);
                    nuevo.setDescription(description);
                    nuevo.setPrice(precioUnitario);
                    nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    nuevo.setFamily(familia);
                    nuevo.setMark(marca);
                    nuevo.setCompany(company);
                    Product productoGuardado = productRepo.save(nuevo);
                    System.out.println("✅ Producto creado: " + productoGuardado.getCode());
                    return productoGuardado;
                } catch (Exception e) {
                    System.err.println("❌ Error al crear producto: " + productCode + " - " + e.getMessage());
                    return null;
                }
            });

            if (producto == null) {
                System.out.println("⚠️ No se pudo crear/obtener producto: " + productCode);
                continue;
            }

            LocalDateTime fecha = LocalDateTime.of(year, convertirMes(month), 1, 0, 0);

            try {
                if (!ventaRepo.existsByClienteAndProductoAndFecha(cliente, producto, fecha)) {
                    Venta venta = new Venta(cliente, producto, cantidad, precioUnitario, precioTotal, fecha, archivo);
                    Venta ventaGuardada = ventaRepo.save(venta);
                    ventas.add(ventaGuardada);
                    System.out.println("✅ Venta guardada: " + ventaGuardada.getId());
                } else {
                    System.out.println("⚠️ Venta duplicada, no se guardó: " + customerCode + " - " + productCode);
                }
            } catch (Exception e) {
                System.err.println("❌ Error al guardar venta: " + e.getMessage());
                e.printStackTrace();
            }
        }

        workbook.close();
        return ventas;
    }

    private int convertirMes(String nombreMes) {
        return switch (nombreMes.toLowerCase()) {
            case "enero" -> 1; case "febrero" -> 2; case "marzo" -> 3; case "abril" -> 4;
            case "mayo" -> 5; case "junio" -> 6; case "julio" -> 7; case "agosto" -> 8;
            case "septiembre" -> 9; case "octubre" -> 10; case "noviembre" -> 11; case "diciembre" -> 12;
            default -> 1;
        };
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private BigDecimal extractDecimal(Cell cell) {
        return (cell != null && cell.getCellType() == CellType.NUMERIC)
            ? BigDecimal.valueOf(cell.getNumericCellValue())
            : BigDecimal.ZERO;
    }

    private int extractInt(Cell cell) {
        return (cell != null && cell.getCellType() == CellType.NUMERIC)
            ? (int) cell.getNumericCellValue()
            : 0;
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles().isEmpty()) return null;
        return result.getFiles().get(0).getId();
    }
}
