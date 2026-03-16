package ui;

import db.DatabaseManager;
import model.DataPoint;
import utils.ExportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ventana avanzada de análisis estadístico.
 * Permite visualizar el progreso de lectura mediante gráficas dinámicas,
 * filtrar por fechas/métricas y consultar récords personales.
 * <p>
 * Tipos de gráfica disponibles:
 * - Páginas Totales: barras por sesión/día
 * - PPM (Velocidad): barras de velocidad lectora
 * - Progreso Acumulado: línea creciente hasta el total del libro
 * - PPM Comparativa: barras horizontales comparando libros terminados
 * - Mapa de Consistencia: heatmap estilo GitHub
 */
public class GraphWindow extends JFrame {

    private JProgressBar barraProgreso;
    private String libroSeleccionado;
    private final boolean modoOscuro;
    private JPanel container;
    private JPanel filterPanel;
    private JPanel panelHitos;
    private JButton btnVerHitos;

    private JComboBox<String> comboMetrica;
    private BookSearchField libroSearchField;
    private JLabel lblLibro;
    private JLabel lblProgreso;
    private JTextField fieldMinPag, fieldFecha;
    private JLabel lblMinPag, lblFecha, labelEstimacion;
    private JCheckBox checkAgrupar, checkCapitulo;
    private JButton btnExportarPNG;

    private JLabel lblSesionLarga, lblDiaRecord, lblVelocidadMax;

    private final List<String> fechas = new ArrayList<>();
    private final List<Double> valores = new ArrayList<>();
    private final List<Double> valoresSec = new ArrayList<>();

    // Indica si el usuario quiere los hitos abiertos o cerrados (por defecto
    // abiertos al abrir ventana)
    private boolean hitosAbiertosUsuario = true;

    public GraphWindow(boolean modoOscuro, String libroInicial) {
        super("📊 Análisis y Gráficas");
        this.modoOscuro = modoOscuro;
        this.libroSeleccionado = libroInicial;

        setSize(1200, 750);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        inicializarInterfaz();

        // Aplicar libro inicial y métrica después de inicializar
        if (libroInicial != null) {
            libroSearchField.setSelectedBook(libroInicial);
            comboMetrica.setSelectedItem("Páginas Totales");
            libroSeleccionado = libroInicial;
            refrescarGrafica(btnExportarPNG, checkAgrupar.isSelected(), checkCapitulo.isSelected());
        }
    }

