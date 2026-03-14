package utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gestiona las preferencias del usuario persistiendo en un archivo local.
 *
 * Ubicación del archivo de configuración:
 * - Windows: %LOCALAPPDATA%\TrackLectura\config.properties
 * - Otros: ~/.tracklectura/config.properties
 *
 * MEJORAS DE SEGURIDAD respecto a la versión anterior:
 *
 * 1. AES-GCM en lugar de AES-ECB.
 * ECB es determinista: el mismo texto siempre produce el mismo cifrado,
 * lo que filtra patrones. GCM incluye un IV aleatorio (nonce) por cada
 * cifrado y autentica el mensaje (detecta tampering).
 *
 * 2. Clave derivada con PBKDF2 a partir de datos únicos de la máquina
 * (hostname + username + nombre del directorio de la app) más una sal
 * almacenada en disco. Así, aunque alguien copie config.properties a
 * otro ordenador, no puede descifrarlo.
 * La clave ya NO está hardcodeada en el código fuente.
 *
 * 3. Migración transparente: los valores cifrados con AES-ECB de la versión
 * anterior se detectan, se descifran con la clave legacy y se re-cifran
 * con AES-GCM automáticamente.
 */
public class ConfigManager {

    private static final File APP_DATA_DIR = initAppDataDir();
    private static final String CONFIG_FILE = new File(APP_DATA_DIR, "config.properties").getAbsolutePath();
    private static final String SALT_FILE = new File(APP_DATA_DIR, ".salt").getAbsolutePath();

    // Parámetros AES-GCM
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12; // 96 bits, recomendado para GCM
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERS = 120_000;

    // Prefijo para distinguir valores cifrados con GCM de los legacy (ECB)
    private static final String GCM_PREFIX = "GCM:";

    // Clave legacy (AES-ECB) solo para migración de versiones anteriores
    private static final byte[] LEGACY_AES_KEY = "Tr4ckL3ctur4K3y!".getBytes(StandardCharsets.UTF_8);

    private static final Properties props = new Properties();
    private static final SecureRandom rng = new SecureRandom();
    private static SecretKey aesKey = null; // se deriva una vez, se cachea

    static {
        cargar();
        derivarClave();
        migrarAGCM();
    }

    // -------------------------------------------------------------------------
    // Directorio de datos
    // -------------------------------------------------------------------------

