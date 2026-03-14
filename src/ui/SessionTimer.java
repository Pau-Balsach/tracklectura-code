package ui;

import javax.swing.Timer;
import java.util.function.Consumer;

/**
 * Gestiona el cronómetro de una sesión de lectura de forma independiente.
 *
 * Extraído de ReadingTrackerGUI para:
 * - Separar responsabilidades (SRP)
 * - Facilitar tests unitarios
 * - Clarificar el ciclo de vida del temporizador
 */
public class SessionTimer {

    private final Timer swingTimer;
    private long tiempoInicio = 0;
    private long tiempoAcumulado = 0;
    private boolean corriendo = false;

    /**
     * @param onTick Callback invocado cada segundo con el tiempo formateado "HH:MM:SS"
     */
    public SessionTimer(Consumer<String> onTick) {
        this.swingTimer = new Timer(1000, e -> {
            onTick.accept(formatearTiempo(getTotalMillis()));
        });
    }

    public void iniciar() {
        if (!corriendo) {
            tiempoInicio = System.currentTimeMillis();
            corriendo = true;
            swingTimer.start();
        }
    }

    public void pausar() {
        if (corriendo) {
            tiempoAcumulado += System.currentTimeMillis() - tiempoInicio;
            corriendo = false;
            swingTimer.stop();
        }
    }

    public void reiniciar() {
        swingTimer.stop();
        corriendo = false;
        tiempoAcumulado = 0;
        tiempoInicio = 0;
    }

    public boolean isCorriendo() {
        return corriendo;
    }

    /** Devuelve el tiempo total transcurrido en milisegundos (incluye pausa). */
    public long getTotalMillis() {
        return tiempoAcumulado + (corriendo ? System.currentTimeMillis() - tiempoInicio : 0);
    }

    /** Devuelve el tiempo total en minutos (para calcular PPM/PPH). */
    public double getTotalMinutos() {
        return getTotalMillis() / 60_000.0;
    }

    /** Devuelve true si hay tiempo acumulado (sesión ha comenzado). */
    public boolean tieneActividad() {
        return getTotalMillis() > 0;
    }

    public static String formatearTiempo(long millis) {
        long s = millis / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}