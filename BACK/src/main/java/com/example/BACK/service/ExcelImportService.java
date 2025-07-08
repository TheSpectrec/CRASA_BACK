package com.example.BACK.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.BACK.model.Customer;
import com.example.BACK.model.Product;
import com.example.BACK.model.Venta;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.ProductRepository;
import com.example.BACK.repository.VentaRepository;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
public class ExcelImportService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private Drive drive;

    public void procesarReporteVentas(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String customerCode = getCellString(row.getCell(0));
            String customerName = getCellString(row.getCell(1));
            String productDescription = getCellString(row.getCell(3));
            int cantidad = (int) row.getCell(4).getNumericCellValue();
            BigDecimal precioUnitario = new BigDecimal(row.getCell(5).getNumericCellValue());
            BigDecimal total = new BigDecimal(row.getCell(6).getNumericCellValue());
            LocalDate fecha = row.getCell(7).getLocalDateTimeCellValue().toLocalDate();

            Customer cliente = customerRepo.findByCustomerCode(customerCode).orElseGet(() -> {
                Customer nuevo = new Customer();
                nuevo.setCustomerCode(customerCode);
                return nuevo;
            });
            cliente.setName(customerName);
            customerRepo.save(cliente);

            Product producto = productRepo.findByDescription(productDescription).orElseGet(() -> {
                Product nuevo = new Product();
                nuevo.setCode("SIN-CODIGO-" + System.currentTimeMillis());
                nuevo.setDescription(productDescription);
                nuevo.setPrice(precioUnitario);
                return productRepo.save(nuevo);
            });

            if (producto.getPrice().compareTo(precioUnitario) != 0) {
                producto.setPrice(precioUnitario);
                productRepo.save(producto);
            }

            Venta venta = new Venta(cliente, producto, cantidad, precioUnitario, total, fecha.atStartOfDay());
            ventaRepo.saveAndFlush(venta);
        }

        workbook.close();
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

        List<File> files = archivos.getFiles();
        System.out.println("Archivos encontrados en carpeta CRASA_VENTAS: " + files.size());

        for (File archivo : files) {
            try (InputStream inputStream = drive.files().get(archivo.getId()).executeMediaAsInputStream()) {
                if (archivo.getMimeType().contains("spreadsheet") || archivo.getName().endsWith(".xlsx")) {
                    procesarReporteVentas(inputStream);
                }
            } catch (Exception e) {
                System.out.println("❌ Error al procesar archivo Excel: " + archivo.getName());
                e.printStackTrace();
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

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }
}
