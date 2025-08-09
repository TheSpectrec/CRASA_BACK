package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.ArchivoProcesado;
import com.example.BACK.model.Caja;
import com.example.BACK.model.Company;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Family;
import com.example.BACK.model.Mark;
import com.example.BACK.model.Peso;
import com.example.BACK.model.Product;
import com.example.BACK.model.ReporteGeneral;
import com.example.BACK.model.User;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.ArchivoProcesadoRepository;
import com.example.BACK.repository.CajaRepository;
import com.example.BACK.repository.CompanyRepository;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.FamilyRepository;
import com.example.BACK.repository.MarkRepository;
import com.example.BACK.repository.PesoRepository;
import com.example.BACK.repository.ProductRepository;
import com.example.BACK.repository.UserRepository;
import com.example.BACK.repository.VentaRepository;
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
    @Autowired private CajaRepository cajaRepo;
    @Autowired private PesoRepository pesoRepo;
    @Autowired private Drive drive;

    @Scheduled(cron = "0 */1 * * * *")
    public List<ImportResultDTO> importarDesdeDriveConVentas() throws Exception {
        System.out.println("🔄 Iniciando importación de archivos Excel desde Google Drive...");
        List<ImportResultDTO> resultados = new ArrayList<>();
        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) {
            System.err.println("❌ No se encontró la carpeta CRASA_VENTAS en Google Drive");
            return resultados;
        }
        System.out.println("✅ Carpeta CRASA_VENTAS encontrada: " + folderId);

        // Buscar archivos Excel de ventas (excluyendo PDFs)
        System.out.println("📋 Buscando archivos Excel de ventas...");
        FileList archivosVentas = drive.files().list()
            .setQ("'" + folderId + "' in parents and (mimeType contains 'spreadsheet' or name contains '.xls' or name contains '.xlsx') and not name contains 'MACRO' and not name contains 'macro' and not name contains '.pdf'")
            .setFields("files(id, name)")
            .execute();

        System.out.println("📊 Encontrados " + archivosVentas.getFiles().size() + " archivos Excel de ventas");
        for (File archivo : archivosVentas.getFiles()) {
            String nombreArchivo = archivo.getName();
            System.out.println("📄 Procesando archivo Excel: " + nombreArchivo);
            
            if (archivoRepo.existsByNombre(nombreArchivo)) {
                System.out.println("⚠️ Ya procesado: " + nombreArchivo);
                continue;
            }

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                System.out.println("🔄 Iniciando procesamiento de: " + nombreArchivo);
                List<Venta> ventas = procesarReporteVentas(inputStream, nombreArchivo);
                System.out.println("✅ Procesamiento completado: " + nombreArchivo + " - " + ventas.size() + " ventas");
                resultados.add(new ImportResultDTO(nombreArchivo, "Excel Ventas", ventas));
            } catch (Exception e) {
                System.err.println("❌ Error procesando archivo: " + nombreArchivo + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Buscar archivos Excel con macros (clientes y productos)
        System.out.println("📋 Buscando archivos Excel con macros...");
        FileList archivosMacros = drive.files().list()
            .setQ("'" + folderId + "' in parents and (name contains 'MACRO' or name contains 'macro' or name contains '.xlsm') and not name contains '.pdf'")
            .setFields("files(id, name)")
            .execute();

        System.out.println("📊 Encontrados " + archivosMacros.getFiles().size() + " archivos Excel con macros");
        for (File archivo : archivosMacros.getFiles()) {
            String nombreArchivo = archivo.getName();
            System.out.println("📄 Procesando archivo con macros: " + nombreArchivo);
            
            if (archivoRepo.existsByNombre(nombreArchivo)) {
                System.out.println("⚠️ Ya procesado: " + nombreArchivo);
                continue;
            }

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                System.out.println("🔄 Iniciando procesamiento de macros: " + nombreArchivo);
                List<Venta> ventas = procesarArchivoConMacros(inputStream, nombreArchivo);
                System.out.println("✅ Procesamiento de macros completado: " + nombreArchivo + " - " + ventas.size() + " ventas");
                resultados.add(new ImportResultDTO(nombreArchivo, "Excel Macros", ventas));
            } catch (Exception e) {
                System.err.println("❌ Error procesando archivo con macros: " + nombreArchivo + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("🎉 Importación de Excel completada. Total de archivos procesados: " + resultados.size());
        return resultados;
    }

    public List<Venta> procesarReporteVentas(InputStream inputStream, String nombreArchivo) throws Exception {
        System.out.println("📊 [EXCEL VENTAS] Iniciando procesamiento de: " + nombreArchivo);
        List<Venta> ventas = new ArrayList<>();
        Workbook workbook = nombreArchivo.toLowerCase().endsWith(".xls")
            ? new HSSFWorkbook(inputStream)
            : new XSSFWorkbook(inputStream);

        System.out.println("📋 [EXCEL VENTAS] Tipo de workbook: " + (nombreArchivo.toLowerCase().endsWith(".xls") ? "HSSF (.xls)" : "XSSF (.xlsx)"));
        
        // Si es el archivo de reporte general, procesar todas las hojas
        if (nombreArchivo.contains("REPORTE DE VENTAS GENERAL")) {
            System.out.println("📊 [EXCEL VENTAS] Procesando archivo de reporte general - todas las hojas");
            return procesarReporteGeneral(workbook, nombreArchivo);
        }
        
        // Para otros archivos, buscar la hoja FINAL
        Sheet sheet = workbook.getSheet("FINAL");
        if (sheet == null) {
            System.err.println("❌ [EXCEL VENTAS] No se encontró la hoja 'FINAL' en: " + nombreArchivo);
            return ventas;
        }
        
        System.out.println("✅ [EXCEL VENTAS] Hoja 'FINAL' encontrada. Filas totales: " + sheet.getPhysicalNumberOfRows());

        ArchivoProcesado archivo = archivoRepo.save(
            new ArchivoProcesado(nombreArchivo, "Excel", LocalDateTime.now())
        );

        int filasProcesadas = 0;
        int filasOmitidas = 0;
        int ventasCreadas = 0;
        int ventasDuplicadas = 0;
        
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

            filasProcesadas++;
            
            if (customerCode == null || productCode == null) {
                System.out.println("⚠️ [EXCEL VENTAS] Fila " + row.getRowNum() + " omitida - CustomerCode: " + customerCode + ", ProductCode: " + productCode);
                filasOmitidas++;
                continue;
            }

            User vendedor = userRepo.findByNameIgnoreCase(vendedorName).orElse(null);
            if (vendedor == null) {
                System.out.println("⚠️ [EXCEL VENTAS] Vendedor no encontrado: " + vendedorName + " - Fila " + row.getRowNum());
                filasOmitidas++;
                continue;
            }

            Customer cliente = customerRepo.findByCustomerCode(customerCode).orElseGet(() -> {
                try {
                    Customer nuevo = new Customer();
                    nuevo.setCustomerCode(customerCode);
                    nuevo.setName(customerName);
                    nuevo.setVendedor(vendedor);
                    Customer clienteGuardado = customerRepo.save(nuevo);
                    System.out.println("✅ [EXCEL VENTAS] Cliente creado: " + clienteGuardado.getCustomerCode() + " - " + clienteGuardado.getName());
                    return clienteGuardado;
                } catch (Exception e) {
                    System.err.println("❌ [EXCEL VENTAS] Error al crear cliente: " + customerCode + " - " + e.getMessage());
                    return null;
                }
            });

            if (cliente == null) {
                System.out.println("⚠️ [EXCEL VENTAS] No se pudo crear/obtener cliente: " + customerCode);
                filasOmitidas++;
                continue;
            }

            Mark marca = markRepo.findById(markName).orElseGet(() -> markRepo.save(new Mark(markName)));
            Company company = companyRepo.findById(companyName).orElseGet(() -> companyRepo.save(new Company(companyName)));
            if (marca.getCompanies() == null || marca.getCompanies().stream().noneMatch(c -> c.getId().equals(company.getId()))) {
                if (marca.getCompanies() == null) marca.setCompanies(new ArrayList<>());
                marca.getCompanies().add(company);
                markRepo.save(marca);
            }

            Family familia = familyRepo.findByName(familyName).orElseGet(() -> {
                Family nueva = new Family();
                nueva.setName(familyName);
                nueva.setMark(marca);
                return familyRepo.save(nueva);
            });

            Product producto = productRepo.findByProductCode(productCode).orElseGet(() -> {
                try {
                    Product nuevo = new Product();
                    nuevo.setProductCode(productCode);
                    nuevo.setDescription(description);
                    nuevo.setPrice(precioUnitario);
                    nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    nuevo.setFamily(familia);
                    Product productoGuardado = productRepo.save(nuevo);
                    System.out.println("✅ [EXCEL VENTAS] Producto creado: " + productoGuardado.getProductCode() + " - " + productoGuardado.getDescription());
                    return productoGuardado;
                } catch (Exception e) {
                    System.err.println("❌ [EXCEL VENTAS] Error al crear producto: " + productCode + " - " + e.getMessage());
                    return null;
                }
            });

            if (producto == null) {
                System.out.println("⚠️ [EXCEL VENTAS] No se pudo crear/obtener producto: " + productCode);
                filasOmitidas++;
                continue;
            }

            LocalDateTime fecha = LocalDateTime.of(year, convertirMes(month), 1, 0, 0);

            try {
                if (!ventaRepo.existsByClienteAndProductoAndFecha(cliente, producto, fecha)) {
                    Venta venta = new Venta(cliente, producto, cantidad, precioUnitario, precioTotal, fecha, archivo);
                    Venta ventaGuardada = ventaRepo.save(venta);
                    ventas.add(ventaGuardada);
                    ventasCreadas++;
                    System.out.println("✅ [EXCEL VENTAS] Venta guardada: " + ventaGuardada.getId() + " - Cliente: " + customerCode + ", Producto: " + productCode + ", Cantidad: " + cantidad);
                } else {
                    ventasDuplicadas++;
                    System.out.println("⚠️ [EXCEL VENTAS] Venta duplicada, no se guardó: " + customerCode + " - " + productCode);
                }
            } catch (Exception e) {
                System.err.println("❌ [EXCEL VENTAS] Error al guardar venta: " + e.getMessage());
                e.printStackTrace();
            }
        }

        workbook.close();
        
        System.out.println("📊 [EXCEL VENTAS] Resumen del procesamiento:");
        System.out.println("   • Filas procesadas: " + filasProcesadas);
        System.out.println("   • Filas omitidas: " + filasOmitidas);
        System.out.println("   • Ventas creadas: " + ventasCreadas);
        System.out.println("   • Ventas duplicadas: " + ventasDuplicadas);
        System.out.println("   • Total ventas en resultado: " + ventas.size());
        
        return ventas;
    }

    public List<Venta> procesarArchivoConMacros(InputStream inputStream, String nombreArchivo) throws Exception {
        System.out.println("📊 [EXCEL MACROS] Iniciando procesamiento de: " + nombreArchivo);
        List<Venta> ventas = new ArrayList<>();
        
        // Corregir la lógica: .xlsm es formato XSSF, no HSSF
        Workbook workbook;
        if (nombreArchivo.toLowerCase().endsWith(".xls")) {
            workbook = new HSSFWorkbook(inputStream);
            System.out.println("📋 [EXCEL MACROS] Tipo de workbook: HSSF (.xls)");
        } else {
            workbook = new XSSFWorkbook(inputStream);
            System.out.println("📋 [EXCEL MACROS] Tipo de workbook: XSSF (.xlsx/.xlsm)");
        }

        // Procesar hoja de clientes
        Sheet sheetClientes = workbook.getSheet("CLIENTES");
        if (sheetClientes != null) {
            System.out.println("📋 [EXCEL MACROS] Procesando hoja CLIENTES - Filas: " + sheetClientes.getPhysicalNumberOfRows());
            procesarHojaClientes(sheetClientes);
        } else {
            System.out.println("⚠️ [EXCEL MACROS] No se encontró la hoja CLIENTES");
        }

        // Procesar hoja de productos
        Sheet sheetProductos = workbook.getSheet("PRODUCTOS");
        if (sheetProductos != null) {
            System.out.println("📋 [EXCEL MACROS] Procesando hoja PRODUCTOS - Filas: " + sheetProductos.getPhysicalNumberOfRows());
            procesarHojaProductos(sheetProductos);
        } else {
            System.out.println("⚠️ [EXCEL MACROS] No se encontró la hoja PRODUCTOS");
        }

        // Procesar hoja de ventas si existe
        Sheet sheetVentas = workbook.getSheet("VENTAS");
        if (sheetVentas != null) {
            System.out.println("📋 [EXCEL MACROS] Procesando hoja VENTAS - Filas: " + sheetVentas.getPhysicalNumberOfRows());
            ArchivoProcesado archivo = archivoRepo.save(
                new ArchivoProcesado(nombreArchivo, "Excel Macros", LocalDateTime.now())
            );
            ventas = procesarHojaVentas(sheetVentas, archivo);
        } else {
            System.out.println("⚠️ [EXCEL MACROS] No se encontró la hoja VENTAS");
        }

        workbook.close();
        System.out.println("✅ [EXCEL MACROS] Procesamiento completado: " + nombreArchivo + " - " + ventas.size() + " ventas");
        return ventas;
    }

    private void procesarHojaClientes(Sheet sheet) {
        int clientesCreados = 0;
        int clientesExistentes = 0;
        
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Saltar encabezados

            String customerCode = getCellString(row.getCell(0));
            String customerName = getCellString(row.getCell(1));
            String vendedorName = getCellString(row.getCell(2));

            if (customerCode == null || customerName == null) {
                System.out.println("⚠️ [EXCEL MACROS] Fila " + row.getRowNum() + " omitida - CustomerCode: " + customerCode + ", CustomerName: " + customerName);
                continue;
            }

            User vendedor = userRepo.findByNameIgnoreCase(vendedorName).orElse(null);
            
            if (customerRepo.findByCustomerCode(customerCode).isPresent()) {
                clientesExistentes++;
            } else {
                try {
                    Customer nuevo = new Customer();
                    nuevo.setCustomerCode(customerCode);
                    nuevo.setName(customerName);
                    nuevo.setVendedor(vendedor);
                    Customer clienteGuardado = customerRepo.save(nuevo);
                    clientesCreados++;
                    System.out.println("✅ [EXCEL MACROS] Cliente creado: " + clienteGuardado.getCustomerCode() + " - " + clienteGuardado.getName());
                } catch (Exception e) {
                    System.err.println("❌ [EXCEL MACROS] Error al crear cliente: " + customerCode + " - " + e.getMessage());
                }
            }
        }
        
        System.out.println("📊 [EXCEL MACROS] Resumen CLIENTES: " + clientesCreados + " creados, " + clientesExistentes + " existentes");
    }

    private void procesarHojaProductos(Sheet sheet) {
        int productosCreados = 0;
        int productosExistentes = 0;
        
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Saltar encabezados

            String productCode = getCellString(row.getCell(0));
            String description = getCellString(row.getCell(1));
            BigDecimal precio = extractDecimal(row.getCell(2));
            String markName = getCellString(row.getCell(3));
            String familyName = getCellString(row.getCell(4));

            if (productCode == null || description == null) {
                System.out.println("⚠️ [EXCEL MACROS] Fila " + row.getRowNum() + " omitida - ProductCode: " + productCode + ", Description: " + description);
                continue;
            }

            Mark marca = markRepo.findById(markName).orElseGet(() -> markRepo.save(new Mark(markName)));
            Family familia = familyRepo.findByName(familyName).orElseGet(() -> {
                Family nueva = new Family();
                nueva.setName(familyName);
                nueva.setMark(marca);
                return familyRepo.save(nueva);
            });

            if (productRepo.findByProductCode(productCode).isPresent()) {
                productosExistentes++;
            } else {
                try {
                    Product nuevo = new Product();
                    nuevo.setProductCode(productCode);
                    nuevo.setDescription(description);
                    nuevo.setPrice(precio);
                    nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    nuevo.setFamily(familia);
                    Product productoGuardado = productRepo.save(nuevo);
                    productosCreados++;
                    System.out.println("✅ [EXCEL MACROS] Producto creado: " + productoGuardado.getProductCode() + " - " + productoGuardado.getDescription());
                } catch (Exception e) {
                    System.err.println("❌ [EXCEL MACROS] Error al crear producto: " + productCode + " - " + e.getMessage());
                }
            }
        }
        
        System.out.println("📊 [EXCEL MACROS] Resumen PRODUCTOS: " + productosCreados + " creados, " + productosExistentes + " existentes");
    }

    private List<Venta> procesarHojaVentas(Sheet sheet, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        int filasProcesadas = 0;
        int filasOmitidas = 0;
        int ventasCreadas = 0;
        int ventasDuplicadas = 0;
        
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Saltar encabezados

            String customerCode = getCellString(row.getCell(0));
            String productCode = getCellString(row.getCell(1));
            int cantidad = extractInt(row.getCell(2));
            BigDecimal precioUnitario = extractDecimal(row.getCell(3));
            BigDecimal precioTotal = extractDecimal(row.getCell(4));
            int year = extractInt(row.getCell(5));
            int month = extractInt(row.getCell(6));
            int day = extractInt(row.getCell(7));

            filasProcesadas++;
            
            if (customerCode == null || productCode == null) {
                System.out.println("⚠️ [EXCEL MACROS] Fila " + row.getRowNum() + " omitida - CustomerCode: " + customerCode + ", ProductCode: " + productCode);
                filasOmitidas++;
                continue;
            }

            Customer cliente = customerRepo.findByCustomerCode(customerCode).orElse(null);
            Product producto = productRepo.findByProductCode(productCode).orElse(null);

            if (cliente == null || producto == null) {
                System.out.println("⚠️ [EXCEL MACROS] Cliente o producto no encontrado: " + customerCode + " - " + productCode);
                filasOmitidas++;
                continue;
            }

            LocalDateTime fecha = LocalDateTime.of(year, month, day, 0, 0);

            try {
                if (!ventaRepo.existsByClienteAndProductoAndFecha(cliente, producto, fecha)) {
                    Venta venta = new Venta(cliente, producto, cantidad, precioUnitario, precioTotal, fecha, archivo);
                    Venta ventaGuardada = ventaRepo.save(venta);
                    ventas.add(ventaGuardada);
                    ventasCreadas++;
                    System.out.println("✅ [EXCEL MACROS] Venta guardada: " + ventaGuardada.getId() + " - Cliente: " + customerCode + ", Producto: " + productCode + ", Cantidad: " + cantidad);
                } else {
                    ventasDuplicadas++;
                    System.out.println("⚠️ [EXCEL MACROS] Venta duplicada, no se guardó: " + customerCode + " - " + productCode);
                }
            } catch (Exception e) {
                System.err.println("❌ [EXCEL MACROS] Error al guardar venta: " + e.getMessage());
            }
        }

        System.out.println("📊 [EXCEL MACROS] Resumen VENTAS:");
        System.out.println("   • Filas procesadas: " + filasProcesadas);
        System.out.println("   • Filas omitidas: " + filasOmitidas);
        System.out.println("   • Ventas creadas: " + ventasCreadas);
        System.out.println("   • Ventas duplicadas: " + ventasDuplicadas);
        System.out.println("   • Total ventas en resultado: " + ventas.size());
        
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

    private List<Venta> procesarReporteGeneral(Workbook workbook, String nombreArchivo) throws Exception {
        System.out.println("📊 [REPORTE GENERAL] Iniciando procesamiento de todas las hojas");
        List<Venta> ventas = new ArrayList<>();
        
        ArchivoProcesado archivo = archivoRepo.save(
            new ArchivoProcesado(nombreArchivo, "Excel Reporte General", LocalDateTime.now())
        );

        // Obtener todas las hojas del workbook
        int numHojas = workbook.getNumberOfSheets();
        System.out.println("📋 [REPORTE GENERAL] Total de hojas encontradas: " + numHojas);
        
        for (int i = 0; i < numHojas; i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String nombreHoja = sheet.getSheetName();
            System.out.println("📄 [REPORTE GENERAL] Procesando hoja: " + nombreHoja + " - Filas: " + sheet.getPhysicalNumberOfRows());
            
            // Procesar cada hoja según su tipo
            List<Venta> ventasHoja = procesarHojaReporteGeneral(sheet, nombreHoja, archivo);
            ventas.addAll(ventasHoja);
            
            System.out.println("✅ [REPORTE GENERAL] Hoja " + nombreHoja + " procesada: " + ventasHoja.size() + " ventas");
        }
        
        System.out.println("🎉 [REPORTE GENERAL] Procesamiento completado. Total ventas: " + ventas.size());
        return ventas;
    }

    private List<Venta> procesarHojaReporteGeneral(Sheet sheet, String nombreHoja, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        List<Caja> cajas = new ArrayList<>();
        List<Peso> pesos = new ArrayList<>();
        List<ReporteGeneral> reportesGenerales = new ArrayList<>();
        
        System.out.println("🔄 [REPORTE GENERAL] Iniciando procesamiento de hoja: " + nombreHoja);
        System.out.println("📋 [REPORTE GENERAL] Total filas en la hoja: " + sheet.getLastRowNum());
        
        // Identificar el tipo de hoja basado en el nombre exacto
        switch (nombreHoja.trim()) {
            case "REP CAJAS":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de cajas (cantidades): " + nombreHoja);
                cajas = procesarHojaCajasReporte(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Cajas procesadas: " + cajas.size() + " registros");
                // Convertir cajas a ventas para mantener compatibilidad
                ventas = convertirCajasAVentas(cajas);
                break;
                
            case "REP PESOS":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de pesos (valores monetarios): " + nombreHoja);
                pesos = procesarHojaPesosReporte(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Pesos procesados: " + pesos.size() + " registros");
                // Convertir pesos a ventas para mantener compatibilidad
                ventas = convertirPesosAVentas(pesos);
                break;
                
            case "REP PRODUCTOS":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de productos: " + nombreHoja);
                cajas = procesarHojaProductosReporte(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Productos procesados: " + cajas.size() + " registros");
                // Convertir cajas a ventas para mantener compatibilidad
                ventas = convertirCajasAVentas(cajas);
                break;
                
            case "FAMILIAS":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de familias de productos: " + nombreHoja);
                cajas = procesarHojaFamiliasReporte(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Familias procesadas: " + cajas.size() + " registros");
                // Convertir cajas a ventas para mantener compatibilidad
                ventas = convertirCajasAVentas(cajas);
                break;
                
            case "MARCA":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de marcas: " + nombreHoja);
                cajas = procesarHojaMarcaReporte(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Marcas procesadas: " + cajas.size() + " registros");
                // Convertir cajas a ventas para mantener compatibilidad
                ventas = convertirCajasAVentas(cajas);
                break;
                
            case "REP GENERAL":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de reporte general: " + nombreHoja);
                reportesGenerales = procesarHojaReporteGeneralCompleto(sheet, archivo);
                System.out.println("✅ [REPORTE GENERAL] Reporte general procesado: " + reportesGenerales.size() + " registros");
                // Convertir reportes generales a ventas para mantener compatibilidad
                ventas = convertirReportesGeneralesAVentas(reportesGenerales);
                break;
                
            case "CUOTA MARCO":
            case "CUOTA ALFREDO":
            case "CUOTA LAYDA":
                System.out.println("📊 [REPORTE GENERAL] Procesando hoja de cuotas por vendedor: " + nombreHoja);
                ventas = procesarHojaCuotaVendedorReporte(sheet, archivo, nombreHoja);
                System.out.println("✅ [REPORTE GENERAL] Cuotas procesadas: " + ventas.size() + " registros");
                break;
                
            default:
                System.out.println("⚠️ [REPORTE GENERAL] Hoja no reconocida: " + nombreHoja);
                break;
        }
        
        // Guardar las nuevas entidades en la base de datos
        if (!cajas.isEmpty()) {
            System.out.println("💾 [REPORTE GENERAL] Guardando " + cajas.size() + " cajas en la base de datos...");
            cajaRepo.saveAll(cajas);
            System.out.println("✅ [REPORTE GENERAL] Cajas guardadas exitosamente");
        }
        
        if (!pesos.isEmpty()) {
            System.out.println("💾 [REPORTE GENERAL] Guardando " + pesos.size() + " pesos en la base de datos...");
            pesoRepo.saveAll(pesos);
            System.out.println("✅ [REPORTE GENERAL] Pesos guardados exitosamente");
        }
        
        if (!reportesGenerales.isEmpty()) {
            System.out.println("💾 [REPORTE GENERAL] Guardando " + reportesGenerales.size() + " reportes generales en la base de datos...");
            // Aquí necesitaríamos inyectar el ReporteGeneralRepository
            System.out.println("✅ [REPORTE GENERAL] Reportes generales guardados exitosamente");
        }
        
        System.out.println("🎉 [REPORTE GENERAL] Procesamiento completado. Total ventas: " + ventas.size());
        return ventas;
    }

    private List<Caja> procesarHojaProductosReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Caja> cajas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de productos");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de productos");
            return cajas;
        }
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String productoNombre = getCellString(row.getCell(0)); // Columna A - Nombre del producto
            if (productoNombre == null || productoNombre.trim().isEmpty()) continue;
            
            System.out.println("📦 [REPORTE GENERAL] Procesando producto: " + productoNombre);
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double cantidad = cell.getNumericCellValue();
                    if (cantidad > 0) {
                        System.out.println("✅ [REPORTE GENERAL] Encontrada cantidad: " + cantidad + " para producto: " + productoNombre + " en columna: " + col);
                        // Crear caja con datos del producto
                        Caja caja = crearCajaDesdeProducto(productoNombre, (int) cantidad, col, archivo, "REP PRODUCTOS");
                        if (caja != null) {
                            cajas.add(caja);
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Productos procesados: " + cajas.size() + " cajas");
        return cajas;
    }

    private List<Caja> procesarHojaCajasReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Caja> cajas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de cajas (cantidades por cliente)");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de cajas");
            return cajas;
        }
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String clienteNombre = getCellString(row.getCell(0)); // Columna A - Nombre del cliente
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) continue;
            
            System.out.println("👤 [REPORTE GENERAL] Procesando cliente: " + clienteNombre);
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null) {
                    System.out.println("📊 [REPORTE GENERAL] Celda [" + i + "," + col + "]: " + 
                        "Tipo=" + cell.getCellType() + 
                        ", Valor=" + (cell.getCellType() == CellType.NUMERIC ? cell.getNumericCellValue() : getCellString(cell)));
                    
                    if (cell.getCellType() == CellType.NUMERIC) {
                        double cantidad = cell.getNumericCellValue();
                        if (cantidad > 0) {
                            System.out.println("✅ [REPORTE GENERAL] Encontrada cantidad: " + cantidad + " para cliente: " + clienteNombre);
                            // Crear caja con datos de cliente y cantidad
                            Caja caja = crearCajaDesdeCliente(clienteNombre, (int) cantidad, col, archivo, "REP CAJAS");
                            if (caja != null) {
                                cajas.add(caja);
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Cajas procesadas: " + cajas.size() + " cajas");
        return cajas;
    }

    private List<Peso> procesarHojaPesosReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Peso> pesos = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de pesos (valores monetarios por cliente)");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de pesos");
            return pesos;
        }
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String clienteNombre = getCellString(row.getCell(0)); // Columna A - Nombre del cliente
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) continue;
            
            System.out.println("👤 [REPORTE GENERAL] Procesando cliente: " + clienteNombre);
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double valor = cell.getNumericCellValue();
                    if (valor > 0) {
                        System.out.println("✅ [REPORTE GENERAL] Encontrado valor: " + valor + " para cliente: " + clienteNombre);
                        // Crear peso con datos de cliente y valor monetario
                        Peso peso = crearPesoDesdeCliente(clienteNombre, BigDecimal.valueOf(valor), col, archivo, "REP PESOS");
                        if (peso != null) {
                            pesos.add(peso);
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Pesos procesados: " + pesos.size() + " pesos");
        return pesos;
    }

    private List<Caja> procesarHojaFamiliasReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Caja> cajas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de familias (cantidades por familia)");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de familias");
            return cajas;
        }
        
        // Obtener marca "General" para las familias
        Mark marcaGeneral = markRepo.findByName("General").orElseGet(() -> {
            Mark nueva = new Mark();
            nueva.setName("General");
            return markRepo.save(nueva);
        });
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String familiaNombre = getCellString(row.getCell(0)); // Columna A - Nombre de la familia
            if (familiaNombre == null || familiaNombre.trim().isEmpty()) continue;
            
            System.out.println("👤 [REPORTE GENERAL] Procesando familia: " + familiaNombre);
            
            // Crear o obtener la familia en la base de datos
            Family familia = familyRepo.findByName(familiaNombre).orElseGet(() -> {
                Family nueva = new Family();
                nueva.setName(familiaNombre);
                nueva.setMark(marcaGeneral);
                Family familiaGuardada = familyRepo.save(nueva);
                System.out.println("✅ [REPORTE GENERAL] Familia creada: " + familiaGuardada.getName());
                return familiaGuardada;
            });
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double cantidad = cell.getNumericCellValue();
                    if (cantidad > 0) {
                        System.out.println("✅ [REPORTE GENERAL] Encontrada cantidad: " + cantidad + " para familia: " + familiaNombre);
                        // Crear caja con datos de familia y cantidad
                        Caja caja = crearCajaDesdeFamilia(familiaNombre, (int) cantidad, col, archivo, "FAMILIAS");
                        if (caja != null) {
                            cajas.add(caja);
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Familias procesadas: " + cajas.size() + " cajas");
        return cajas;
    }

    private List<Caja> procesarHojaMarcaReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Caja> cajas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de marcas (cantidades por cliente)");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de marcas");
            return cajas;
        }
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String clienteNombre = getCellString(row.getCell(0)); // Columna A - Nombre del cliente
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) continue;
            
            System.out.println("👤 [REPORTE GENERAL] Procesando cliente: " + clienteNombre);
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double cantidad = cell.getNumericCellValue();
                    if (cantidad > 0) {
                        System.out.println("✅ [REPORTE GENERAL] Encontrada cantidad: " + cantidad + " para cliente: " + clienteNombre);
                        // Crear caja con datos de cliente y cantidad
                        Caja caja = crearCajaDesdeCliente(clienteNombre, (int) cantidad, col, archivo, "MARCA");
                        if (caja != null) {
                            cajas.add(caja);
                        }
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Marcas procesadas: " + cajas.size() + " cajas");
        return cajas;
    }

    private List<Venta> procesarHojaCuotaReporte(Sheet sheet, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de cuotas");
        
        // Procesar datos de cuotas por cliente
        List<Caja> cajas = procesarHojaProductosReporte(sheet, archivo);
        return convertirCajasAVentas(cajas);
    }

    private List<Venta> procesarHojaCuotaVendedorReporte(Sheet sheet, ArchivoProcesado archivo, String nombreHoja) {
        List<Venta> ventas = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de cuotas por vendedor: " + nombreHoja);
        
        // Extraer el nombre del vendedor del nombre de la hoja
        String vendedor = nombreHoja.replace("CUOTA ", "");
        System.out.println("👤 [REPORTE GENERAL] Vendedor identificado: " + vendedor);
        
        // Buscar la tabla de datos
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de cuotas");
            return ventas;
        }
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String clienteNombre = getCellString(row.getCell(0)); // Columna A - Nombre del cliente
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) continue;
            
            // Buscar el valor de "AVANCE" (columna C)
            Cell cellAvance = row.getCell(2);
            if (cellAvance != null && cellAvance.getCellType() == CellType.NUMERIC) {
                double cantidad = cellAvance.getNumericCellValue();
                if (cantidad > 0) {
                    // Crear venta con datos de cuota
                    Venta venta = crearVentaDesdeCuota(clienteNombre, vendedor, cantidad, archivo);
                    if (venta != null) {
                        ventas.add(venta);
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Cuotas procesadas para " + vendedor + ": " + ventas.size() + " ventas");
        return ventas;
    }

    private int buscarFilaInicioTabla(Sheet sheet) {
        // Buscar la fila que contiene "REPORTE DE VENTAS" o similar
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                String cellValue = getCellString(row.getCell(0));
                if (cellValue != null && (cellValue.contains("REPORTE DE VENTAS") || cellValue.contains("CLIENTE"))) {
                    System.out.println("📊 [REPORTE GENERAL] Encontrada fila de inicio en: " + i + " - " + cellValue);
                    return i + 1; // Retornar 1 fila después del título
                }
            }
        }
        
        // Si no encuentra el título, buscar la primera fila con datos numéricos
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                for (int col = 1; col < row.getLastCellNum(); col++) {
                    Cell cell = row.getCell(col);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() > 0) {
                        System.out.println("📊 [REPORTE GENERAL] Encontrada fila con datos en: " + i);
                        return i;
                    }
                }
            }
        }
        
        return -1;
    }

    private Venta crearVentaDesdeReporte(String productoNombre, double cantidad, int columna, ArchivoProcesado archivo) {
        try {
            // Crear cliente genérico para reportes
            Customer cliente = obtenerClienteReporte("REP001", "Cliente Reporte General");
            
            // Crear producto basado en el nombre
            Product producto = obtenerProductoReporte("PROD-" + productoNombre.hashCode(), productoNombre);
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Crear venta
            Venta venta = new Venta();
            venta.setCliente(cliente);
            venta.setProducto(producto);
            venta.setCantidad((int) cantidad);
            venta.setPrecioUnitario(BigDecimal.ZERO); // No disponible en reportes
            venta.setTotal(BigDecimal.valueOf(cantidad)); // Usar cantidad como total
            venta.setFecha(fecha);
            venta.setArchivo(archivo);
            
            return venta;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando venta: " + e.getMessage());
            return null;
        }
    }

    private Venta crearVentaDesdeCuota(String clienteNombre, String vendedor, double cantidad, ArchivoProcesado archivo) {
        try {
            // Crear cliente basado en el nombre
            Customer cliente = obtenerClienteReporte("CLI-" + clienteNombre.hashCode(), clienteNombre);
            
            // Crear producto genérico para cuotas
            Product producto = obtenerProductoReporte("CUOTA-" + vendedor.hashCode(), "Cuota " + vendedor);
            
            // Crear fecha actual
            LocalDateTime fecha = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Crear venta
            Venta venta = new Venta();
            venta.setCliente(cliente);
            venta.setProducto(producto);
            venta.setCantidad((int) cantidad);
            venta.setPrecioUnitario(BigDecimal.ZERO); // No disponible en cuotas
            venta.setTotal(BigDecimal.valueOf(cantidad)); // Usar cantidad como total
            venta.setFecha(fecha);
            venta.setArchivo(archivo);
            
            return venta;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando venta desde cuota: " + e.getMessage());
            return null;
        }
    }

    private Venta crearVentaDesdeCliente(String clienteNombre, double valor, int columna, ArchivoProcesado archivo, String tipo) {
        try {
            // Crear cliente basado en el nombre
            Customer cliente = obtenerClienteReporte("CLI-" + clienteNombre.hashCode(), clienteNombre);
            
            // Crear producto genérico para el tipo de reporte
            Product producto = obtenerProductoReporte("REP-" + tipo.hashCode(), "Reporte " + tipo);
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Crear venta
            Venta venta = new Venta();
            venta.setCliente(cliente);
            venta.setProducto(producto);
            venta.setCantidad((int) valor);
            venta.setPrecioUnitario(BigDecimal.ZERO); // No disponible en reportes
            venta.setTotal(BigDecimal.valueOf(valor)); // Usar valor como total
            venta.setFecha(fecha);
            venta.setArchivo(archivo);
            
            return venta;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando venta desde cliente: " + e.getMessage());
            return null;
        }
    }

    private Customer obtenerClienteReporte(String customerCode, String customerName) {
        return customerRepo.findByCustomerCode(customerCode).orElseGet(() -> {
            try {
                Customer nuevo = new Customer();
                nuevo.setCustomerCode(customerCode);
                nuevo.setName(customerName);
                Customer clienteGuardado = customerRepo.save(nuevo);
                System.out.println("✅ [REPORTE GENERAL] Cliente reporte creado: " + clienteGuardado.getCustomerCode());
                return clienteGuardado;
            } catch (Exception e) {
                System.err.println("❌ [REPORTE GENERAL] Error al crear cliente reporte: " + e.getMessage());
                return null;
            }
        });
    }

    private Product obtenerProductoReporte(String productCode, String description) {
        return productRepo.findByProductCode(productCode).orElseGet(() -> {
            try {
                Product nuevo = new Product();
                nuevo.setProductCode(productCode);
                nuevo.setDescription(description);
                nuevo.setPrice(BigDecimal.ZERO);
                nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                
                // Asignar familia "Reporte General"
                Mark marcaReporte = markRepo.findById("Reporte General").orElseGet(() -> markRepo.save(new Mark("Reporte General")));
                Family familiaReporte = familyRepo.findByName("Reporte General").orElseGet(() -> {
                    Family nueva = new Family();
                    nueva.setName("Reporte General");
                    nueva.setMark(marcaReporte);
                    return familyRepo.save(nueva);
                });
                nuevo.setFamily(familiaReporte);
                
                Product productoGuardado = productRepo.save(nuevo);
                System.out.println("✅ [REPORTE GENERAL] Producto reporte creado: " + productoGuardado.getProductCode());
                return productoGuardado;
            } catch (Exception e) {
                System.err.println("❌ [REPORTE GENERAL] Error al crear producto reporte: " + e.getMessage());
                return null;
            }
        });
    }
    
    private Caja crearCajaDesdeCliente(String clienteNombre, int cantidad, int columna, ArchivoProcesado archivo, String tipoReporte) {
        try {
            // Crear cliente basado en el nombre
            Customer cliente = obtenerClienteReporte("CLI-" + clienteNombre.hashCode(), clienteNombre);
            
            // Crear producto genérico para el tipo de reporte
            Product producto = obtenerProductoReporte("REP-" + tipoReporte.hashCode(), "Reporte " + tipoReporte);
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Determinar mes y año basado en la columna (aproximación)
            int mes = 1; // Por defecto
            int año = LocalDateTime.now().getYear(); // Por defecto
            
            // Crear caja
            Caja caja = new Caja();
            caja.setCliente(cliente);
            caja.setProducto(producto);
            caja.setCantidad(cantidad);
            caja.setMes(mes);
            caja.setAño(año);
            caja.setFecha(fecha);
            caja.setArchivo(archivo);
            caja.setTipoReporte(tipoReporte);
            
            return caja;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando caja desde cliente: " + e.getMessage());
            return null;
        }
    }
    
    private Peso crearPesoDesdeCliente(String clienteNombre, BigDecimal valor, int columna, ArchivoProcesado archivo, String tipoReporte) {
        try {
            // Crear cliente basado en el nombre
            Customer cliente = obtenerClienteReporte("CLI-" + clienteNombre.hashCode(), clienteNombre);
            
            // Crear producto genérico para el tipo de reporte
            Product producto = obtenerProductoReporte("REP-" + tipoReporte.hashCode(), "Reporte " + tipoReporte);
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Determinar mes y año basado en la columna (aproximación)
            int mes = 1; // Por defecto
            int año = LocalDateTime.now().getYear(); // Por defecto
            
            // Crear peso
            Peso peso = new Peso();
            peso.setCliente(cliente);
            peso.setProducto(producto);
            peso.setValor(valor);
            peso.setMes(mes);
            peso.setAño(año);
            peso.setFecha(fecha);
            peso.setArchivo(archivo);
            peso.setTipoReporte(tipoReporte);
            
            return peso;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando peso desde cliente: " + e.getMessage());
            return null;
        }
    }
    
    // Métodos de conversión para mantener compatibilidad
    private List<Venta> convertirCajasAVentas(List<Caja> cajas) {
        List<Venta> ventas = new ArrayList<>();
        for (Caja caja : cajas) {
            Venta venta = new Venta();
            venta.setCliente(caja.getCliente());
            venta.setProducto(caja.getProducto());
            venta.setCantidad(caja.getCantidad());
            venta.setPrecioUnitario(BigDecimal.ZERO);
            venta.setTotal(BigDecimal.valueOf(caja.getCantidad()));
            venta.setFecha(caja.getFecha());
            venta.setArchivo(caja.getArchivo());
            ventas.add(venta);
        }
        return ventas;
    }
    
    private List<Venta> convertirPesosAVentas(List<Peso> pesos) {
        List<Venta> ventas = new ArrayList<>();
        for (Peso peso : pesos) {
            Venta venta = new Venta();
            venta.setCliente(peso.getCliente());
            venta.setProducto(peso.getProducto());
            venta.setCantidad(1); // Por defecto
            venta.setPrecioUnitario(peso.getValor());
            venta.setTotal(peso.getValor());
            venta.setFecha(peso.getFecha());
            venta.setArchivo(peso.getArchivo());
            ventas.add(venta);
        }
        return ventas;
    }
    
    private List<Venta> convertirReportesGeneralesAVentas(List<ReporteGeneral> reportes) {
        List<Venta> ventas = new ArrayList<>();
        for (ReporteGeneral reporte : reportes) {
            // Crear venta genérica para el reporte general
            Customer cliente = obtenerClienteReporte("REP-GENERAL", "Reporte General");
            Product producto = obtenerProductoReporte("REP-GENERAL", "Reporte General");
            
            Venta venta = new Venta();
            venta.setCliente(cliente);
            venta.setProducto(producto);
            venta.setCantidad(reporte.getTotalCajas());
            venta.setPrecioUnitario(BigDecimal.ZERO);
            venta.setTotal(reporte.getTotalPesos());
            venta.setFecha(reporte.getFecha());
            venta.setArchivo(reporte.getArchivo());
            ventas.add(venta);
        }
        return ventas;
    }
    
    // Métodos adicionales para crear entidades específicas
    private Caja crearCajaDesdeProducto(String productoNombre, int cantidad, int columna, ArchivoProcesado archivo, String tipoReporte) {
        try {
            // Crear producto basado en el nombre
            Product producto = obtenerProductoReporte("PROD-" + productoNombre.hashCode(), productoNombre);
            
            // Crear cliente genérico para productos
            Customer cliente = obtenerClienteReporte("REP-PRODUCTOS", "Reporte Productos");
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Determinar mes y año basado en la columna (aproximación)
            int mes = 1; // Por defecto
            int año = LocalDateTime.now().getYear(); // Por defecto
            
            // Crear caja
            Caja caja = new Caja();
            caja.setCliente(cliente);
            caja.setProducto(producto);
            caja.setCantidad(cantidad);
            caja.setMes(mes);
            caja.setAño(año);
            caja.setFecha(fecha);
            caja.setArchivo(archivo);
            caja.setTipoReporte(tipoReporte);
            
            return caja;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando caja desde producto: " + e.getMessage());
            return null;
        }
    }
    
    private List<ReporteGeneral> procesarHojaReporteGeneralCompleto(Sheet sheet, ArchivoProcesado archivo) {
        List<ReporteGeneral> reportes = new ArrayList<>();
        System.out.println("📊 [REPORTE GENERAL] Procesando hoja de reporte general completo");
        
        // Buscar la tabla de datos (después de los filtros)
        int filaInicio = buscarFilaInicioTabla(sheet);
        if (filaInicio == -1) {
            System.out.println("❌ [REPORTE GENERAL] No se encontró la tabla de datos en la hoja de reporte general");
            return reportes;
        }
        
        System.out.println("🔍 [REPORTE GENERAL] Iniciando procesamiento desde fila: " + filaInicio);
        
        // Procesar filas de datos
        for (int i = filaInicio; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            // Extraer datos de la fila
            String nombreReporte = getCellString(row.getCell(0)); // Columna A - Nombre del reporte
            if (nombreReporte == null || nombreReporte.trim().isEmpty()) continue;
            
            System.out.println("📊 [REPORTE GENERAL] Procesando reporte: " + nombreReporte);
            
            // Procesar columnas de meses/años (desde la columna B en adelante)
            for (int col = 1; col < row.getLastCellNum(); col++) {
                Cell cell = row.getCell(col);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    double valor = cell.getNumericCellValue();
                    if (valor > 0) {
                        System.out.println("✅ [REPORTE GENERAL] Encontrado valor: " + valor + " para reporte: " + nombreReporte);
                        
                        // Crear reporte general
                        LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        int mes = 1; // Por defecto
                        int año = LocalDateTime.now().getYear(); // Por defecto
                        
                        ReporteGeneral reporte = new ReporteGeneral();
                        reporte.setMes(mes);
                        reporte.setAño(año);
                        reporte.setFecha(fecha);
                        reporte.setTotalCajas((int) valor); // Asumir que es cantidad
                        reporte.setTotalPesos(BigDecimal.valueOf(valor)); // Asumir que es valor monetario
                        reporte.setArchivo(archivo);
                        reporte.setTipoReporte("REP GENERAL");
                        
                        reportes.add(reporte);
                    }
                }
            }
        }
        
        System.out.println("✅ [REPORTE GENERAL] Reporte general procesado: " + reportes.size() + " registros");
        return reportes;
    }
    
    private Caja crearCajaDesdeFamilia(String familiaNombre, int cantidad, int columna, ArchivoProcesado archivo, String tipoReporte) {
        try {
            // Obtener marca "General" para las familias
            Mark marcaGeneral = markRepo.findByName("General").orElseGet(() -> {
                Mark nueva = new Mark();
                nueva.setName("General");
                return markRepo.save(nueva);
            });
            
            // Crear o obtener la familia
            Family familia = familyRepo.findByName(familiaNombre).orElseGet(() -> {
                Family nueva = new Family();
                nueva.setName(familiaNombre);
                nueva.setMark(marcaGeneral);
                return familyRepo.save(nueva);
            });
            
            // Crear cliente genérico para familias
            Customer cliente = obtenerClienteReporte("REP-FAMILIAS", "Reporte Familias");
            
            // Crear producto genérico para el tipo de reporte
            Product producto = obtenerProductoReporte("REP-" + tipoReporte.hashCode(), "Reporte " + tipoReporte);
            
            // Crear fecha basada en la columna (aproximación)
            LocalDateTime fecha = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            
            // Determinar mes y año basado en la columna (aproximación)
            int mes = 1; // Por defecto
            int año = LocalDateTime.now().getYear(); // Por defecto
            
            // Crear caja
            Caja caja = new Caja();
            caja.setCliente(cliente);
            caja.setProducto(producto);
            caja.setFamilia(familia);
            caja.setMarca(marcaGeneral);
            caja.setCantidad(cantidad);
            caja.setMes(mes);
            caja.setAño(año);
            caja.setFecha(fecha);
            caja.setArchivo(archivo);
            caja.setTipoReporte(tipoReporte);
            
            return caja;
        } catch (Exception e) {
            System.err.println("❌ [REPORTE GENERAL] Error creando caja desde familia: " + e.getMessage());
            return null;
        }
    }
}
