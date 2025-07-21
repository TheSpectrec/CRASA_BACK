package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
public class PdfImportService {

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
                .setQ("'" + folderId + "' in parents and mimeType contains 'pdf'")
                .setFields("files(id, name)")
                .execute();

            System.out.println("📁 Encontrados " + archivos.getFiles().size() + " archivos PDF");

            for (File archivo : archivos.getFiles()) {
                String nombreArchivo = archivo.getName();
                if (archivoRepo.existsByNombre(nombreArchivo)) {
                    System.out.println("⚠️ Ya procesado: " + nombreArchivo);
                    continue;
                }

                try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                    List<Venta> ventas = procesarFacturaPDF(inputStream, nombreArchivo);
                    resultados.add(new ImportResultDTO(nombreArchivo, "PDF", ventas));
                    System.out.println("✅ PDF procesado: " + nombreArchivo + " (" + ventas.size() + " ventas)");
                } catch (Exception e) {
                    System.err.println("❌ Error procesando PDF: " + nombreArchivo + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error general en importación PDF: " + e.getMessage());
            e.printStackTrace();
        }
        
        return resultados;
    }

    public List<Venta> procesarFacturaPDF(InputStream inputStream, String nombreArchivo) throws Exception {
        List<Venta> ventasArchivo = new ArrayList<>();
        
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream no puede ser null");
        }
        
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String contenido = stripper.getText(document);

            if (contenido == null || contenido.trim().isEmpty()) {
                System.out.println("⚠️ PDF vacío o sin contenido legible: " + nombreArchivo);
                return ventasArchivo;
            }

            // Crear registro de archivo procesado
            ArchivoProcesado archivoProcesado = archivoRepo.save(
                new ArchivoProcesado(nombreArchivo, "PDF", LocalDateTime.now())
            );

            // Detectar formato y procesar
            if (contenido.contains("CRASA REPRESENTACIONES")) {
                ventasArchivo = procesarFormatoCrasa(contenido, archivoProcesado);
                System.out.println("🔍 Formato detectado: CRASA");
            } else if (contenido.contains("COMERCIALIZADORA ELORO")) {
                ventasArchivo = procesarFormatoEloro(contenido, archivoProcesado);
                System.out.println("🔍 Formato detectado: ELORO");
            } else if (contenido.contains("Con Alimentos S.A. de C.V.")) {
                ventasArchivo = procesarFormatoConAlimentos(contenido, archivoProcesado);
                System.out.println("🔍 Formato detectado: CON ALIMENTOS");
            } else if (contenido.contains("SERVICIO COMERCIAL GARIS")) {
                ventasArchivo = procesarFormatoLacostena(contenido, archivoProcesado);
                System.out.println("🔍 Formato detectado: LACOSTENA");
            } else {
                System.out.println("⚠️ Formato no reconocido en: " + nombreArchivo);
                // Intentar extracción genérica
                ventasArchivo = procesarFormatoGenerico(contenido, archivoProcesado);
            }

            // Guardar ventas únicas
            int ventasGuardadas = 0;
            for (Venta venta : ventasArchivo) {
                if (!ventaRepo.existsByClienteAndProductoAndFecha(venta.getCliente(), venta.getProducto(), venta.getFecha())) {
                    ventaRepo.save(venta);
                    ventasGuardadas++;
                } else {
                    System.out.println("⚠️ Venta duplicada omitida: " + venta.getCliente().getName() + " - " + venta.getProducto().getDescription());
                }
            }
            
