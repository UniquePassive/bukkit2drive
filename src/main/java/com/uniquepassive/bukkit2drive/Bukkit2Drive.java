package com.uniquepassive.bukkit2drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Bukkit2Drive extends JavaPlugin {

    private static final String APPLICATION_NAME = "Bukkit2Drive";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

    private static NetHttpTransport httpTransport;
    private static FileDataStoreFactory dataStoreFactory;
    private Drive driveService;

    private Timer timer = new Timer();

    @Override
    public void onEnable() {
        try {
            if (httpTransport == null) {
                httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                dataStoreFactory = new FileDataStoreFactory(new java.io.File("driveService"));
            }

            Credential credential = authorize();

            driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(
                    APPLICATION_NAME).build();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "Launching backup of the worlds!");

                    try {
                        File rootFolder = getRootFolder();
                        File backupFolder = createBackupFolder(rootFolder);

                        zipAndUpload(backupFolder, "world.zip", Paths.get("world"));
                        zipAndUpload(backupFolder, "world_nether.zip", Paths.get("world_nether"));
                        zipAndUpload(backupFolder, "world_the_end.zip", Paths.get("world_the_end"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            };

            timer.schedule(task, 0, 3 * 60 * 60 * 1000);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private File getRootFolder() throws IOException {
        File folderMetadata = new File();
        folderMetadata.setName("Bukkit2Drive");
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        return driveService
                .files()
                .create(folderMetadata)
                .setFields("id")
                .execute();
    }

    private File createBackupFolder(File rootFolder) throws IOException {
        File folderMetadata = new File();
        folderMetadata.setName(dateFormat.format(new Date()));
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(rootFolder.getId()));

        return driveService
                .files()
                .create(folderMetadata)
                .setFields("id, parents")
                .execute();
    }

    private void zipAndUpload(File backupFolder, String zipName, Path path) throws IOException {
        java.io.File outFile = new java.io.File("temp.zip");

        Zipping.zipDir(path, outFile);

        File fileMetadata = new File();
        fileMetadata.setName(zipName);
        fileMetadata.setParents(Collections.singletonList(backupFolder.getId()));
        FileContent mediaContent = new FileContent("application/zip", outFile);

        driveService
                .files()
                .create(fileMetadata, mediaContent)
                .setFields("id, parents")
                .execute();
    }

    private static Credential authorize() throws IOException {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(Bukkit2Drive.class.getResourceAsStream("/client_secrets.json")));

        if (clientSecrets.getDetails().getClientId().startsWith("Enter")
                || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            System.out.println(
                    "Enter Client ID and Secret from https://code.google.com/apis/console/?api=driveService "
                            + "into src/main/resources/client_secrets.json");
            System.exit(1);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singleton(DriveScopes.DRIVE_FILE)).setDataStoreFactory(dataStoreFactory)
                .build();

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    @Override
    public void onDisable() {
        timer.cancel();
    }
}
