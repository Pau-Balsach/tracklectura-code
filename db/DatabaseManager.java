package db;

import model.DataPoint;
import model.Sesion;
import java.sql.Connection;
import java.util.List;

/**
 * Gestor central de la base de datos.
 * Conecta con Supabase a través de PostgresDatabaseService.
 */
public class DatabaseManager {
    private static SQLiteSyncService service;

    static {
        inicializar();
    }

    public static void inicializar() {
        try {
            service = new SQLiteSyncService();
            service.conectar();
            service.crearEsquema();
        } catch (Exception e) {
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null,
                    "Error al conectar con la base de datos:\n" + e.getMessage(),
                    "Error Crítico de Base de Datos",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void cerrarYSincronizar() {
        if (service != null) {
            System.out.println("Sincronizando antes de cerrar...");
            service.sincronizarConNube();
            if (service instanceof SQLiteSyncService) {
                ((SQLiteSyncService) service).cerrar();
            }
        }
    }

    public static Connection getConnection() {
        return service.getConnection();
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
}