            System.out.println("💾 Ventas guardadas: " + ventasGuardadas + "/" + ventasArchivo.size());
        }
        
        return ventasArchivo;
    }

    private List<Venta> procesarFormatoCrasa(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        try {
            Customer cliente = obtenerCliente(
                extraer(contenido, "Cliente:\\s+(\\d+)"),
                extraer(contenido, "Cliente:\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ\\s]+)")
            );
            
            if (cliente == null) {
                System.out.println("⚠️ No se pudo extraer cliente de formato CRASA");
                return ventas;
            }

            // Mejorada expresión regular para capturar productos
            Pattern pattern = Pattern.compile(
                "\\d+\\s+Caj\\s+XBX\\s+(C\\d{4,}\\s+-\\s+[A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+\\d+\\s+\\d+\\.\\d{2}\\s+Kg\\s+\\d+%\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );
            
            Matcher matcher = pattern.matcher(contenido);

            while (matcher.find()) {
                try {
                    String linea = matcher.group(1).trim();
                    String[] partes = linea.split("\\s+");
                    String code = partes[0].replace("C", "");
                    String desc = linea.substring(linea.indexOf("-") + 1).trim();
                    BigDecimal precio = new BigDecimal(matcher.group(2));
                    BigDecimal total = new BigDecimal(matcher.group(3));
                    int cantidad = total.divide(precio, 2, BigDecimal.ROUND_HALF_UP).intValue();

                    if (cantidad <= 0) {
                        System.out.println("⚠️ Cantidad inválida calculada: " + cantidad);
                        continue;
                    }

                    Product producto = obtenerProducto(code, desc, precio, "CRASA");
                    ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                } catch (Exception e) {
                    System.err.println("❌ Error procesando línea CRASA: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error en procesarFormatoCrasa: " + e.getMessage());
        }
        
        return ventas;
    }

    private List<Venta> procesarFormatoEloro(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        try {
            Customer cliente = obtenerCliente(
                extraer(contenido, "RFC:\\s*([A-Z0-9]{10,13})"),
                extraer(contenido, "VENDIDO\\s+A\\n+([A-ZÁÉÍÓÚÑ\\s]+)")
            );
            
            if (cliente == null) {
                System.out.println("⚠️ No se pudo extraer cliente de formato ELORO");
                return ventas;
            }

            Pattern pattern = Pattern.compile(
                "(XBX\\d{12,})\\s+(\\d+\\.\\d{2})\\s+[\\dA-Z]+\\s+(\\d+\\.\\d{2})",
                Pattern.MULTILINE
            );
            
            Matcher matcher = pattern.matcher(contenido);

            while (matcher.find()) {
                try {
                    String desc = matcher.group(1);
                    int cantidad = (int) Double.parseDouble(matcher.group(2));
                    BigDecimal total = new BigDecimal(matcher.group(3));
                    
                    if (cantidad <= 0) continue;
                    
                    BigDecimal precio = total.divide(new BigDecimal(cantidad), 2, BigDecimal.ROUND_HALF_UP);
                    Product producto = obtenerProducto("AUTO-" + System.currentTimeMillis(), desc, precio, "ELORO");
                    ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                } catch (Exception e) {
                    System.err.println("❌ Error procesando línea ELORO: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error en procesarFormatoEloro: " + e.getMessage());
        }
        
        return ventas;
    }

    private List<Venta> procesarFormatoConAlimentos(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        try {
            Customer cliente = obtenerCliente(
                extraer(contenido, "CLIENTE\\s+(\\d+)"),
                extraer(contenido, "VENDIDO\\s+A\\n+([A-ZÁÉÍÓÚÑ\\s]+)")
            );
            
            if (cliente == null) {
                System.out.println("⚠️ No se pudo extraer cliente de formato CON ALIMENTOS");
                return ventas;
            }

            Pattern pattern = Pattern.compile(
                "(\\d{4,})\\s+\\d{12,}\\s+\\d+\\s+([A-ZÁÉÍÓÚÑ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CJ\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})",
                Pattern.MULTILINE
            );
            
            Matcher matcher = pattern.matcher(contenido);

            while (matcher.find()) {
                try {
                    String code = matcher.group(1);
                    String desc = matcher.group(2).trim();
                    int cantidad = Integer.parseInt(matcher.group(3));
                    BigDecimal precio = new BigDecimal(matcher.group(4));
                    BigDecimal total = new BigDecimal(matcher.group(5));
                    
                    if (cantidad <= 0) continue;
                    
                    Product producto = obtenerProducto(code, desc, precio, "CON ALIMENTOS");
                    ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                } catch (Exception e) {
                    System.err.println("❌ Error procesando línea CON ALIMENTOS: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error en procesarFormatoConAlimentos: " + e.getMessage());
        }
        
        return ventas;
    }

    private List<Venta> procesarFormatoLacostena(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        try {
            Customer cliente = obtenerCliente(
                extraer(contenido, "CLIENTE\\s+(\\d+)"),
                extraer(contenido, "VENDIDO\\s+A\\n+([A-ZÁÉÍÓÚ/\\s]+)")
            );
            
            if (cliente == null) {
                System.out.println("⚠️ No se pudo extraer cliente de formato LACOSTENA");
                return ventas;
            }

            Pattern pattern = Pattern.compile(
                "(\\d{3,6})\\s+\\d+\\s+\\d+\\s+([A-ZÁÉÍÓÚ/\\s0-9]+?)\\s+XBX\\s+(\\d+)\\s+CA\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})",
                Pattern.MULTILINE
            );
            
            Matcher matcher = pattern.matcher(contenido);

            while (matcher.find()) {
                try {
                    String code = matcher.group(1);
                    String desc = matcher.group(2).trim();
                    int cantidad = Integer.parseInt(matcher.group(3));
                    BigDecimal precio = new BigDecimal(matcher.group(4));
                    BigDecimal total = new BigDecimal(matcher.group(5));
                    
                    if (cantidad <= 0) continue;
                    
                    Product producto = obtenerProducto(code, desc, precio, "LACOSTENA");
                    ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                } catch (Exception e) {
                    System.err.println("❌ Error procesando línea LACOSTENA: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error en procesarFormatoLacostena: " + e.getMessage());
        }
        
        return ventas;
    }

    private List<Venta> procesarFormatoGenerico(String contenido, ArchivoProcesado archivo) {
        List<Venta> ventas = new ArrayList<>();
        
        try {
            // Intentar extraer información básica de cliente
            Customer cliente = obtenerClienteGenerico(contenido);
            if (cliente == null) {
                System.out.println("⚠️ No se pudo extraer cliente con formato genérico");
                return ventas;
            }

            // Buscar patrones comunes de productos con precios
            Pattern[] patrones = {
                Pattern.compile("(\\d+)\\s+([A-ZÁÉÍÓÚÑ\\s/]+?)\\s+(\\d+\\.\\d{2})\\s+(\\d+\\.\\d{2})", Pattern.MULTILINE),
                Pattern.compile("([A-Z0-9]+)\\s+([A-ZÁÉÍÓÚÑ\\s/]+?)\\s+\\$\\s*(\\d+\\.\\d{2})", Pattern.MULTILINE),
                Pattern.compile("(\\d+)\\s+([A-ZÁÉÍÓÚÑ\\s]+)\\s+\\$([0-9,]+\\.\\d{2})", Pattern.MULTILINE)
            };

            for (Pattern pattern : patrones) {
                Matcher matcher = pattern.matcher(contenido);
                while (matcher.find() && ventas.size() < 50) { // Límite de seguridad
                    try {
                        String code = "GEN-" + System.currentTimeMillis();
                        String desc = matcher.group(2).trim();
                        BigDecimal precio = new BigDecimal(matcher.group(3).replace(",", ""));
                        int cantidad = 1;
                        BigDecimal total = precio;

                        if (matcher.groupCount() >= 4) {
                            total = new BigDecimal(matcher.group(4).replace(",", ""));
                            cantidad = total.divide(precio, 2, BigDecimal.ROUND_HALF_UP).intValue();
                        }

                        if (cantidad > 0 && precio.compareTo(BigDecimal.ZERO) > 0) {
                            Product producto = obtenerProducto(code, desc, precio, "GENERICO");
                            ventas.add(new Venta(cliente, producto, cantidad, precio, total, LocalDateTime.now(), archivo));
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error procesando línea genérica: " + e.getMessage());
                    }
                }
                if (!ventas.isEmpty()) break; // Si encontró algo, no probar otros patrones
            }
        } catch (Exception e) {
            System.err.println("❌ Error en procesarFormatoGenerico: " + e.getMessage());
        }
        
        return ventas;
    }

    private Customer obtenerClienteGenerico(String contenido) {
        // Buscar patrones comunes de clientes
        String[] patronesCliente = {
            "CLIENTE\\s+([0-9]+)",
            "VENDIDO\\s+A[:\\s]+([A-ZÁÉÍÓÚÑ\\s]+)",
            "FACTURADO\\s+A[:\\s]+([A-ZÁÉÍÓÚÑ\\s]+)",
            "RFC[:\\s]+([A-Z0-9]{10,13})"
        };

        for (String patron : patronesCliente) {
            String codigo = extraer(contenido, patron);
            if (codigo != null) {
                return obtenerCliente(codigo, "Cliente " + codigo);
            }
        }
        
        // Cliente genérico como fallback
        return obtenerCliente("GEN-" + System.currentTimeMillis(), "Cliente Genérico");
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
            System.out.println("➕ Cliente creado: " + finalCode + " - " + finalName);
            return guardado;
        });
    }

    private Product obtenerProducto(String code, String desc, BigDecimal precio, String marcaNombre) {
        final String finalCode = (code == null || code.trim().isEmpty()) ? 
            "AUTO-" + System.currentTimeMillis() : code.trim();
        final String finalDesc = (desc != null) ? desc.trim() : "Producto " + finalCode;
        
        return productRepo.findById(finalCode).orElseGet(() -> {
            try {
                Product nuevo = new Product();
                nuevo.setCode(finalCode);
                nuevo.setDescription(finalDesc);
                nuevo.setPrice(precio);
                nuevo.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                
                // Crear o encontrar familia con marca
                Family familia = obtenerOCrearFamilia("General", marcaNombre);
                nuevo.setFamily(familia);
                
                Product guardado = productRepo.save(nuevo);
                System.out.println("➕ Producto creado: " + finalCode + " - " + finalDesc);
                return guardado;
            } catch (Exception e) {
                System.err.println("❌ Error creando producto: " + e.getMessage());
                throw e;
            }
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
                System.out.println("➕ Familia creada: " + nombreFamilia + " para marca: " + nombreMarca);
                return guardada;
            } catch (Exception e) {
                System.err.println("❌ Error creando familia: " + e.getMessage());
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
                    System.out.println("➕ Marca creada: " + nombreMarca);
                    return guardada;
                } catch (Exception e) {
                    System.err.println("❌ Error creando marca: " + e.getMessage());
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
                    System.out.println("➕ Compañía creada: " + nombreCompany);
                    return guardada;
                } catch (Exception e) {
                    System.err.println("❌ Error creando compañía: " + e.getMessage());
                    throw e;
                }
            });
    }

    private String extraer(String texto, String regex) {
        try {
            Matcher matcher = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(texto);
            return matcher.find() ? matcher.group(1).trim() : null;
        } catch (Exception e) {
            System.err.println("❌ Error en extracción regex: " + e.getMessage());
            return null;
        }
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
}
