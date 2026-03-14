package model;

/** Representa un punto de datos para las gráficas. */
public class DataPoint {
    private final String etiqueta;
    private final double valor;
    private final double valorSec; // Opcional: segundo eje en gráficas duales
    private final String capitulos;

    public DataPoint(String etiqueta, double valor, double valorSec, String capitulos) {
        this.etiqueta = etiqueta;
        this.valor = valor;
        this.valorSec = valorSec;
        this.capitulos = capitulos;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public double getValor() {
        return valor;
    }

    public double getValorSec() {
        return valorSec;
    }

    public String getCapitulos() {
        return capitulos;
    }
}
