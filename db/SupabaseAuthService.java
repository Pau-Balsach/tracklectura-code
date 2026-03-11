package db;

import utils.ConfigManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Servicio para manejar la autenticación de usuarios de Supabase usando su API
 * REST.
 */
public class SupabaseAuthService {
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static String currentUserId = null;
    private static String currentAccessToken = null;
    private static String currentUserEmail = null;

    /**
     * Intenta iniciar sesión con email y contraseña.
     * 
     * @return null si tiene éxito, o el mensaje de error si falla.
     */
    public static String login(String email, String password) {
        String urlStr = ConfigManager.getSupabaseUrl() + "/auth/v1/token?grant_type=password";
        String anonKey = ConfigManager.getSupabaseAnonKey();

        if (urlStr.equals("/auth/v1/token?grant_type=password") || anonKey.isEmpty()) {
            return "Faltan credenciales de Supabase en la configuración.";
        }

        try {
            // Cuerpo JSON para el login
            String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}",
                    email.replace("\"", "\\\""),
                    password.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parseamos el JSON para obtener el JWT y el UUID
                String res = response.body();
                currentAccessToken = extractJsonValue(res, "access_token");
                String userObj = res.substring(res.indexOf("\"user\":"));
                currentUserId = extractJsonValue(userObj, "id");
                currentUserEmail = email;
                return null; // Éxito
            } else {
                String body = response.body();
                String msg = extractJsonValue(body, "error_description");
                if (msg == null || msg.isEmpty())
                    msg = extractJsonValue(body, "msg");
                if (msg == null || msg.isEmpty())
                    msg = extractJsonValue(body, "message");
                if (msg == null || msg.isEmpty())
                    msg = "Usuario o contraseña incorrectos.";
                return "Error al iniciar sesión: " + msg;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error de red: " + e.getMessage();
        }
    }

    /**
     * Intenta registrar un nuevo usuario con email y contraseña.
     * 
     * @return null si tiene éxito, o el mensaje de error si falla.
     */
    public static String signup(String email, String password) {
        String urlStr = ConfigManager.getSupabaseUrl() + "/auth/v1/signup";
        String anonKey = ConfigManager.getSupabaseAnonKey();

        if (urlStr.equals("/auth/v1/signup") || anonKey.isEmpty()) {
            return "Faltan credenciales de Supabase en la configuración.";
        }

        try {
            String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}",
                    email.replace("\"", "\\\""),
                    password.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // SignUp exitoso, ahora seteamos como en el login (si auto confirm no está
                // activo, puede que no devuelva token)
                String res = response.body();
                String token = extractJsonValue(res, "access_token");
                if (token != null && !token.isEmpty()) {
                    currentAccessToken = token;
                    String userObj = res.substring(res.indexOf("\"user\":"));
                    currentUserId = extractJsonValue(userObj, "id");
                    currentUserEmail = email;
                }
                return null;
            } else {
                String body = response.body();
                String msg = extractJsonValue(body, "msg");
                if (msg == null || msg.isEmpty())
                    msg = extractJsonValue(body, "error_description");
                if (msg == null || msg.isEmpty())
                    msg = extractJsonValue(body, "message");
                if (msg == null || msg.isEmpty())
                    msg = "No se pudo completar el registro.";
                return "Error en registro: " + msg;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error de red: " + e.getMessage();
        }
    }

    public static String getCurrentUserId() {
        return currentUserId;
    }

    public static String getCurrentAccessToken() {
        return currentAccessToken;
    }

    public static String getCurrentUserEmail() {
        return currentUserEmail;
    }

    /**
     * Helper súper simple para extraer un valor de JSON sin necesitar Jackson/GSON.
     * Busca la clave "key":"value" y extrae "value".
     */
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