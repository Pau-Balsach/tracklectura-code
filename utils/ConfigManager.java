package utils;

import java.io.*;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Gestiona las preferencias del usuario persitiendo en un archivo local.
 *
 * Ubicación del archivo de configuración:
 * - Windows: %LOCALAPPDATA%\TrackLectura\config.properties
 * - Otros: ~/.tracklectura/config.properties
 */
public class ConfigManager {

    private static final File APP_DATA_DIR = initAppDataDir();
    private static final String CONFIG_FILE = new File(APP_DATA_DIR, "config.properties").getAbsolutePath();
    private static final byte[] AES_KEY = "Tr4ckL3ctur4K3y!".getBytes();
    private static final Properties props = new Properties();

    static {
        cargar();
        migrarAEncriptado();
    }

    // --- Directorio de datos ---

    private static File initAppDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File baseDir = (localAppData != null && !localAppData.isEmpty())
                ? new File(localAppData, "TrackLectura")
                : new File(System.getProperty("user.home"), ".tracklectura");
        if (!baseDir.exists())
            baseDir.mkdirs();
        return baseDir;
    }

    /**
     * Devuelve el directorio de datos de la aplicación (config + base de datos
     * local).
     */
    public static File getAppDataDirectory() {
        return APP_DATA_DIR;
    }

    // --- Carga y guardado ---

    private static void cargar() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void guardar() {
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "TrackLectura Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- API genérica ---

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static void set(String key, String value) {
        props.setProperty(key, value);
        guardar();
    }

    // --- Preferencias de la app ---

    public static boolean isDarkMode() {
        return Boolean.parseBoolean(get("darkMode", "false"));
    }

    public static void setDarkMode(boolean v) {
        set("darkMode", String.valueOf(v));
    }

    public static String getExportPath() {
        return get("exportPath", System.getProperty("user.home"));
    }

    public static void setExportPath(String p) {
        set("exportPath", p);
    }

    public static int getDailyGoal() {
        return Integer.parseInt(get("dailyGoal", "30"));
    }

    public static void setDailyGoal(int goal) {
        set("dailyGoal", String.valueOf(goal));
    }

    public static boolean isOfflineMode() {
        return Boolean.parseBoolean(get("offlineMode", "false"));
    }

    public static void setOfflineMode(boolean v) {
        set("offlineMode", String.valueOf(v));
    }

    public static String getLastSyncTimestamp() {
        return get("lastSyncTimestamp", "1970-01-01T00:00:00Z");
    }

    public static void setLastSyncTimestamp(String ts) {
        set("lastSyncTimestamp", ts);
    }

    // --- Credenciales de Supabase (cifradas en disco) ---

    public static String getSupabaseUrl() {
        return decrypt(get("supabaseUrl", ""));
    }

    public static void setSupabaseUrl(String url) {
        set("supabaseUrl", encrypt(url));
    }

    public static String getSupabaseAnonKey() {
        return decrypt(get("supabaseAnonKey", ""));
    }

    public static void setSupabaseAnonKey(String key) {
        set("supabaseAnonKey", encrypt(key));
    }

    // --- Credenciales de sesión guardadas (cifradas en disco) ---

    public static String getSavedEmail() {
        return decrypt(get("savedEmail", ""));
    }

    public static void setSavedEmail(String email) {
        set("savedEmail", encrypt(email));
    }

    public static String getSavedPassword() {
        return decrypt(get("savedPass", ""));
    }

    public static void setSavedPassword(String p) {
        set("savedPass", encrypt(p));
    }

    // --- Cifrado AES ---

    private static String encrypt(String value) {
        if (value == null || value.isEmpty())
            return value;
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes("UTF-8")));
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty())
            return encryptedValue;
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedValue)), "UTF-8");
        } catch (Exception e) {
            // Si falla (valor en texto plano legacy), devolver el original sin modificar
            return encryptedValue;
        }
    }

    /**
     * Migra al vuelo los valores en texto plano guardados por versiones anteriores,
     * cifrándolos correctamente.
     */
    private static void migrarAEncriptado() {
        String[] keys = { "supabaseUrl", "supabaseAnonKey", "savedEmail", "savedPass" };
        boolean changed = false;
        for (String k : keys) {
            String raw = props.getProperty(k, "");
            if (raw.isEmpty())
                continue;
            // Si decrypt devuelve el mismo valor, es texto plano: lo ciframos
            if (decrypt(raw).equals(raw)) {
                props.setProperty(k, encrypt(raw));
                changed = true;
            }
        }
        if (changed)
            guardar();
    }
}