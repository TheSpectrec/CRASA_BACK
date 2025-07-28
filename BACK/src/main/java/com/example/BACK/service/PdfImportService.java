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
import com.example.BACK.model.Customer;
import com.example.BACK.model.Family;
import com.example.BACK.model.Mark;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.ArchivoProcesadoRepository;
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
    @Autowired private MarkRepository markRepo;
    @Autowired private Drive drive;

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

            ArchivoProcesado archivoProcesado = archivoRepo.save(
                new ArchivoProcesado(nombreArchivo, "PDF", LocalDateTime.now())
            );

            if (contenido.contains("Con Alimentos S.A. de C.V.")) {
                ventasArchivo = procesarFormatoConAlimentos(contenido, archivoProcesado);
            } else if (contenido.contains("CRASA REPRESENTACIONES")) {
                ventasArchivo = procesarFormatoCrasa(contenido, archivoProcesado);
            } else if (contenido.contains("COMERCIALIZADORA ELORO")) {
                ventasArchivo = procesarFormatoEloro(contenido, archivoProcesado);
            } else if (contenido.contains("SERVICIO COMERCIAL GARIS")) {
                ventasArchivo = procesarFormatoLacostena(contenido, archivoProcesado);
            } else {
                ventasArchivo = procesarFormatoGenerico(contenido, archivoProcesado);
            }

            System.out.println("📊 Procesando " + ventasArchivo.size() + " ventas del archivo: " + nombreArchivo);
            
            for (Venta venta : ventasArchivo) {
                try {
                    if (venta.getCliente() == null || venta.getProducto() == null) {
                        System.err.println("❌ Venta inválida - Cliente o Producto es null: " + venta);
                        continue;
                    }
                    
                    if (!ventaRepo.existsByClienteAndProductoAndFecha(venta.getCliente(), venta.getProducto(), venta.getFecha())) {
                        Venta ventaGuardada = ventaRepo.save(venta);
                        System.out.println("✅ Venta guardada: " + ventaGuardada.getId());
                    } else {
                        System.out.println("⚠️ Venta duplicada, no se guardó: " + venta.getCliente().getCustomerCode() + " - " + venta.getProducto().getProductCode());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error al guardar venta: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error al procesar PDF: " + nombreArchivo + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        return ventasArchivo;
    }

    private List<Venta> procesarFormatoGenerico(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        Customer cliente = obtenerCliente("GEN001", "Cliente Genérico");

        Matcher matcher = Pattern.compile(
            "(\\w{3,})\\s+-\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+(\\d+)\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})"
        ).matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1);
            String desc = matcher.group(2);
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));
            Product producto = obtenerProducto(code, desc, precio);
            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
        }
        return ventas;
    }

    // Los métodos procesarFormatoCrasa, Eloro, ConAlimentos, Lacostena permanecen como ya los tienes

    // === MÉTODOS AUXILIARES PARA EXTRACCIÓN DE CLIENTE ===
    private String[] extraerClienteEloro(String contenido) {
        String[] lines = contenido.split("\\r?\\n");
        String code = null, name = null;
        
        // Buscar el código de cliente en la línea que contiene "376731 376735"
        for (String line : lines) {
            if (line.contains("376731 376735")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    code = parts[1]; // Tomar el segundo número (376735)
                    break;
                }
            }
        }
        
        // Buscar el nombre de cliente que contiene "ABARROTERA FUENTES SACVTIENDA"
        for (String line : lines) {
            if (line.contains("ABARROTERA FUENTES SACVTIENDA")) {
                name = line.trim();
                break;
            }
        }
        
        if (code != null && name != null) {
            return new String[]{code, name};
        }
        return new String[]{null, null};
    }
    private String extraerCodigoClienteCrasa(String contenido) {
        Pattern p = Pattern.compile("AGENTE\\s+[^\\d]*(\\d{6})");
        Matcher m = p.matcher(contenido);
        if (m.find()) return m.group(1);
        return null;
    }

    // === CRASA: Mejorar extracción de nombre de cliente ===
    private String[] extraerClienteCrasa(String contenido) {
        String customerCode = extraer(contenido, "Cliente:\\s*(\\d+)");
        String customerName = null;
        if (customerCode != null) {
            // Buscar la línea que contiene el código de cliente y tomar la siguiente línea como nombre
            String[] lines = contenido.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("Cliente: " + customerCode)) {
                    // Tomar la siguiente línea no vacía como nombre
                    for (int j = i + 1; j < lines.length; j++) {
                        String trimmed = lines[j].trim();
                        if (!trimmed.isEmpty() && !trimmed.contains("RFC:") && !trimmed.contains("CARR.")) {
                            customerName = trimmed;
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return new String[]{customerCode, customerName};
    }

    private String[] extraerClienteConAlimentos(String contenido) {
        String customerCode = extraer(contenido, "CLIENTE No\\.\\s*(\\d+)");
        String customerName = extraerLineaSiguiente(contenido, "VENDIDO A");
        return new String[]{customerCode, customerName};
    }

    private List<Venta> procesarFormatoCrasa(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        String[] clienteDatos = extraerClienteCrasa(contenido);
        String customerCode = clienteDatos[0];
        String customerName = clienteDatos[1];
        System.out.println("[CRASA] customerCode extraído: " + customerCode);
        System.out.println("[CRASA] customerName extraído: " + customerName);
        if (customerCode == null || customerName == null) {
            System.err.println("❌ No se pudo extraer el cliente del PDF CRASA");
            return ventas;
        }
        Customer cliente = obtenerCliente(customerCode, customerName);
        
        // Procesar línea por línea para encontrar productos
        String[] lineas = contenido.split("\\r?\\n");
        int count = 0;
        
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            
            // Buscar líneas que empiecen con código de producto (C seguido de números)
            if (linea.matches("^[A-Z]\\d+\\s+-.*")) {
                // Extraer código de producto
                String productCode = linea.substring(0, linea.indexOf(" - "));
                
                // Construir descripción completa (puede estar en múltiples líneas)
                StringBuilder descBuilder = new StringBuilder();
                descBuilder.append(linea.substring(linea.indexOf(" - ") + 3));
                
                // Buscar la línea que contiene "0%" y los datos numéricos
                int j = i + 1;
                while (j < lineas.length && !lineas[j].contains("0%")) {
                    if (!lineas[j].trim().isEmpty() && !lineas[j].contains("Obj. Imp:")) {
                        descBuilder.append(" ").append(lineas[j].trim());
                    }
                    j++;
                }
                
                if (j < lineas.length && lineas[j].contains("0%")) {
                    String datosLinea = lineas[j];
                    
                    // Extraer precio unitario (después de "0%")
                    Pattern precioPattern = Pattern.compile("0%\\s+([\\d,]+\\.[\\d]{2})");
                    Matcher precioMatcher = precioPattern.matcher(datosLinea);
                    
                    // Extraer cantidad (después de "XBX")
                    Pattern cantidadPattern = Pattern.compile("XBX([\\d]+)");
                    Matcher cantidadMatcher = cantidadPattern.matcher(datosLinea);
                    
                    // Extraer total (último número con coma)
                    Pattern totalPattern = Pattern.compile("([\\d,]+\\.[\\d]{2})$");
                    Matcher totalMatcher = totalPattern.matcher(datosLinea);
                    
                    if (precioMatcher.find() && cantidadMatcher.find() && totalMatcher.find()) {
                        String description = descBuilder.toString().trim();
                        String precioUnitarioStr = precioMatcher.group(1).replace(",", "");
                        String cantidadStr = cantidadMatcher.group(1);
                        String totalStr = totalMatcher.group(1).replace(",", "");
                        
                        try {
                            int cantidad = Integer.parseInt(cantidadStr);
                            java.math.BigDecimal precioUnitario = new java.math.BigDecimal(precioUnitarioStr);
                            java.math.BigDecimal total = new java.math.BigDecimal(totalStr);
                            Product producto = obtenerProducto(productCode, description, precioUnitario);
                            Venta venta = new Venta();
                            venta.setCliente(cliente);
                            venta.setProducto(producto);
                            venta.setCantidad(cantidad);
                            venta.setPrecioUnitario(precioUnitario);
                            venta.setTotal(total);
                            venta.setArchivo(archivo);
                            venta.setFecha(java.time.LocalDateTime.now());
                            ventas.add(venta);
                            count++;
                            System.out.println("[CRASA] Venta extraída: productCode=" + productCode + ", description=" + description + ", cantidad=" + cantidad + ", precioUnitario=" + precioUnitario + ", total=" + total);
                        } catch (Exception e) {
                            System.err.println("❌ Error al parsear venta CRASA: " + e.getMessage());
                        }
                    }
                }
            }
        }
        if (count == 0) {
            System.out.println("? No se detectaron ventas en CRASA para este PDF");
        }
        return ventas;
    }

    // === ELORO: Ajustar extracción de ventas ===
    private List<Venta> procesarFormatoEloro(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        String[] clienteDatos = extraerClienteEloro(contenido);
        String customerCode = clienteDatos[0];
        String customerName = clienteDatos[1];
        System.out.println("[ELORO] customerCode extraído: " + customerCode);
        System.out.println("[ELORO] customerName extraído: " + customerName);
        if (customerCode == null || customerName == null) {
            System.err.println("❌ No se pudo extraer el cliente del PDF ELORO");
            return ventas;
        }
        Customer cliente = obtenerCliente(customerCode, customerName);
        // Regex ajustado para campos pegados (cantidad+cveSAT, total+productCode)
        Pattern pattern = Pattern.compile(
            "^([A-Za-zÁÉÍÓÚÑñ0-9/.,\\-\\s]+?)\\s+([0-9]+\\.[0-9]{2})([0-9]{8})\\s+([0-9]+\\.[0-9]{2})\\s+([0-9,]+\\.[0-9]{2})([0-9]{6})\\s+CA02$"
        );
        String[] lineas = contenido.split("\\r?\\n");
        int count = 0;
        for (String linea : lineas) {
            String normalizada = linea.trim().replaceAll("\\s+", " ");
            Matcher matcher = pattern.matcher(normalizada);
            if (matcher.find()) {
                String description = matcher.group(1).trim();
                String cantidadStr = matcher.group(2).replace(",", "");
                String precioUnitarioStr = matcher.group(4).replace(",", "");
                String totalStr = matcher.group(5).replace(",", "");
                String productCode = matcher.group(6);
                try {
                    int cantidad = (int) Double.parseDouble(cantidadStr);
                    java.math.BigDecimal precioUnitario = new java.math.BigDecimal(precioUnitarioStr);
                    java.math.BigDecimal total = new java.math.BigDecimal(totalStr);
                    Product producto = obtenerProducto(productCode, description, precioUnitario);
                    Venta venta = new Venta();
                    venta.setCliente(cliente);
                    venta.setProducto(producto);
                    venta.setCantidad(cantidad);
                    venta.setPrecioUnitario(precioUnitario);
                    venta.setTotal(total);
                    venta.setArchivo(archivo);
                    venta.setFecha(java.time.LocalDateTime.now()); // Asignar fecha actual
                    ventas.add(venta);
                    count++;
                    System.out.println("[ELORO] Venta extraída: productCode=" + productCode + ", description=" + description + ", cantidad=" + cantidad + ", precioUnitario=" + precioUnitario + ", total=" + total);
                } catch (Exception e) {
                    System.err.println("❌ Error al parsear venta ELORO: " + e.getMessage());
                }
            }
        }
        if (count == 0) {
            System.out.println("? No se detectaron ventas en ELORO para este PDF");
        }
        return ventas;
    }

    private List<Venta> procesarFormatoConAlimentos(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        // Extraer cliente: Amarillo (customerCode y name)
        String customerCode = extraer(contenido, "AXEL OWEN\\s+(\\d+)");
        String customerName = extraerLineaSiguiente(contenido, "VENDIDO A");
        System.out.println("[CON ALIMENTOS] customerCode extraído: " + customerCode);
        System.out.println("[CON ALIMENTOS] customerName extraído: " + customerName);
        if (customerCode == null || customerName == null) {
            System.err.println("❌ No se pudo extraer el cliente del PDF CON ALIMENTOS");
            return ventas;
        }
        Customer cliente = obtenerCliente(customerCode, customerName);
        
        // Procesar línea por línea para encontrar productos
        String[] lineas = contenido.split("\\r?\\n");
        int count = 0;
        
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            
            // Buscar líneas que empiecen con código de producto (4 dígitos seguidos de espacios)
            if (linea.matches("^\\d{4}\\s+\\d{13}\\s+\\d{8}\\s+.*")) {
                // Extraer código de producto (primeros 4 dígitos)
                String productCode = linea.substring(0, 4);
                
                // Construir descripción completa (después del tercer grupo de números hasta XBX)
                String[] partes = linea.split("\\s+");
                StringBuilder descBuilder = new StringBuilder();
                int idxXBX = -1;
                
                // Encontrar el índice de XBX en la línea actual
                for (int j = 0; j < partes.length; j++) {
                    if (partes[j].equals("XBX")) {
                        idxXBX = j;
                        break;
                    }
                }
                
                // Si no encontramos XBX en esta línea, buscar en las siguientes líneas
                if (idxXBX == -1) {
                    // Agregar la descripción de la línea actual (después del tercer grupo de números)
                    for (int k = 3; k < partes.length; k++) {
                        descBuilder.append(partes[k]);
                        if (k < partes.length - 1) descBuilder.append(" ");
                    }
                    
                    // Buscar XBX en las siguientes líneas
                    for (int j = i + 1; j < lineas.length && j < i + 3; j++) {
                        String siguienteLinea = lineas[j].trim();
                        if (siguienteLinea.contains("XBX")) {
                            String[] partesSiguiente = siguienteLinea.split("\\s+");
                            for (int k = 0; k < partesSiguiente.length; k++) {
                                if (partesSiguiente[k].equals("XBX")) {
                                    // Agregar la descripción de la línea siguiente hasta XBX
                                    for (int m = 0; m < k; m++) {
                                        descBuilder.append(" ").append(partesSiguiente[m]);
                                    }
                                    break;
                                }
                            }
                            break;
                        } else if (!siguienteLinea.isEmpty() && !siguienteLinea.matches("^\\d{4}\\s+.*")) {
                            // Si la línea siguiente no es un nuevo producto, agregarla a la descripción
                            descBuilder.append(" ").append(siguienteLinea);
                        }
                    }
                } else if (idxXBX > 2) { // Después del código EAN y CVE
                    for (int j = 3; j < idxXBX; j++) {
                        descBuilder.append(partes[j]);
                        if (j < idxXBX - 1) descBuilder.append(" ");
                    }
                }
                
                // Buscar cantidad, precio y total en la línea que contiene XBX
                String lineaConDatos = linea;
                for (int j = i; j < lineas.length && j < i + 3; j++) {
                    if (lineas[j].contains("XBX") && lineas[j].contains("CJ")) {
                        lineaConDatos = lineas[j];
                        break;
                    }
                }
                
                Pattern cantidadPattern = Pattern.compile("(\\d+)\\s+CJ");
                Pattern precioPattern = Pattern.compile("(\\d+[.,]\\d{2})\\s+(\\d+[.,]\\d{2})");
                
                Matcher cantidadMatcher = cantidadPattern.matcher(lineaConDatos);
                Matcher precioMatcher = precioPattern.matcher(lineaConDatos);
                
                if (cantidadMatcher.find() && precioMatcher.find()) {
                    String description = descBuilder.toString().trim();
                    String cantidadStr = cantidadMatcher.group(1);
                    String precioUnitarioStr = precioMatcher.group(1).replace(",", "");
                    String totalStr = precioMatcher.group(2).replace(",", "");
                    
                    try {
                        int cantidad = Integer.parseInt(cantidadStr);
                        java.math.BigDecimal precioUnitario = new java.math.BigDecimal(precioUnitarioStr);
                        java.math.BigDecimal total = new java.math.BigDecimal(totalStr);
                        Product producto = obtenerProducto(productCode, description, precioUnitario);
                        Venta venta = new Venta();
                        venta.setCliente(cliente);
                        venta.setProducto(producto);
                        venta.setCantidad(cantidad);
                        venta.setPrecioUnitario(precioUnitario);
                        venta.setTotal(total);
                        venta.setArchivo(archivo);
                        venta.setFecha(java.time.LocalDateTime.now());
                        ventas.add(venta);
                        count++;
                        System.out.println("[CON ALIMENTOS] Venta extraída: productCode=" + productCode + ", description=" + description + ", cantidad=" + cantidad + ", precioUnitario=" + precioUnitario + ", total=" + total);
                    } catch (Exception e) {
                        System.err.println("❌ Error al parsear venta CON ALIMENTOS: " + e.getMessage());
                    }
                }
            }
        }
        
        if (count == 0) {
            System.out.println("? No se detectaron ventas en CON ALIMENTOS para este PDF");
        }
        return ventas;
    }

    private List<Venta> procesarFormatoLacostena(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        // Buscar el número después de SERV COM GARIS
        String customerName = extraerLineaSiguiente(contenido, "VENDIDO A");
        String customerCode = extraer(contenido, "CLIENTE\\s+(\\d+)");
        if (customerCode == null) {
            customerCode = extraer(contenido, "SERV COM GARIS\\s+(\\d+)");
        }
        System.out.println("[LACOSTENA] customerCode extraído: " + customerCode);
        System.out.println("[LACOSTENA] customerName extraído: " + customerName);
        if (customerCode == null && customerName == null) {
            System.err.println("❌ No se pudo extraer el cliente del PDF LA COSTEÑA");
            return ventas;
        }
        Customer cliente = obtenerCliente(customerCode != null ? customerCode : customerName, customerName != null ? customerName : (customerCode != null ? customerCode : "Cliente La Costeña"));
        // Procesar líneas de productos de forma robusta
        String[] lineas = contenido.split("\\r?\\n");
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty() || !l.contains("XBX") || !l.contains("CA")) continue;
            try {
                // Split por espacios, pero la descripción puede tener espacios, así que usamos contexto
                String[] tokens = l.split("\\s+");
                // Buscar índices de los delimitadores
                int idxXBX = -1, idxCA = -1;
                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i].equals("XBX")) idxXBX = i;
                    if (tokens[i].equals("CA") && idxCA == -1 && idxXBX != -1 && i > idxXBX) idxCA = i;
                }
                if (idxXBX == -1 || idxCA == -1 || idxCA + 1 >= tokens.length) continue;
                // productCode: primer token
                String productCode = tokens[0];
                // description: desde token[3] hasta idxXBX-1
                StringBuilder descBuilder = new StringBuilder();
                for (int i = 3; i < idxXBX; i++) {
                    descBuilder.append(tokens[i]);
                    if (i < idxXBX - 1) descBuilder.append(" ");
                }
                String description = descBuilder.toString();
                // cantidad: el token antes de CA
                int cantidad = Integer.parseInt(tokens[idxCA - 1]);
                // precio unitario: el token después de CA
                String precioUnitarioStr = tokens[idxCA + 1].replace(",", "");
                java.math.BigDecimal precioUnitario = new java.math.BigDecimal(precioUnitarioStr);
                // total: buscar el último número decimal de la línea
                String totalStr = null;
                for (int i = tokens.length - 1; i >= 0; i--) {
                    if (tokens[i].matches("[\\d$,.]+") && tokens[i].contains(".")) {
                        totalStr = tokens[i].replace(",", "").replace("$", "");
                        break;
                    }
                }
                if (totalStr == null) continue;
                java.math.BigDecimal total = new java.math.BigDecimal(totalStr);
                Product producto = obtenerProducto(productCode, description, precioUnitario);
                Venta venta = new Venta();
                venta.setCliente(cliente);
                venta.setProducto(producto);
                venta.setCantidad(cantidad);
                venta.setPrecioUnitario(precioUnitario);
                venta.setTotal(total);
                venta.setArchivo(archivo);
                venta.setFecha(java.time.LocalDateTime.now());
                ventas.add(venta);
                System.out.println("[LACOSTENA] Venta extraída: productCode=" + productCode + ", description=" + description + ", cantidad=" + cantidad + ", precioUnitario=" + precioUnitario + ", total=" + total);
            } catch (Exception e) {
                System.err.println("❌ Error al parsear venta LACOSTENA: " + e.getMessage());
            }
        }
        if (ventas.isEmpty()) {
            System.out.println("? No se detectaron ventas en LACOSTENA para este PDF");
        }
        return ventas;
    }

    
    private Customer obtenerCliente(String code, String name) {
        if (code == null || name == null) {
            System.err.println("❌ Datos de cliente inválidos - Code: " + code + ", Name: " + name);
            return null;
        }
        
        final String finalCode = code;
        final String finalName = name;
        
        return customerRepo.findByCustomerCode(finalCode).orElseGet(() -> {
            try {
                Customer nuevo = new Customer();
                nuevo.setCustomerCode(finalCode);
                nuevo.setName(finalName);
                Customer clienteGuardado = customerRepo.save(nuevo);
                System.out.println("✅ Cliente creado: " + clienteGuardado.getCustomerCode() + " - " + clienteGuardado.getName());
                return clienteGuardado;
            } catch (Exception e) {
                System.err.println("❌ Error al crear cliente: " + finalCode + " - " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    private Product obtenerProducto(String code, String desc, BigDecimal precio) {
        final String finalCode = (code == null || code.trim().isEmpty()) ? "AUTO-" + System.currentTimeMillis() : code;
        final String finalDesc = desc != null ? desc : "Descripción no disponible";
        final BigDecimal finalPrecio = precio != null ? precio : BigDecimal.ZERO;

        return productRepo.findByProductCode(finalCode).orElseGet(() -> {
            try {
                Product nuevo = new Product();
                nuevo.setProductCode(finalCode);
                nuevo.setDescription(finalDesc);
                nuevo.setPrice(finalPrecio);
                nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

                // Buscar o crear la marca 'General'
                Mark marcaGeneral = markRepo.findByName("General").orElseGet(() -> {
                    Mark nueva = new Mark();
                    nueva.setName("General");
                    return markRepo.save(nueva);
                });

                // Buscar o crear la familia 'General' asociada a la marca 'General'
                Family familia = familyRepo.findByName("General").orElseGet(() -> {
                    Family nuevaFamilia = new Family("General");
                    nuevaFamilia.setMark(marcaGeneral);
                    return familyRepo.save(nuevaFamilia);
                });
                nuevo.setFamily(familia);

                Product productoGuardado = productRepo.save(nuevo);
                System.out.println("✅ Producto creado: " + productoGuardado.getProductCode());
                return productoGuardado;
            } catch (Exception e) {
                System.err.println("❌ Error al crear producto: " + finalCode + " - " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    private String extraer(String texto, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extraerLineaSiguiente(String texto, String patron) {
        Pattern p = Pattern.compile(patron, Pattern.MULTILINE);
        Matcher m = p.matcher(texto);
        if (m.find()) {
            int end = m.end();
            int nextLineStart = texto.indexOf("\n", end);
            if (nextLineStart != -1) {
                String[] lines = texto.substring(nextLineStart + 1).split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) return line.trim();
                }
            }
        }
        return null;
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
