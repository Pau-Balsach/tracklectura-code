package db;

import model.DataPoint;
import model.Sesion;
import java.sql.*;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SQLiteSyncService implements DatabaseService {
    private final PostgresDatabaseService remote;
    private Connection conn;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean syncPending = new AtomicBoolean(false);
    private boolean isOffline = false;

    private static final String DB_FILE = new java.io.File(utils.ConfigManager.getAppDataDirectory(),
            "tracklectura_local.db").getAbsolutePath();

    public SQLiteSyncService() {
        this.remote = new PostgresDatabaseService();
    }

    private String getLocalUserId() {
        if (utils.ConfigManager.isOfflineMode())
            return "guest";
        String id = SupabaseAuthService.getCurrentUserId();
        if (id == null || id.isEmpty()) {
            return "unknown";
        }
        return id;
    }

    @Override
    public void conectar() throws Exception {
        if (!utils.ConfigManager.isOfflineMode()) {
            try {
                remote.conectar();
            } catch (Exception e) {
                // MEJORA: No fallar silenciosamente — loguear y continuar en modo degradado
                System.err.println("[SQLiteSyncService] No se pudo conectar al remoto: " + e.getMessage()
                        + " — continuando en modo local.");
            }
        } else {
            isOffline = true;
        }

        java.io.File dbFile = new java.io.File(DB_FILE);
        java.io.File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String url = "jdbc:sqlite:" + DB_FILE;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver not found: " + e.getMessage());
        }
        conn = DriverManager.getConnection(url);

        retryScheduler.scheduleWithFixedDelay(() -> {
            if (!utils.ConfigManager.isOfflineMode() && syncPending.get()) {
                System.out.println("🔄 Reintentando sincronización de pendientes...");
                sincronizarPendientes();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void crearEsquema() throws Exception {
        remote.crearEsquema();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS libros (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT, " +
                    "nombre TEXT, " +
                    "paginas_totales INTEGER, " +
                    "cover_url TEXT, " +
                    "estado TEXT DEFAULT 'Por leer', " +
                    "dirty INTEGER DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS sesiones (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT UNIQUE, " +
                    "user_id TEXT, " +
                    "libro_id INTEGER, " +
                    "capitulo TEXT, " +
                    "pag_inicio INTEGER, " +
                    "pag_fin INTEGER, " +
                    "paginas_leidas INTEGER, " +
                    "minutos REAL, " +
                    "ppm REAL, " +
                    "pph REAL, " +
                    "fecha TEXT, " +
                    "dirty INTEGER DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS deleted_sesiones (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "user_id TEXT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS deleted_libros (" +
                    "libro_id INTEGER PRIMARY KEY, " +
                    "user_id TEXT)");

            // MEJORA: Índices explícitos para consultas frecuentes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sesiones_libro_user " +
                    "ON sesiones(libro_id, user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sesiones_user_dirty " +
                    "ON sesiones(user_id, dirty)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sesiones_fecha " +
                    "ON sesiones(fecha)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_libros_user " +
                    "ON libros(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_libros_user_dirty " +
                    "ON libros(user_id, dirty)");

            // --- MIGRACIÓN PARA 'libros' ---
            boolean needsLibrosMigration = false;
            ResultSet rsCheckLibros = conn.getMetaData().getColumns(null, null, "libros", "id");
            if (rsCheckLibros.next()) {
                try (ResultSet rsNulls = stmt.executeQuery("SELECT COUNT(*) FROM libros WHERE id IS NULL")) {
                    if (rsNulls.next() && rsNulls.getInt(1) > 0) {
                        needsLibrosMigration = true;
                    }
                }
            }

            if (needsLibrosMigration) {
                System.out.println("Migrando tabla 'libros' para corregir IDs nulos...");
                stmt.execute("DROP TABLE IF EXISTS libros_new");
                stmt.execute("CREATE TABLE libros_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "user_id TEXT, " +
                        "nombre TEXT, " +
                        "paginas_totales INTEGER, " +
                        "cover_url TEXT, " +
                        "estado TEXT DEFAULT 'Por leer', " +
                        "dirty INTEGER DEFAULT 0)");
                stmt.execute("INSERT INTO libros_new (id, user_id, nombre, paginas_totales, cover_url, estado, dirty) "
                        +
                        "SELECT id, user_id, nombre, paginas_totales, cover_url, estado, dirty FROM libros WHERE id IS NOT NULL");
                stmt.execute("INSERT INTO libros_new (user_id, nombre, paginas_totales, cover_url, estado, dirty) " +
                        "SELECT user_id, nombre, paginas_totales, cover_url, estado, dirty FROM libros WHERE id IS NULL");
                stmt.execute("DROP TABLE libros");
                stmt.execute("ALTER TABLE libros_new RENAME TO libros");
                System.out.println("Migración de 'libros' completada.");
            }

            // --- MIGRACIÓN PARA 'sesiones' ---
            boolean needsSesionesMigration = false;
            ResultSet rsCheckSesiones = conn.getMetaData().getColumns(null, null, "sesiones", "id");
            if (rsCheckSesiones.next()) {
                try (ResultSet rsNulls = stmt.executeQuery("SELECT COUNT(*) FROM sesiones WHERE id IS NULL")) {
                    if (rsNulls.next() && rsNulls.getInt(1) > 0) {
                        needsSesionesMigration = true;
                    }
                }
            }

            if (needsSesionesMigration) {
                System.out.println("Migrando tabla 'sesiones' para corregir IDs nulos...");
                stmt.execute("DROP TABLE IF EXISTS sesiones_new");
                stmt.execute("CREATE TABLE sesiones_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "uuid TEXT UNIQUE, " +
                        "user_id TEXT, " +
                        "libro_id INTEGER, " +
                        "capitulo TEXT, " +
                        "pag_inicio INTEGER, " +
                        "pag_fin INTEGER, " +
                        "paginas_leidas INTEGER, " +
                        "minutos REAL, " +
                        "ppm REAL, " +
                        "pph REAL, " +
                        "fecha TEXT, " +
                        "dirty INTEGER DEFAULT 0)");
                stmt.execute(
                        "INSERT INTO sesiones_new (id, user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, dirty) "
                                +
                                "SELECT id, user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, dirty FROM sesiones WHERE id IS NOT NULL");
                stmt.execute(
                        "INSERT INTO sesiones_new (user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, dirty) "
                                +
                                "SELECT user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, dirty FROM sesiones WHERE id IS NULL");
                stmt.execute("DROP TABLE sesiones");
                stmt.execute("ALTER TABLE sesiones_new RENAME TO sesiones");
                System.out.println("Migración de 'sesiones' completada.");
            }
        }

        try (Statement stmt = conn.createStatement()) {
            try { stmt.execute("ALTER TABLE sesiones ADD COLUMN uuid TEXT"); } catch (Exception ignored) { }
            try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sesiones_uuid ON sesiones(uuid)"); } catch (Exception ignored) { }
            try { stmt.execute("ALTER TABLE sesiones ADD COLUMN sincronizado INTEGER DEFAULT 0"); } catch (Exception ignored) { }
            try { stmt.execute("ALTER TABLE libros ADD COLUMN sincronizado INTEGER DEFAULT 0"); } catch (Exception ignored) { }
        }

        sincronizarConNube();
        repararIDsCorruptos();
    }

    @Override
    public void registrarUsuario(String email) {
        if (!utils.ConfigManager.isOfflineMode()) {
            syncExecutor.submit(() -> remote.registrarUsuario(email));
        }
    }

    @Override
    public void limpiarDatosDeOtrosUsuarios(String currentUserId) {
        if (currentUserId == null || currentUserId.isEmpty() || currentUserId.equals("unknown")) {
            return;
        }
        try {
            try (PreparedStatement ps = conn
                    .prepareStatement("UPDATE libros SET user_id = ? WHERE user_id = 'unknown'")) {
                ps.setString(1, currentUserId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn
                    .prepareStatement("UPDATE sesiones SET user_id = ? WHERE user_id = 'unknown'")) {
                ps.setString(1, currentUserId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn
                    .prepareStatement(
                            "DELETE FROM libros WHERE user_id != ? AND user_id != 'guest' AND user_id != 'unknown'")) {
                ps.setString(1, currentUserId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps2 = conn
                    .prepareStatement(
                            "DELETE FROM sesiones WHERE user_id != ? AND user_id != 'guest' AND user_id != 'unknown'")) {
                ps2.setString(1, currentUserId);
                ps2.executeUpdate();
            }
            System.out.println("✅ Base de datos local preparada para el usuario: " + currentUserId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public void guardarLibro(String nombre, int paginas) {
        try (PreparedStatement ps = conn
                .prepareStatement(
                        "INSERT INTO libros(user_id, nombre, paginas_totales, sincronizado, dirty) VALUES(?,?,?,0,1)")) {
            ps.setString(1, getLocalUserId());
            ps.setString(2, nombre);
            ps.setInt(3, paginas);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        syncPending.set(true);
        syncExecutor.execute(this::sincronizarPendientes);
    }

    @Override
    public void actualizarPaginasTotales(int libroId, int nuevasP) {
        try (PreparedStatement ps = conn
                .prepareStatement(
                        "UPDATE libros SET paginas_totales=?, sincronizado=0, dirty=1 WHERE id=? AND user_id=?")) {
            ps.setInt(1, nuevasP);
            ps.setInt(2, libroId);
            ps.setString(3, getLocalUserId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        syncExecutor.execute(this::sincronizarPendientes);
    }

    @Override
    public void guardarCoverUrl(int libroId, String url) {
        try (PreparedStatement ps = conn
                .prepareStatement("UPDATE libros SET cover_url=?, sincronizado=0, dirty=1 WHERE id=? AND user_id=?")) {
            ps.setString(1, url);
            ps.setInt(2, libroId);
            ps.setString(3, getLocalUserId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        syncExecutor.execute(this::sincronizarPendientes);
    }

    @Override
    public String obtenerCoverUrl(int libroId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT cover_url FROM libros WHERE id=? AND user_id=?")) {
            ps.setInt(1, libroId);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String url = rs.getString("cover_url");
                if (url != null && !url.isEmpty())
                    return url;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!utils.ConfigManager.isOfflineMode())
            return remote.obtenerCoverUrl(libroId);
        return null;
    }

    @Override
    public void guardarSesion(int lId, String cap, int ini, int fin, int pags, double m, double ppm, double pph,
                              String fecha) {
        String uuid = java.util.UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sesiones(user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, uuid, sincronizado, dirty) VALUES(?,?,?,?,?,?,?,?,?,?,?,0,1)")) {
            ps.setString(1, getLocalUserId());
            ps.setInt(2, lId);
            ps.setString(3, cap);
            ps.setInt(4, ini);
            ps.setInt(5, fin);
            ps.setInt(6, pags);
            ps.setDouble(7, m);
            ps.setDouble(8, ppm);
            ps.setDouble(9, pph);
            ps.setString(10, fecha);
            ps.setString(11, uuid);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        syncPending.set(true);
        syncExecutor.execute(this::sincronizarPendientes);
    }

    @Override
    public int obtenerLibroId(String nombre) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM libros WHERE nombre=? AND user_id=?")) {
            ps.setString(1, nombre);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getInt("id");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return remote.obtenerLibroId(nombre);
    }

    @Override
    public void actualizarEstadoLibro(int libroId, String estado) {
        try (PreparedStatement ps = conn
                .prepareStatement("UPDATE libros SET estado=?, sincronizado=0, dirty=1 WHERE id=? AND user_id=?")) {
            ps.setString(1, estado);
            ps.setInt(2, libroId);
            ps.setString(3, getLocalUserId());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        syncExecutor.execute(this::sincronizarPendientes);
    }

    @Override
    public String obtenerEstadoLibro(int libroId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT estado FROM libros WHERE id=? AND user_id=?")) {
            ps.setInt(1, libroId);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String st = rs.getString("estado");
                if (st != null && !st.isEmpty())
                    return st;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!utils.ConfigManager.isOfflineMode())
            return remote.obtenerEstadoLibro(libroId);
        return "Por leer";
    }

    @Override
    public List<String> obtenerTodosLosLibros() {
        List<String> res = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT nombre FROM libros WHERE user_id=?")) {
            ps.setString(1, getLocalUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    res.add(rs.getString("nombre"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (res.isEmpty() && !isOffline)
            return remote.obtenerTodosLosLibros();
        return res;
    }

    @Override
    public int obtenerUltimaPaginaLeida(int lId) {
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT MAX(pag_fin) as m FROM sesiones WHERE libro_id=? AND user_id=?")) {
            ps.setInt(1, lId);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int max = rs.getInt("m");
                if (max > 0)
                    return max;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!utils.ConfigManager.isOfflineMode())
            return remote.obtenerUltimaPaginaLeida(lId);
        return 0;
    }

    @Override
    public boolean actualizarSesionCompleta(int id, int ini, int fin, int pags, double mins, double ppm, double pph,
                                            String cap) {
        String uid = getLocalUserId();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sesiones SET pag_inicio=?, pag_fin=?, paginas_leidas=?, minutos=?, ppm=?, pph=?, capitulo=?, sincronizado=0, dirty=1 WHERE id=? AND user_id=?")) {
            ps.setInt(1, ini);
            ps.setInt(2, fin);
            ps.setInt(3, pags);
            ps.setDouble(4, mins);
            ps.setDouble(5, ppm);
            ps.setDouble(6, pph);
            ps.setString(7, cap);
            ps.setInt(8, id);
            ps.setString(9, uid);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                syncExecutor.execute(this::sincronizarPendientes);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public int obtenerPaginasTotales(int libroId) {
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT paginas_totales FROM libros WHERE id=? AND user_id=?")) {
            ps.setInt(1, libroId);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int tot = rs.getInt(1);
                if (tot > 0)
                    return tot;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!utils.ConfigManager.isOfflineMode())
            return remote.obtenerPaginasTotales(libroId);
        return 0;
    }

    @Override
    public List<Sesion> obtenerSesionesPorLibro(int lId) {
        List<Sesion> list = new ArrayList<>();
        try (PreparedStatement ps = conn
                .prepareStatement("SELECT * FROM sesiones WHERE libro_id=? AND user_id=? ORDER BY id DESC")) {
            ps.setInt(1, lId);
            ps.setString(2, getLocalUserId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Sesion s = new Sesion(rs.getInt("id"), rs.getString("uuid"), rs.getInt("libro_id"),
                        rs.getString("fecha"), rs.getString("capitulo"),
                        rs.getInt("pag_inicio"), rs.getInt("pag_fin"), rs.getInt("paginas_leidas"),
                        rs.getDouble("minutos"), rs.getDouble("ppm"), rs.getDouble("pph"));
                list.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (list.isEmpty() && !isOffline)
            return remote.obtenerSesionesPorLibro(lId);
        return list;
    }

    public List<Sesion> obtenerTodasLasSesiones() {
        List<Sesion> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM sesiones WHERE user_id=? ORDER BY id DESC")) {
            ps.setString(1, getLocalUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sesion ses = new Sesion(rs.getInt("id"), rs.getString("uuid"), rs.getInt("libro_id"),
                            rs.getString("fecha"), rs.getString("capitulo"),
                            rs.getInt("pag_inicio"), rs.getInt("pag_fin"), rs.getInt("paginas_leidas"),
                            rs.getDouble("minutos"), rs.getDouble("ppm"), rs.getDouble("pph"));
                    list.add(ses);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean eliminarSesion(int sId) {
        String uuid = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM sesiones WHERE id=?")) {
            ps.setInt(1, sId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) uuid = rs.getString("uuid");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sesiones WHERE id=? AND user_id=?")) {
            ps.setInt(1, sId);
            ps.setString(2, getLocalUserId());
            boolean ok = ps.executeUpdate() > 0;
            if (ok && uuid != null && !utils.ConfigManager.isOfflineMode()) {
                final String finalUuid = uuid;
                syncExecutor.submit(() -> {
                    if (!remote.eliminarSesionPorUuid(finalUuid)) {
                        try (PreparedStatement psDel = conn.prepareStatement(
                                "INSERT OR IGNORE INTO deleted_sesiones(uuid, user_id) VALUES(?,?)")) {
                            psDel.setString(1, finalUuid);
                            psDel.setString(2, getLocalUserId());
                            psDel.executeUpdate();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean eliminarSesionPorUuid(String uuid) {
        if (!utils.ConfigManager.isOfflineMode()) {
            syncExecutor.submit(() -> remote.eliminarSesionPorUuid(uuid));
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sesiones WHERE uuid=? AND user_id=?")) {
            ps.setString(1, uuid);
            ps.setString(2, getLocalUserId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean insertarSesionManual(int lId, String fecha, String cap, int ini, int fin, int pags, double mins,
                                        double ppm, double pph) {
        String uuid = java.util.UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sesiones(user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, uuid, sincronizado, dirty) VALUES(?,?,?,?,?,?,?,?,?,?,?,0,1)")) {
            ps.setString(1, getLocalUserId());
            ps.setInt(2, lId);
            ps.setString(3, cap);
            ps.setInt(4, ini);
            ps.setInt(5, fin);
            ps.setInt(6, pags);
            ps.setDouble(7, mins);
            ps.setDouble(8, ppm);
            ps.setDouble(9, pph);
            ps.setString(10, fecha);
            ps.setString(11, uuid);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) syncExecutor.execute(this::sincronizarPendientes);
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public double obtenerPromedioPPH(int libroId) {
        double avg = obtenerSesionesPorLibro(libroId).stream().mapToDouble(Sesion::getPph).average().orElse(0.0);
        if (avg <= 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerPromedioPPH(libroId);
        return avg;
    }

    @Override
    public double obtenerPromedioPPM(int libroId) {
        double avg = obtenerSesionesPorLibro(libroId).stream().mapToDouble(Sesion::getPpm).average().orElse(0.0);
        if (avg <= 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerPromedioPPM(libroId);
        return avg;
    }

    @Override
    public double obtenerVelocidadMaxima(int libroId) {
        double max = obtenerSesionesPorLibro(libroId).stream().mapToDouble(Sesion::getPpm).max().orElse(0.0);
        if (max <= 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerVelocidadMaxima(libroId);
        return max;
    }

    @Override
    public double obtenerSesionMasLarga(int libroId) {
        double max = obtenerSesionesPorLibro(libroId).stream().mapToDouble(Sesion::getMinutos).max().orElse(0.0);
        if (max <= 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerSesionMasLarga(libroId);
        return max;
    }

    private String normalizeDate(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return "N/A";
        String f = fechaStr;
        if (f.contains(" ")) f = f.split(" ")[0];
        else if (f.contains("T")) f = f.split("T")[0];

        DateTimeFormatter fmtApp = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtIso = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try { return LocalDate.parse(f, fmtApp).format(fmtApp); } catch (Exception e1) { }
        try { return LocalDate.parse(f, fmtIso).format(fmtApp); } catch (Exception e2) { }
        try { return LocalDate.parse(f, DateTimeFormatter.ofPattern("M/d/yyyy")).format(fmtApp); } catch (Exception e3) { }
        return f;
    }

    @Override
    public String obtenerDiaMasLectura(int libroId) {
        if (utils.ConfigManager.isOfflineMode()) return "N/D (Modo Offline)";
        return remote.obtenerDiaMasLectura(libroId);
    }

    @Override
    public double obtenerPorcentajeProgreso(int libroId) {
        int tot = obtenerPaginasTotales(libroId);
        if (tot == 0) return 0;
        return (obtenerUltimaPaginaLeida(libroId) / (double) tot) * 100;
    }

    @Override
    public int obtenerRachaActual() {
        try {
            List<Sesion> todas = obtenerTodasLasSesiones();
            int racha = 0;
            if (!todas.isEmpty()) {
                List<LocalDate> fechas = new ArrayList<>();
                DateTimeFormatter fmtApp = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                for (Sesion s : todas) {
                    if (s.getFecha() == null || s.getFecha().isEmpty()) continue;
                    String f = normalizeDate(s.getFecha());
                    if (f.equals("N/A")) continue;
                    try { fechas.add(LocalDate.parse(f, fmtApp)); } catch (Exception ignored) { }
                }
                if (!fechas.isEmpty()) {
                    LocalDate hoy = LocalDate.now();
                    List<LocalDate> unicas = fechas.stream().distinct().filter(d -> !d.isAfter(hoy))
                            .sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList());
                    if (!unicas.isEmpty() && (unicas.get(0).equals(hoy) || unicas.get(0).equals(hoy.minusDays(1)))) {
                        racha = 1;
                        for (int i = 0; i < unicas.size() - 1; i++) {
                            if (unicas.get(i).minusDays(1).equals(unicas.get(i + 1))) racha++;
                            else break;
                        }
                    }
                }
            }
            if (racha == 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerRachaActual();
            return racha;
        } catch (Exception e) {
            if (!utils.ConfigManager.isOfflineMode()) return remote.obtenerRachaActual();
            return 0;
        }
    }

    @Override
    public int obtenerPaginasLeidasHoy() {
        String hoyNormalizado = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        int total = 0;
        try {
            for (Sesion s : obtenerTodasLasSesiones()) {
                if (s.getFecha() == null || s.getFecha().isEmpty()) continue;
                if (normalizeDate(s.getFecha()).equals(hoyNormalizado)) total += s.getPaginasLeidas();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (total == 0 && !utils.ConfigManager.isOfflineMode()) return remote.obtenerPaginasLeidasHoy();
        return total;
    }

    @Override
    public List<DataPoint> obtenerDatosGrafica(String column, int libroId, int minPag, boolean agruparPorDia,
                                               boolean esHeatmap, boolean esDual) {
        if (!utils.ConfigManager.isOfflineMode() && !isOffline)
            return remote.obtenerDatosGrafica(column, libroId, minPag, agruparPorDia, esHeatmap, esDual);
        List<Sesion> lista = esHeatmap ? obtenerTodasLasSesiones() : obtenerSesionesPorLibro(libroId);
        if (!esHeatmap)
            lista = lista.stream().filter(s -> s.getPaginaInicio() >= minPag).collect(Collectors.toList());
        Collections.reverse(lista);

        List<DataPoint> result = new ArrayList<>();
        if (agruparPorDia || esHeatmap) {
            Map<String, List<Sesion>> porDia = new LinkedHashMap<>();
            for (Sesion s : lista) {
                String dia = (s.getFecha() != null && s.getFecha().length() >= 5) ? normalizeDate(s.getFecha()) : "N/A";
                porDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(s);
            }
            for (Map.Entry<String, List<Sesion>> entry : porDia.entrySet()) {
                String fecha = entry.getKey();
                List<Sesion> diaList = entry.getValue();
                String capStr = diaList.stream().map(Sesion::getCapitulo).filter(c -> c != null && !c.isEmpty())
                        .collect(Collectors.joining(";"));
                double val = 0, valSec = 0;
                if (column.equals("paginas")) val = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum();
                else if (column.equals("pag_fin")) val = diaList.stream().mapToDouble(Sesion::getPaginaFin).max().orElse(0);
                else if (column.equals("ppm")) { val = diaList.stream().mapToDouble(Sesion::getPpm).average().orElse(0); valSec = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum(); }
                else if (column.equals("pph")) val = diaList.stream().mapToDouble(Sesion::getPph).average().orElse(0);
                else if (column.equals("minutos")) val = diaList.stream().mapToDouble(Sesion::getMinutos).sum();
                if (esDual) { val = diaList.stream().mapToDouble(Sesion::getPaginasLeidas).sum(); valSec = diaList.stream().mapToDouble(Sesion::getMinutos).sum(); }
                result.add(new DataPoint(fecha, val, valSec, capStr));
            }
        } else {
            for (Sesion s : lista) {
                double val = 0, valSec = 0;
                if (column.equals("paginas")) val = s.getPaginasLeidas();
                else if (column.equals("pag_fin")) val = s.getPaginaFin();
                else if (column.equals("ppm")) { val = s.getPpm(); valSec = s.getPaginasLeidas(); }
                else if (column.equals("pph")) val = s.getPph();
                else if (column.equals("minutos")) val = s.getMinutos();
                if (esDual) { val = s.getPaginasLeidas(); valSec = s.getMinutos(); }
                result.add(new DataPoint(s.getFecha(), val, valSec, s.getCapitulo()));
            }
        }
        return result;
    }

    @Override
    public List<String[]> obtenerDatosParaExportar(int libroId, int minPag, String fFiltro, boolean agrupar) {
        if (!utils.ConfigManager.isOfflineMode() && !isOffline)
            return remote.obtenerDatosParaExportar(libroId, minPag, fFiltro, agrupar);
        return new ArrayList<>();
    }

    @Override
    public void sincronizarConNube() {
        if (utils.ConfigManager.isOfflineMode()) return;
        sincronizarPendientes();
        descargarDeNube();
        utils.ConfigManager.setLastSyncTimestamp(java.time.Instant.now().toString());
    }

    private void sincronizarPendientes() {
        if (utils.ConfigManager.isOfflineMode()) return;
        boolean hayFallos = false;
        try {
            String uid = getLocalUserId();
            try (PreparedStatement st = conn
                    .prepareStatement("SELECT * FROM libros WHERE (sincronizado=0 OR dirty=1) AND user_id=?")) {
                st.setString(1, uid);
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int paginas = rs.getInt("paginas_totales");
                    int isSync = rs.getInt("sincronizado");
                    int localId = rs.getInt("id");
                    int rid = -1;
                    try {
                        if (isSync == 0) {
                            remote.guardarLibro(nombre, paginas);
                            rid = remote.obtenerLibroId(nombre);
                        } else {
                            rid = localId;
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Error al sincronizar libro '" + nombre + "': " + e.getMessage());
                        hayFallos = true;
                        continue;
                    }
                    if (rid == -1) { hayFallos = true; continue; }
                    try {
                        remote.actualizarEstadoLibro(rid, rs.getString("estado"));
                        remote.actualizarPaginasTotales(rid, paginas);
                        String cv = rs.getString("cover_url");
                        if (cv != null && !cv.isEmpty()) remote.guardarCoverUrl(rid, cv);
                    } catch (Exception e) { hayFallos = true; continue; }

                    int oldId = rs.getInt("id");
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE libros SET id=?, sincronizado=1, dirty=0 WHERE id=?")) {
                        upd.setInt(1, rid); upd.setInt(2, oldId); upd.executeUpdate();
                    }
                    try (PreparedStatement updS = conn.prepareStatement(
                            "UPDATE sesiones SET libro_id=? WHERE libro_id=?")) {
                        updS.setInt(1, rid); updS.setInt(2, oldId); updS.executeUpdate();
                    }
                }
            }

            try (PreparedStatement st = conn
                    .prepareStatement("SELECT * FROM sesiones WHERE (sincronizado=0 OR dirty=1) AND user_id=?")) {
                st.setString(1, uid);
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    int libroId = rs.getInt("libro_id");
                    boolean libroSincronizado;
                    try (PreparedStatement chkLibro = conn.prepareStatement(
                            "SELECT sincronizado FROM libros WHERE id=? AND user_id=?")) {
                        chkLibro.setInt(1, libroId);
                        chkLibro.setString(2, uid);
                        ResultSet rsLibro = chkLibro.executeQuery();
                        libroSincronizado = rsLibro.next() && rsLibro.getInt("sincronizado") == 1;
                    }
                    if (!libroSincronizado) { hayFallos = true; continue; }

                    boolean ok = false;
                    try {
                        ok = remote.insertarSesionManualConUuid(
                                rs.getInt("libro_id"), rs.getString("fecha"), rs.getString("capitulo"),
                                rs.getInt("pag_inicio"), rs.getInt("pag_fin"), rs.getInt("paginas_leidas"),
                                rs.getDouble("minutos"), rs.getDouble("ppm"), rs.getDouble("pph"),
                                rs.getString("uuid"));
                    } catch (Exception e) { hayFallos = true; continue; }

                    if (ok) {
                        try (PreparedStatement upd = conn
                                .prepareStatement("UPDATE sesiones SET sincronizado=1, dirty=0 WHERE id=?")) {
                            upd.setInt(1, rs.getInt("id")); upd.executeUpdate();
                        }
                    } else {
                        hayFallos = true;
                    }
                }
            }

            try (PreparedStatement st = conn.prepareStatement("SELECT * FROM deleted_libros WHERE user_id=?")) {
                st.setString(1, uid);
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    int libroId = rs.getInt("libro_id");
                    try {
                        if (remote.eliminarLibro(libroId)) {
                            try (PreparedStatement del = conn.prepareStatement("DELETE FROM deleted_libros WHERE libro_id=?")) {
                                del.setInt(1, libroId); del.executeUpdate();
                            }
                        } else { hayFallos = true; }
                    } catch (Exception e) { hayFallos = true; }
                }
            }

            try (PreparedStatement st = conn.prepareStatement("SELECT * FROM deleted_sesiones WHERE user_id=?")) {
                st.setString(1, uid);
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    try {
                        if (remote.eliminarSesionPorUuid(uuid)) {
                            try (PreparedStatement del = conn.prepareStatement("DELETE FROM deleted_sesiones WHERE uuid=?")) {
                                del.setString(1, uuid); del.executeUpdate();
                            }
                        } else { hayFallos = true; }
                    } catch (Exception e) { hayFallos = true; }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            hayFallos = true;
        }
        syncPending.set(hayFallos);
        if (!hayFallos) System.out.println("✅ Sincronización completada sin pendientes.");
    }

    @Override
    public List<Sesion> obtenerTodasLasSesionesDesde(String timestamp) { return new ArrayList<>(); }

    @Override
    public List<model.Libro> obtenerTodosLosLibrosDesde(String timestamp) { return new ArrayList<>(); }

    private void descargarDeNube() {
        if (utils.ConfigManager.isOfflineMode()) return;
        try {
            String uid = getLocalUserId();
            String lastSync = utils.ConfigManager.getLastSyncTimestamp();

            try (PreparedStatement countSt = conn.prepareStatement("SELECT count(*) FROM sesiones WHERE user_id=?")) {
                countSt.setString(1, uid);
                ResultSet rsCount = countSt.executeQuery();
                if (rsCount.next() && rsCount.getInt(1) == 0) lastSync = null;
            }

            List<model.Libro> remoteBooks = remote.obtenerTodosLosLibrosDesde(lastSync);
            conn.setAutoCommit(false);

            for (model.Libro b : remoteBooks) {
                try (PreparedStatement check = conn.prepareStatement("SELECT id FROM libros WHERE id=? AND user_id=?")) {
                    check.setInt(1, b.getId());
                    check.setString(2, getLocalUserId());
                    if (!check.executeQuery().next()) {
                        try (PreparedStatement in = conn.prepareStatement(
                                "INSERT INTO libros(id, user_id, nombre, paginas_totales, cover_url, estado, sincronizado) VALUES(?,?,?,?,?,?,1)")) {
                            in.setInt(1, b.getId()); in.setString(2, getLocalUserId());
                            in.setString(3, b.getNombre()); in.setInt(4, b.getPaginasTotales());
                            in.setString(5, b.getCoverUrl()); in.setString(6, b.getEstado());
                            in.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE libros SET nombre=?, paginas_totales=?, cover_url=?, estado=?, dirty=0 WHERE id=? AND user_id=?")) {
                            upd.setString(1, b.getNombre()); upd.setInt(2, b.getPaginasTotales());
                            upd.setString(3, b.getCoverUrl()); upd.setString(4, b.getEstado());
                            upd.setInt(5, b.getId()); upd.setString(6, getLocalUserId());
                            upd.executeUpdate();
                        }
                    }
                }
            }

            List<Sesion> remoteSesList = remote.obtenerTodasLasSesionesDesde(lastSync);
            if (!remoteSesList.isEmpty()) {
                for (Sesion s : remoteSesList) {
                    int remoteId = s.getId();
                    String uuid = s.getUuid();
                    try (PreparedStatement pst = conn.prepareStatement("SELECT id FROM sesiones WHERE uuid=?")) {
                        pst.setString(1, uuid);
                        ResultSet rs = pst.executeQuery();
                        if (rs.next()) {
                            int localId = rs.getInt("id");
                            if (localId != remoteId) {
                                try (PreparedStatement upd = conn.prepareStatement("UPDATE sesiones SET id=? WHERE uuid=?")) {
                                    upd.setInt(1, remoteId); upd.setString(2, uuid); upd.executeUpdate();
                                }
                            }
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO sesiones(uuid, user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, sincronizado, dirty, id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,?)")) {
                        ps.setString(1, uuid); ps.setString(2, getLocalUserId());
                        ps.setInt(3, s.getLibroId()); ps.setString(4, s.getCapitulo());
                        ps.setInt(5, s.getPaginaInicio()); ps.setInt(6, s.getPaginaFin());
                        ps.setInt(7, s.getPaginasLeidas()); ps.setDouble(8, s.getMinutos());
                        ps.setDouble(9, s.getPpm()); ps.setDouble(10, s.getPph());
                        ps.setString(11, s.getFecha()); ps.setInt(12, 1); ps.setInt(13, remoteId);
                        ps.executeUpdate();
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private void repararIDsCorruptos() {
        if (utils.ConfigManager.isOfflineMode()) return;
        System.out.println("🔍 Comprobando posibles IDs corruptos (-1)...");
        try {
            String uid = getLocalUserId();
            try (PreparedStatement check = conn.prepareStatement("SELECT id, nombre FROM libros WHERE id = -1 AND user_id = ?")) {
                check.setString(1, uid);
                ResultSet rs = check.executeQuery();
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int rid = remote.obtenerLibroId(nombre);
                    if (rid != -1) {
                        conn.setAutoCommit(false);
                        try {
                            try (PreparedStatement upd = conn.prepareStatement(
                                    "UPDATE libros SET id = ?, sincronizado = 1, dirty = 0 WHERE id = -1 AND nombre = ? AND user_id = ?")) {
                                upd.setInt(1, rid); upd.setString(2, nombre); upd.setString(3, uid); upd.executeUpdate();
                            }
                            try (PreparedStatement updS = conn.prepareStatement(
                                    "UPDATE sesiones SET libro_id = ? WHERE libro_id = -1 AND user_id = ?")) {
                                updS.setInt(1, rid); updS.setString(2, uid); updS.executeUpdate();
                            }
                            conn.commit();
                        } catch (SQLException e) {
                            conn.rollback(); e.printStackTrace();
                        } finally { conn.setAutoCommit(true); }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public boolean insertarSesionManualConUuid(int lId, String fecha, String cap, int ini, int fin, int pags,
                                               double mins, double ppm, double pph, String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO sesiones(uuid, user_id, libro_id, capitulo, pag_inicio, pag_fin, paginas_leidas, minutos, ppm, pph, fecha, sincronizado, dirty) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid); ps.setString(2, getLocalUserId()); ps.setInt(3, lId);
            ps.setString(4, cap); ps.setInt(5, ini); ps.setInt(6, fin); ps.setInt(7, pags);
            ps.setDouble(8, mins); ps.setDouble(9, ppm); ps.setDouble(10, pph); ps.setString(11, fecha);
            ps.setInt(12, 1); ps.setInt(13, 1);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) syncExecutor.execute(this::sincronizarPendientes);
            return ok;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    @Override
    public boolean eliminarLibro(int libroId) {
        String uid = getLocalUserId();
        try { conn.setAutoCommit(false); } catch (SQLException ex) { ex.printStackTrace(); return false; }
        try {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sesiones WHERE libro_id=? AND user_id=?")) {
                ps.setInt(1, libroId); ps.setString(2, uid); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM libros WHERE id=? AND user_id=?")) {
                ps.setInt(1, libroId); ps.setString(2, uid);
                if (ps.executeUpdate() == 0) { conn.rollback(); return false; }
            }
            conn.commit();
            if (utils.ConfigManager.isOfflineMode()) {
                try (PreparedStatement psDel = conn.prepareStatement("INSERT OR IGNORE INTO deleted_libros(libro_id, user_id) VALUES(?,?)")) {
                    psDel.setInt(1, libroId); psDel.setString(2, getLocalUserId()); psDel.executeUpdate();
                }
            } else {
                syncExecutor.submit(() -> {
                    if (!remote.eliminarLibro(libroId)) {
                        try (PreparedStatement psDel = conn.prepareStatement("INSERT OR IGNORE INTO deleted_libros(libro_id, user_id) VALUES(?,?)")) {
                            psDel.setInt(1, libroId); psDel.setString(2, getLocalUserId()); psDel.executeUpdate();
                        } catch (Exception ex) { ex.printStackTrace(); }
                    }
                });
            }
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public void cerrar() {
        retryScheduler.shutdown();
        syncExecutor.shutdown();
    }

    @Override
    public List<DataPoint> obtenerPpmMediaPorLibroTerminado() {
        List<DataPoint> resultado = new ArrayList<>();
        String uid = getLocalUserId();
        try {
            String sql = "SELECT l.nombre, AVG(s.ppm) as media_ppm " +
                    "FROM libros l JOIN sesiones s ON s.libro_id = l.id " +
                    "WHERE l.estado = 'Terminado' AND l.user_id = ? " +
                    "GROUP BY l.id, l.nombre ORDER BY media_ppm DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uid);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    resultado.add(new DataPoint(rs.getString("nombre"), rs.getDouble("media_ppm"), 0, ""));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return resultado;
    }

    /**
     * MEJORA: Comprueba si hay datos pendientes de sincronizar.
     * Permite al UI mostrar un indicador visual de sync pendiente.
     */
    public boolean haySincronizacionPendiente() {
        if (utils.ConfigManager.isOfflineMode()) return false;
        try {
            String uid = getLocalUserId();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM sesiones WHERE (dirty=1 OR sincronizado=0) AND user_id=?")) {
                ps.setString(1, uid);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM libros WHERE (dirty=1 OR sincronizado=0) AND user_id=?")) {
                ps.setString(1, uid);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM deleted_sesiones WHERE user_id=?")) {
                ps.setString(1, uid);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM deleted_libros WHERE user_id=?")) {
                ps.setString(1, uid);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }
}