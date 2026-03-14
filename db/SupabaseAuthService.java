package db;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import utils.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Servicio de autenticación contra Supabase.
 *
 * MEJORA PRINCIPAL: renovación automática del access token.
 *
 * El JWT de Supabase caduca en 1 hora por defecto. Antes, cuando el token
 * caducaba todas las peticiones HTTP devolvían 401 y los métodos como
 * obtenerSesionesPorLibro() devolvían listas vacías — los gráficos mostraban
 * "sin registros" aunque los datos existían en la base de datos.
 *
 * Solución:
 * - Al hacer login se guarda también el refresh_token.
 * - refreshAccessToken() intercambia el refresh_token por un nuevo
 * access_token sin necesidad de que el usuario vuelva a introducir
 * su contraseña.
 * - PostgresDatabaseService llama a ensureValidToken() antes de cada
 * petición HTTP para renovar el token si está próximo a caducar.
 */
public class SupabaseAuthService {

    private static final HttpClient client = HttpClient.newBuilder().build();

    private static String currentUserId = null;
    private static String currentAccessToken = null;
    private static String currentRefreshToken = null; // NUEVO: necesario para renovar
    private static String currentUserEmail = null;
    private static long tokenExpiresAt = 0; // epoch millis en que caduca

    // Renovar si quedan menos de 5 minutos para que caduque el token
    private static final long REFRESH_MARGIN_MS = 5 * 60 * 1000L;

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    public static String login(String email, String password) {
        String urlStr = ConfigManager.getSupabaseUrl() + "/auth/v1/token?grant_type=password";
        String anonKey = ConfigManager.getSupabaseAnonKey();

        if (ConfigManager.getSupabaseUrl().isEmpty() || anonKey.isEmpty())
            return "Faltan credenciales de Supabase en la configuración.";

        try {
            JsonObject body = new JsonObject();
            body.addProperty("email", email);
            body.addProperty("password", password);

            HttpResponse<String> response = enviarPost(urlStr, anonKey, body.toString());

            if (response.statusCode() == 200)
                return parseAuthResponse(response.body(), email);
            else
                return "Error al iniciar sesion: "
                        + extraerMensajeError(response.body(), "Usuario o contrasena incorrectos.");

        } catch (Exception e) {
            e.printStackTrace();
            return "Error de red: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Signup
    // -------------------------------------------------------------------------

    public static String signup(String email, String password) {
        String urlStr = ConfigManager.getSupabaseUrl() + "/auth/v1/signup";
        String anonKey = ConfigManager.getSupabaseAnonKey();

        if (ConfigManager.getSupabaseUrl().isEmpty() || anonKey.isEmpty())
            return "Faltan credenciales de Supabase en la configuracion.";

        try {
            JsonObject body = new JsonObject();
            body.addProperty("email", email);
            body.addProperty("password", password);

            HttpResponse<String> response = enviarPost(urlStr, anonKey, body.toString());

            if (response.statusCode() == 200)
                return parseAuthResponse(response.body(), email);
            else
                return "Error en registro: "
                        + extraerMensajeError(response.body(), "No se pudo completar el registro.");

        } catch (Exception e) {
            e.printStackTrace();
            return "Error de red: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Renovacion automatica del token (NUEVO)
    // -------------------------------------------------------------------------

    /**
     * Comprueba si el access token esta proximo a caducar y lo renueva
     * usando el refresh token si es necesario.
     *
     * Llamar a este metodo antes de cualquier peticion HTTP a Supabase
     * garantiza que el token sea siempre valido sin interrumpir al usuario.
     *
     * @return true si el token es valido (o se renovo con exito),
     *         false si no hay refresh token o la renovacion fallo.
     */
    public static boolean ensureValidToken() {
        if (currentAccessToken == null)
            return false;

        // Si el token no caduca en los proximos 5 minutos, esta bien
        if (System.currentTimeMillis() < tokenExpiresAt - REFRESH_MARGIN_MS)
            return true;

        System.out.println("[Auth] Token proximo a caducar, renovando...");
        return refreshAccessToken();
    }

    /**
     * Usa el refresh_token para obtener un nuevo access_token de Supabase
     * sin que el usuario tenga que volver a introducir su contrasena.
     *
     * El refresh_token dura 60 dias por defecto en Supabase, por lo que
     * cubre cualquier sesion normal de uso de la aplicacion.
     */
    public static boolean refreshAccessToken() {
        if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
            System.err.println("[Auth] No hay refresh token. Se requiere nuevo login.");
            return false;
        }

        String urlStr = ConfigManager.getSupabaseUrl() + "/auth/v1/token?grant_type=refresh_token";
        String anonKey = ConfigManager.getSupabaseAnonKey();

        try {
            JsonObject body = new JsonObject();
            body.addProperty("refresh_token", currentRefreshToken);

            HttpResponse<String> response = enviarPost(urlStr, anonKey, body.toString());

            if (response.statusCode() == 200) {
                String error = parseAuthResponse(response.body(), currentUserEmail);
                if (error == null) {
                    System.out.println("[Auth] Token renovado correctamente.");
                    return true;
                }
            }

            System.err.println("[Auth] Fallo al renovar token (status " + response.statusCode() + ").");
            return false;

        } catch (Exception e) {
            System.err.println("[Auth] Error renovando token: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public static String getCurrentUserId() {
        return currentUserId;
    }

    public static String getCurrentAccessToken() {
        return currentAccessToken;
    }

    public static String getCurrentUserEmail() {
        return currentUserEmail;
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private static HttpResponse<String> enviarPost(String url, String anonKey, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Parsea la respuesta de autenticacion y guarda:
     * - access_token (caduca en ~1h)
     * - refresh_token (caduca en ~60 dias)
     * - expires_in (segundos hasta caducidad del access_token)
     * - user.id
     */
    private static String parseAuthResponse(String responseBody, String email) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

            if (root.has("access_token") && !root.get("access_token").isJsonNull()) {
                currentAccessToken = root.get("access_token").getAsString();
            }

            // Guardar el refresh_token para renovaciones futuras
            if (root.has("refresh_token") && !root.get("refresh_token").isJsonNull()) {
                currentRefreshToken = root.get("refresh_token").getAsString();
            }

            // Calcular cuándo caduca el access_token
            if (root.has("expires_in") && !root.get("expires_in").isJsonNull()) {
                long expiresInSeconds = root.get("expires_in").getAsLong();
                tokenExpiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L);
                System.out.println("[Auth] Token valido durante " + expiresInSeconds / 60 + " minutos.");
            }

            if (root.has("user") && root.get("user").isJsonObject()) {
                JsonObject user = root.getAsJsonObject("user");
                if (user.has("id") && !user.get("id").isJsonNull()) {
                    currentUserId = user.get("id").getAsString();
                }
            }

            currentUserEmail = email;
            return null;

        } catch (JsonSyntaxException e) {
            System.err.println("[Auth] Respuesta JSON invalida: " + responseBody);
            return "Respuesta inesperada del servidor.";
        }
    }

    private static String extraerMensajeError(String responseBody, String fallback) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            for (String campo : new String[] { "error_description", "message", "msg" }) {
                if (root.has(campo) && !root.get(campo).isJsonNull()) {
                    String valor = root.get(campo).getAsString();
                    if (!valor.isBlank())
                        return valor;
                }
            }
        } catch (JsonSyntaxException ignored) {
        }
        return fallback;
    }

    /** @deprecated Usar Gson directamente. */
    @Deprecated
    public static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1)
            return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1)
            return null;
        return json.substring(start, end);
    }
}
