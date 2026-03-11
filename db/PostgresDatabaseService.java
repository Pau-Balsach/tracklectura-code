package db;

import com.google.gson.*;
import model.DataPoint;
import model.Sesion;
import utils.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.sql.Connection;

public class PostgresDatabaseService implements DatabaseService {

    private static boolean configurado = false;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private String getBaseUrl() {
        return ConfigManager.getSupabaseUrl() + "/rest/v1";
    }

    private HttpRequest.Builder baseRequest() {
        return HttpRequest.newBuilder()
                .header("apikey", ConfigManager.getSupabaseAnonKey())
                .header("Authorization", "Bearer " + SupabaseAuthService.getCurrentAccessToken())
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates"); // UPSERT equivalent
    }

    @Override
    public void conectar() throws Exception {
        if (!configurado) {
            System.out.println("✅ REST Supabase Configurado.");
            configurado = true;
        }
    }

    @Override
    public void crearEsquema() throws Exception {
        // No-Op in REST API. User manages tables in Supabase Web.
    }

    @Override
    public void registrarUsuario(String email) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", SupabaseAuthService.getCurrentUserId());
            json.addProperty("email", email);

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/usuarios"))
                    .header("Prefer", "resolution=merge-duplicates") // UPSERT equivalent
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void limpiarDatosDeOtrosUsuarios(String currentUserId) {
        // No-Op in Postgres: Cada usuario solo puede ver lo suyo por RLS o query
        // manual,
        // no hay necesidad de borrar nada de la DB central.
    }

    @Override
    public Connection getConnection() {
        return null; // Not applicable for REST
    }

