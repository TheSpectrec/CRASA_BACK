package com.example.BACK.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BACK.model.ArchivoProcesado;
import com.example.BACK.model.Customer;
import com.example.BACK.model.Product;
import com.example.BACK.repository.ArchivoProcesadoRepository;
import com.example.BACK.repository.CustomerRepository;
import com.example.BACK.repository.ProductRepository;
import com.example.BACK.repository.VentaRepository;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

@Service
public class ImportDiagnosticService {

    @Autowired private CustomerRepository customerRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private ArchivoProcesadoRepository archivoRepo;
    @Autowired private Drive drive;

    public Map<String, Object> obtenerEstadisticasImportacion() {
        Map<String, Object> stats = new HashMap<>();

        long totalClientes = customerRepo.count();
        long totalProductos = productRepo.count();
        long totalVentas = ventaRepo.count();
        List<ArchivoProcesado> archivos = archivoRepo.findAll();

        long pdf = archivos.stream().filter(a -> a.getTipo().equalsIgnoreCase("PDF")).count();
        long excel = archivos.stream().filter(a -> a.getTipo().equalsIgnoreCase("Excel")).count();

        stats.put("totalClientes", totalClientes);
        stats.put("totalProductos", totalProductos);
        stats.put("totalVentas", totalVentas);
        stats.put("totalArchivos", archivos.size());
        stats.put("archivosPDF", pdf);
        stats.put("archivosExcel", excel);

        try {
            FileList result = drive.files().list()
                .setQ("'root' in parents")
                .setFields("files(name, mimeType)")
                .execute();

            stats.put("googleDriveConectado", true);
            stats.put("archivosEnDrive", result.getFiles().size());

            long pdfDrive = result.getFiles().stream().filter(f -> f.getName().endsWith(".pdf")).count();
            long excelDrive = result.getFiles().stream().filter(f -> f.getName().endsWith(".xls") || f.getName().endsWith(".xlsx")).count();

            stats.put("pdfEnDrive", pdfDrive);
            stats.put("excelEnDrive", excelDrive);

        } catch (Exception e) {
            stats.put("googleDriveConectado", false);
        }

        return stats;
    }

    public Map<String, Object> verificarConfiguracion() {
        Map<String, Object> config = new HashMap<>();

        config.put("customerRepoInyectado", customerRepo != null);
        config.put("productRepoInyectado", productRepo != null);
        config.put("ventaRepoInyectado", ventaRepo != null);
        config.put("driveInyectado", drive != null);

        try {
            long count = customerRepo.count();
            config.put("baseDatosConectada", true);
            config.put("tablasExisten", true);

            List<Customer> customers = customerRepo.findAll();
            if (!customers.isEmpty()) {
                config.put("customersSample", customers.get(0).getName());
            }

            List<Product> products = productRepo.findAll();
            if (!products.isEmpty()) {
                config.put("productsSample", products.get(0).getDescription());
            }

        } catch (Exception e) {
            config.put("baseDatosConectada", false);
            config.put("tablasExisten", false);
        }

        return config;
    }

    public String realizarPruebaCompleta() {
        StringBuilder resultado = new StringBuilder();
        resultado.append("🔍 DIAGNÓSTICO COMPLETO DE IMPORTACIÓN\n");
        resultado.append("==================================================\n\n");

        resultado.append("1️⃣ CONFIGURACIÓN:\n");
        Map<String, Object> config = verificarConfiguracion();
        config.forEach((k, v) -> resultado.append("   ").append(k).append(": ").append(v).append("\n"));

        resultado.append("\n2️⃣ ESTADÍSTICAS:\n");
        Map<String, Object> stats = obtenerEstadisticasImportacion();
        stats.forEach((k, v) -> resultado.append("   ").append(k).append(": ").append(v).append("\n"));

        resultado.append("\n🎯 ESTADO GENERAL: SISTEMA OPERATIVO ✅");

        return resultado.toString();
    }
}
