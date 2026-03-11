package utils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servicio de utilidad para exportar datos de lectura a formatos externos.
 * Gestiona la generación de archivos CSV y capturas de pantalla de las
 * gráficas.
 */
public class ExportService {

    // Ruta por defecto para las exportaciones
    public static String rutaExportacion = ConfigManager.getExportPath();

    /**
     * Exporta datos a un archivo CSV e informa al usuario de la ubicación exacta.
     */
    public static boolean exportarDatosCSV(List<String[]> datos, String nombreLibro) {
        try {
            String nombreLimpio = nombreLibro.replace(" ", "_").replaceAll("[^a-zA-Z0-9._-]", "");
            File carpeta = new File(rutaExportacion);
            if (!carpeta.exists())
                carpeta.mkdirs();

            File archivoBase = new File(carpeta, "Lectura_" + nombreLimpio + ".csv");
            File archivoFinal = obtenerArchivoUnico(archivoBase);

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(archivoFinal), StandardCharsets.UTF_8))) {

                writer.write('\ufeff'); // BOM para compatibilidad con Excel

                String[] cabecera = { "Fecha", "Capítulo", "Páginas", "Minutos", "PPM", "PPH" };
                writer.write(String.join(";", cabecera));
                writer.newLine();

                for (String[] fila : datos) {
                    writer.write(String.join(";", fila));
                    writer.newLine();
                }

                // Cambio clave: Mostrar archivoFinal.getAbsolutePath()
                JOptionPane.showMessageDialog(null,
                        "✅ Datos exportados con éxito.\n\nUbicación:\n" + archivoFinal.getAbsolutePath(),
                        "Exportación CSV", JOptionPane.INFORMATION_MESSAGE);

                return true;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "❌ Error al exportar CSV: " + e.getMessage());
            return false;
        }
    }

    /**
     * Captura el componente visual como PNG e informa de la ruta completa de
     * guardado.
     */
    public static void exportarAPNG(JPanel panel, String nombreLibro) {
        try {
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            panel.paint(g2);
            g2.dispose();

            File carpeta = new File(rutaExportacion);
            if (!carpeta.exists())
                carpeta.mkdirs();

            String nombreLimpio = nombreLibro.replace(" ", "_").replaceAll("[^a-zA-Z0-9._-]", "");
            File archivoBase = new File(carpeta, "Grafica_" + nombreLimpio + ".png");
            File archivoFinal = obtenerArchivoUnico(archivoBase);

            ImageIO.write(image, "png", archivoFinal);

            // Cambio clave: Mostrar archivoFinal.getAbsolutePath()
            JOptionPane.showMessageDialog(null,
                    "✅ Imagen guardada con éxito.\n\nUbicación:\n" + archivoFinal.getAbsolutePath(),
                    "Exportación de Imagen", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "❌ Error al exportar imagen: " + e.getMessage());
        }
    }

    /**
     * Método auxiliar para evitar la sobreescritura de archivos mediante sufijos
     * numéricos.
     */
    private static File obtenerArchivoUnico(File archivo) {
        if (!archivo.exists())
            return archivo;

        String ruta = archivo.getAbsolutePath();
        String nombre = ruta;
        String ext = "";

        int dot = ruta.lastIndexOf('.');
        if (dot > 0) {
            nombre = ruta.substring(0, dot);
            ext = ruta.substring(dot);
        }

        int i = 1;
        while (new File(nombre + " (" + i + ")" + ext).exists()) {
            i++;
        }
        return new File(nombre + " (" + i + ")" + ext);
    }

}