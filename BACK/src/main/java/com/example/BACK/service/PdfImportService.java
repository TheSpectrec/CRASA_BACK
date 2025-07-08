package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BACK.model.Customer;
import com.example.BACK.model.Family;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.FamilyRepository;
import com.example.BACK.repository.ProductRepository;
import com.example.BACK.repository.VentaRepository;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
public class PdfImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;

    @Autowired private FamilyRepository familyRepo;
    @Autowired private Drive drive;

    @Transactional
    public void procesarFacturaPDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String contenido = stripper.getText(document);
            System.out.println("Contenido PDF:\n" + contenido);

            if (contenido.contains("Cliente:") && contenido.contains("CRASA REPRESENTACIONES")) {
                procesarFormatoCrasa(contenido);
            } else if (contenido.contains("COMERCIALIZADORA ELORO")) {
                procesarFormatoEloro(contenido);
            } else if (contenido.contains("Con Alimentos S.A. de C.V.")) {
                procesarFormatoConAlimentos(contenido);
            } else if (contenido.contains("SERVICIO COMERCIAL GARIS")) {
                procesarFormatoLacostena(contenido);
            } else {
                System.out.println("❌ Formato de PDF no reconocido.");
            }
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void importarDesdeCarpetaCRASAVentas() throws Exception {
        String folderName = "CRASA_VENTAS";
        String folderId = obtenerFolderIdPorNombre(folderName);

        if (folderId == null) {
            System.out.println("Carpeta 'CRASA_VENTAS' no encontrada en Drive.");
            return;
        }

        FileList archivos = drive.files().list()
            .setQ("'" + folderId + "' in parents")
            .setFields("files(id, name, mimeType)")
            .execute();

        for (File archivo : archivos.getFiles()) {
            if (archivo.getMimeType().contains("pdf")) {
                try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                    procesarFacturaPDF(inputStream);
                } catch (Exception e) {
                    System.out.println("❌ Error al procesar archivo PDF: " + archivo.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    private String obtenerFolderIdPorNombre(String nombreCarpeta) throws Exception {
        FileList result = drive.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + nombreCarpeta + "'")
            .setFields("files(id, name)")
            .execute();

        if (result.getFiles().isEmpty()) return null;
        return result.getFiles().get(0).getId();
    }

    private void guardarVentaSiNoExiste(Customer cliente, Product producto, int cantidad, BigDecimal precio, BigDecimal total, LocalDateTime fecha) {
    LocalDateTime fechaNormalizada = fecha.toLocalDate().atStartOfDay();
    boolean existe = ventaRepo.existsByClienteAndProductoAndFecha(cliente, producto, fechaNormalizada);

    if (!existe) {
        ventaRepo.saveAndFlush(new Venta(cliente, producto, cantidad, precio, total, fechaNormalizada));
    } else {
        System.out.println("⚠️ Venta duplicada detectada. No insertada: Cliente=" + cliente.getCustomerCode() + ", Producto=" + producto.getCode());
    }
}



    private void procesarFormatoCrasa(String contenido) {
        String customerCode = extraer(contenido, "Cliente:\\s+(\\d+)");
        String customerName = extraer(contenido, "Cliente:\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ\\s]+)");
        if (customerCode == null || customerName == null) return;

        Customer cliente = obtenerCliente(customerCode, customerName);

        Pattern productoPattern = Pattern.compile(
            "(C\\d{4,})\\s+-\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+0%\\s+(\\d+\\.\\d{2}).*?XBX\\d+\\s+(\\d+\\.\\d{2})",
            Pattern.DOTALL
        );
        Matcher matcher = productoPattern.matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1).replace("C", "");
            String description = matcher.group(2).trim();
            BigDecimal precio = new BigDecimal(matcher.group(3));
            BigDecimal total = new BigDecimal(matcher.group(4));
            int cantidad = total.divide(precio, 0, BigDecimal.ROUND_HALF_UP).intValue();

            Product producto = obtenerProducto(code, description, precio);
            guardarVentaSiNoExiste(cliente, producto, cantidad, precio, total, LocalDateTime.now());
        }
    }

    private void procesarFormatoEloro(String contenido) {
        String customerCode = extraer(contenido, "RFC:\\s*([A-Z0-9]{10,13})");
        String customerName = extraer(contenido, "AVENDIDO A\\n+([A-ZÁÉÍÓÚÑ\\s]+)");
        if (customerCode == null || customerName == null) return;

        Customer cliente = obtenerCliente(customerCode, customerName);

        Pattern productoPattern = Pattern.compile("(XBX\\d{12,})\\s+(\\d+\\.\\d{2})\\s+[\\dA-Z]+\\s+(\\d+\\.\\d{2})");
        Matcher matcher = productoPattern.matcher(contenido);

        while (matcher.find()) {
            String code = String.valueOf(System.currentTimeMillis());
            String description = matcher.group(1);
            int cantidad = (int) Double.parseDouble(matcher.group(2));
            BigDecimal total = new BigDecimal(matcher.group(3));
            BigDecimal precio = total.divide(new BigDecimal(cantidad), 2, BigDecimal.ROUND_HALF_UP);

            Product producto = obtenerProducto(code, description, precio);
            guardarVentaSiNoExiste(cliente, producto, cantidad, precio, total, LocalDateTime.now());
        }
    }

    private void procesarFormatoConAlimentos(String contenido) {
        String customerName = extraer(contenido, "VENDIDO A\\n+([A-ZÁÉÍÓÚÑ\\s]+)");
        String customerCode = extraer(contenido, "CLIENTE\\s+(\\d+)");
        if (customerCode == null || customerName == null) return;

        Customer cliente = obtenerCliente(customerCode, customerName);

        Pattern productoPattern = Pattern.compile("(\\d{4,})\\s+\\d{12,}\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CJ\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})", Pattern.MULTILINE);
        Matcher matcher = productoPattern.matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1);
            String description = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));

            Product producto = obtenerProducto(code, description, precio);
            guardarVentaSiNoExiste(cliente, producto, cantidad, precio, total, LocalDateTime.now());
        }
    }

    private void procesarFormatoLacostena(String contenido) {
        String customerName = extraer(contenido, "VENDIDO A\\n+([A-ZÁÉÍÓÚ/\\s]+)");
        String customerCode = extraer(contenido, "CLIENTE\\s+(\\d+)");
        if (customerCode == null || customerName == null) return;

        Customer cliente = obtenerCliente(customerCode, customerName);

        Pattern productoPattern = Pattern.compile("(\\d{3,6})\\s+\\d+\\s+\\d+\\s+([A-ZÁÉÍÓÚ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CA\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})", Pattern.MULTILINE);
        Matcher matcher = productoPattern.matcher(contenido);

        while (matcher.find()) {
            String code = matcher.group(1);
            String description = matcher.group(2).trim();
            int cantidad = Integer.parseInt(matcher.group(3));
            BigDecimal precio = new BigDecimal(matcher.group(4));
            BigDecimal total = new BigDecimal(matcher.group(5));

            Product producto = obtenerProducto(code, description, precio);
            guardarVentaSiNoExiste(cliente, producto, cantidad, precio, total, LocalDateTime.now());
        }
    }

    private Customer obtenerCliente(String code, String name) {
        return customerRepo.findByCustomerCode(code).orElseGet(() -> {
            Customer nuevo = new Customer();
            nuevo.setCustomerCode(code);
            nuevo.setName(name);
            return customerRepo.save(nuevo);
        });
    }

    private Product obtenerProducto(String code, String desc, BigDecimal precio) {
    return productRepo.findById(code).orElseGet(() -> {
        Product nuevo = new Product();
        nuevo.setCode(code);
        nuevo.setDescription(desc);
        nuevo.setPrice(precio);
        nuevo.setCreatedAt(java.sql.Timestamp.valueOf(LocalDateTime.now())); // conversión segura


        // Asignar familia por defecto
        Family familiaDefault = familyRepo.findByName("General")
            .orElseGet(() -> familyRepo.save(new Family("General")));

        nuevo.setFamily(familiaDefault);

        return productRepo.save(nuevo);
    });
}

    private String extraer(String texto, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
