package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BACK.dto.ImportResultDTO;
import com.example.BACK.model.ArchivoProcesado;
import com.example.BACK.model.Company;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Family;
import com.example.BACK.model.Mark;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.ArchivoProcesadoRepository;
import com.example.BACK.repository.CompanyRepository;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.FamilyRepository;
import com.example.BACK.repository.MarkRepository;
import com.example.BACK.repository.ProductRepository;
import com.example.BACK.repository.VentaRepository;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
@Transactional
public class PdfImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private FamilyRepository familyRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private Drive drive;
    
    // Nuevos repositorios necesarios
    @Autowired private MarkRepository markRepo;
    @Autowired private CompanyRepository companyRepo;

    @Scheduled(cron = "0 */1 * * * *")
    public List<ImportResultDTO> importarDesdeDriveConVentas() throws Exception {
        List<ImportResultDTO> resultados = new ArrayList<>();
        String folderId = obtenerFolderIdPorNombre("CRASA_VENTAS");
        if (folderId == null) return resultados;

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents and mimeType contains 'pdf'")
            .setFields("files(id, name)")
            .execute();

        for (File archivo : archivos.getFiles()) {
            String nombreArchivo = archivo.getName();
            if (archivoRepo.existsByNombre(nombreArchivo)) continue;

            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                List<Venta> ventas = procesarFacturaPDF(inputStream, nombreArchivo);
                resultados.add(new ImportResultDTO(nombreArchivo, "PDF", ventas));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultados;
    }

    public List<Venta> procesarFacturaPDF(InputStream inputStream, String nombreArchivo) throws Exception {
        List<Venta> ventasArchivo = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String contenido = stripper.getText(document);

            System.out.println("\n" + "=".repeat(80));
            System.out.println("📄 PROCESANDO ARCHIVO: " + nombreArchivo);
            System.out.println("=".repeat(80));
            System.out.println("📝 CONTENIDO COMPLETO DEL PDF:");
            System.out.println("-".repeat(40));
            System.out.println(contenido);
            System.out.println("-".repeat(40));
            System.out.println("📏 LONGITUD DEL CONTENIDO: " + contenido.length() + " caracteres");

            ArchivoProcesado archivoProcesado = archivoRepo.save(
                new ArchivoProcesado(nombreArchivo, "PDF", LocalDateTime.now())
            );

            // Detectar formato - ORDEN ESPECÍFICO para evitar conflictos
            String formatoDetectado = "GENÉRICO";
            if (contenido.contains("Con Alimentos S.A. de C.V.")) {
                formatoDetectado = "CON ALIMENTOS S.A. DE C.V.";
                System.out.println("🎯 FORMATO DETECTADO: " + formatoDetectado);
                ventasArchivo = procesarFormatoConAlimentos(contenido, archivoProcesado);
            } else if (contenido.contains("COMERCIALIZADORA ELORO")) {
                formatoDetectado = "COMERCIALIZADORA ELORO";
                System.out.println("🎯 FORMATO DETECTADO: " + formatoDetectado);
                ventasArchivo = procesarFormatoEloro(contenido, archivoProcesado);
            } else if (contenido.contains("SERVICIO COMERCIAL GARIS") || contenido.contains("CCO820507BV4")) {
                formatoDetectado = "SERVICIO COMERCIAL GARIS (LACOSTENA)";
                System.out.println("🎯 FORMATO DETECTADO: " + formatoDetectado);
                ventasArchivo = procesarFormatoLacostena(contenido, archivoProcesado);
            } else if (contenido.contains("CRASA REPRESENTACIONES") || contenido.contains("CRE140120TI9")) {
                formatoDetectado = "CRASA REPRESENTACIONES";
                System.out.println("🎯 FORMATO DETECTADO: " + formatoDetectado);
                ventasArchivo = procesarFormatoCrasa(contenido, archivoProcesado);
            } else {
                System.out.println("🎯 FORMATO DETECTADO: " + formatoDetectado + " (no coincide con ningún patrón conocido)");
                ventasArchivo = procesarFormatoGenerico(contenido, archivoProcesado);
            }

            System.out.println("\n💾 GUARDANDO VENTAS:");
            System.out.println("📊 Procesando " + ventasArchivo.size() + " ventas del archivo: " + nombreArchivo);
            
            int ventasGuardadas = 0;
            int ventasDuplicadas = 0;
            int ventasError = 0;
            
            for (Venta venta : ventasArchivo) {
                try {
                    if (venta.getCliente() == null || venta.getProducto() == null) {
                        System.err.println("❌ Venta inválida - Cliente o Producto es null: " + venta);
                        ventasError++;
                        continue;
                    }
                    
                    System.out.println("🔍 Verificando venta: Cliente=" + venta.getCliente().getCustomerCode() + 
                                      ", Producto=" + venta.getProducto().getCode() + 
                                      ", Fecha=" + venta.getFecha());
                    
                    if (!ventaRepo.existsByClienteAndProductoAndFecha(venta.getCliente(), venta.getProducto(), venta.getFecha())) {
                        Venta ventaGuardada = ventaRepo.save(venta);
                        System.out.println("✅ Venta guardada: " + ventaGuardada.getId() + 
                                          " - Cliente: " + ventaGuardada.getCliente().getName() + 
                                          " - Producto: " + ventaGuardada.getProducto().getDescription());
                        ventasGuardadas++;
                    } else {
                        System.out.println("⚠️ Venta duplicada, no se guardó: " + venta.getCliente().getCustomerCode() + " - " + venta.getProducto().getCode());
                        ventasDuplicadas++;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error al guardar venta: " + e.getMessage());
                    e.printStackTrace();
                    ventasError++;
                }
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📈 RESUMEN FINAL - ARCHIVO: " + nombreArchivo);
            System.out.println("=".repeat(80));
            System.out.println("📊 Ventas procesadas: " + ventasArchivo.size());
            System.out.println("✅ Ventas guardadas: " + ventasGuardadas);
            System.out.println("⚠️ Ventas duplicadas: " + ventasDuplicadas);
            System.out.println("❌ Ventas con error: " + ventasError);
            System.out.println("=".repeat(80));
        } catch (Exception e) {
            System.err.println("❌ Error al procesar PDF: " + nombreArchivo + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        return ventasArchivo;
    }

    private List<Venta> procesarFormatoGenerico(String contenido, ArchivoProcesado archivo) {
        System.out.println("\n🔍 PROCESANDO FORMATO GENÉRICO:");
        List<Venta> ventas = new ArrayList<>();
        
        System.out.println("👤 USANDO CLIENTE GENÉRICO:");
        Customer cliente = obtenerCliente("GEN001", "Cliente Genérico");
        if (cliente == null) {
            System.out.println("❌ No se pudo crear cliente genérico");
            return ventas;
        }
        System.out.println("✅ Cliente genérico obtenido: " + cliente.getCustomerCode());

        Pattern pattern = Pattern.compile(
            "(\\w{3,})\\s+-\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+(\\d+)\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})"
        );
        Matcher matcher = pattern.matcher(contenido);
        
        System.out.println("🔎 BUSCANDO PRODUCTOS CON PATRÓN GENÉRICO: " + pattern.pattern());
        int matchesFound = 0;

        while (matcher.find()) {
            matchesFound++;
            System.out.println("\n📦 PRODUCTO " + matchesFound + " ENCONTRADO:");
            System.out.println("   - Match completo: " + matcher.group(0));
            System.out.println("   - Grupo 1 (código): " + matcher.group(1));
            System.out.println("   - Grupo 2 (descripción): " + matcher.group(2));
            System.out.println("   - Grupo 3 (cantidad): " + matcher.group(3));
            System.out.println("   - Grupo 4 (precio): " + matcher.group(4));
            System.out.println("   - Grupo 5 (total): " + matcher.group(5));
            
            String code = matcher.group(1);
            String desc = matcher.group(2);
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));
            
            System.out.println("   - Datos procesados: Code=" + code + ", Desc=" + desc + ", Cant=" + cantidad + ", Precio=" + precio + ", Total=" + total);
            
            Product producto = obtenerProducto(code, desc, precio);
            if (producto != null) {
                ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                System.out.println("✅ Venta creada para producto: " + producto.getCode());
            } else {
                System.out.println("❌ No se pudo crear producto");
            }
        }
        
        System.out.println("📊 RESUMEN FORMATO GENÉRICO:");
        System.out.println("   - Matches encontrados: " + matchesFound);
        System.out.println("   - Ventas creadas: " + ventas.size());
        
        return ventas;
    }

    // Los métodos procesarFormatoCrasa, Eloro, ConAlimentos, Lacostena permanecen como ya los tienes

    private List<Venta> procesarFormatoCrasa(String contenido, ArchivoProcesado archivo) {
        System.out.println("\n🔍 PROCESANDO FORMATO CRASA:");
        List<Venta> ventas = new ArrayList<>();
        
        // Extraer datos del cliente
        String clienteCode = extraer(contenido, "Cliente:\\s+(\\d+)");
        // Buscar el nombre en la línea siguiente después del RFC
        String clienteName = extraer(contenido, "Cliente:\\s+\\d+\\s+RFC:\\s+[A-Z0-9]+\\s*\\n([A-ZÁÉÍÓÚÑ\\s]+)");
        
        System.out.println("👤 EXTRACCIÓN DE CLIENTE:");
        System.out.println("   - Código extraído: " + clienteCode);
        System.out.println("   - Nombre extraído: " + clienteName);
        
        Customer cliente = obtenerCliente(clienteCode, clienteName);
        if (cliente == null) {
            System.out.println("❌ No se pudo obtener cliente, retornando ventas vacías");
            return ventas;
        }
        System.out.println("✅ Cliente obtenido: " + cliente.getCustomerCode() + " - " + cliente.getName());

        // Buscar productos - PATRÓN AJUSTADO para estructura CRASA real
        Pattern pattern = Pattern.compile(
            "(C\\d{4})\\s+-\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+0%\\s+(\\d+\\.\\d{2}).*?Caj XBX(\\d+)\\s+(\\d+,?\\d*\\.\\d{2})",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(contenido);
        
        System.out.println("🔎 BUSCANDO PRODUCTOS CON PATRÓN: " + pattern.pattern());
        int matchesFound = 0;

        while (matcher.find()) {
            matchesFound++;
            System.out.println("\n📦 PRODUCTO " + matchesFound + " ENCONTRADO:");
            System.out.println("   - Match completo: " + matcher.group(0));
            System.out.println("   - Grupo 1 (código): " + matcher.group(1));
            System.out.println("   - Grupo 2 (descripción): " + matcher.group(2));
            System.out.println("   - Grupo 3 (precio): " + matcher.group(3));
            System.out.println("   - Grupo 4 (cantidad): " + matcher.group(4));
            System.out.println("   - Grupo 5 (total): " + matcher.group(5));
            
            String code = matcher.group(1).trim(); // Mantener el código completo (ej: C8207)
            String desc = matcher.group(2).trim();
            BigDecimal precio = new BigDecimal(matcher.group(3));
            int cantidad = Integer.parseInt(matcher.group(4));
            // Limpiar el total (remover comas si las hay)
            String totalStr = matcher.group(5).replace(",", "");
            BigDecimal total = new BigDecimal(totalStr);
            
            System.out.println("   - Código procesado: " + code);
            System.out.println("   - Descripción: " + desc);
            System.out.println("   - Precio: " + precio);
            System.out.println("   - Cantidad: " + cantidad);
            System.out.println("   - Total: " + total);

            Product producto = obtenerProducto(code, desc, precio);
            if (producto != null) {
                ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                System.out.println("✅ Venta creada para producto: " + producto.getCode());
            } else {
                System.out.println("❌ No se pudo crear producto");
            }
        }
        
        System.out.println("📊 RESUMEN FORMATO CRASA:");
        System.out.println("   - Matches encontrados: " + matchesFound);
        System.out.println("   - Ventas creadas: " + ventas.size());
        
        return ventas;
    }

    private List<Venta> procesarFormatoEloro(String contenido, ArchivoProcesado archivo) {
        System.out.println("\n🔍 PROCESANDO FORMATO ELORO:");
        List<Venta> ventas = new ArrayList<>();
        
        // Extraer datos del cliente
        String clienteCode = extraer(contenido, "RFC:\\s*([A-Z0-9]{10,13})");
        String clienteName = extraer(contenido, "([A-ZÁÉÍÓÚÑ\\s]+)\\s+SACVTIENDA");
        
        System.out.println("👤 EXTRACCIÓN DE CLIENTE:");
        System.out.println("   - Código extraído: " + clienteCode);
        System.out.println("   - Nombre extraído: " + clienteName);
        
        Customer cliente = obtenerCliente(clienteCode, clienteName);
        if (cliente == null) {
            System.out.println("❌ No se pudo obtener cliente, retornando ventas vacías");
            return ventas;
        }
        System.out.println("✅ Cliente obtenido: " + cliente.getCustomerCode() + " - " + cliente.getName());

        // Buscar productos - NUEVO PATRÓN para estructura ELORO
        Pattern pattern = Pattern.compile(
            "([A-Za-z\\s\\d/]+?)\\s+XBX(\\d{10,})\\s+(\\d+\\.\\d{2})\\d+\\s+(\\d+\\.\\d{2})\\s+(\\d+,?\\d*\\.\\d{2})\\d+\\s+CA\\d+"
        );
        Matcher matcher = pattern.matcher(contenido);
        
        System.out.println("🔎 BUSCANDO PRODUCTOS CON PATRÓN ELORO: " + pattern.pattern());
        int matchesFound = 0;

        while (matcher.find()) {
            matchesFound++;
            System.out.println("\n📦 PRODUCTO " + matchesFound + " ENCONTRADO:");
            System.out.println("   - Match completo: " + matcher.group(0));
            System.out.println("   - Grupo 1 (descripción): " + matcher.group(1));
            System.out.println("   - Grupo 2 (código): " + matcher.group(2));
            System.out.println("   - Grupo 3 (cantidad): " + matcher.group(3));
            System.out.println("   - Grupo 4 (precio): " + matcher.group(4));
            System.out.println("   - Grupo 5 (total): " + matcher.group(5));
            
            String desc = matcher.group(1).trim();
            String code = matcher.group(2).trim();
            int cantidad = (int) Double.parseDouble(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            String totalStr = matcher.group(5).replace(",", "");
            BigDecimal total = new BigDecimal(totalStr);
            
            System.out.println("   - Datos procesados: Code=" + code + ", Desc=" + desc + ", Cant=" + cantidad + ", Precio=" + precio + ", Total=" + total);
            
            Product producto = obtenerProducto(code, desc, precio);
            if (producto != null) {
                ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                System.out.println("✅ Venta creada para producto: " + producto.getCode());
            } else {
                System.out.println("❌ No se pudo crear producto");
            }
        }
        
        System.out.println("📊 RESUMEN FORMATO ELORO:");
        System.out.println("   - Matches encontrados: " + matchesFound);
        System.out.println("   - Ventas creadas: " + ventas.size());
        
        return ventas;
    }

    private List<Venta> procesarFormatoConAlimentos(String contenido, ArchivoProcesado archivo) {
        System.out.println("\n🔍 PROCESANDO FORMATO CON ALIMENTOS:");
        List<Venta> ventas = new ArrayList<>();
        
        // Extraer datos del cliente
        String clienteCode = extraer(contenido, "CLIENTE\\s+(\\d+)");
        String clienteName = extraer(contenido, "VENDIDO A\\s*\\n([A-ZÁÉÍÓÚÑ\\s]+?)\\s*\\n");
        
        System.out.println("👤 EXTRACCIÓN DE CLIENTE:");
        System.out.println("   - Código extraído: " + clienteCode);
        System.out.println("   - Nombre extraído: " + clienteName);
        
        Customer cliente = obtenerCliente(clienteCode, clienteName);
        if (cliente == null) {
            System.out.println("❌ No se pudo obtener cliente, retornando ventas vacías");
            return ventas;
        }
        System.out.println("✅ Cliente obtenido: " + cliente.getCustomerCode() + " - " + cliente.getName());

        // Buscar productos - NUEVO PATRÓN para estructura CON ALIMENTOS
        Pattern pattern = Pattern.compile(
            "(\\d{4,})\\s+\\d{12,}\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CJ\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})"
        );
        Matcher matcher = pattern.matcher(contenido);
        
        System.out.println("🔎 BUSCANDO PRODUCTOS CON PATRÓN CON ALIMENTOS: " + pattern.pattern());
        int matchesFound = 0;

        while (matcher.find()) {
            matchesFound++;
            System.out.println("\n📦 PRODUCTO " + matchesFound + " ENCONTRADO:");
            System.out.println("   - Match completo: " + matcher.group(0));
            System.out.println("   - Grupo 1 (código): " + matcher.group(1));
            System.out.println("   - Grupo 2 (descripción): " + matcher.group(2));
            System.out.println("   - Grupo 3 (cantidad): " + matcher.group(3));
            System.out.println("   - Grupo 4 (precio): " + matcher.group(4));
            System.out.println("   - Grupo 5 (total): " + matcher.group(5));
            
            String code = matcher.group(1).trim();
            String desc = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));
            
            System.out.println("   - Datos procesados: Code=" + code + ", Desc=" + desc + ", Cant=" + cantidad + ", Precio=" + precio + ", Total=" + total);
            
            Product producto = obtenerProducto(code, desc, precio);
            if (producto != null) {
                ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                System.out.println("✅ Venta creada para producto: " + producto.getCode());
            } else {
                System.out.println("❌ No se pudo crear producto");
            }
        }
        
        System.out.println("📊 RESUMEN FORMATO CON ALIMENTOS:");
        System.out.println("   - Matches encontrados: " + matchesFound);
        System.out.println("   - Ventas creadas: " + ventas.size());
        
        return ventas;
    }

    private List<Venta> procesarFormatoLacostena(String contenido, ArchivoProcesado archivo) {
        System.out.println("\n🔍 PROCESANDO FORMATO LACOSTENA:");
        List<Venta> ventas = new ArrayList<>();
        
        // Extraer datos del cliente
        String clienteCode = extraer(contenido, "CLIENTE\\s+(\\d+)");
        String clienteName = extraer(contenido, "VENDIDO A\\s*\\n([A-ZÁÉÍÓÚÑ\\s]+?)\\s*\\n");
        
        System.out.println("👤 EXTRACCIÓN DE CLIENTE:");
        System.out.println("   - Código extraído: " + clienteCode);
        System.out.println("   - Nombre extraído: " + clienteName);
        
        Customer cliente = obtenerCliente(clienteCode, clienteName);
        if (cliente == null) {
            System.out.println("❌ No se pudo obtener cliente, retornando ventas vacías");
            return ventas;
        }
        System.out.println("✅ Cliente obtenido: " + cliente.getCustomerCode() + " - " + cliente.getName());

        // Buscar productos - NUEVO PATRÓN para estructura LACOSTENA
        Pattern pattern = Pattern.compile(
            "(\\d{3,6})\\s+\\d+\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ/\\s0-9\\.]+?)\\s+XBX\\s+(\\d+)\\s+CA\\s+(\\d+\\.\\d{2})\\s+(\\d+,?\\d*\\.\\d{2})"
        );
        Matcher matcher = pattern.matcher(contenido);
        
        System.out.println("🔎 BUSCANDO PRODUCTOS CON PATRÓN LACOSTENA: " + pattern.pattern());
        int matchesFound = 0;

        while (matcher.find()) {
            matchesFound++;
            System.out.println("\n📦 PRODUCTO " + matchesFound + " ENCONTRADO:");
            System.out.println("   - Match completo: " + matcher.group(0));
            System.out.println("   - Grupo 1 (código): " + matcher.group(1));
            System.out.println("   - Grupo 2 (descripción): " + matcher.group(2));
            System.out.println("   - Grupo 3 (cantidad): " + matcher.group(3));
            System.out.println("   - Grupo 4 (precio): " + matcher.group(4));
            System.out.println("   - Grupo 5 (total): " + matcher.group(5));
            
            String code = matcher.group(1).trim();
            String desc = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            String totalStr = matcher.group(5).replace(",", "");
            BigDecimal total = new BigDecimal(totalStr);
            
            System.out.println("   - Datos procesados: Code=" + code + ", Desc=" + desc + ", Cant=" + cantidad + ", Precio=" + precio + ", Total=" + total);
            
            Product producto = obtenerProducto(code, desc, precio);
            if (producto != null) {
                ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                System.out.println("✅ Venta creada para producto: " + producto.getCode());
            } else {
                System.out.println("❌ No se pudo crear producto");
            }
        }
        
        System.out.println("📊 RESUMEN FORMATO LACOSTENA:");
        System.out.println("   - Matches encontrados: " + matchesFound);
        System.out.println("   - Ventas creadas: " + ventas.size());
        
        return ventas;
    }

    
    private Customer obtenerCliente(String code, String name) {
        System.out.println("\n👤 OBTENER CLIENTE:");
        System.out.println("   - Input Code: " + code);
        System.out.println("   - Input Name: " + name);
        
        if (code == null || name == null) {
            System.err.println("❌ Datos de cliente inválidos - Code: " + code + ", Name: " + name);
            return null;
        }
        
        final String finalCode = code.trim();
        final String finalName = name.trim();
        
        System.out.println("   - Buscando cliente con código: " + finalCode);
        
        return customerRepo.findByCustomerCode(finalCode).orElseGet(() -> {
            try {
                System.out.println("   - Cliente no encontrado, creando nuevo...");
                Customer nuevo = new Customer();
                nuevo.setCustomerCode(finalCode);
                nuevo.setName(finalName);
                Customer clienteGuardado = customerRepo.save(nuevo);
                System.out.println("✅ Cliente creado exitosamente:");
                System.out.println("   - ID: " + clienteGuardado.getId());
                System.out.println("   - Code: " + clienteGuardado.getCustomerCode());
                System.out.println("   - Name: " + clienteGuardado.getName());
                return clienteGuardado;
            } catch (Exception e) {
                System.err.println("❌ Error al crear cliente: " + finalCode + " - " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    private Product obtenerProducto(String code, String desc, BigDecimal precio) {
        System.out.println("\n📦 OBTENER PRODUCTO:");
        System.out.println("   - Input Code: " + code);
        System.out.println("   - Input Desc: " + desc);
        System.out.println("   - Input Precio: " + precio);
        
        final String finalCode = (code == null || code.trim().isEmpty()) ? "AUTO-" + System.currentTimeMillis() : code.trim();
        final String finalDesc = desc != null ? desc.trim() : "Descripción no disponible";
        final BigDecimal finalPrecio = precio != null ? precio : BigDecimal.ZERO;
        
        System.out.println("   - Código final: " + finalCode);
        System.out.println("   - Buscando producto con código: " + finalCode);
        
        return productRepo.findById(finalCode).orElseGet(() -> {
            try {
                System.out.println("   - Producto no encontrado, creando nuevo...");
                Product nuevo = new Product();
                nuevo.setCode(finalCode);
                nuevo.setDescription(finalDesc);
                nuevo.setPrice(finalPrecio);
                nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                
                // Crear o obtener la jerarquía completa: Company -> Mark -> Family
                System.out.println("   - Obteniendo familia...");
                Family familia = obtenerFamiliaGeneral();
                if (familia == null) {
                    System.err.println("❌ No se pudo obtener familia para el producto: " + finalCode);
                    return null;
                }
                System.out.println("   - Familia obtenida: " + familia.getName() + " (ID: " + familia.getId() + ")");
                nuevo.setFamily(familia);
                
                System.out.println("   - Guardando producto...");
                Product productoGuardado = productRepo.save(nuevo);
                System.out.println("✅ Producto creado exitosamente:");
                System.out.println("   - Code: " + productoGuardado.getCode());
                System.out.println("   - Description: " + productoGuardado.getDescription());
                System.out.println("   - Price: " + productoGuardado.getPrice());
                System.out.println("   - Family: " + productoGuardado.getFamily().getName());
                return productoGuardado;
            } catch (Exception e) {
                System.err.println("❌ Error al crear producto: " + finalCode + " - " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }
    
    private Family obtenerFamiliaGeneral() {
        System.out.println("\n🏷️ OBTENER FAMILIA GENERAL:");
        System.out.println("   - Buscando familia 'General'...");
        
        return familyRepo.findByName("General").orElseGet(() -> {
            try {
                System.out.println("   - Familia 'General' no encontrada, creando nueva...");
                
                // Crear o obtener Mark General
                System.out.println("   - Obteniendo Mark General...");
                Mark markGeneral = obtenerMarkGeneral();
                if (markGeneral == null) {
                    System.err.println("❌ No se pudo obtener mark para Family General");
                    return null;
                }
                System.out.println("   - Mark obtenido: " + markGeneral.getName() + " (ID: " + markGeneral.getId() + ")");
                
                Family nuevaFamilia = new Family("General");
                nuevaFamilia.setMark(markGeneral);
                
                System.out.println("   - Guardando familia...");
                Family familiaGuardada = familyRepo.save(nuevaFamilia);
                System.out.println("✅ Familia creada exitosamente:");
                System.out.println("   - ID: " + familiaGuardada.getId());
                System.out.println("   - Name: " + familiaGuardada.getName());
                System.out.println("   - Mark: " + familiaGuardada.getMark().getName());
                return familiaGuardada;
            } catch (Exception e) {
                System.err.println("❌ Error al crear familia General: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }
    
    private Mark obtenerMarkGeneral() {
        System.out.println("\n🔖 OBTENER MARK GENERAL:");
        System.out.println("   - Buscando mark 'General'...");
        
        return markRepo.findByName("General").orElseGet(() -> {
            try {
                System.out.println("   - Mark 'General' no encontrado, creando nuevo...");
                
                // Crear o obtener Company General
                System.out.println("   - Obteniendo Company General...");
                Company companyGeneral = obtenerCompanyGeneral();
                if (companyGeneral == null) {
                    System.err.println("❌ No se pudo obtener company para Mark General");
                    return null;
                }
                System.out.println("   - Company obtenida: " + companyGeneral.getName() + " (ID: " + companyGeneral.getId() + ")");
                
                Mark nuevoMark = new Mark("General");
                nuevoMark.setCompany(companyGeneral);
                
                System.out.println("   - Guardando mark...");
                Mark markGuardado = markRepo.save(nuevoMark);
                System.out.println("✅ Mark creado exitosamente:");
                System.out.println("   - ID: " + markGuardado.getId());
                System.out.println("   - Name: " + markGuardado.getName());
                System.out.println("   - Company: " + markGuardado.getCompany().getName());
                return markGuardado;
            } catch (Exception e) {
                System.err.println("❌ Error al crear mark General: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }
    
    private Company obtenerCompanyGeneral() {
        System.out.println("\n🏢 OBTENER COMPANY GENERAL:");
        System.out.println("   - Buscando company 'General'...");
        
        return companyRepo.findByName("General").orElseGet(() -> {
            try {
                System.out.println("   - Company 'General' no encontrada, creando nueva...");
                
                Company nuevaCompany = new Company("General");
                
                System.out.println("   - Guardando company...");
                Company companyGuardada = companyRepo.save(nuevaCompany);
                System.out.println("✅ Company creada exitosamente:");
                System.out.println("   - ID: " + companyGuardada.getId());
                System.out.println("   - Name: " + companyGuardada.getName());
                return companyGuardada;
            } catch (Exception e) {
                System.err.println("❌ Error al crear company General: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    private String extraer(String texto, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();
        if (result.getFiles().isEmpty()) return null;
        return result.getFiles().get(0).getId();
    }

    private String safeSplit(String line, String delimiter, int index) {
        if (line != null && line.matches(".*" + delimiter + ".*")) {
            String[] parts = line.split(delimiter);
            if (parts.length > index) {
                return parts[index].trim();
            }
        }
        return "";
    }
}