    private static File initAppDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File baseDir = (localAppData != null && !localAppData.isBlank())
                ? new File(localAppData, "TrackLectura")
                : new File(System.getProperty("user.home"), ".tracklectura");
        if (!baseDir.exists())
            baseDir.mkdirs();
        return baseDir;
    }

    public static File getAppDataDirectory() {
        return APP_DATA_DIR;
    }

    // -------------------------------------------------------------------------
    // Carga y guardado
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // API genérica
    // -------------------------------------------------------------------------

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static void set(String key, String value) {
        props.setProperty(key, value);
        guardar();
    }

    // -------------------------------------------------------------------------
    // Preferencias de la app
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Credenciales de Supabase (cifradas con AES-GCM)
    // -------------------------------------------------------------------------

    public static String getSupabaseUrl() {
        return decrypt(get("supabaseUrl", ""));
    }

    public static void setSupabaseUrl(String u) {
        set("supabaseUrl", encrypt(u));
    }

    public static String getSupabaseAnonKey() {
        return decrypt(get("supabaseAnonKey", ""));
    }

    public static void setSupabaseAnonKey(String k) {
        set("supabaseAnonKey", encrypt(k));
    }

    // -------------------------------------------------------------------------
    // Credenciales de sesión guardadas (cifradas con AES-GCM)
    // -------------------------------------------------------------------------

    public static String getSavedEmail() {
        return decrypt(get("savedEmail", ""));
    }

    public static void setSavedEmail(String e) {
        set("savedEmail", encrypt(e));
    }

    public static String getSavedPassword() {
        return decrypt(get("savedPass", ""));
    }

    public static void setSavedPassword(String p) {
        set("savedPass", encrypt(p));
    }

    // -------------------------------------------------------------------------
    // Derivación de clave (PBKDF2 + sal única por instalación)
    // -------------------------------------------------------------------------

    /**
     * Deriva la clave AES-256 una única vez por sesión usando:
     * - hostname + username + nombre del directorio de la app (específico de la
     * máquina)
     * - sal aleatoria de 32 bytes guardada en .salt (específica de la instalación)
     * - PBKDF2WithHmacSHA256 con 120.000 iteraciones
     *
     * Resultado: aunque alguien copie config.properties a otro PC,
     * no puede descifrar las credenciales sin el archivo .salt y sin ser
     * el mismo usuario en la misma máquina.
     */
    private static void derivarClave() {
        try {
            byte[] sal = cargarOCrearSal();

            // Datos únicos de la máquina como "contraseña" para PBKDF2
            String maquinaId = obtenerIdMaquina();

            PBEKeySpec spec = new PBEKeySpec(
                    maquinaId.toCharArray(), sal, PBKDF2_ITERS, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            spec.clearPassword(); // limpiar la contraseña de memoria

            aesKey = new SecretKeySpec(keyBytes, "AES");

        } catch (Exception e) {
            // En caso de fallo inesperado, fallback a clave fija legacy para
            // no dejar la app inutilizable, pero logueamos el problema.
            System.err.println("[ConfigManager] Error derivando clave, usando fallback: " + e.getMessage());
            aesKey = new SecretKeySpec(LEGACY_AES_KEY, "AES");
        }
    }

    /** Combina datos identificativos de la máquina y el usuario. */
    private static String obtenerIdMaquina() {
        String hostname = System.getenv().getOrDefault("COMPUTERNAME",
                System.getenv().getOrDefault("HOSTNAME", "localhost"));
        String username = System.getProperty("user.name", "user");
        String appDir = APP_DATA_DIR.getAbsolutePath();
        return hostname + "|" + username + "|" + appDir;
    }

    /** Lee la sal desde .salt o la genera y guarda si no existe. */
    private static byte[] cargarOCrearSal() throws IOException {
        File saltFile = new File(SALT_FILE);
        if (saltFile.exists()) {
            return Base64.getDecoder().decode(
                    new String(java.nio.file.Files.readAllBytes(saltFile.toPath()), StandardCharsets.UTF_8).trim());
        }
        // Primera ejecución: generar sal aleatoria y persistirla
        byte[] sal = new byte[32];
        rng.nextBytes(sal);
        String encoded = Base64.getEncoder().encodeToString(sal);
        try (FileOutputStream fos = new FileOutputStream(saltFile)) {
            fos.write(encoded.getBytes(StandardCharsets.UTF_8));
        }
        // Ocultar el archivo en Windows para que no llame la atención
        try {
            java.nio.file.Files.setAttribute(saltFile.toPath(), "dos:hidden", true);
        } catch (Exception ignored) {
            /* no disponible en Linux/Mac */ }
        return sal;
    }

    // -------------------------------------------------------------------------
    // Cifrado y descifrado AES-GCM
    // -------------------------------------------------------------------------

    /**
     * Cifra con AES-256-GCM.
     * Formato del valor almacenado: "GCM:" + Base64(IV + ciphertext + tag)
     * El IV (12 bytes aleatorios) va concatenado al inicio del payload.
     */
    private static String encrypt(String value) {
        if (value == null || value.isEmpty())
            return value;
        try {
            byte[] iv = new byte[IV_BYTES];
            rng.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // Concatenar IV + ciphertext en un solo bloque
            byte[] payload = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, payload, IV_BYTES, ciphertext.length);

            return GCM_PREFIX + Base64.getEncoder().encodeToString(payload);

        } catch (Exception e) {
            System.err.println("[ConfigManager] Error cifrando: " + e.getMessage());
            return "";
        }
    }

    /**
     * Descifra un valor AES-GCM (prefijo "GCM:") o, para migración,
     * un valor AES-ECB legacy sin prefijo.
     */
    private static String decrypt(String stored) {
        if (stored == null || stored.isEmpty())
            return stored;

        if (stored.startsWith(GCM_PREFIX)) {
            // Valor cifrado con la nueva versión
            return decryptGCM(stored.substring(GCM_PREFIX.length()));
        } else {
            // Intentar descifrar como AES-ECB legacy (migración)
            return decryptLegacyECB(stored);
        }
    }

    private static String decryptGCM(String base64Payload) {
        try {
            byte[] payload = Base64.getDecoder().decode(base64Payload);

            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);

        } catch (Exception e) {
            System.err.println("[ConfigManager] Error descifrando GCM: " + e.getMessage());
            return stored_fallback(base64Payload);
        }
    }

    /** Solo para migración — usa la clave hardcodeada de la versión anterior. */
    private static String decryptLegacyECB(String encryptedValue) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(LEGACY_AES_KEY, "AES"));
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(encryptedValue)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Si tampoco es ECB legacy, probablemente sea texto plano puro (versión muy
            // antigua)
            return encryptedValue;
        }
    }

    // Evitar NPE en corner cases de migración
    private static String stored_fallback(String v) {
        return v;
    }

    // -------------------------------------------------------------------------
    // Migración automática ECB → GCM
    // -------------------------------------------------------------------------

    /**
     * Detecta valores guardados sin el prefijo "GCM:" (cifrados con AES-ECB
     * o en texto plano de versiones anteriores), los descifra con la lógica
     * legacy y los re-cifra con AES-GCM.
     * Se ejecuta una sola vez al inicio; en ejecuciones posteriores todos los
     * valores ya tendrán el prefijo y no se tocará nada.
     */
    private static void migrarAGCM() {
        String[] claves = { "supabaseUrl", "supabaseAnonKey", "savedEmail", "savedPass" };
        boolean changed = false;

        for (String k : claves) {
            String raw = props.getProperty(k, "");
            if (raw.isEmpty() || raw.startsWith(GCM_PREFIX))
                continue;

            // Es un valor legacy: descifrarlo y re-cifrarlo con GCM
            String plaintext = decryptLegacyECB(raw);
            if (plaintext != null && !plaintext.isEmpty()) {
                props.setProperty(k, encrypt(plaintext));
                changed = true;
                System.out.println("[ConfigManager] Migrado a AES-GCM: " + k);
            }
        }

        if (changed)
            guardar();
    }
}
