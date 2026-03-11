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
 * Las credenciales de Supabase se configuran la primera vez desde la pantalla
 * de ajustes, y quedan guardadas cifradas en config.properties (no incluido en el repo).
 */
public class TrackerApp {

    public static void main(String[] args) {
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