    @Override
    public void guardarLibro(String nombre, int paginas) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("nombre", nombre);
            json.addProperty("paginas_totales", paginas);
            json.addProperty("estado", "Por leer");
            json.addProperty("user_id", SupabaseAuthService.getCurrentUserId());

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros"))
                    .header("Prefer", "resolution=ignore-duplicates") // UPSERT equivalent mapping
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void actualizarPaginasTotales(int libroId, int nuevasPaginas) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("paginas_totales", nuevasPaginas);

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void guardarCoverUrl(int libroId, String url) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("cover_url", url);

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String obtenerCoverUrl(int libroId) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId + "&select=cover_url"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (!arr.isEmpty()) {
                    JsonElement el = arr.get(0).getAsJsonObject().get("cover_url");
                    return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void guardarSesion(int libroId, String cap, int pIni, int pFin, int pags, double mins, double ppm,
            double pph, String fecha) {
        insertarSesionManual(libroId, fecha, cap, pIni, pFin, pags, mins, ppm, pph);
    }

    @Override
    public int obtenerLibroId(String nombre) {
        try {
            String userId = SupabaseAuthService.getCurrentUserId();
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?nombre=eq." + java.net.URLEncoder.encode(nombre, "UTF-8")
                            + "&user_id=eq." + userId + "&select=id"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (!arr.isEmpty()) {
                    return arr.get(0).getAsJsonObject().get("id").getAsInt();
                } else {
                    System.err.println("⚠️ Libro no encontrado en Supabase: " + nombre);
                }
            } else {
                System.err.println("❌ Error " + resp.statusCode() + " al obtener ID de libro: " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Fallo crítico de red al obtener ID de libro: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void actualizarEstadoLibro(int libroId, String estado) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("estado", estado);

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String obtenerEstadoLibro(int libroId) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId + "&select=estado"))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
                if (arr.size() > 0) {
                    JsonElement estadoElement = arr.get(0).getAsJsonObject().get("estado");
                    if (estadoElement != null && !estadoElement.isJsonNull()) {
                        return estadoElement.getAsString();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Por leer";
    }

    @Override
    public List<String> obtenerTodosLosLibros() {
        List<String> list = new ArrayList<>();
        try {
            String userId = SupabaseAuthService.getCurrentUserId();
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?user_id=eq." + userId + "&select=nombre&order=nombre.asc"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    list.add(e.getAsJsonObject().get("nombre").getAsString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public int obtenerUltimaPaginaLeida(int libroId) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?libro_id=eq." + libroId
                            + "&select=pagina_fin&order=pagina_fin.desc&limit=1"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (!arr.isEmpty()) {
                    return arr.get(0).getAsJsonObject().get("pagina_fin").getAsInt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean actualizarSesionCompleta(int id, int ini, int fin, int pags, double mins, double ppm, double pph,
            String cap) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("pagina_inicio", ini);
            json.addProperty("pagina_fin", fin);
            json.addProperty("paginas", pags);
            json.addProperty("minutos", mins);
            json.addProperty("ppm", ppm);
            json.addProperty("pph", pph);
            json.addProperty("capitulos", cap);

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?id=eq." + id))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int obtenerPaginasTotales(int libroId) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId + "&select=paginas_totales"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (!arr.isEmpty()) {
                    return arr.get(0).getAsJsonObject().get("paginas_totales").getAsInt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // -- Métodos de parseo seguro para evitar JsonNull --
    private int safeInt(JsonObject o, String key, int def) {
        if (!o.has(key) || o.get(key).isJsonNull())
            return def;
        return o.get(key).getAsInt();
    }

    private double safeDouble(JsonObject o, String key, double def) {
        if (!o.has(key) || o.get(key).isJsonNull())
            return def;
        return o.get(key).getAsDouble();
    }

    // -- Métodos Analíticos (Evaluación Local en Java) --

    private String normalizeDate(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty())
            return "N/A";
        String f = fechaStr;
        if (f.contains(" ")) {
            f = f.split(" ")[0]; // Get only the date portion (dd/MM/yyyy HH:mm)
        } else if (f.contains("T")) {
            f = f.split("T")[0]; // Handle ISO 8601 format: 2026-03-05T03:07:00
        }

        DateTimeFormatter fmtApp = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtIso = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            LocalDate d = LocalDate.parse(f, fmtApp);
            return d.format(fmtApp);
        } catch (Exception e1) {
            try {
                LocalDate d = LocalDate.parse(f, fmtIso);
                return d.format(fmtApp); // Normalize to dd/MM/yyyy
            } catch (Exception e2) {
                try {
                    // Try parsing M/d/yyyy just in case
                    LocalDate d = LocalDate.parse(f, DateTimeFormatter.ofPattern("M/d/yyyy"));
                    return d.format(fmtApp);
                } catch (Exception e3) {
                    return f; // fallback
                }
            }
        }
    }

    public List<Sesion> obtenerTodasLasSesiones() {
        List<Sesion> list = new ArrayList<>();
        try {
            String userId = SupabaseAuthService.getCurrentUserId();
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?user_id=eq." + userId + "&order=id.asc"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Sesion(
                            safeInt(o, "id", -1),
                            o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : "",
                            safeInt(o, "libro_id", -1),
                            o.has("fecha") && !o.get("fecha").isJsonNull() ? o.get("fecha").getAsString() : "",
                            o.has("capitulos") && !o.get("capitulos").isJsonNull() ? o.get("capitulos").getAsString()
                                    : "",
                            safeInt(o, "pagina_inicio", 0),
                            safeInt(o, "pagina_fin", 0),
                            safeInt(o, "paginas", 0),
                            safeDouble(o, "minutos", 0.0),
                            safeDouble(o, "ppm", 0.0),
                            safeDouble(o, "pph", 0.0)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Sesion> obtenerSesionesPorLibro(int libroId) {
        List<Sesion> list = new ArrayList<>();
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?libro_id=eq." + libroId + "&order=id.asc"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Sesion(
                            safeInt(o, "id", -1),
                            o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : "",
                            safeInt(o, "libro_id", -1),
                            o.has("fecha") && !o.get("fecha").isJsonNull() ? o.get("fecha").getAsString() : "",
                            o.has("capitulos") && !o.get("capitulos").isJsonNull() ? o.get("capitulos").getAsString()
                                    : "",
                            safeInt(o, "pagina_inicio", 0),
                            safeInt(o, "pagina_fin", 0),
                            safeInt(o, "paginas", 0),
                            safeDouble(o, "minutos", 0.0),
                            safeDouble(o, "ppm", 0.0),
                            safeDouble(o, "pph", 0.0)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public double obtenerPromedioPPH(int libroId) {
        List<Sesion> list = obtenerSesionesPorLibro(libroId);
        return list.stream().mapToDouble(Sesion::getPph).average().orElse(0.0);
    }

    @Override
    public double obtenerPromedioPPM(int libroId) {
        List<Sesion> list = obtenerSesionesPorLibro(libroId);
        return list.stream().mapToDouble(Sesion::getPpm).average().orElse(0.0);
    }

    @Override
    public double obtenerVelocidadMaxima(int libroId) {
        List<Sesion> list = obtenerSesionesPorLibro(libroId);
        return list.stream().mapToDouble(Sesion::getPpm).max().orElse(0.0);
    }

    @Override
    public double obtenerSesionMasLarga(int libroId) {
        List<Sesion> list = obtenerSesionesPorLibro(libroId);
        return list.stream().mapToDouble(Sesion::getMinutos).max().orElse(0.0);
    }

    @Override
    public String obtenerDiaMasLectura(int libroId) {
        List<Sesion> list = obtenerSesionesPorLibro(libroId);
        Map<String, Integer> map = list.stream()
                .filter(s -> s.getFecha() != null && s.getFecha().length() >= 5)
                .collect(Collectors.groupingBy(s -> normalizeDate(s.getFecha()),
                        Collectors.summingInt(Sesion::getPaginasLeidas)));

        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + " pág)")
                .orElse("N/A");
    }

    @Override
    public double obtenerPorcentajeProgreso(int libroId) {
        int total = obtenerPaginasTotales(libroId);
        if (total <= 0)
            return 0;
        int leido = obtenerUltimaPaginaLeida(libroId);
        return Math.min(100.0, (leido * 100.0) / total);
    }

    @Override
    public int obtenerRachaActual() {
        try {
            List<Sesion> todas = obtenerTodasLasSesiones();
            if (todas.isEmpty())
                return 0;

            List<LocalDate> fechas = new ArrayList<>();
            DateTimeFormatter fmtApp = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            for (Sesion s : todas) {
                if (s.getFecha() == null || s.getFecha().isEmpty())
                    continue;
                String f = normalizeDate(s.getFecha());
                if (f.equals("N/A")) {
                    continue;
                }

                try {
                    LocalDate d = LocalDate.parse(f, fmtApp);
                    fechas.add(d);
                } catch (Exception ignored) {
                }
            }

            if (fechas.isEmpty())
                return 0;

            LocalDate hoy = LocalDate.now();
            List<LocalDate> unicas = fechas.stream()
                    .distinct()
                    .filter(d -> !d.isAfter(hoy)) // Ignore future dates
                    .sorted((a, b) -> b.compareTo(a))
                    .collect(Collectors.toList());

            if (unicas.isEmpty())
                return 0;

            if (!unicas.get(0).equals(hoy) && !unicas.get(0).equals(hoy.minusDays(1))) {
                return 0;
            }

            int racha = 1;
            for (int i = 0; i < unicas.size() - 1; i++) {
                if (unicas.get(i).minusDays(1).equals(unicas.get(i + 1)))
                    racha++;
                else {
                    break;
                }
            }
            return racha;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int obtenerPaginasLeidasHoy() {
        String hoyNormalizado = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        try {
            List<Sesion> todas = obtenerTodasLasSesiones();
            int total = 0;
            for (Sesion s : todas) {
                if (s.getFecha() == null || s.getFecha().isEmpty())
                    continue;
                String f = normalizeDate(s.getFecha());
                if (f.equals(hoyNormalizado)) {
                    total += s.getPaginasLeidas();
                }
            }
            return total;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean eliminarSesion(int sessionId) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?id=eq." + sessionId))
                    .DELETE()
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean eliminarSesionPorUuid(String uuid) {
        try {
            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?uuid=eq." + uuid))
                    .DELETE()
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean insertarSesionManual(int libroId, String fecha, String cap, int ini, int fin, int pags, double mins,
            double ppm, double pph) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("libro_id", libroId);
            json.addProperty("fecha", fecha);
            json.addProperty("capitulos", cap);
            json.addProperty("pagina_inicio", ini);
            json.addProperty("pagina_fin", fin);
            json.addProperty("paginas", pags);
            json.addProperty("minutos", mins);
            json.addProperty("ppm", ppm);
            json.addProperty("pph", pph);
            json.addProperty("user_id", SupabaseAuthService.getCurrentUserId());

            // If No UUID is provided for manual entry, we generate one to satisfy
            // uniqueness
            json.addProperty("uuid", java.util.UUID.randomUUID().toString());

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones"))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean insertarSesionManualConUuid(int libroId, String fecha, String cap, int ini, int fin, int pags,
            double mins, double ppm, double pph, String uuid) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("libro_id", libroId);
            json.addProperty("fecha", fecha);
            json.addProperty("capitulos", cap);
            json.addProperty("pagina_inicio", ini);
            json.addProperty("pagina_fin", fin);
            json.addProperty("paginas", pags);
            json.addProperty("minutos", mins);
            json.addProperty("ppm", ppm);
            json.addProperty("pph", pph);
            json.addProperty("uuid", uuid);
            json.addProperty("user_id", SupabaseAuthService.getCurrentUserId());

            HttpRequest req = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?on_conflict=uuid"))
                    .header("Prefer", "resolution=merge-duplicates")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<DataPoint> obtenerDatosGrafica(String column, int libroId, int minPag, boolean agruparPorDia,
            boolean esHeatmap, boolean esDual) {
        // Obtenemos todo y procesamos local en Java
        List<Sesion> lista = esHeatmap ? obtenerTodasLasSesiones() : obtenerSesionesPorLibro(libroId);
        // Filtrar por pInicio >= minPag si no es heatmap
        if (!esHeatmap) {
            lista = lista.stream().filter(s -> s.getPaginaInicio() >= minPag).collect(Collectors.toList());
        }
        // obtenerSesionesPorLibro ya devuelve orden ASC (cronológico)
        // por lo que no hace falta invertirlo para el gráfico.

        List<DataPoint> result = new ArrayList<>();
        if (agruparPorDia || esHeatmap) {
            Map<String, List<Sesion>> porDia = new LinkedHashMap<>();
            for (Sesion s : lista) {
                String dia = "N/A";
                if (s.getFecha() != null && s.getFecha().length() >= 5)
                    dia = normalizeDate(s.getFecha());
                porDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(s);
            }

            for (Map.Entry<String, List<Sesion>> entry : porDia.entrySet()) {
                String fecha = entry.getKey();
                List<Sesion> diaList = entry.getValue();
                String capStr = diaList.stream().map(Sesion::getCapitulo).filter(c -> c != null && !c.isEmpty())
                        .collect(Collectors.joining(";"));

                double val = 0;
                double valSec = 0;

                if (column.equals("paginas"))
                    val = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum();
                else if (column.equals("pag_fin"))
                    val = diaList.stream().mapToDouble(Sesion::getPaginaFin).max().orElse(0);
                else if (column.equals("ppm")) {
                    val = diaList.stream().mapToDouble(Sesion::getPpm).average().orElse(0);
                    valSec = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum();
                } else if (column.equals("pph")) {
                    val = diaList.stream().mapToDouble(Sesion::getPph).average().orElse(0);
                } else if (column.equals("minutos")) {
                    val = diaList.stream().mapToDouble(Sesion::getMinutos).sum();
                }

                if (esDual) {
                    val = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum();
                    valSec = diaList.stream().mapToDouble(Sesion::getMinutos).sum();
                }

                result.add(new DataPoint(fecha, val, valSec, capStr));
            }
        } else {
            for (Sesion s : lista) {
                double val = 0;
                double valSec = 0;
                if (column.equals("paginas"))
                    val = s.getPaginasLeidas();
                else if (column.equals("pag_fin"))
                    val = s.getPaginaFin();
                else if (column.equals("ppm")) {
                    val = s.getPpm();
                    valSec = s.getPaginasLeidas();
                } else if (column.equals("pph"))
                    val = s.getPph();
                else if (column.equals("minutos"))
                    val = s.getMinutos();

                if (esDual) {
                    val = s.getPaginasLeidas();
                    valSec = s.getMinutos();
                }
                result.add(new DataPoint(s.getFecha(), val, valSec, s.getCapitulo()));
            }
        }
        return result;
    }

    @Override
    public List<String[]> obtenerDatosParaExportar(int libroId, int minPag, String fFiltro, boolean agrupar) {
        List<Sesion> lista = obtenerSesionesPorLibro(libroId);
        lista = lista.stream()
                .filter(s -> s.getPaginaInicio() >= minPag && s.getFecha().compareTo(fFiltro) >= 0)
                .collect(Collectors.toList());
        Collections.reverse(lista);

        List<String[]> data = new ArrayList<>();
        if (agrupar) {
            Map<String, List<Sesion>> porDia = new LinkedHashMap<>();
            for (Sesion s : lista) {
                String dia = "N/A";
                if (s.getFecha() != null && s.getFecha().length() >= 5)
                    dia = normalizeDate(s.getFecha());
                porDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(s);
            }
            for (Map.Entry<String, List<Sesion>> entry : porDia.entrySet()) {
                String f = entry.getKey();
                List<Sesion> dList = entry.getValue();
                String cap = dList.stream().map(Sesion::getCapitulo).filter(c -> c != null && !c.isEmpty())
                        .collect(Collectors.joining("|"));
                int paginas = dList.stream().mapToInt(Sesion::getPaginasLeidas).sum();
                double min = dList.stream().mapToDouble(Sesion::getMinutos).average().orElse(0);
                double ppm = dList.stream().mapToDouble(Sesion::getPpm).average().orElse(0);
                double pph = dList.stream().mapToDouble(Sesion::getPph).average().orElse(0);

                data.add(new String[] { f, cap.isEmpty() ? "N/A" : cap, String.valueOf(paginas),
                        String.format(Locale.US, "%.1f", min), String.format(Locale.US, "%.2f", ppm),
                        String.format(Locale.US, "%.2f", pph) });
            }
        } else {
            for (Sesion s : lista) {
                String cap = s.getCapitulo();
                data.add(new String[] {
                        s.getFecha(), cap == null || cap.isEmpty() ? "N/A" : cap, String.valueOf(s.getPaginasLeidas()),
                        String.format(Locale.US, "%.1f", s.getMinutos()), String.format(Locale.US, "%.2f", s.getPpm()),
                        String.format(Locale.US, "%.2f", s.getPph())
                });
            }
        }
        return data;
    }

    @Override
    public void sincronizarConNube() {
        // No-op para el servicio remoto
    }

    @Override
    public List<Sesion> obtenerTodasLasSesionesDesde(String timestamp) {
        List<Sesion> list = new ArrayList<>();
        try {
            String userId = SupabaseAuthService.getCurrentUserId();
            // Filtrar por updated_at > timestamp
            String url = getBaseUrl() + "/sesiones?user_id=eq." + userId;
            if (timestamp != null && !timestamp.isEmpty()) {
                url += "&updated_at=gt." + java.net.URLEncoder.encode(timestamp, "UTF-8");
            }
            url += "&order=id.asc";

            HttpRequest req = baseRequest()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Sesion(
                            safeInt(o, "id", -1),
                            o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : "",
                            safeInt(o, "libro_id", -1),
                            o.has("fecha") && !o.get("fecha").isJsonNull() ? o.get("fecha").getAsString() : "",
                            o.has("capitulos") && !o.get("capitulos").isJsonNull() ? o.get("capitulos").getAsString()
                                    : "",
                            safeInt(o, "pagina_inicio", 0),
                            safeInt(o, "pagina_fin", 0),
                            safeInt(o, "paginas", 0),
                            safeDouble(o, "minutos", 0.0),
                            safeDouble(o, "ppm", 0.0),
                            safeDouble(o, "pph", 0.0)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean eliminarLibro(int libroId) {
        try {
            // Primero eliminar todas las sesiones del libro (por restricciones FK en
            // Supabase)
            HttpRequest reqSesiones = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/sesiones?libro_id=eq." + libroId))
                    .DELETE()
                    .build();
            httpClient.send(reqSesiones, HttpResponse.BodyHandlers.discarding());

            // Luego eliminar el libro
            HttpRequest reqLibro = baseRequest()
                    .uri(URI.create(getBaseUrl() + "/libros?id=eq." + libroId))
                    .DELETE()
                    .build();
            return httpClient.send(reqLibro, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<model.Libro> obtenerTodosLosLibrosDesde(String timestamp) {
        List<model.Libro> list = new ArrayList<>();
        try {
            String userId = SupabaseAuthService.getCurrentUserId();
            String url = getBaseUrl() + "/libros?user_id=eq." + userId;
            if (timestamp != null && !timestamp.isEmpty()) {
                url += "&updated_at=gt." + java.net.URLEncoder.encode(timestamp, "UTF-8");
            }
            url += "&order=nombre.asc";

            HttpRequest req = baseRequest()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new model.Libro(
                            safeInt(o, "id", -1),
                            o.has("nombre") && !o.get("nombre").isJsonNull() ? o.get("nombre").getAsString() : "",
                            safeInt(o, "paginas_totales", 0),
                            o.has("cover_url") && !o.get("cover_url").isJsonNull() ? o.get("cover_url").getAsString()
                                    : null,
                            o.has("estado") && !o.get("estado").isJsonNull() ? o.get("estado").getAsString()
                                    : "Por leer"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<DataPoint> obtenerPpmMediaPorLibroTerminado() {
        List<DataPoint> resultado = new ArrayList<>();
        try {
            String uid = SupabaseAuthService.getCurrentUserId();
            if (uid == null)
                return resultado;

            HttpRequest reqLibros = baseRequest()
                    .uri(URI.create(
                            getBaseUrl() + "/libros?user_id=eq." + uid + "&estado=eq.Terminado&select=id,nombre"))
                    .GET().build();
            JsonArray libros = JsonParser.parseString(
                    httpClient.send(reqLibros, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray();

            for (JsonElement el : libros) {
                JsonObject libro = el.getAsJsonObject();
                int libroId = libro.get("id").getAsInt();
                String nombre = libro.get("nombre").getAsString();

                HttpRequest reqSes = baseRequest()
                        .uri(URI.create(getBaseUrl() + "/sesiones?libro_id=eq." + libroId
                                + "&user_id=eq." + uid + "&select=ppm"))
                        .GET().build();
                JsonArray sesiones = JsonParser.parseString(
                        httpClient.send(reqSes, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray();

                double suma = 0;
                int count = 0;
                for (JsonElement s : sesiones) {
                    JsonObject ses = s.getAsJsonObject();
                    if (ses.has("ppm") && !ses.get("ppm").isJsonNull()) {
                        suma += ses.get("ppm").getAsDouble();
                        count++;
                    }
                }
                if (count > 0)
                    resultado.add(new DataPoint(nombre, suma / count, 0, ""));
            }
            resultado.sort((a, b) -> Double.compare(b.getValor(), a.getValor()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultado;
    }
}