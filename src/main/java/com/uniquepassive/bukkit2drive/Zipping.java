package com.uniquepassive.bukkit2drive;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipping {

    private static Path buildPath(Path root, Path child) {
        if (root == null) {
            return child;
        } else {
            return Paths.get(root.toString(), child.toString());
        }
    }

    private static void addZipDir(ZipOutputStream out, Path root, Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                Path entry = buildPath(root, child.getFileName());
                if (Files.isDirectory(child)) {
                    addZipDir(out, entry, child);
                } else {
                    out.putNextEntry(new ZipEntry(entry.toString()));
                    Files.copy(child, out);
                    out.closeEntry();
                }
            }
        }
    }

    public static void zipDir(Path inDir, File outFile) throws IOException {
        if (!Files.isDirectory(inDir)) {
            throw new IllegalArgumentException("Path must be a directory.");
        }

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile));

        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            addZipDir(out, inDir.getFileName(), inDir);
        }
    }
}
