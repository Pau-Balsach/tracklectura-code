package utils;

/**
 * Utilidades de cálculo de métricas de lectura.
 *
 * Clase de métodos estáticos puros (sin efectos secundarios, sin BD, sin UI)
 * que pueden testarse de forma unitaria sin infraestructura adicional.
 */
public final class ReadingCalculator {

    private ReadingCalculator() { /* no instanciable */ }

    /**
     * Calcula las páginas leídas en una sesión.
     *
     * @param paginaInicio Página donde empezó a leer (inclusive)
     * @param paginaFin    Página donde terminó (inclusive)
     * @return Número de páginas leídas; 0 si los parámetros son incoherentes
     */
    public static int calcularPaginasLeidas(int paginaInicio, int paginaFin) {
        return Math.max(0, paginaFin - paginaInicio);
    }

    /**
     * Calcula la velocidad lectora en páginas por minuto.
     *
     * @param paginas Páginas leídas
     * @param minutos Minutos de lectura (> 0)
     * @return PPM, o 0.0 si los parámetros no son válidos
     */
    public static double calcularPPM(int paginas, double minutos) {
        if (paginas <= 0 || minutos <= 0) return 0.0;
        return paginas / minutos;
    }

    /**
     * Calcula la velocidad lectora en páginas por hora.
     *
     * @param ppm Páginas por minuto
     * @return PPH
     */
    public static double calcularPPH(double ppm) {
        return ppm * 60.0;
    }

    /**
     * Estima el tiempo restante para terminar un libro.
     *
     * @param paginasRestantes Páginas que quedan por leer
     * @param promedioPPH      Velocidad media del lector en PPH
     * @return Estimación formateada, ej: "2h 15m", o null si no hay datos suficientes
     */
    public static String estimarTiempoRestante(int paginasRestantes, double promedioPPH) {
        if (paginasRestantes <= 0) return "¡Libro terminado!";
        if (promedioPPH <= 0) return null;

        double horas = paginasRestantes / promedioPPH;
        int h = (int) horas;
        int m = (int) Math.round((horas - h) * 60);

        if (h > 0) {
            return String.format("%dh %dm", h, m);
        } else {
            return String.format("%dm", m);
        }
    }

    /**
     * Calcula el porcentaje de progreso de un libro.
     *
     * @param paginaActual   Última página leída
     * @param paginasTotales Total de páginas del libro
     * @return Porcentaje entre 0.0 y 100.0, o -1 si no hay total definido
     */
    public static double calcularPorcentaje(int paginaActual, int paginasTotales) {
        if (paginasTotales <= 0) return -1;
        return Math.min(100.0, (paginaActual * 100.0) / paginasTotales);
    }

    /**
     * Valida que los datos de una sesión son coherentes antes de guardar.
     *
     * @return null si todo es válido, o un mensaje de error descriptivo
     */
    public static String validarSesion(int paginaInicio, int paginaFin, double minutos) {
        if (paginaFin < paginaInicio) {
            return "La página final no puede ser menor que la inicial.";
        }
        if (minutos <= 0.01) {
            return "La sesión es demasiado corta para guardarse.";
        }
        return null; // válido
    }

    // --- NUEVOS MÉTODOS PARA GRÁFICOS ---

    /**
     * Extrae la hora de la sesión SOLO si fue registrada con formato de hora.
     * Resuelve el problema de las sesiones manuales.
     */
    public static Integer extraerHoraSegura(String fechaStr) {
        if (fechaStr == null || !fechaStr.contains(":")) return null; // Ignora sesiones sin hora
        try {
            String clean = fechaStr.replace(",", "").trim();
            // Soporta formatos con o sin coma: "15/03/2026 06:26" o "15/03/2026, 06:26"
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return java.time.LocalDateTime.parse(clean, fmt).getHour();
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrae el día de la semana (1=Lunes, 7=Domingo) */
    public static Integer extraerDiaSemana(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return null;
        try {
            String soloFecha = fechaStr.contains(" ") ? fechaStr.split(" ")[0].replace(",", "") : fechaStr;
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return java.time.LocalDate.parse(soloFecha, fmt).getDayOfWeek().getValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrae el mes y año (ej. "2026-03") para la evolución mensual */
    public static String extraerMesAnio(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return null;
        try {
            String soloFecha = fechaStr.contains(" ") ? fechaStr.split(" ")[0].replace(",", "") : fechaStr;
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.LocalDate date = java.time.LocalDate.parse(soloFecha, fmt);
            return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
        } catch (Exception e) {
            return null;
        }
    }
}