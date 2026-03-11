package model;

/**
 * Modelo que representa un libro registrado en la aplicación.
 */
public class Libro {
    private final int id;
    private final String nombre;
    private final int paginas_totales;
    private final String cover_url;
    private final String estado;

    public Libro(int id, String nombre, int paginas_totales, String cover_url, String estado) {
        this.id = id;
        this.nombre = nombre;
        this.paginas_totales = paginas_totales;
        this.cover_url = cover_url;
        this.estado = estado;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPaginasTotales() {
        return paginas_totales;
    }

    public String getCoverUrl() {
        return cover_url;
    }

    public String getEstado() {
        return estado;
    }
}