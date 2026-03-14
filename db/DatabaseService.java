package db;

import model.DataPoint;
import model.Sesion;
import java.util.List;
import java.sql.Connection;

public interface DatabaseService {
        void conectar() throws Exception;

        void crearEsquema() throws Exception;

        void registrarUsuario(String email);

        void limpiarDatosDeOtrosUsuarios(String currentUserId);

        Connection getConnection();

        void guardarLibro(String nombre, int paginas);

        void actualizarPaginasTotales(int libroId, int nuevasPaginas);

        void guardarCoverUrl(int libroId, String url);

        String obtenerCoverUrl(int libroId);

        void guardarSesion(int libroId, String cap, int pIni, int pFin, int pags, double mins, double ppm,
                        double pph, String fecha);

        int obtenerLibroId(String nombre);

        void actualizarEstadoLibro(int libroId, String estado);

        String obtenerEstadoLibro(int libroId);

        List<String> obtenerTodosLosLibros();

        int obtenerUltimaPaginaLeida(int libroId);

        boolean actualizarSesionCompleta(int id, int ini, int fin, int pags, double mins, double ppm, double pph,
                        String cap);

        // Estadísticas
        int obtenerPaginasTotales(int libroId);

        double obtenerPromedioPPH(int libroId);

        double obtenerPromedioPPM(int libroId);

        double obtenerVelocidadMaxima(int libroId);

        double obtenerSesionMasLarga(int libroId);

        String obtenerDiaMasLectura(int libroId);

        double obtenerPorcentajeProgreso(int libroId);

        int obtenerRachaActual();

        int obtenerPaginasLeidasHoy();

        List<Sesion> obtenerSesionesPorLibro(int libroId);

        boolean eliminarSesion(int sessionId);

        boolean eliminarSesionPorUuid(String uuid);

        boolean insertarSesionManual(int libroId, String fecha, String cap, int ini, int fin, int pags,
                        double mins, double ppm, double pph);

        boolean insertarSesionManualConUuid(int lId, String fecha, String cap, int ini, int fin, int pags, double mins,
                        double ppm, double pph, String uuid);

        // Nuevo método para abstraer SQL de gráficas
        List<DataPoint> obtenerDatosGrafica(String column, int libroId, int minPag, boolean agruparPorDia,
                        boolean esHeatmap,
                        boolean esDual);

        // Nuevo método para abstraer SQL de exportación
        List<String[]> obtenerDatosParaExportar(int libroId, int minPag, String fFiltro, boolean agrupar);

        // Nuevo método para sincronización masiva (Local-First)
        void sincronizarConNube();

        List<Sesion> obtenerTodasLasSesionesDesde(String timestamp);

        List<model.Libro> obtenerTodosLosLibrosDesde(String timestamp);

        /**
         * Elimina un libro y todas sus sesiones (local y remoto).
         * 
         * @param libroId ID del libro a eliminar
         * @return true si se eliminó correctamente
         */
        boolean eliminarLibro(int libroId);

        List<DataPoint> obtenerPpmMediaPorLibroTerminado();

}
