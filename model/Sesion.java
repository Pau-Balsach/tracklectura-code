package model;

/** Modelo que representa una sesión de lectura individual. */
public class Sesion {
    private final int id;
    private final String uuid; // Identificador único para sincronización nube
    private final int libroId;
    private final String fecha;
    private final String capitulo;
    private final int paginaInicio;
    private final int paginaFin;
    private final int paginasLeidas;
    private final double minutos;
    private final double ppm;
    private final double pph;

    public Sesion(int id, String uuid, int libroId, String fecha, String capitulo,
            int paginaInicio, int paginaFin, int paginasLeidas,
            double minutos, double ppm, double pph) {
        this.id = id;
        this.uuid = uuid;
        this.libroId = libroId;
        this.fecha = fecha;
        this.capitulo = capitulo;
        this.paginaInicio = paginaInicio;
        this.paginaFin = paginaFin;
        this.paginasLeidas = paginasLeidas;
        this.minutos = minutos;
        this.ppm = ppm;
        this.pph = pph;
    }

    public int getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public int getLibroId() {
        return libroId;
    }

    public String getFecha() {
        return fecha;
    }

    public String getCapitulo() {
        return capitulo;
    }

    public int getPaginaInicio() {
        return paginaInicio;
    }

    public int getPaginaFin() {
        return paginaFin;
    }

    public int getPaginasLeidas() {
        return paginasLeidas;
    }

    public double getMinutos() {
        return minutos;
    }

    public double getPpm() {
        return ppm;
    }

    public double getPph() {
        return pph;
    }
}