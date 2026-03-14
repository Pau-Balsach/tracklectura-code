package main;

import ui.LoginWindow;
import ui.ReadingTrackerGUI;
import utils.ConfigManager;
import db.DatabaseManager;
import javax.swing.*;

/**
 * Punto de entrada de la aplicación.
 *
 * Las credenciales de Supabase ya NO están hardcodeadas aquí.
 * Se cargan desde (en orden de prioridad):
 * 1. Variables de entorno SUPABASE_URL y SUPABASE_ANON_KEY
 * 2. Archivo externo supabase.properties junto al JAR
 * 3. Lo que el usuario haya guardado previamente en config.properties
 *
 * Si no se encuentra ninguna, se muestra un diálogo de configuración
 * la primera vez.
 */
public class TrackerApp {

    public static void main(String[] args) {

        // --- Cargar credenciales de Supabase de forma segura ---
        cargarCredencialesSupabase();

        // Hook de apagado: sincronizar cambios locales antes de cerrar la JVM
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::cerrarYSincronizar));

        // Aplicar tema visual (FlatLaf) según la preferencia guardada
        try {
            if (ConfigManager.isDarkMode()) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                /* sin LAF personalizado */ }
        }

        // Lanzar la UI en el Event Dispatch Thread de Swing
        SwingUtilities.invokeLater(
                () -> new LoginWindow(() -> new ReadingTrackerGUI().setVisible(true)).setVisible(true));
    }

    /**
     * Estrategia de carga de credenciales (tres niveles de fallback):
     *
     * 1. Variables de entorno — ideal para CI/CD y usuarios avanzados.
     * Ejemplo en Windows: setx SUPABASE_URL "https://xxx.supabase.co"
     * Ejemplo en Linux/Mac: export SUPABASE_URL="https://xxx.supabase.co"
     *
     * 2. Archivo supabase.properties junto al JAR — para distribución
     * a usuarios finales sin que las claves estén en el código.
     * Contenido del archivo:
     * supabase.url=https://xxx.supabase.co
     * supabase.anonKey=eyJhbGci...
     * IMPORTANTE: añadir "supabase.properties" al .gitignore
     *
     * 3. Configuración previa guardada por el propio ConfigManager.
     * Si el usuario ya configuró la app antes, se reutiliza.
     *
     * Si ninguna fuente tiene credenciales, se muestra un diálogo
     * para que el usuario las introduzca manualmente la primera vez.
     */
    private static void cargarCredencialesSupabase() {

        // Nivel 1: variables de entorno (mayor prioridad, nunca tocan el disco)
        String envUrl = System.getenv("SUPABASE_URL");
        String envKey = System.getenv("SUPABASE_ANON_KEY");
        if (envUrl != null && !envUrl.isBlank() && envKey != null && !envKey.isBlank()) {
            ConfigManager.setSupabaseUrl(envUrl);
            ConfigManager.setSupabaseAnonKey(envKey);
            return;
        }

        // Nivel 2: archivo supabase.properties junto al JAR
        java.io.File externalFile = resolverArchivoExterno("supabase.properties");
        if (externalFile != null && externalFile.exists()) {
            java.util.Properties p = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(externalFile)) {
                p.load(fis);
                String fileUrl = p.getProperty("supabase.url", "").trim();
                String fileKey = p.getProperty("supabase.anonKey", "").trim();
                if (!fileUrl.isEmpty() && !fileKey.isEmpty()) {
                    ConfigManager.setSupabaseUrl(fileUrl);
                    ConfigManager.setSupabaseAnonKey(fileKey);
                    return;
                }
            } catch (Exception e) {
                System.err.println("No se pudo leer supabase.properties: " + e.getMessage());
            }
        }

        // Nivel 3: ya configurado previamente por el usuario — no hacer nada
        if (!ConfigManager.getSupabaseUrl().isEmpty() && !ConfigManager.getSupabaseAnonKey().isEmpty()) {
            return;
        }

        // Sin credenciales: pedir al usuario que las configure manualmente
        mostrarDialogoConfiguracionInicial();
    }

    /**
     * Busca el archivo en el mismo directorio que el JAR en ejecución.
     * Funciona tanto ejecutando desde IDE como desde un JAR empaquetado.
     */
    private static java.io.File resolverArchivoExterno(String nombreArchivo) {
        try {
            java.net.URL location = TrackerApp.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            java.io.File jarDir = new java.io.File(location.toURI()).getParentFile();
            return new java.io.File(jarDir, nombreArchivo);
        } catch (Exception e) {
            // Fallback: directorio de trabajo actual
            return new java.io.File(System.getProperty("user.dir"), nombreArchivo);
        }
    }

    /**
     * Diálogo de primera configuración si no se encontraron credenciales por
     * ninguna vía. Solo se muestra una vez; los valores se guardan cifrados
     * en config.properties para usos futuros.
     */
    private static void mostrarDialogoConfiguracionInicial() {
        JTextField urlField = new JTextField(40);
        JTextField keyField = new JTextField(40);
        urlField.setToolTipText("Ejemplo: https://abcdefgh.supabase.co");

        Object[] mensaje = {
                "<html><b>Primera configuración de TrackLectura</b><br>" +
                        "Introduce las credenciales de tu proyecto Supabase.<br>" +
                        "<small>Puedes encontrarlas en Settings → API de tu proyecto.</small></html>",
                " ",
                "URL del proyecto Supabase:", urlField,
                "Anon Key:", keyField
        };

        int opcion = JOptionPane.showConfirmDialog(
                null, mensaje,
                "Configuración de Supabase",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (opcion == JOptionPane.OK_OPTION) {
            String url = urlField.getText().trim();
            String key = keyField.getText().trim();
            if (!url.isEmpty() && !key.isEmpty()) {
                // Se guardan cifrados con AES-GCM (ver ConfigManager mejorado)
                ConfigManager.setSupabaseUrl(url);
                ConfigManager.setSupabaseAnonKey(key);
            } else {
                JOptionPane.showMessageDialog(null,
                        "Sin credenciales la app solo funcionará en modo offline.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
}