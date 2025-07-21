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
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.ArchivoProcesadoRepository;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.FamilyRepository;
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

            if (contenido.contains("CRASA REPRESENTACIONES")) {
                ventasArchivo = procesarFormatoCrasa(contenido, archivoProcesado);
            } else if (contenido.contains("COMERCIALIZADORA ELORO")) {
                ventasArchivo = procesarFormatoEloro(contenido, archivoProcesado);
            } else if (contenido.contains("Con Alimentos S.A. de C.V.")) {
                ventasArchivo = procesarFormatoConAlimentos(contenido, archivoProcesado);
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
                        System.out.println("⚠️ Venta duplicada, no se guardó: " + venta.getCliente().getCustomerCode() + " - " + venta.getProducto().getCode());
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

    private List<Venta> procesarFormatoCrasa(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        Customer cliente = obtenerCliente(
            extraer(contenido, "Cliente:\\s+(\\d+)"),
            extraer(contenido, "Cliente:\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ\\s]+)")
        );
        if (cliente == null) return ventas;

        Matcher matcher = Pattern.compile(
            "\\d+\\s+Caj XBX\\s+(C\\d{4,}\\s+-\\s+[A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+\\d+\\s+\\d+\\.\\d{2}\\s+Kg\\s+\\d+%\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})",
            Pattern.MULTILINE
        ).matcher(contenido);

        while (matcher.find()) {
            String linea = matcher.group(1).trim();
            String code = safeSplit(linea, "\\s+", 0).replace("C", "");
            String desc = linea.contains("-") ? linea.substring(linea.indexOf("-") + 1).trim() : linea;
            BigDecimal precio = new BigDecimal(matcher.group(2));
            BigDecimal total = new BigDecimal(matcher.group(3));
            int cantidad = total.divide(precio, 2, BigDecimal.ROUND_HALF_UP).intValue();

            Product producto = obtenerProducto(code, desc, precio);
            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
        }
        return ventas;
    }

    private List<Venta> procesarFormatoEloro(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        Customer cliente = obtenerCliente(
            extraer(contenido, "RFC:\\s*([A-Z0-9]{10,13})"),
            extraer(contenido, "AVENDIDO A\\n+([A-ZÁÉÍÓÚÑ\\s]+)")
        );
        if (cliente == null) return ventas;

        Matcher matcher = Pattern.compile(
            "(XBX\\d{12,})\\s+(\\d+\\.\\d{2})\\s+[\\dA-Z]+\\s+(\\d+\\.\\d{2})"
        ).matcher(contenido);

        while (matcher.find()) {
            String desc = matcher.group(1);
            int cantidad = (int) Double.parseDouble(matcher.group(2));
            BigDecimal total = new BigDecimal(matcher.group(3));
            BigDecimal precio = total.divide(new BigDecimal(cantidad), 2, BigDecimal.ROUND_HALF_UP);
            Product producto = obtenerProducto("AUTO-" + System.currentTimeMillis(), desc, precio);
            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
        }
        return ventas;
    }

    private List<Venta> procesarFormatoConAlimentos(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        Customer cliente = obtenerCliente(
            extraer(contenido, "CLIENTE\\s+(\\d+)"),
            extraer(contenido, "VENDIDO A\\n+([A-ZÁÉÍÓÚÑ\\s]+)")
        );
        if (cliente == null) return ventas;

        Matcher matcher = Pattern.compile(
            "(\\d{4,})\\s+\\d{12,}\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CJ\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})"
        ).matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1);
            String desc = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));
            Product producto = obtenerProducto(code, desc, precio);
            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
        }
        return ventas;
    }

    private List<Venta> procesarFormatoLacostena(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        Customer cliente = obtenerCliente(
            extraer(contenido, "CLIENTE\\s+(\\d+)"),
            extraer(contenido, "VENDIDO A\\n+([A-ZÁÉÍÓÚ/\\s]+)")
        );
        if (cliente == null) return ventas;

        Matcher matcher = Pattern.compile(
            "(\\d{3,6})\\s+\\d+\\s+\\d+\\s+([A-ZÁÉÍÓÚ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CA\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})"
        ).matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1);
            String desc = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));
            Product producto = obtenerProducto(code, desc, precio);
            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
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
        
        return productRepo.findById(finalCode).orElseGet(() -> {
            try {
                Product nuevo = new Product();
                nuevo.setCode(finalCode);
                nuevo.setDescription(finalDesc);
                nuevo.setPrice(finalPrecio);
                nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                
                Family familia = familyRepo.findByName("General").orElseGet(() -> {
                    Family nuevaFamilia = new Family("General");
                    return familyRepo.save(nuevaFamilia);
                });
                nuevo.setFamily(familia);
                
                Product productoGuardado = productRepo.save(nuevo);
                System.out.println("✅ Producto creado: " + productoGuardado.getCode());
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
