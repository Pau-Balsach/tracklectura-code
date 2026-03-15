package db;

import model.DataPoint;
import model.Sesion;
import java.sql.Connection;
import java.util.List;

/**
 * Gestor central de la base de datos.
 * Conecta con Supabase a través de SQLiteSyncService (local-first).
 *
 * MEJORA: La inicialización es idempotente. Llamar a {@link #inicializar()}
 * varias veces (por ejemplo, desde el bloque static y desde LoginWindow)
 * ya no crea conexiones duplicadas ni deja el estado inconsistente.
 */
public class DatabaseManager {

    private static volatile SQLiteSyncService service;
    /** Guarda si ya se ejecutó una inicialización completa con éxito. */
    private static volatile boolean inicializado = false;

    static {
        inicializar();
    }

    /**
     * Inicializa el servicio de base de datos.
     * Si ya fue inicializado correctamente, este método no hace nada.
     * Hilo-seguro gracias a {@code synchronized}.
     */
    public static synchronized void inicializar() {
        if (inicializado && service != null) {
            System.out.println("[DatabaseManager] Ya inicializado, saltando.");
            return;
        }
        try {
            service = new SQLiteSyncService();
            service.conectar();
            service.crearEsquema();
            inicializado = true;
        } catch (Exception e) {
            e.printStackTrace();
            inicializado = false;
            javax.swing.JOptionPane.showMessageDialog(null,
                    "Error al conectar con la base de datos:\n" + e.getMessage(),
                    "Error Crítico de Base de Datos",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reinicia forzosamente la conexión (útil tras login cuando cambia el usuario).
     * A diferencia de {@link #inicializar()}, siempre recrea el servicio.
     */
    public static synchronized void reinicializar() {
        if (service != null) {
            try {
                service.cerrar();
            } catch (Exception ignored) { }
        }
        inicializado = false;
        inicializar();
    }

    public static void cerrarYSincronizar() {
        if (service != null) {
            System.out.println("Sincronizando antes de cerrar...");
            service.sincronizarConNube();
            service.cerrar();
        }
    }

    /** @deprecated Exponer la conexión JDBC rompe la encapsulación. Usar los métodos del servicio. */
    @Deprecated
    public static Connection getConnection() {
        return service != null ? service.getConnection() : null;
    }

    public static void guardarLibro(String nombre, int paginas) {
        service.guardarLibro(nombre, paginas);
    }

    public static void actualizarPaginasTotales(int libroId, int nuevasPaginas) {
        service.actualizarPaginasTotales(libroId, nuevasPaginas);
    }

    public static void guardarCoverUrl(int libroId, String url) {
        service.guardarCoverUrl(libroId, url);
    }

    public static String obtenerCoverUrl(int libroId) {
        return service.obtenerCoverUrl(libroId);
    }

    public static void guardarSesion(int libroId, String cap, int pIni, int pFin, int pags, double mins,
                                     double ppm, double pph, String fecha) {
        service.guardarSesion(libroId, cap, pIni, pFin, pags, mins, ppm, pph, fecha);
    }

    public static int obtenerLibroId(String nombre) {
        return service.obtenerLibroId(nombre);
    }

    public static void actualizarEstadoLibro(int libroId, String estado) {
        service.actualizarEstadoLibro(libroId, estado);
    }

    public static String obtenerEstadoLibro(int libroId) {
        return service.obtenerEstadoLibro(libroId);
    }

    public static List<String> obtenerTodosLosLibros() {
        return service.obtenerTodosLosLibros();
    }

    public static List<model.Libro> obtenerTodosLosLibrosDesde(String timestamp) {
        return service.obtenerTodosLosLibrosDesde(timestamp);
    }

    public static int obtenerUltimaPaginaLeida(int libroId) {
        return service.obtenerUltimaPaginaLeida(libroId);
    }

    public static int obtenerPaginasTotales(int libroId) {
        return service.obtenerPaginasTotales(libroId);
    }

    public static double obtenerPromedioPPH(int libroId) {
        return service.obtenerPromedioPPH(libroId);
    }

    public static double obtenerVelocidadMaxima(int libroId) {
        return service.obtenerVelocidadMaxima(libroId);
    }

    public static double obtenerSesionMasLarga(int libroId) {
        return service.obtenerSesionMasLarga(libroId);
    }

    public static String obtenerDiaMasLectura(int libroId) {
        return service.obtenerDiaMasLectura(libroId);
    }

    public static double obtenerPorcentajeProgreso(int libroId) {
        return service.obtenerPorcentajeProgreso(libroId);
    }

    public static int obtenerRachaActual() {
        return service.obtenerRachaActual();
    }

    public static int obtenerPaginasLeidasHoy() {
        if (service != null)
            return service.obtenerPaginasLeidasHoy();
        return 0;
    }

    public static List<Sesion> obtenerSesionesPorLibro(int libroId) {
        return service.obtenerSesionesPorLibro(libroId);
    }

    public static List<Sesion> obtenerTodasLasSesionesDesde(String timestamp) {
        return service.obtenerTodasLasSesionesDesde(timestamp);
    }

    public static boolean eliminarSesion(int sessionId) {
        return service.eliminarSesion(sessionId);
    }

    public static boolean insertarSesionManual(int libroId, String fecha, String cap, int ini, int fin,
                                               int pags, double mins, double ppm, double pph) {
        return service.insertarSesionManual(libroId, fecha, cap, ini, fin, pags, mins, ppm, pph);
    }

    public static boolean actualizarSesionCompleta(int id, int ini, int fin, int pags, double mins, double ppm,
                                                   double pph, String cap) {
        return service.actualizarSesionCompleta(id, ini, fin, pags, mins, ppm, pph, cap);
    }

    public static DatabaseService getService() {
        return service;
    }

    public static int obtenerUltimaPagina(int libroId) {
        return service.obtenerUltimaPaginaLeida(libroId);
    }

    public static List<DataPoint> obtenerDatosGrafica(String column, int libroId, int minPag, boolean agruparPorDia,
                                                      boolean esHeatmap, boolean esDual) {
        return service.obtenerDatosGrafica(column, libroId, minPag, agruparPorDia, esHeatmap, esDual);
    }

    public static List<String[]> obtenerDatosParaExportar(int libroId, int minPag, String fFiltro, boolean agrupar) {
        return service.obtenerDatosParaExportar(libroId, minPag, fFiltro, agrupar);
    }

    public static boolean eliminarLibro(int libroId) {
        return service.eliminarLibro(libroId);
    }

    public static List<DataPoint> obtenerPpmMediaPorLibroTerminado() {
        return service.obtenerPpmMediaPorLibroTerminado();
    }

    /** Devuelve true si hay sesiones o libros pendientes de sincronizar. */
    public static boolean haySincronizacionPendiente() {
        if (service == null) return false;
        return service.haySincronizacionPendiente();
    }
}