    /**
     * Configura la disposición de los paneles (Filtros, Gráfica e Hitos).
     */
    private void inicializarInterfaz() {
        Color fondo = modoOscuro ? new Color(45, 45, 45) : new Color(240, 240, 240);
        Color texto = modoOscuro ? Color.WHITE : Color.BLACK;

        // --- PANEL DE FILTROS (Ubicado en el Sur) ---
        filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        filterPanel.setBackground(fondo);

        // 1. Crear comboMetrica
        comboMetrica = new JComboBox<>(new String[] {
                "Páginas Totales", "PPM (Velocidad)", "Progreso Acumulado", "Meta Anual", "Evolución Mensual",
                "Páginas por día de la semana", "Actividad por Hora", "PPM Comparativa", "Correlación: Minutos vs PPM",
                "Mapa de Consistencia"
        });
        comboMetrica.setBackground(modoOscuro ? new Color(60, 60, 60) : Color.WHITE);
        comboMetrica.setForeground(texto);

        // 2. Crear libroSearchField inmediatamente después
        libroSearchField = new BookSearchField();
        libroSearchField.setPreferredSize(new Dimension(200, 26));
        List<String> libros = DatabaseManager.obtenerTodosLosLibros();
        libroSearchField.setBooks(libros);
        if (!libros.isEmpty()) {
            libroSeleccionado = libros.getFirst();
            libroSearchField.setSelectedBook(libroSeleccionado);
        }
        libroSearchField.setOnSelectionChanged(libro -> {
            if (libro != null) {
                libroSeleccionado = libro;
                refrescarGrafica(btnExportarPNG, checkAgrupar.isSelected(), checkCapitulo.isSelected());
            }
        });

        lblLibro = crearLabel("Libro:", texto);

        fieldMinPag = new JTextField("0", 4);
        fieldFecha = new JTextField("", 8);

        lblMinPag = crearLabel("Pág >:", texto);
        lblFecha = crearLabel("Fecha:", texto);

        checkAgrupar = new JCheckBox("Agrupar", true);
        checkAgrupar.setBackground(fondo);
        checkAgrupar.setForeground(texto);

        checkCapitulo = new JCheckBox("Capítulos", false);
        checkCapitulo.setBackground(fondo);
        checkCapitulo.setForeground(texto);

        JButton btnActualizar = new JButton("🔄 Filtros");
        JButton btnRuta = new JButton("📁 Carpeta");
        btnExportarPNG = new JButton("💾 PNG");
        JButton btnExportarCSV = new JButton("📥 CSV");
        btnVerHitos = new JButton("🏆 Récords");

        configurarBotonPlano(btnActualizar);
        configurarBotonPlano(btnRuta);
        configurarBotonPlano(btnExportarPNG);
        configurarBotonPlano(btnExportarCSV);
        configurarBotonPlano(btnVerHitos);
        btnVerHitos.setVisible(false);

        labelEstimacion = new JLabel("");
        labelEstimacion.setFont(new Font("SansSerif", Font.BOLD, 13));
        labelEstimacion.setForeground(modoOscuro ? new Color(100, 149, 237) : new Color(41, 128, 185));

        // Barra de progreso — solo llenado, sin texto porcentual
        barraProgreso = new JProgressBar(0, 100);
        barraProgreso.setPreferredSize(new Dimension(150, 20));
        barraProgreso.setStringPainted(true);
        barraProgreso.setForeground(new Color(46, 204, 113));

        // Establecer el color del texto a negro para mantener el contraste sobre el
        // fondo verde
        UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
        UIManager.put("ProgressBar.selectionBackground", Color.BLACK);
        SwingUtilities.updateComponentTreeUI(barraProgreso);

        // 3. Añadir componentes al filterPanel
        filterPanel.add(crearLabel("Métrica:", texto));
        filterPanel.add(comboMetrica);
        filterPanel.add(lblLibro);
        filterPanel.add(libroSearchField);
        filterPanel.add(lblMinPag);
        filterPanel.add(fieldMinPag);
        filterPanel.add(lblFecha);
        filterPanel.add(fieldFecha);
        filterPanel.add(checkAgrupar);
        filterPanel.add(checkCapitulo);
        filterPanel.add(btnActualizar);
        filterPanel.add(btnExportarCSV);
        filterPanel.add(btnExportarPNG);
        filterPanel.add(btnRuta);
        filterPanel.add(btnVerHitos);
        filterPanel.add(labelEstimacion);
        lblProgreso = crearLabel("Progreso:", texto);
        filterPanel.add(lblProgreso);
        filterPanel.add(barraProgreso);

        // 4. Listener del combo — ahora libroSearchField ya existe, no hay NPE
        comboMetrica.addItemListener(e -> {
            if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED)
                return;
            String sel = (String) comboMetrica.getSelectedItem();
            boolean esComparativo = "Mapa de Consistencia".equals(sel)
                    || "PPM Comparativa".equals(sel)
                    || "Progreso Acumulado".equals(sel)
                    || "Meta Anual".equals(sel)
                    || "Evolución Mensual".equals(sel);
            boolean esAcumulado = "Progreso Acumulado".equals(sel);
            boolean esScatter = "Correlación: Minutos vs PPM".equals(sel);
            boolean esMetricaSinFiltros = "Páginas por día de la semana".equals(sel)
                    || "Actividad por Hora".equals(sel);

            boolean soportaTodos = "Páginas por día de la semana".equals(sel)
                    || "Actividad por Hora".equals(sel)
                    || "Correlación: Minutos vs PPM".equals(sel);
            List<String> lbros = DatabaseManager.obtenerTodosLosLibros();
            if (soportaTodos) {
                lbros.addFirst("--- Todos los libros ---");
            }
            String prevSeleccionado = libroSeleccionado;
            libroSearchField.setBooks(lbros);
            if (!soportaTodos && "--- Todos los libros ---".equals(prevSeleccionado)) {
                if (!lbros.isEmpty()) {
                    libroSeleccionado = lbros.getFirst();
                } else {
                    libroSeleccionado = null;
                }
            }
            if (libroSeleccionado != null) {
                libroSearchField.setSelectedBook(libroSeleccionado);
            }

            lblLibro.setVisible(!esComparativo || esAcumulado);
            libroSearchField.setVisible(!esComparativo || esAcumulado);
            lblMinPag.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);
            fieldMinPag.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);
            lblFecha.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);
            fieldFecha.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);
            checkAgrupar.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);
            checkCapitulo.setVisible(!esComparativo && !esScatter && !esMetricaSinFiltros);

            lblProgreso.setVisible(!esComparativo);
            barraProgreso.setVisible(!esComparativo);

            // Hitos y barra: solo para gráficos individuales
            if (!esComparativo) {
                panelHitos.setVisible(hitosAbiertosUsuario);
                btnVerHitos.setVisible(!hitosAbiertosUsuario);
            } else {
                panelHitos.setVisible(false);
                btnVerHitos.setVisible(false);
            }
            barraProgreso.setVisible(!esComparativo);

            filterPanel.revalidate();
            filterPanel.repaint();
            revalidate();

            refrescarGrafica(btnExportarPNG, checkAgrupar.isSelected(), checkCapitulo.isSelected());
        });

        // --- PANEL DE GRÁFICA (Centro) ---
        container = new JPanel(new BorderLayout());
        container.setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);

        // --- PANEL DE HITOS PERSONALES (Lateral Derecho) ---
        panelHitos = new JPanel(new GridBagLayout());
        panelHitos.setBackground(fondo);
        Dimension fixedDim = new Dimension(230, 0);
        panelHitos.setPreferredSize(fixedDim);
        panelHitos.setMinimumSize(fixedDim);
        panelHitos.setMaximumSize(new Dimension(230, 9999));

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY),
                "🏆 Hitos Personales");
        border.setTitleColor(texto);
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 14));
        panelHitos.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(10, 5, 10, 10), border));

        JButton btnCerrar = new JButton("✕");
        btnCerrar.setFocusPainted(false);
        btnCerrar.setBorderPainted(false);
        btnCerrar.setContentAreaFilled(false);
        btnCerrar.setForeground(texto);
        btnCerrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCerrar.setFont(new Font("SansSerif", Font.BOLD, 12));

        lblSesionLarga = new JLabel();
        lblDiaRecord = new JLabel();
        lblVelocidadMax = new JLabel();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        panelHitos.add(btnCerrar, gbc);

        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridy = 1;
        panelHitos.add(lblSesionLarga, gbc);
        gbc.gridy = 2;
        panelHitos.add(lblDiaRecord, gbc);
        gbc.gridy = 3;
        panelHitos.add(lblVelocidadMax, gbc);

        add(container, BorderLayout.CENTER);
        add(filterPanel, BorderLayout.SOUTH);
        add(panelHitos, BorderLayout.EAST);

        // --- LÓGICA DE EVENTOS ---
        btnCerrar.addActionListener(ignored -> {
            hitosAbiertosUsuario = false;
            panelHitos.setVisible(false);
            btnVerHitos.setVisible(true);
            revalidate();
        });

        btnVerHitos.addActionListener(ignored -> {
            hitosAbiertosUsuario = true;
            panelHitos.setVisible(true);
            btnVerHitos.setVisible(false);
            revalidate();
        });

        btnActualizar.addActionListener(
                ignored -> refrescarGrafica(btnExportarPNG, checkAgrupar.isSelected(), checkCapitulo.isSelected()));

        btnRuta.addActionListener(ignored -> {
            JFileChooser chooser = new JFileChooser(ExportService.rutaExportacion);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                ExportService.rutaExportacion = chooser.getSelectedFile().getAbsolutePath();
                utils.ConfigManager.setExportPath(ExportService.rutaExportacion);
            }
        });

        btnExportarCSV.addActionListener(ignored -> {
            String metricaSel = (String) comboMetrica.getSelectedItem();
            String nombreArchivo = "PPM Comparativa".equals(metricaSel)
                    ? "PPM_Comparativa_Libros_Terminados"
                    : libroSeleccionado;

            if (nombreArchivo != null) {
                List<String[]> datos = obtenerDatosParaExportar(checkAgrupar.isSelected());
                ExportService.exportarDatosCSV(datos, nombreArchivo);
            }
        });

        refrescarGrafica(btnExportarPNG, checkAgrupar.isSelected(), checkCapitulo.isSelected());
    }

    /**
     * Consulta y formatea en HTML los récords históricos del libro.
     */
    private void cargarHitosPersonales() {
        if (libroSeleccionado == null)
            return;
        int id = DatabaseManager.obtenerLibroId(libroSeleccionado);
        double maxMin = DatabaseManager.obtenerSesionMasLarga(id);
        String diaRec = DatabaseManager.obtenerDiaMasLectura(id);
        double maxPpm = DatabaseManager.obtenerVelocidadMaxima(id);

        String colorResalte = modoOscuro ? "#87CEFA" : "#00509E";
        String colorTitulo = modoOscuro ? "white" : "black";
        String estiloBase = "<html><div style='text-align: center; width: 190px;'>";

        lblSesionLarga.setText(estiloBase + "<b style='color:" + colorTitulo + ";'>⏱️ Sesión más larga</b><br>"
                + "<span style='font-size: 15px; color: " + colorResalte + ";'>" + String.format("%.1f", maxMin)
                + " min</span></div></html>");

        lblDiaRecord.setText(estiloBase + "<b style='color:" + colorTitulo + ";'>📅 Día récord</b><br>"
                + "<span style='font-size: 13px; color: " + colorResalte + ";'>" + diaRec + "</span></div></html>");

        lblVelocidadMax.setText(estiloBase + "<b style='color:" + colorTitulo + ";'>⚡ Velocidad máxima</b><br>"
                + "<span style='font-size: 15px; color: " + colorResalte + ";'>" + String.format("%.2f", maxPpm)
                + " PPM</span></div></html>");

        panelHitos.revalidate();
        panelHitos.repaint();
    }

    private void configurarBotonPlano(JButton btn) {
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY));
        btn.setBackground(modoOscuro ? new Color(60, 60, 60) : new Color(225, 225, 225));
        btn.setForeground(modoOscuro ? Color.WHITE : Color.BLACK);
    }

    private JLabel crearLabel(String texto, Color color) {
        JLabel l = new JLabel(texto);
        l.setForeground(color);
        return l;
    }

    private List<String[]> obtenerDatosParaExportar(boolean agrupar) {
        String metricaSel = (String) comboMetrica.getSelectedItem();

        if ("PPM Comparativa".equals(metricaSel)) {
            List<DataPoint> datos = DatabaseManager.obtenerPpmMediaPorLibroTerminado();
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] { "Libro", "PPM Media" });
            for (DataPoint p : datos) {
                rows.add(new String[] { p.getEtiqueta(), String.format("%.2f", p.getValor()) });
            }
            return rows;
        }

        if (libroSeleccionado == null)
            return new ArrayList<>();
        int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
        int minPag = fieldMinPag.getText().isEmpty() ? 0 : Integer.parseInt(fieldMinPag.getText());
        String fFiltro = fieldFecha.getText().isEmpty() ? "01/01/2000" : fieldFecha.getText();
        return DatabaseManager.obtenerDatosParaExportar(libroId, minPag, fFiltro, agrupar);
    }

    private static LocalDate parsearFecha(String s) {
        if (s == null)
            return null;
        String d = s.length() > 10 ? s.substring(0, 10).trim() : s.trim();
        DateTimeFormatter fmtDmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtIso = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            return LocalDate.parse(d, fmtDmy);
        } catch (Exception e1) {
            try {
                return LocalDate.parse(d, fmtIso);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private void refrescarGrafica(JButton btnExportar, boolean agruparPorDia, boolean mostrarCap) {
        fechas.clear();
        valores.clear();
        valoresSec.clear();

        String metricaSel = (String) comboMetrica.getSelectedItem();
        boolean esHeatmap = "Mapa de Consistencia".equals(metricaSel);
        boolean esDual = "Págs + Tiempo".equals(metricaSel);
        boolean esComparativa = "PPM Comparativa".equals(metricaSel);
        boolean esAcumulado = "Progreso Acumulado".equals(metricaSel);
        boolean esMetaAnual = "Meta Anual".equals(metricaSel);
        boolean esEvolucionMensual = "Evolución Mensual".equals(metricaSel);
        boolean esNuevaMetricaLibro = "Correlación: Minutos vs PPM".equals(metricaSel) ||
                "Páginas por día de la semana".equals(metricaSel) ||
                "Actividad por Hora".equals(metricaSel);

        // Actualizar libroSeleccionado desde el campo solo para gráficos individuales
        if (!esComparativa && !esHeatmap && !esMetaAnual && !esEvolucionMensual) {
            String sel = libroSearchField.getSelectedBook();
            if (sel != null)
                libroSeleccionado = sel;
        }

        // Si no hay libro para gráficos individuales, mostrar mensaje y salir
        if (!esComparativa && !esHeatmap && !esMetaAnual && !esEvolucionMensual && libroSeleccionado == null) {
            container.removeAll();
            JLabel lbl = new JLabel("Selecciona un libro para ver su gráfica.", SwingConstants.CENTER);
            lbl.setForeground(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
            container.add(lbl, BorderLayout.CENTER);
            container.revalidate();
            container.repaint();
            return;
        }

        // Lógica de datos para gráficos individuales (no comparativa, no heatmap)
        if (!esComparativa && !esHeatmap && !esMetaAnual && !esEvolucionMensual && !esNuevaMetricaLibro) {
            try {
                int minPag = fieldMinPag.getText().isEmpty() ? 0 : Integer.parseInt(fieldMinPag.getText());
                // Progreso acumulado usa columna paginas siempre
                String colSql = (metricaSel != null && metricaSel.startsWith("Páginas")) ? "paginas"
                        : esAcumulado ? "pag_fin" : "ppm";

                int minPagEfectivo = esAcumulado ? 0 : minPag;
                boolean agruparEfectivo = !esAcumulado && agruparPorDia; // ← false para acumulado
                List<DataPoint> puntos = DatabaseManager.obtenerDatosGrafica(colSql,
                        DatabaseManager.obtenerLibroId(libroSeleccionado),
                        minPagEfectivo, agruparEfectivo, false, esDual);

                Map<LocalDate, double[]> mapaAgrupado = new java.util.TreeMap<>();
                Map<LocalDate, double[]> mapaAgrupadoSec = new java.util.TreeMap<>();
                Map<LocalDate, String> mapaCap = new java.util.TreeMap<>();
                Map<LocalDate, String> mapaEtiqueta = new java.util.TreeMap<>();

                for (DataPoint p : puntos) {
                    String rawEtiqueta = p.getEtiqueta();
                    LocalDate fecha = parsearFecha(rawEtiqueta);
                    if (fecha == null)
                        continue;

                    // Filtro de página mínima
                    double paginasDelPunto = "ppm".equals(colSql) ? p.getValorSec() : p.getValor();
                    if (minPag > 0 && paginasDelPunto < minPagEfectivo)
                        continue;

                    // Filtro de fecha mínima
                    String fFiltroStr = fieldFecha.getText().trim();
                    if (!fFiltroStr.isEmpty()) {
                        LocalDate fFiltro = parsearFecha(fFiltroStr);
                        if (fFiltro != null && fecha.isBefore(fFiltro))
                            continue;
                    }

                    double val = p.getValor();
                    double valSec = p.getValorSec();
                    String cap = p.getCapitulos();
                    String etiquetaNorm = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                    if (agruparEfectivo) {
                        double[] acum = mapaAgrupado.getOrDefault(fecha, new double[] { 0.0, 0 });
                        if (colSql.equals("paginas") || esDual) {
                            acum[0] += val;
                        } else {
                            acum[0] += val;
                            acum[1]++;
                        }
                        mapaAgrupado.put(fecha, acum);
                        if (esDual) {
                            double[] acumSec = mapaAgrupadoSec.getOrDefault(fecha, new double[] { 0.0, 0 });
                            acumSec[0] += valSec;
                            mapaAgrupadoSec.put(fecha, acumSec);
                        }
                        String capExist = mapaCap.getOrDefault(fecha, "");
                        if (cap != null && !cap.isEmpty())
                            mapaCap.put(fecha, capExist.isEmpty() ? cap : capExist + ";" + cap);
                        mapaEtiqueta.put(fecha, etiquetaNorm);
                    } else {
                        fechas.add(etiquetaNorm + (mostrarCap && cap != null ? ";" + cap : ""));
                        valores.add(val);
                        if (esDual)
                            valoresSec.add(valSec);
                    }
                }

                if (agruparEfectivo) {
                    for (Map.Entry<LocalDate, double[]> entry : mapaAgrupado.entrySet()) {
                        LocalDate fech = entry.getKey();
                        double[] acum = entry.getValue();
                        double valor = (colSql.equals("paginas") || esDual)
                                ? acum[0]
                                : (acum[1] > 0 ? acum[0] / acum[1] : 0);
                        String etiq = mapaEtiqueta.get(fech);
                        if (mostrarCap && mapaCap.containsKey(fech))
                            etiq += ";" + mapaCap.get(fech);
                        fechas.add(etiq);
                        valores.add(valor);
                        if (esDual) {
                            double[] acumSec = mapaAgrupadoSec.getOrDefault(fech, new double[] { 0, 0 });
                            valoresSec.add(acumSec[0]);
                        }
                    }
                } else {
                    List<int[]> indices = new ArrayList<>();
                    for (int i = 0; i < fechas.size(); i++)
                        indices.add(new int[] { i });
                    List<LocalDate> fechasParsed = new ArrayList<>();
                    for (String f : fechas) {
                        String solo = f.contains(";") ? f.split(";")[0] : f;
                        fechasParsed.add(parsearFecha(solo));
                    }
                    indices.sort(Comparator.comparing(idx -> {
                        LocalDate d = fechasParsed.get(idx[0]);
                        return d != null ? d : LocalDate.MIN;
                    }));
                    List<String> fechasOrd = new ArrayList<>();
                    List<Double> valoresOrd = new ArrayList<>();
                    List<Double> valoresSecOrd = new ArrayList<>();
                    for (int[] idx : indices) {
                        fechasOrd.add(fechas.get(idx[0]));
                        valoresOrd.add(valores.get(idx[0]));
                        if (esDual)
                            valoresSecOrd.add(valoresSec.get(idx[0]));
                    }
                    fechas.clear();
                    fechas.addAll(fechasOrd);
                    valores.clear();
                    valores.addAll(valoresOrd);
                    if (esDual) {
                        valoresSec.clear();
                        valoresSec.addAll(valoresSecOrd);
                    }
                }
                // Acumulación de páginas
                if (esAcumulado && !valores.isEmpty()) {
                    Map<LocalDate, Double> porDia = new java.util.TreeMap<>();
                    for (int i = 0; i < fechas.size(); i++) {
                        String solo = fechas.get(i).contains(";") ? fechas.get(i).split(";")[0] : fechas.get(i);
                        LocalDate d = parsearFecha(solo);
                        if (d != null)
                            porDia.merge(d, valores.get(i), Double::max);
                    }
                    fechas.clear();
                    valores.clear();
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    for (Map.Entry<LocalDate, Double> e : porDia.entrySet()) {
                        fechas.add(e.getKey().format(fmt));
                        valores.add(e.getValue());
                    }
                }

            } catch (Exception e) {
                System.err.println("Error en refrescarGrafica: " + e.getMessage());
            }
        }

        // --- NUEVAS MÉTRICAS BASADAS EN SESIONES ---
        if (esNuevaMetricaLibro) {
            try {
                List<model.Sesion> sesiones;
                if ("--- Todos los libros ---".equals(libroSeleccionado)) {
                    sesiones = DatabaseManager.obtenerTodasLasSesionesDesde("1970-01-01");
                } else {
                    int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
                    sesiones = DatabaseManager.obtenerSesionesPorLibro(libroId);
                }

                switch (metricaSel) {
                    case "Correlación: Minutos vs PPM":
                        for (model.Sesion s : sesiones) {
                            if (s.getMinutos() > 0 && s.getPpm() > 0) {
                                fechas.add(String.valueOf(s.getMinutos())); // X: Minutos
                                valores.add(s.getPpm()); // Y: PPM
                            }
                        }
                        break;
                    case "Páginas por día de la semana":
                        double[] pagsPorDia = new double[7];
                        String[] nombresDias = { "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };
                        boolean hayLecturaSemana = false;
                        for (model.Sesion s : sesiones) {
                            Integer dia = utils.ReadingCalculator.extraerDiaSemana(s.getFecha());
                            if (dia != null)
                                pagsPorDia[dia - 1] += s.getPaginasLeidas();
                        }
                        for (int i = 0; i < 7; i++) {
                            if (pagsPorDia[i] > 0)
                                hayLecturaSemana = true;
                            fechas.add(nombresDias[i]);
                            valores.add(pagsPorDia[i]);
                        }
                        if (!hayLecturaSemana) {
                            fechas.clear();
                            valores.clear();
                        }
                        break;
                    case "Actividad por Hora":
                        double[] pagsPorHora = new double[24];
                        boolean hayHoras = false;
                        for (model.Sesion s : sesiones) {
                            Integer hora = utils.ReadingCalculator.extraerHoraSegura(s.getFecha());
                            if (hora != null) {
                                pagsPorHora[hora] += s.getPaginasLeidas();
                                hayHoras = true;
                            }
                        }
                        if (hayHoras) {
                            for (int i = 0; i < 24; i++) {
                                fechas.add(i + ":00");
                                valores.add(pagsPorHora[i]);
                            }
                        }
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error en refrescarGrafica: " + e.getMessage());
            }
        }

        // --- NUEVA MÉTRICA GLOBAL: EVOLUCIÓN MENSUAL ---
        if (esEvolucionMensual) {
            try {
                List<model.Sesion> todasSesiones = DatabaseManager.obtenerTodasLasSesionesDesde("1970-01-01");
                Map<String, Double> porMes = new java.util.TreeMap<>();
                for (model.Sesion s : todasSesiones) {
                    String mes = utils.ReadingCalculator.extraerMesAnio(s.getFecha());
                    if (mes != null)
                        porMes.put(mes, porMes.getOrDefault(mes, 0.0) + s.getPaginasLeidas());
                }
                for (Map.Entry<String, Double> entry : porMes.entrySet()) {
                    fechas.add(entry.getKey());
                    valores.add(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("Error en evolución mensual: " + e.getMessage());
            }
        }

        // Heatmap necesita sus propios datos (usa todas las sesiones globales)
        if (esHeatmap) {
            try {
                List<DataPoint> puntos = DatabaseManager.obtenerDatosGrafica(
                        "paginas", -1, 0, true, true, false);

                for (DataPoint p : puntos) {
                    fechas.add(p.getEtiqueta());
                    valores.add(p.getValor());
                }
            } catch (Exception e) {
                System.err.println("Error en heatmap: " + e.getMessage());
            }
        }

        // Barra de progreso e hitos: solo para gráficos individuales
        if (!esComparativa && !esHeatmap && !esMetaAnual && !esEvolucionMensual && libroSeleccionado != null) {
            if ("--- Todos los libros ---".equals(libroSeleccionado)) {
                barraProgreso.setVisible(false);
                if (lblProgreso != null)
                    lblProgreso.setVisible(false);
                labelEstimacion.setText("");
                labelEstimacion.setVisible(false);
                panelHitos.setVisible(false);
                btnVerHitos.setVisible(false);
            } else {
                barraProgreso.setVisible(true);
                if (lblProgreso != null)
                    lblProgreso.setVisible(true);
                int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
                double porcentaje = DatabaseManager.obtenerPorcentajeProgreso(libroId);
                barraProgreso.setValue((int) porcentaje);
                barraProgreso.setToolTipText("Has leído el " + String.format("%.1f", porcentaje) + "% del libro");
                labelEstimacion.setText("");
                labelEstimacion.setVisible(false);
                cargarHitosPersonales();
            }
        }

        if (!esComparativa && !esHeatmap && !esMetaAnual && !esEvolucionMensual && !esNuevaMetricaLibro
                && fechas.isEmpty()) {
            container.removeAll();
            String msg = "Este libro aún no tiene sesiones almacenadas.";
            if ("Actividad por Hora".equals(metricaSel) && libroSeleccionado != null) {
                msg = "Este libro no tiene registros de hora (solo fecha).";
            }
            JLabel lbl = new JLabel(msg, SwingConstants.CENTER);
            lbl.setForeground(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
            container.add(lbl, BorderLayout.CENTER);
            container.revalidate();
            container.repaint();
            return;
        }

        // Construir el panel correcto según tipo
        JPanel panel;

        if (esHeatmap) {
            PanelHeatmap hp = new PanelHeatmap(fechas, valores, modoOscuro);
            hp.setPreferredSize(new Dimension(850, 500));
            panel = hp;
        } else if ("Meta Anual".equals(metricaSel)) {
            // Obtener todos los libros y contar terminados
            List<model.Libro> todosLibros = DatabaseManager.obtenerTodosLosLibrosDesde("1970-01-01");
            int terminados = (int) todosLibros.stream().filter(l -> "Terminado".equalsIgnoreCase(l.getEstado()))
                    .count();
            int meta = 12; // Puedes cambiar esto por ConfigManager.getYearlyGoal() si lo implementas
            panel = new PanelMetaAnual(terminados, meta, modoOscuro);
            panel.setPreferredSize(new Dimension(850, 500));
        } else if ("Correlación: Minutos vs PPM".equals(metricaSel)) {
            PanelScatter ps = new PanelScatter(fechas, valores, modoOscuro);
            ps.setPreferredSize(new Dimension(850, 500));
            panel = ps;
        } else if ("Páginas por día de la semana".equals(metricaSel) || "Actividad por Hora".equals(metricaSel)
                || "Evolución Mensual".equals(metricaSel)) {
            PanelLineaSimple pls = new PanelLineaSimple(fechas, valores, modoOscuro);
            int calculado = Math.max(850, fechas.size() * 90 + 150);
            pls.setPreferredSize(new Dimension(calculado, 550));
            panel = pls;
        } else if (esComparativa) {
            List<DataPoint> datosComp = DatabaseManager.obtenerPpmMediaPorLibroTerminado();
            PanelBarrasComparacion pbc = new PanelBarrasComparacion(datosComp, modoOscuro);
            pbc.setPreferredSize(new Dimension(850, Math.max(400, datosComp.size() * 55 + 100)));
            panel = pbc;
        } else if (esAcumulado) {
            // NUEVO: Añadir el punto 0 al inicio para que la gráfica nazca desde el suelo
            if (!valores.isEmpty() && valores.getFirst() > 0) {
                valores.addFirst(0.0);
                fechas.addFirst("Inicio");
            }

            int totalPags = libroSeleccionado != null
                    ? DatabaseManager.obtenerPaginasTotales(DatabaseManager.obtenerLibroId(libroSeleccionado))
                    : 0;
            PanelLineaAcumulada pla = new PanelLineaAcumulada(fechas, valores, totalPags, modoOscuro);
            // Para pocos puntos no estirar, máximo 120px por punto
            int calculado = Math.min(fechas.size() * 120 + 150, 1200);
            pla.setPreferredSize(new Dimension(calculado, 550));
            panel = pla;
        } else {
            PanelGrafica sp = new PanelGrafica(fechas, valores, metricaSel, modoOscuro);
            int calculado = Math.max(850, fechas.size() * 90 + 150);
            sp.setPreferredSize(new Dimension(calculado, 550));
            panel = sp;
        }

        final JPanel panelFinal = panel;
        for (var al : btnExportar.getActionListeners())
            btnExportar.removeActionListener(al);
        btnExportar.addActionListener(ignored -> ExportService.exportarAPNG(
                panelFinal, libroSeleccionado != null ? libroSeleccionado : "comparativa"));

        container.removeAll();
        JScrollPane scroll = new JScrollPane(panel);
        scroll.getViewport().setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        scroll.setBorder(null);
        container.add(scroll, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLASES INTERNAS DE RENDERIZADO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gráfico de barras verticales para páginas o PPM por sesión/día.
     */
    public static class PanelGrafica extends JPanel {
        private final List<String> fechas;
        private final List<Double> valores;
        private final String tituloEje;
        private final boolean modoOscuro;

        public PanelGrafica(List<String> f, List<Double> v, String t, boolean modoOscuro) {
            this.fechas = f;
            this.valores = v;
            this.tituloEje = t;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (valores.isEmpty()) {
                g.setColor(modoOscuro ? new Color(200, 200, 200) : Color.GRAY);
                g.drawString("No hay datos disponibles.", 50, 50);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color colorTexto = modoOscuro ? new Color(220, 220, 220) : new Color(60, 60, 60);
            Color colorEjes = modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY;

            int h = getHeight(), mIzq = 80, mInf = 150, mSup = 60;
            double maxV = valores.stream().max(Double::compare).orElse(10.0);
            double escalaY = (h - mInf - mSup) / (maxV * 1.15);

            for (int i = 0; i < valores.size(); i++) {
                int x = mIzq + (i * 90) + 25;
                int barH = (int) (valores.get(i) * escalaY);
                int y = h - mInf - barH;

                g2.setColor(tituloEje.contains("Pág") ? new Color(70, 130, 180) : new Color(85, 170, 85));
                g2.fillRect(x, y, 50, barH);

                g2.setColor(colorTexto);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                String formato = tituloEje.contains("Pág") ? "%.1f" : "%.2f";
                g2.drawString(String.format(formato, valores.get(i)), x + 5, y - 5);

                AffineTransform old = g2.getTransform();
                g2.translate(x + 25, h - mInf + 15);
                g2.rotate(Math.toRadians(45));
                String[] partes = fechas.get(i).split(";");
                for (int j = 0; j < partes.length; j++) {
                    g2.setFont(new Font("SansSerif", Font.PLAIN, j == 0 ? 10 : 9));
                    g2.drawString(partes[j], 0, j * 12);
                }
                g2.setTransform(old);
            }

            g2.setColor(colorEjes);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(mIzq, mSup, mIzq, h - mInf);
            g2.drawLine(mIzq, h - mInf, getWidth() - 50, h - mInf);

            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g2.drawString(tituloEje, 10, mSup - 20);
        }
    }

    /**
     * Gráfico combinado de barras (páginas leídas) + línea (minutos), con doble eje
     * Y.
     */
    public static class PanelGraficaDual extends JPanel {
        private final List<String> fechas;
        private final List<Double> paginas;
        private final List<Double> minutos;
        private final boolean modoOscuro;

        public PanelGraficaDual(List<String> f, List<Double> pags, List<Double> mins, boolean modoOscuro) {
            this.fechas = f;
            this.paginas = pags;
            this.minutos = mins;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (paginas.isEmpty()) {
                g.setColor(modoOscuro ? new Color(200, 200, 200) : Color.GRAY);
                g.drawString("No hay datos disponibles.", 50, 50);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color colorTexto = modoOscuro ? new Color(220, 220, 220) : new Color(50, 50, 50);
            Color colorEjes = modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY;
            Color colorBarras = new Color(70, 130, 180);
            Color colorLinea = new Color(230, 100, 60);

            int h = getHeight();
            int mIzq = 70, mDer = 60, mInf = 150, mSup = 50;
            int barW = 50, paso = 90;
            int areaH = h - mInf - mSup;

            double maxPag = paginas.stream().max(Double::compare).orElse(10.0);
            double maxMin = minutos.isEmpty() ? 10.0 : minutos.stream().max(Double::compare).orElse(10.0);
            double escPag = areaH / (maxPag * 1.15);
            double escMin = areaH / (maxMin * 1.15);

            g2.setColor(colorEjes);
            g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 4 }, 0));
            int nGrid = 5;
            for (int gi = 1; gi <= nGrid; gi++) {
                int yGrid = h - mInf - (areaH * gi / nGrid);
                g2.drawLine(mIzq, yGrid, getWidth() - mDer, yGrid);
            }

            int metaDiaria = utils.ConfigManager.getDailyGoal();
            if (metaDiaria > 0 && metaDiaria <= maxPag * 1.5) {
                int yMeta = h - mInf - (int) (metaDiaria * escPag);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
                        new float[] { 8, 4 }, 0));
                g2.setColor(new Color(255, 69, 0, 180));
                g2.drawLine(mIzq, yMeta, getWidth() - mDer, yMeta);
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2.drawString("META: " + metaDiaria, getWidth() - mDer - 55, yMeta - 5);
            }

            for (int i = 0; i < paginas.size(); i++) {
                int x = mIzq + (i * paso) + 5;
                int barH = (int) (paginas.get(i) * escPag);
                int y = h - mInf - barH;

                GradientPaint grad = new GradientPaint(x, y, colorBarras.brighter(), x, h - mInf, colorBarras.darker());
                g2.setPaint(grad);
                g2.fillRoundRect(x, y, barW, barH, 6, 6);

                g2.setColor(colorTexto);
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                String lbl = String.valueOf((int) Math.round(paginas.get(i)));
                g2.drawString(lbl, x + barW / 2 - g2.getFontMetrics().stringWidth(lbl) / 2, y - 4);

                AffineTransform old = g2.getTransform();
                g2.translate(x + barW / 2, h - mInf + 12);
                g2.rotate(Math.toRadians(45));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                String[] partes = fechas.get(i).split(";");
                for (int j = 0; j < partes.length; j++)
                    g2.drawString(partes[j], 0, j * 11);
                g2.setTransform(old);
            }

            if (!minutos.isEmpty()) {
                g2.setColor(colorLinea);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int[] puntoX = new int[minutos.size()];
                int[] puntoY = new int[minutos.size()];
                for (int i = 0; i < minutos.size(); i++) {
                    puntoX[i] = mIzq + (i * paso) + 5 + barW / 2;
                    puntoY[i] = h - mInf - (int) (minutos.get(i) * escMin);
                }
                for (int i = 0; i < puntoX.length - 1; i++)
                    g2.drawLine(puntoX[i], puntoY[i], puntoX[i + 1], puntoY[i + 1]);
                for (int i = 0; i < puntoX.length; i++) {
                    g2.fillOval(puntoX[i] - 4, puntoY[i] - 4, 8, 8);
                    g2.setColor(colorTexto);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                    String mv = String.format("%.0fm", minutos.get(i));
                    g2.drawString(mv, puntoX[i] - g2.getFontMetrics().stringWidth(mv) / 2, puntoY[i] - 7);
                    g2.setColor(colorLinea);
                }
            }

            g2.setColor(colorEjes);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mIzq, mSup, mIzq, h - mInf);
            g2.drawLine(getWidth() - mDer, mSup, getWidth() - mDer, h - mInf);
            g2.drawLine(mIzq, h - mInf, getWidth() - mDer, h - mInf);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            for (int gi = 0; gi <= nGrid; gi++) {
                int yMarca = h - mInf - (areaH * gi / nGrid);
                int valMarca = (int) Math.round(maxPag * gi / nGrid);
                g2.setColor(colorTexto);
                String marcaTxt = String.valueOf(valMarca);
                g2.drawString(marcaTxt, mIzq - g2.getFontMetrics().stringWidth(marcaTxt) - 5, yMarca + 4);
            }
            for (int gi = 0; gi <= nGrid; gi++) {
                int yMarca = h - mInf - (areaH * gi / nGrid);
                int valMarca = (int) Math.round(maxMin * gi / nGrid);
                g2.setColor(colorLinea);
                String marcaTxt = valMarca + "m";
                g2.drawString(marcaTxt, getWidth() - mDer + 4, yMarca + 4);
            }

            g2.setColor(colorBarras);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("📖 Páginas leídas", mIzq + 5, mSup - 10);
            g2.setColor(colorLinea);
            String tituloMinutos = "⏱ Minutos";
            g2.drawString(tituloMinutos, getWidth() - mDer - g2.getFontMetrics().stringWidth(tituloMinutos) - 5,
                    mSup - 10);

            int lx = mIzq + 10, ly = mSup + 15;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(colorBarras);
            g2.fillRoundRect(lx, ly, 14, 10, 3, 3);
            g2.setColor(colorTexto);
            g2.drawString("Páginas", lx + 18, ly + 9);
            g2.setColor(colorLinea);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawLine(lx + 80, ly + 5, lx + 94, ly + 5);
            g2.fillOval(lx + 84, ly + 2, 6, 6);
            g2.setColor(colorTexto);
            g2.drawString("Minutos", lx + 98, ly + 9);
        }
    }

    /**
     * Heatmap de consistencia estilo GitHub.
     */
    public static class PanelHeatmap extends JPanel {
        private final Map<LocalDate, Double> datos = new java.util.HashMap<>();
        private final boolean modoOscuro;

        public PanelHeatmap(List<String> fechasStr, List<Double> valores, boolean modoOscuro) {
            this.modoOscuro = modoOscuro;
            for (int i = 0; i < fechasStr.size(); i++) {
                try {
                    String f = fechasStr.get(i).contains(";") ? fechasStr.get(i).split(";")[0] : fechasStr.get(i);
                    LocalDate ld = parsearFecha(f);
                    if (ld != null) {
                        datos.put(ld, valores.get(i));
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing date: " + e.getMessage());
                }
            }
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cellSize = 15, gap = 3;
            int gridH = 7 * (cellSize + gap);
            int legendH = 50;
            int marginY = (getHeight() - gridH - legendH) / 2;
            if (marginY < 40)
                marginY = 40;

            LocalDate hoy = LocalDate.now();
            LocalDate inicio = hoy.minusMonths(6).with(java.time.DayOfWeek.MONDAY);

            long numDiasTotal = java.time.temporal.ChronoUnit.DAYS.between(inicio, hoy) + 1;
            int semanasVisibles = (int) Math.ceil(numDiasTotal / 7.0) + 1;
            int gridW = semanasVisibles * (cellSize + gap);
            int marginX = (getWidth() - gridW) / 2;
            if (marginX < 60)
                marginX = 60;

            String[] diasNames = { "Lun", "", "Mié", "", "Vie", "", "Dom" };
            g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i < 7; i++) {
                if (!diasNames[i].isEmpty())
                    g2.drawString(diasNames[i], marginX - 40, marginY + i * (cellSize + gap) + 12);
            }

            int col = 0;
            LocalDate actual = inicio;
            int lastMonth = -1;

            while (actual.isBefore(hoy) || actual.isEqual(hoy)) {
                int fila = actual.getDayOfWeek().getValue() - 1;
                int xBox = marginX + col * (cellSize + gap);
                int yBox = marginY + fila * (cellSize + gap);

                Double pagsValue = datos.getOrDefault(actual, 0.0);
                g2.setColor(getColorIntensidad(pagsValue));
                g2.fillRoundRect(xBox, yBox, cellSize, cellSize, 3, 3);

                if (actual.getMonthValue() != lastMonth) {
                    String mesStr = actual.getMonth().getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault());
                    g2.setColor(modoOscuro ? Color.GRAY : Color.DARK_GRAY);
                    g2.drawString(mesStr, xBox, marginY - 10);
                    lastMonth = actual.getMonthValue();
                }

                if (fila == 6)
                    col++;
                actual = actual.plusDays(1);
            }

            int lx = marginX;
            int ly = marginY + 7 * (cellSize + gap) + 30;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String[] rangosText = { "0", "1-20", "21-50", "51-80", "80+" };
            for (int i = 0; i < rangosText.length; i++) {
                int rx = lx + i * 85;
                g2.setColor(getColorLeyenda(i));
                g2.fillRoundRect(rx, ly, cellSize, cellSize, 3, 3);
                g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                g2.drawString(rangosText[i] + " págs", rx + cellSize + 5, ly + 12);
            }
        }

        private Color getColorIntensidad(Double pags) {
            if (pags <= 0)
                return modoOscuro ? new Color(45, 45, 45) : new Color(230, 230, 230);
            if (pags <= 20)
                return new Color(155, 200, 255);
            if (pags <= 50)
                return new Color(100, 160, 240);
            if (pags <= 80)
                return new Color(40, 100, 200);
            return new Color(15, 60, 150);
        }

        private Color getColorLeyenda(int nivel) {
            if (nivel == 0)
                return modoOscuro ? new Color(45, 45, 45) : new Color(230, 230, 230);
            if (nivel == 1)
                return new Color(155, 200, 255);
            if (nivel == 2)
                return new Color(100, 160, 240);
            if (nivel == 3)
                return new Color(40, 100, 200);
            return new Color(15, 60, 150);
        }
    }

    /**
     * Gráfico de barras horizontales comparando PPM media de libros terminados.
     */
    public static class PanelBarrasComparacion extends JPanel {
        private final List<DataPoint> datos;
        private final boolean modoOscuro;

        public PanelBarrasComparacion(List<DataPoint> datos, boolean modoOscuro) {
            this.datos = datos;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (datos.isEmpty()) {
                g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("No hay libros terminados con sesiones registradas.", 40, 60);
                return;
            }

            Color colorTexto = modoOscuro ? Color.WHITE : Color.BLACK;
            Color colorGrid = modoOscuro ? new Color(60, 60, 60) : new Color(220, 220, 220);
            Color colorBarra = new Color(70, 130, 200);
            Color colorBarraTop = new Color(46, 204, 113);

            int w = getWidth(), h = getHeight();
            int mIzq = 210, mDer = 90, mSup = 50, mInf = 45;
            int areaW = w - mIzq - mDer;
            int areaH = h - mSup - mInf;
            int n = datos.size();
            int alturaBarra = Math.min(38, (areaH / Math.max(n, 1)) - 6);
            int espaciado = Math.max(4, (areaH - alturaBarra * n) / (n + 1));

            double maxPpm = datos.stream().mapToDouble(DataPoint::getValor).max().orElse(1);

            // Título
            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            String titulo = "⚡ PPM Media — Libros Terminados";
            g2.drawString(titulo, mIzq + (areaW - g2.getFontMetrics().stringWidth(titulo)) / 2, 32);

            // Grid vertical + etiquetas eje superior
            int nGrid = 5;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= nGrid; i++) {
                int xGrid = mIzq + (areaW * i / nGrid);
                g2.setColor(colorGrid);
                float[] dash = { 4f };
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
                g2.drawLine(xGrid, mSup, xGrid, h - mInf);
                String etiq = String.format("%.1f", maxPpm * i / nGrid);
                g2.setColor(colorTexto);
                g2.drawString(etiq, xGrid - g2.getFontMetrics().stringWidth(etiq) / 2, mSup - 8);
            }
            g2.setStroke(new BasicStroke(1.5f));

            // Label eje X
            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String labelEje = "PPM (páginas / minuto)";
            g2.drawString(labelEje, mIzq + (areaW - g2.getFontMetrics().stringWidth(labelEje)) / 2, h - 12);

            // Barras
            for (int i = 0; i < n; i++) {
                DataPoint p = datos.get(i);
                int yBarra = mSup + espaciado + i * (alturaBarra + espaciado);
                int anchoBarra = (int) (areaW * p.getValor() / maxPpm);

                g2.setColor(i == 0 ? colorBarraTop : colorBarra);
                g2.fillRoundRect(mIzq, yBarra, anchoBarra, alturaBarra, 6, 6);

                // Nombre libro (izquierda)
                g2.setColor(colorTexto);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                String nombre = p.getEtiqueta().length() > 30
                        ? p.getEtiqueta().substring(0, 29) + "…"
                        : p.getEtiqueta();
                g2.drawString(nombre,
                        mIzq - g2.getFontMetrics().stringWidth(nombre) - 8,
                        yBarra + alturaBarra / 2 + 5);

                // Valor al final de la barra
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                g2.drawString(String.format("%.2f ppm", p.getValor()),
                        mIzq + anchoBarra + 6,
                        yBarra + alturaBarra / 2 + 5);
            }

            // Eje X base
            g2.setColor(colorTexto);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mIzq, h - mInf, w - mDer, h - mInf);
        }
    }

    /**
     * Gráfico de línea de progreso acumulado de páginas.
     * Muestra una línea creciente con línea de meta (total del libro).
     */
    public static class PanelLineaAcumulada extends JPanel {
        private final List<String> fechas;
        private final List<Double> valores;
        private final int totalPaginas;
        private final boolean modoOscuro;

        public PanelLineaAcumulada(List<String> fechas, List<Double> valores, int totalPaginas, boolean modoOscuro) {
            this.fechas = fechas;
            this.valores = valores;
            this.totalPaginas = totalPaginas;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (valores.isEmpty()) {
                g.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
                g.drawString("No hay datos disponibles.", 50, 50);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color colorTexto = modoOscuro ? new Color(220, 220, 220) : new Color(50, 50, 50);
            Color colorEjes = modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY;
            Color colorLinea = new Color(70, 160, 210);
            Color colorMeta = new Color(46, 204, 113);
            Color colorRelleno = new Color(70, 160, 210, 40);

            int w = getWidth(), h = getHeight();
            int mIzq = 70, mDer = 80, mSup = 50, mInf = 150;
            int areaW = w - mIzq - mDer;
            int areaH = h - mSup - mInf;
            int n = valores.size();

            double maxVal = Math.max(
                    valores.stream().mapToDouble(Double::doubleValue).max().orElse(1),
                    totalPaginas > 0 ? totalPaginas : 1);

            // Grid horizontal
            int nGrid = 5;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= nGrid; i++) {
                int yGrid = h - mInf - (areaH * i / nGrid);
                g2.setColor(colorEjes);
                float[] dash = { 4f };
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
                g2.drawLine(mIzq, yGrid, w - mDer, yGrid);
                // Etiquetas eje Y
                String etiq = String.valueOf((int) (maxVal * i / nGrid));
                g2.setColor(colorTexto);
                g2.drawString(etiq, mIzq - g2.getFontMetrics().stringWidth(etiq) - 5, yGrid + 4);
            }
            g2.setStroke(new BasicStroke(1.5f));

            // Línea de meta (total páginas del libro)
            if (totalPaginas > 0) {
                int yMeta = h - mInf - (int) (areaH * totalPaginas / maxVal);
                g2.setColor(colorMeta);
                float[] dash = { 8f, 4f };
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
                g2.drawLine(mIzq, yMeta, w - mDer, yMeta);
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2.drawString("META: " + totalPaginas + " págs", w - mDer - 120, yMeta - 5);
                g2.setStroke(new BasicStroke(1.5f));
            }

            // Calcular coordenadas de puntos
            int[] px = new int[n];
            int[] py = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = mIzq + (n > 1 ? i * areaW / (n - 1) : areaW / 2);
                py[i] = h - mInf - (int) (areaH * valores.get(i) / maxVal);
            }

            // Relleno bajo la línea
            Polygon relleno = new Polygon();
            relleno.addPoint(px[0], h - mInf);
            for (int i = 0; i < n; i++)
                relleno.addPoint(px[i], py[i]);
            relleno.addPoint(px[n - 1], h - mInf);
            g2.setColor(colorRelleno);
            g2.fillPolygon(relleno);

            // Línea principal
            g2.setColor(colorLinea);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < n - 1; i++)
                g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);

            // Puntos + etiquetas de fecha
            for (int i = 0; i < n; i++) {
                // Punto
                g2.setColor(colorLinea);
                g2.fillOval(px[i] - 4, py[i] - 4, 8, 8);

                // Valor encima del punto
                g2.setColor(colorTexto);
                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                String val = String.valueOf((int) Math.round(valores.get(i)));
                g2.drawString(val, px[i] - g2.getFontMetrics().stringWidth(val) / 2, py[i] - 7);

                // Fecha rotada
                AffineTransform old = g2.getTransform();
                g2.translate(px[i], h - mInf + 12);
                g2.rotate(Math.toRadians(45));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                String[] partes = fechas.get(i).split(";");
                for (int j = 0; j < partes.length; j++)
                    g2.drawString(partes[j], 0, j * 11);
                g2.setTransform(old);
            }

            // Ejes
            g2.setColor(colorEjes);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mIzq, mSup, mIzq, h - mInf);
            g2.drawLine(mIzq, h - mInf, w - mDer, h - mInf);

            // Título eje Y
            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g2.drawString("Páginas acumuladas", mIzq + 5, mSup - 10);
        }
    }

    /**
     * Gráfico de líneas simples para Pág/Día, Pág/Hora, y Pág/Mes, sin relleno bajo
     * la curva.
     */
    public static class PanelLineaSimple extends JPanel {
        private final List<String> fechas;
        private final List<Double> valores;
        private final boolean modoOscuro;

        public PanelLineaSimple(List<String> fechas, List<Double> valores, boolean modoOscuro) {
            this.fechas = fechas;
            this.valores = valores;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (valores.isEmpty()) {
                g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
                g2.drawString("No hay datos para mostrar.", 50, 50);
                return;
            }

            Color colorTexto = modoOscuro ? Color.WHITE : Color.BLACK;
            Color colorEjes = modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY;
            Color colorLinea = modoOscuro ? new Color(100, 149, 237) : new Color(41, 128, 185);
            Color colorPuntos = modoOscuro ? new Color(80, 129, 217) : new Color(31, 108, 165);

            int w = getWidth(), h = getHeight();
            int mIzq = 70, mDer = 80, mSup = 50, mInf = 100;
            int areaW = w - mIzq - mDer;
            int areaH = h - mSup - mInf;
            int n = valores.size();

            double maxVal = Math.max(valores.stream().mapToDouble(Double::doubleValue).max().orElse(10), 10);

            // Grid horizontal
            int nGrid = 5;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= nGrid; i++) {
                int yGrid = h - mInf - (areaH * i / nGrid);
                g2.setColor(colorEjes);
                float[] dash = { 4f };
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
                g2.drawLine(mIzq, yGrid, w - mDer, yGrid);

                // Etiquetas eje Y
                String etiq = String.valueOf((int) (maxVal * i / nGrid));
                g2.setColor(colorTexto);
                g2.drawString(etiq, mIzq - g2.getFontMetrics().stringWidth(etiq) - 5, yGrid + 4);
            }
            g2.setStroke(new BasicStroke(1.5f));

            // Calcular coordenadas de puntos
            int[] px = new int[n];
            int[] py = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = mIzq + (n > 1 ? i * areaW / (n - 1) : areaW / 2);
                py[i] = h - mInf - (int) (areaH * valores.get(i) / maxVal);
            }

            // Línea principal conectora
            g2.setColor(colorLinea);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < n - 1; i++) {
                g2.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
            }

            // Puntos y etiquetas X
            for (int i = 0; i < n; i++) {
                // Borrar fondo del punto para que sea más limpio
                g2.setColor(getBackground());
                g2.fillOval(px[i] - 5, py[i] - 5, 10, 10);

                // Dibujar el contorno del punto
                g2.setColor(colorPuntos);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval(px[i] - 5, py[i] - 5, 10, 10);

                // Valor numérico encima
                g2.setColor(colorTexto);
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                String val = String.valueOf((int) Math.round(valores.get(i)));
                g2.drawString(val, px[i] - g2.getFontMetrics().stringWidth(val) / 2, py[i] - 12);

                // Etiqueta Eje X (rotada a veces no hace falta si son cortos, pero por si
                // acaso)
                AffineTransform old = g2.getTransform();
                g2.translate(px[i], h - mInf + 15);
                g2.rotate(Math.toRadians(45));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));

                String labelStr = fechas.get(i);
                if (labelStr != null && labelStr.matches("\\d{4}-\\d{2}")) {
                    String[] meses = { "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov",
                            "Dic" };
                    int m = Integer.parseInt(labelStr.substring(5, 7));
                    labelStr = meses[m - 1] + " " + labelStr.substring(0, 4);
                }

                g2.drawString(labelStr, 0, 0);
                g2.setTransform(old);
            }

            // Ejes fijos
            g2.setColor(modoOscuro ? new Color(100, 100, 100) : Color.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mIzq, mSup, mIzq, h - mInf); // Y
            g2.drawLine(mIzq, h - mInf, w - mDer, h - mInf); // X

            // Título Y general
            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g2.drawString("Páginas Leídas", mIzq + 5, mSup - 10);
        }
    }

    /**
     * Gráfico de dispersión (Scatter Plot) para Correlación Minutos vs PPM.
     */
    public static class PanelScatter extends JPanel {
        private final List<String> minutosStr;
        private final List<Double> ppm;
        private final boolean modoOscuro;

        public PanelScatter(List<String> minutos, List<Double> ppm, boolean modoOscuro) {
            this.minutosStr = minutos;
            this.ppm = ppm;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (ppm.isEmpty()) {
                g2.setColor(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
                g2.drawString("No hay suficientes datos para correlación.", 50, 50);
                return;
            }

            int w = getWidth(), h = getHeight();
            int mIzq = 70, mDer = 80, mSup = 50, mInf = 60;
            int areaW = w - mIzq - mDer, areaH = h - mSup - mInf;

            double maxMinutos = minutosStr.stream().mapToDouble(Double::parseDouble).max().orElse(10);
            double maxPpm = ppm.stream().mapToDouble(Double::doubleValue).max().orElse(10);

            // Dibujar Grid y Etiquetas Eje Y (PPM)
            int nGridY = 5;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            Color colorTexto = modoOscuro ? Color.WHITE : Color.BLACK;
            for (int i = 0; i <= nGridY; i++) {
                int yGrid = h - mInf - (areaH * i / nGridY);
                // Grid horizontal
                g2.setColor(modoOscuro ? new Color(60, 60, 60) : new Color(220, 220, 220));
                g2.setStroke(
                        new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 4f }, 0));
                g2.drawLine(mIzq, yGrid, w - mDer, yGrid);

                // Etiqueta Y
                String etiq = String.format("%.1f", maxPpm * i / nGridY);
                g2.setColor(colorTexto);
                g2.drawString(etiq, mIzq - g2.getFontMetrics().stringWidth(etiq) - 5, yGrid + 4);
            }

            // Dibujar Grid y Etiquetas Eje X (Minutos)
            int nGridX = 5;
            for (int i = 0; i <= nGridX; i++) {
                int xGrid = mIzq + (areaW * i / nGridX);
                // Grid vertical
                g2.setColor(modoOscuro ? new Color(60, 60, 60) : new Color(220, 220, 220));
                g2.setStroke(
                        new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 4f }, 0));
                g2.drawLine(xGrid, mSup, xGrid, h - mInf);

                // Etiqueta X
                String etiq = String.valueOf((int) (maxMinutos * i / nGridX));
                g2.setColor(colorTexto);
                g2.drawString(etiq, xGrid - g2.getFontMetrics().stringWidth(etiq) / 2, h - mInf + 15);
            }

            // Dibujar Ejes Principales
            g2.setColor(modoOscuro ? new Color(100, 100, 100) : Color.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(mIzq, mSup, mIzq, h - mInf); // Y
            g2.drawLine(mIzq, h - mInf, w - mDer, h - mInf); // X

            // Calcular coordenadas y ordenar para dibujar las líneas
            List<Point> puntos = new ArrayList<>();
            for (int i = 0; i < ppm.size(); i++) {
                double min = Double.parseDouble(minutosStr.get(i));
                double p = ppm.get(i);

                int x = mIzq + (int) ((min / maxMinutos) * areaW);
                int y = h - mInf - (int) ((p / maxPpm) * areaH);
                puntos.add(new Point(x, y));
            }

            // Ordenar por coordenada X para la línea de tendencia
            puntos.sort(Comparator.comparingInt(p -> p.x));

            // Dibujar las líneas conectando los puntos
            if (puntos.size() > 1) {
                g2.setColor(new Color(100, 149, 237, 100)); // Azul translúcido para la línea
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < puntos.size() - 1; i++) {
                    Point p1 = puntos.get(i);
                    Point p2 = puntos.get(i + 1);
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }

            // Puntos de dispersión (con borde para mejor visibilidad)
            for (Point p : puntos) {
                g2.setColor(new Color(100, 149, 237, 180)); // Azul semitransparente relleno
                g2.fillOval(p.x - 5, p.y - 5, 10, 10);
                g2.setColor(new Color(70, 130, 200)); // Borde más oscuro
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawOval(p.x - 5, p.y - 5, 10, 10);
            }

            // Etiquetas de los ejes
            g2.setColor(colorTexto);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString("Duración de la Sesión (Minutos)", w / 2 - 80, h - 20);

            AffineTransform old = g2.getTransform();
            g2.translate(30, h / 2 + 40);
            g2.rotate(-Math.PI / 2);
            g2.drawString("Velocidad Lectora (PPM)", 0, 0);
            g2.setTransform(old);
        }
    }

    /**
     * Gráfico de Donut para la Meta Anual de Lectura.
     */
    public static class PanelMetaAnual extends JPanel {
        private final int terminados;
        private final int meta;
        private final boolean modoOscuro;

        public PanelMetaAnual(int terminados, int meta, boolean modoOscuro) {
            this.terminados = terminados;
            this.meta = meta;
            this.modoOscuro = modoOscuro;
            setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int radioExterior = 150;
            int radioInterior = 110;

            double porcentaje = Math.min(1.0, (double) terminados / meta);
            int anguloLleno = (int) (porcentaje * 360);

            // Fondo del anillo (Gris)
            g2.setColor(modoOscuro ? new Color(60, 60, 60) : new Color(220, 220, 220));
            g2.fillOval(cx - radioExterior, cy - radioExterior, radioExterior * 2, radioExterior * 2);

            // Relleno del anillo (Verde o Azul dependiendo del progreso)
            g2.setColor(terminados >= meta ? new Color(46, 204, 113) : new Color(70, 130, 180));
            g2.fillArc(cx - radioExterior, cy - radioExterior, radioExterior * 2, radioExterior * 2, 90, -anguloLleno);

            // Hueco central (Color de fondo para hacer el donut)
            g2.setColor(getBackground());
            g2.fillOval(cx - radioInterior, cy - radioInterior, radioInterior * 2, radioInterior * 2);

            // Textos centrales
            g2.setColor(modoOscuro ? Color.WHITE : Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 40));
            String txtCentro = terminados + " / " + meta;
            g2.drawString(txtCentro, cx - g2.getFontMetrics().stringWidth(txtCentro) / 2, cy + 15);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
            String subTxt = "Libros Terminados";
            g2.drawString(subTxt, cx - g2.getFontMetrics().stringWidth(subTxt) / 2, cy + 45);
        }
    }
}