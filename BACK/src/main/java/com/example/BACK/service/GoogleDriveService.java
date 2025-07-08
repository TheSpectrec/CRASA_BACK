package com.example.BACK.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

@Service
public class GoogleDriveService {

    @Autowired
    private Drive drive;

    // Crear carpeta para importar facturas o reportes
    public String crearCarpeta(String nombreCarpeta) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(nombreCarpeta);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = drive.files().create(fileMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }

    // Subir archivo PDF o Excel a Drive
    public String subirArchivo(String nombreArchivo, String mimeType, InputStream contenido, String carpetaId) throws IOException {
        // Copiar InputStream a archivo temporal
        java.io.File tempFile = java.io.File.createTempFile("upload", nombreArchivo);
        Files.copy(contenido, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        File fileMetadata = new File();
        fileMetadata.setName(nombreArchivo);
        if (carpetaId != null) {
            fileMetadata.setParents(java.util.Collections.singletonList(carpetaId));
        }

        FileContent mediaContent = new FileContent(mimeType, tempFile);

        File file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        tempFile.delete();

        return file.getId();
    }

    // Listar archivos recientes (opcional para UI)
    public void listarArchivos() throws IOException {
        FileList result = drive.files().list()
                .setPageSize(10)
                .setFields("files(id, name)")
                .execute();

        for (File file : result.getFiles()) {
            System.out.println("Archivo: " + file.getName() + " (ID: " + file.getId() + ")");
        }
    }
} 
