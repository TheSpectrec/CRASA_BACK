package com.example.BACK.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.client.auth.oauth2.Credential;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStreamReader;
import java.util.Collections;

@Configuration
public class GoogleDriveConfig {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    @Bean
public Drive googleDrive() {
    try {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var inputStream = getClass().getResourceAsStream(CREDENTIALS_FILE_PATH);

        if (inputStream == null) {
            throw new RuntimeException("No se encontró el archivo credentials.json en /resources");
        }

        var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));

        var flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton(DriveScopes.DRIVE))
                .setAccessType("offline")
                .build();

        var credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("CRASA Drive Integration")
                .build();
    } catch (Exception e) {
        e.printStackTrace(); // muestra en consola
        throw new RuntimeException("Error al inicializar Google Drive: " + e.getMessage(), e);
    }
}

}
