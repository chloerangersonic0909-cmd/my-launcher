package net.kdt.pojavlaunch.utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtils {
    /**
     * Gets an InputStream for a given ZIP entry, throwing an IOException if the ZIP entry does not
     * exist.
     * @param zipFile The ZipFile to get the entry from
     * @param entryPath The full path inside of the ZipFile
     * @return The InputStream provided by the ZipFile
     * @throws IOException if the entry was not found
     */
    public static InputStream getEntryStream(ZipFile zipFile, String entryPath) throws IOException{
        ZipEntry entry = zipFile.getEntry(entryPath);
        if(entry == null) throw new IOException("No entry in ZIP file: "+entryPath);
        return zipFile.getInputStream(entry);
    }

    /**
     * Extracts all files in a ZipFile inside of a given directory to a given destination directory
     * How to specify dirName:
     * If you want to extract all files in the ZipFile, specify ""
     * If you want to extract a single directory, specify its full path followed by a trailing /
     * @param zipFile The ZipFile to extract files from
     * @param dirName The directory to extract the files from
     * @param destination The destination directory to extract the files into
     * @throws IOException if it was not possible to create a directory or file extraction failed
     */
    public static void zipExtract(ZipFile zipFile, String dirName, File destination) throws IOException {
        if (zipFile == null || destination == null || dirName == null) {
            throw new IOException("Invalid ZIP extraction arguments");
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Unable to create extraction directory");
        }

        final String rootPath = destination.getCanonicalPath() + File.separator;
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        int extractedEntries = 0;
        long totalBytes = 0L;
        byte[] buffer = new byte[32768];

        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();
            String entryName = zipEntry.getName();
            if (entryName == null || !entryName.startsWith(dirName) || zipEntry.isDirectory()) continue;
            if (++extractedEntries > 100000) throw new IOException("ZIP contains too many files");

            String relativePath = entryName.substring(dirName.length()).replace('\\', '/');
            if (relativePath.isEmpty() || relativePath.startsWith("/") || relativePath.indexOf('\0') >= 0) {
                throw new IOException("Unsafe ZIP entry path");
            }
            for (String segment : relativePath.split("/")) {
                if (segment.equals("..") || segment.equals(".") || segment.indexOf(':') >= 0) {
                    throw new IOException("Unsafe ZIP entry path: " + entryName);
                }
            }

            File outputFile = new File(destination, relativePath);
            String outputPath = outputFile.getCanonicalPath();
            if (!outputPath.startsWith(rootPath)) {
                throw new IOException("ZIP entry escapes destination: " + entryName);
            }
            FileUtils.ensureParentDirectory(outputFile);

            try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                 OutputStream outputStream = new FileOutputStream(outputFile)) {
                int read;
                long entryBytes = 0L;
                while ((read = inputStream.read(buffer)) != -1) {
                    entryBytes += read;
                    totalBytes += read;
                    if (entryBytes > 2L * 1024L * 1024L * 1024L ||
                            totalBytes > 8L * 1024L * 1024L * 1024L) {
                        throw new IOException("ZIP expands beyond the allowed size");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
        }
    }
}
