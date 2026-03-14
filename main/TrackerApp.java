package main;

import ui.LoginWindow;
import ui.ReadingTrackerGUI;
import utils.ConfigManager;
import db.DatabaseManager;
import javax.swing.*;

/**
 * Punto de entrada de la aplicación.
 * Configura el entorno visual, registra el hook de cierre para sincronización
 * y lanza la ventana de login.
 *
 * Las credenciales de Supabase (anon key) son públicas por diseño y permiten
 * conectarse a la base de datos compartida del proyecto.
 */
public class TrackerApp {

    private static final String DEFAULT_URL = "https://cptbivigiemodirfwgny.supabase.co";
    private static final String DEFAULT_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNwdGJpdmlnaWVtb2RpcmZ3Z255Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI1ODgwMDIsImV4cCI6MjA4ODE2NDAwMn0.CALP_aPbh-olrJQIqENIlFI4T1SLz4Okdjj2PebAdD4";

    public static void main(String[] args) {
        // Auto-configurar credenciales si no existen en el sistema
        if (ConfigManager.getSupabaseUrl().isEmpty())
            ConfigManager.setSupabaseUrl(DEFAULT_URL);
        if (ConfigManager.getSupabaseAnonKey().isEmpty())
            ConfigManager.setSupabaseAnonKey(DEFAULT_KEY);

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
                /* sin LAF personalizado */
            }
        }

        // Lanzar la UI en el Event Dispatch Thread de Swing
        SwingUtilities
                .invokeLater(() -> new LoginWindow(() -> new ReadingTrackerGUI().setVisible(true)).setVisible(true));
    }
}
