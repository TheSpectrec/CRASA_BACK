package com.example.BACK.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import jakarta.annotation.PostConstruct;

@Service
public class GoogleDriveService {

    private static final int TIMEOUT = 1 * 60000; // 1 minutos
    private Drive drive;

    @PostConstruct
public void initDrive() throws Exception {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

    try (InputStream serviceAccountStream = new FileInputStream("C:/Users/luisp/credentials/google-drive-service-account.json")) {
        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
            .createScoped(Collections.singletonList(DriveScopes.DRIVE));

        HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);

        this.drive = new Drive.Builder(httpTransport, GsonFactory.getDefaultInstance(), request -> {
            adapter.initialize(request);
            request.setConnectTimeout(TIMEOUT);
            request.setReadTimeout(TIMEOUT);
        })
        .setApplicationName("Mi Aplicación")
        .build();
    }
}

    public String crearCarpeta(String nombreCarpeta) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(nombreCarpeta);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = drive.files().create(fileMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }

    public String subirArchivo(String nombreArchivo, String mimeType, InputStream contenido, String carpetaId) throws IOException {
        java.io.File tempFile = java.io.File.createTempFile("upload", nombreArchivo);
        Files.copy(contenido, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        File fileMetadata = new File();
        fileMetadata.setName(nombreArchivo);
        if (carpetaId != null) {
            fileMetadata.setParents(Collections.singletonList(carpetaId));
        }

        FileContent mediaContent = new FileContent(mimeType, tempFile);
        File file = drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        tempFile.delete();
        return file.getId();
    }

    public void listarArchivos() throws IOException {
        FileList result = drive.files().list()
                .setPageSize(10)
                .setFields("files(id, name)")
                .execute();

        for (File file : result.getFiles()) {
            System.out.println("Archivo: " + file.getName() + " (ID: " + file.getId() + ")");
        }
    }

    public Drive getDrive() {
        return this.drive;
    }
}
