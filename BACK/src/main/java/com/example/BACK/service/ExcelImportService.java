package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.*;
import com.example.BACK.repository.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;

@Service
@Transactional
public class ExcelImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private FamilyRepository familyRepo;
    @Autowired private MarkRepository markRepo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private Drive drive;

    @Scheduled(cron = "0 */1 * * * *")
    public List<ImportResultDTO> importarDesdeDriveConVentas() throws Exception {
        List<ImportResultDTO> resultados = new ArrayList<>();
        
        try {
            String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
            if (folderId == null) {
                System.out.println("⚠️ Carpeta CRASA_VENTAS no encontrada");
                return resultados;
            }

            FileList archivos = drive.files().list()
                .setQ("'" + folderId + "' in parents and (mimeType contains 'spreadsheet' or name contains '.xlsx' or name contains '.xls')")
                .setFields("files(id, name)")
                .execute();

            System.out.println("📊 Encontrados " + archivos.getFiles().size() + " archivos Excel");

            for (File archivo : archivos.getFiles()) {
                String nombreArchivo = archivo.getName();
                if (archivoRepo.existsByNombre(nombreArchivo)) {
                    System.out.println("⚠️ Ya procesado: " + nombreArchivo);
                    continue;
                }

                try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                    List<Venta> ventas = procesarReporteVentas(inputStream, nombreArchivo);
                    resultados.add(new ImportResultDTO(nombreArchivo, "Excel", ventas));
                    System.out.println("✅ Excel procesado: " + nombreArchivo + " (" + ventas.size() + " ventas)");
                } catch (Exception e) {
                    System.err.println("❌ Error procesando Excel: " + nombreArchivo + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error general en importación Excel: " + e.getMessage());
            e.printStackTrace();
        }

        return resultados;
    }

    public List<Venta> procesarReporteVentas(InputStream inputStream, String nombreArchivo) throws Exception {
        List<Venta> ventas = new ArrayList<>();
        
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream no puede ser null");
        }
        
        Workbook workbook = null;
        try {
            // Determinar tipo de archivo Excel y crear workbook apropiado
            if (nombreArchivo.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
                System.out.println("📊 Procesando archivo XLSX: " + nombreArchivo);
            } else if (nombreArchivo.toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(inputStream);
                System.out.println("📊 Procesando archivo XLS: " + nombreArchivo);
            } else {
                // Intentar XLSX por defecto
                try {
                    workbook = new XSSFWorkbook(inputStream);
                    System.out.println("📊 Procesando como XLSX: " + nombreArchivo);
                } catch (Exception e) {
                    workbook = new HSSFWorkbook(inputStream);
                    System.out.println("📊 Procesando como XLS: " + nombreArchivo);
                }
            }

            if (workbook.getNumberOfSheets() == 0) {
                System.out.println("⚠️ Excel sin hojas: " + nombreArchivo);
                return ventas;
            }

            // Crear registro de archivo procesado
            ArchivoProcesado archivo = archivoRepo.save(
                new ArchivoProcesado(nombreArchivo, "Excel", LocalDateTime.now())
            );

            // Procesar todas las hojas del archivo
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                System.out.println("📋 Procesando hoja: " + sheetName);
                
                List<Venta> ventasHoja = procesarHojaExcel(sheet, archivo, nombreArchivo);
                ventas.addAll(ventasHoja);
                
                System.out.println("📋 Hoja " + sheetName + " procesada: " + ventasHoja.size() + " ventas");
            }

            // Guardar ventas únicas
            int ventasGuardadas = 0;
            for (Venta venta : ventas) {
                if (!ventaRepo.existsByClienteAndProductoAndFecha(venta.getCliente(), venta.getProducto(), venta.getFecha())) {
                    ventaRepo.save(venta);
                    ventasGuardadas++;
                } else {
                    System.out.println("⚠️ Venta duplicada omitida: " + venta.getCliente().getName() + " - " + venta.getProducto().getDescription());
                }
            }
            
            System.out.println("💾 Ventas guardadas: " + ventasGuardadas + "/" + ventas.size());

        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
        
        return ventas;
    }

    private List<Venta> procesarHojaExcel(Sheet sheet, ArchivoProcesado archivo, String nombreArchivo) {
        List<Venta> ventas = new ArrayList<>();
        
        if (sheet.getPhysicalNumberOfRows() == 0) {
            System.out.println("⚠️ Hoja vacía: " + sheet.getSheetName());
            return ventas;
        }

        // Detectar formato de la hoja
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            System.out.println("⚠️ Hoja sin encabezados: " + sheet.getSheetName());
            return ventas;
        }

        // Analizar encabezados para determinar formato
        Map<String, Integer> columnMap = analizarEncabezados(headerRow);
        
        if (columnMap.isEmpty()) {
            System.out.println("⚠️ No se reconocieron columnas en: " + sheet.getSheetName());
            return ventas;
        }

        System.out.println("🔍 Columnas detectadas: " + columnMap.keySet());

        // Procesar filas de datos
        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            try {
                Venta venta = procesarFilaExcel(row, columnMap, archivo);
                if (venta != null) {
                    ventas.add(venta);
                }
            } catch (Exception e) {
                System.err.println("❌ Error procesando fila " + (rowNum + 1) + " en " + sheet.getSheetName() + ": " + e.getMessage());
            }
        }

        return ventas;
    }

    private Map<String, Integer> analizarEncabezados(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        for (Cell cell : headerRow) {
            if (cell == null) continue;
            
            String header = getCellString(cell);
            if (header == null || header.trim().isEmpty()) continue;
            
            header = header.toLowerCase().trim();
            
            // Mapear diferentes variaciones de nombres de columnas
            if (header.contains("cliente") || header.contains("customer") || header.contains("codigo")) {
                columnMap.put("customerCode", cell.getColumnIndex());
            } else if (header.contains("nombre") && header.contains("cliente")) {
                columnMap.put("customerName", cell.getColumnIndex());
            } else if (header.contains("producto") || header.contains("product") || header.contains("descripcion")) {
                columnMap.put("productDescription", cell.getColumnIndex());
            } else if (header.contains("codigo") && header.contains("producto")) {
                columnMap.put("productCode", cell.getColumnIndex());
            } else if (header.contains("cantidad") || header.contains("qty") || header.contains("quantity")) {
                columnMap.put("cantidad", cell.getColumnIndex());
            } else if (header.contains("precio") && (header.contains("unit") || header.contains("individual"))) {
                columnMap.put("precio", cell.getColumnIndex());
            } else if (header.contains("total") || header.contains("importe")) {
                columnMap.put("total", cell.getColumnIndex());
            } else if (header.contains("fecha") || header.contains("date")) {
                columnMap.put("fecha", cell.getColumnIndex());
            }
        }
        
        return columnMap;
    }

    private Venta procesarFilaExcel(Row row, Map<String, Integer> columnMap, ArchivoProcesado archivo) {
        try {
            // Extraer datos básicos
            String customerCode = extractColumnValue(row, columnMap, "customerCode");
            String customerName = extractColumnValue(row, columnMap, "customerName");
            String productCode = extractColumnValue(row, columnMap, "productCode");
            String productDescription = extractColumnValue(row, columnMap, "productDescription");
            
            // Validar datos mínimos requeridos
            if ((customerCode == null || customerCode.trim().isEmpty()) && 
                (customerName == null || customerName.trim().isEmpty())) {
                return null;
            }
            
            if (productDescription == null || productDescription.trim().isEmpty()) {
                return null;
            }

            // Extraer valores numéricos
            int cantidad = extractIntValue(row, columnMap, "cantidad", 1);
            BigDecimal precio = extractDecimalValue(row, columnMap, "precio", BigDecimal.ZERO);
            BigDecimal total = extractDecimalValue(row, columnMap, "total", BigDecimal.ZERO);
            
            // Calcular valores faltantes
            if (precio.equals(BigDecimal.ZERO) && !total.equals(BigDecimal.ZERO) && cantidad > 0) {
                precio = total.divide(new BigDecimal(cantidad), 2, BigDecimal.ROUND_HALF_UP);
            } else if (total.equals(BigDecimal.ZERO) && !precio.equals(BigDecimal.ZERO)) {
                total = precio.multiply(new BigDecimal(cantidad));
            }

            // Validar valores
            if (cantidad <= 0 || precio.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("⚠️ Fila con valores inválidos omitida - Cantidad: " + cantidad + ", Precio: " + precio);
                return null;
            }

            // Extraer fecha
            LocalDateTime fechaHora = extractDateValue(row, columnMap, "fecha");
            if (fechaHora == null) {
                fechaHora = LocalDateTime.now();
            }

            // Crear o encontrar cliente
            String finalCustomerCode = (customerCode != null && !customerCode.trim().isEmpty()) 
                ? customerCode.trim() 
                : "AUTO-" + System.currentTimeMillis();
            String finalCustomerName = (customerName != null && !customerName.trim().isEmpty()) 
                ? customerName.trim() 
                : "Cliente " + finalCustomerCode;

            Customer cliente = obtenerCliente(finalCustomerCode, finalCustomerName);

            // Crear o encontrar producto
            String finalProductCode = (productCode != null && !productCode.trim().isEmpty()) 
                ? productCode.trim() 
                : "AUTO-" + System.currentTimeMillis();

            Product producto = obtenerProducto(finalProductCode, productDescription.trim(), precio, "EXCEL");

            return new Venta(cliente, producto, cantidad, precio, total, fechaHora, archivo);

        } catch (Exception e) {
            System.err.println("❌ Error procesando fila: " + e.getMessage());
            return null;
        }
    }

    private String extractColumnValue(Row row, Map<String, Integer> columnMap, String columnKey) {
        Integer columnIndex = columnMap.get(columnKey);
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        return getCellString(cell);
    }

    private int extractIntValue(Row row, Map<String, Integer> columnMap, String columnKey, int defaultValue) {
        Integer columnIndex = columnMap.get(columnKey);
        if (columnIndex == null) return defaultValue;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return defaultValue;
        
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> Integer.parseInt(cell.getStringCellValue().trim());
                default -> defaultValue;
            };
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal extractDecimalValue(Row row, Map<String, Integer> columnMap, String columnKey, BigDecimal defaultValue) {
        Integer columnIndex = columnMap.get(columnKey);
        if (columnIndex == null) return defaultValue;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return defaultValue;
        
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> new BigDecimal(cell.getNumericCellValue());
                case STRING -> new BigDecimal(cell.getStringCellValue().trim().replace(",", ""));
                default -> defaultValue;
            };
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private LocalDateTime extractDateValue(Row row, Map<String, Integer> columnMap, String columnKey) {
        Integer columnIndex = columnMap.get(columnKey);
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue();
                    } else {
                        yield null;
                    }
                }
                case STRING -> {
                    // Intentar parsear string como fecha
                    String dateStr = cell.getStringCellValue().trim();
                    // Aquí podrías agregar lógica para parsear diferentes formatos de fecha
                    yield null; // Por ahora, retornar null para strings
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private Customer obtenerCliente(String code, String name) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        final String finalCode = code.trim();
        final String finalName = (name != null) ? name.trim() : "Cliente " + finalCode;
        
        return customerRepo.findByCustomerCode(finalCode).orElseGet(() -> {
            Customer nuevo = new Customer();
            nuevo.setCustomerCode(finalCode);
            nuevo.setName(finalName);
            Customer guardado = customerRepo.save(nuevo);
            System.out.println("➕ Cliente creado desde Excel: " + finalCode + " - " + finalName);
            return guardado;
        });
    }

    private Product obtenerProducto(String code, String desc, BigDecimal precio, String marcaNombre) {
        final String finalCode = (code == null || code.trim().isEmpty()) ? 
            "AUTO-" + System.currentTimeMillis() : code.trim();
        final String finalDesc = (desc != null) ? desc.trim() : "Producto " + finalCode;
        
        return productRepo.findById(finalCode).orElseGet(() -> {
            // También intentar buscar por descripción
            return productRepo.findByDescription(finalDesc).orElseGet(() -> {
                try {
                    Product nuevo = new Product();
                    nuevo.setCode(finalCode);
                    nuevo.setDescription(finalDesc);
                    nuevo.setPrice(precio);
                    nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    
                    // Crear o encontrar familia con marca
                    Family familia = obtenerOCrearFamilia("Excel Imports", marcaNombre);
                    nuevo.setFamily(familia);
                    
                    Product guardado = productRepo.save(nuevo);
                    System.out.println("➕ Producto creado desde Excel: " + finalCode + " - " + finalDesc);
                    return guardado;
                } catch (Exception e) {
                    System.err.println("❌ Error creando producto desde Excel: " + e.getMessage());
                    throw e;
                }
            });
        });
    }

    private Family obtenerOCrearFamilia(String nombreFamilia, String nombreMarca) {
        return familyRepo.findByName(nombreFamilia).orElseGet(() -> {
            try {
                // Crear o encontrar marca
                Mark marca = obtenerOCrearMarca(nombreMarca);
                
                Family nuevaFamilia = new Family();
                nuevaFamilia.setName(nombreFamilia);
                nuevaFamilia.setMark(marca);
                
                Family guardada = familyRepo.save(nuevaFamilia);
                System.out.println("➕ Familia creada desde Excel: " + nombreFamilia + " para marca: " + nombreMarca);
                return guardada;
            } catch (Exception e) {
                System.err.println("❌ Error creando familia desde Excel: " + e.getMessage());
                throw e;
            }
        });
    }

    private Mark obtenerOCrearMarca(String nombreMarca) {
        return markRepo.findByNameIgnoreCase(nombreMarca)
            .orElseGet(() -> {
                try {
                    // Crear o encontrar compañía
                    Company company = obtenerOCrearCompany("Importaciones Automáticas");
                    
                    Mark nuevaMarca = new Mark();
                    nuevaMarca.setName(nombreMarca);
                    nuevaMarca.setCompany(company);
                    
                    Mark guardada = markRepo.save(nuevaMarca);
                    System.out.println("➕ Marca creada desde Excel: " + nombreMarca);
                    return guardada;
                } catch (Exception e) {
                    System.err.println("❌ Error creando marca desde Excel: " + e.getMessage());
                    throw e;
                }
            });
    }

    private Company obtenerOCrearCompany(String nombreCompany) {
        return companyRepo.findByNameIgnoreCase(nombreCompany)
            .orElseGet(() -> {
                try {
                    Company nuevaCompany = new Company();
                    nuevaCompany.setName(nombreCompany);
                    
                    Company guardada = companyRepo.save(nuevaCompany);
                    System.out.println("➕ Compañía creada desde Excel: " + nombreCompany);
                    return guardada;
                } catch (Exception e) {
                    System.err.println("❌ Error creando compañía desde Excel: " + e.getMessage());
                    throw e;
                }
            });
    }

    public String importarDesdeCarpetaCRASAVentasConResumen() throws Exception {
        StringBuilder resumen = new StringBuilder();
        int excelProcesados = 0;
        int errores = 0;
        int totalVentas = 0;

        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) return "❌ Carpeta no encontrada.";

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents and (mimeType contains 'spreadsheet' or name contains '.xlsx' or name contains '.xls')")
            .setFields("files(id, name)")
            .execute();

        for (File archivo : archivos.getFiles()) {
            String nombreArchivo = archivo.getName();
            if (archivoRepo.existsByNombre(nombreArchivo)) {
                System.out.println("⚠️ Ya procesado (resumen): " + nombreArchivo);
                continue;
            }

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                List<Venta> ventas = procesarReporteVentas(inputStream, nombreArchivo);
                totalVentas += ventas.size();
                excelProcesados++;
                resumen.append("✅ ").append(nombreArchivo).append(" - ").append(ventas.size()).append(" ventas\n");
            } catch (Exception e) {
                resumen.append("❌ Error al procesar: ").append(nombreArchivo).append(" - ").append(e.getMessage()).append("\n");
                errores++;
            }
        }

        resumen.append("\n📊 RESUMEN:\n");
        resumen.append("✅ Excel procesados: ").append(excelProcesados).append("\n");
        resumen.append("❌ Con errores: ").append(errores).append("\n");
        resumen.append("📈 Total ventas importadas: ").append(totalVentas).append("\n");
        return resumen.toString();
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles().isEmpty()) {
            System.out.println("⚠️ Carpeta no encontrada: " + nombreCarpeta);
            return null;
        }
        
        return result.getFiles().get(0).getId();
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toString();
                    } else {
                        double numValue = cell.getNumericCellValue();
                        if (numValue == (long) numValue) {
                            yield String.valueOf((long) numValue);
                        } else {
                            yield String.valueOf(numValue);
                        }
                    }
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        yield cell.getStringCellValue().trim();
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            System.err.println("❌ Error leyendo celda: " + e.getMessage());
            return null;
        }
    }
}
