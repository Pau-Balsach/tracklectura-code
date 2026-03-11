package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileUtil {
    private static final String COVERS_DIR = System.getProperty("user.home") + File.separator + "tracklectura_covers";

    static {
        File dir = new File(COVERS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Copies a source image to the local covers directory.
     * 
     * @param sourceFile The original image file selected by the user.
     * @param bookId     The ID of the book this cover belongs to.
     * @return The absolute path of the copied file, or null if it fails.
     */
    public static String saveBookCover(File sourceFile, int bookId) {
        if (sourceFile == null || !sourceFile.exists())
            return null;

        String extension = "";
        String fileName = sourceFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileName.substring(dotIndex);
        }

        // Use bookId for a unique and predictable name
        String destFileName = "cover_book_" + bookId + extension;
        File destFile = new File(COVERS_DIR, destFileName);

        try {
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return destFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getCoversDir() {
        return COVERS_DIR;
    }
}