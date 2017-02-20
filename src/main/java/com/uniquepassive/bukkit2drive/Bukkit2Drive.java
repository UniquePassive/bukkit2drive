package com.uniquepassive.bukkit2drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Bukkit2Drive extends JavaPlugin {

    private static final String APPLICATION_NAME = "Bukkit2Drive";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static NetHttpTransport httpTransport;
    private static FileDataStoreFactory dataStoreFactory;
    private static Drive driveService;

    private Timer timer = new Timer();

    @Override
    public void onEnable() {
        try {
            if (httpTransport == null) {
                httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                dataStoreFactory = new FileDataStoreFactory(new java.io.File("driveService"));

                Credential credential = authorize();

                driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runBackup();
                }
            }, 0, 3 * 60 * 60 * 1000);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void runBackup() {
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

    private File getRootFolder() throws IOException {
        List<File> fileList = driveService
                .files()
                .list()
                .execute()
                .getFiles()
                .stream()
                .filter(f -> f.getName().equals(APPLICATION_NAME))
                .collect(Collectors.toList());

        if (fileList.isEmpty()) {
            File folderMetadata = new File();
            folderMetadata.setName(APPLICATION_NAME);
            folderMetadata.setMimeType("application/vnd.google-apps.folder");

            return driveService
                    .files()
                    .create(folderMetadata)
                    .setFields("id")
                    .execute();
        }

        return fileList.get(0);
    }

    private File createBackupFolder(File rootFolder) throws IOException {
        File folderMetadata = new File();
        folderMetadata.setName(DATE_FORMAT.format(new Date()));
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

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
                JSON_FACTORY,
                clientSecrets,
                Collections.singleton(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(dataStoreFactory)
                .addRefreshListener(new DataStoreCredentialRefreshListener(clientSecrets.getInstalled().getClientId(),
                        dataStoreFactory))
                .setAccessType("offline")
                .build();

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
                .authorize("user");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backup")
                && sender instanceof ConsoleCommandSender) {
            runBackup();
            return true;
        }

        return false;
    }

    @Override
    public void onDisable() {
        timer.cancel();
    }
}
