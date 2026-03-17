package ui;

import db.DatabaseManager;
import model.DataPoint;
import ui.charts.*;
import utils.ExportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * Ventana avanzada de análisis estadístico.
 * <p>
 * Responsabilidades de esta clase:
 * <ul>
 * <li>Construir y disponer los paneles de la ventana (filtros, gráfica,
 * hitos)</li>
 * <li>Gestionar eventos de la UI (cambio de métrica, libro, botones)</li>
 * <li>Delegar la obtención de datos a {@link GraphDataProcessor}</li>
 * <li>Instanciar el panel de gráfica correcto según la métrica</li>
 * </ul>
 * <p>
 * Los paneles de renderizado viven en {@code ui.charts.*}.<br>
 * La lógica de datos vive en {@link GraphDataProcessor}.
 */
public class GraphWindow extends JFrame {

    // ── Estado ────────────────────────────────────────────────────────────────
    private String libroSeleccionado;
    private final boolean modoOscuro;
    private boolean hitosAbiertosUsuario = true;

    // ── Paneles principales ───────────────────────────────────────────────────
    private JPanel container;
    private JPanel filterPanel;
    private JPanel panelHitos;

    // ── Controles de filtro ───────────────────────────────────────────────────
    private JComboBox<String> comboMetrica;
    private BookSearchField libroSearchField;
    private JTextField fieldMinPag, fieldFecha;
    private JCheckBox checkAgrupar, checkCapitulo;
    private JButton btnActualizar, btnExportarPNG, btnVerHitos;
    private JLabel lblLibro, lblProgreso, lblMinPag, lblFecha, labelEstimacion;
    private JProgressBar barraProgreso;

    // ── Hitos personales ──────────────────────────────────────────────────────
    private JLabel lblSesionLarga, lblDiaRecord, lblVelocidadMax;

    // ── Constructor ───────────────────────────────────────────────────────────

    public GraphWindow(boolean modoOscuro, String libroInicial) {
        super("📊 Análisis y Gráficas");
        this.modoOscuro = modoOscuro;
        this.libroSeleccionado = libroInicial;

        setSize(1200, 750);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        inicializarInterfaz();

        if (libroInicial != null) {
            libroSearchField.setSelectedBook(libroInicial);
            comboMetrica.setSelectedItem("Páginas Totales");
            libroSeleccionado = libroInicial;
            refrescarGrafica();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construcción de la interfaz
    // ─────────────────────────────────────────────────────────────────────────

    private void inicializarInterfaz() {
        Color fondo = modoOscuro ? new Color(45, 45, 45) : new Color(240, 240, 240);
        Color texto = modoOscuro ? Color.WHITE : Color.BLACK;

        construirFilterPanel(fondo, texto);
        construirContenedor();
        construirPanelHitos(fondo, texto);

        add(container, BorderLayout.CENTER);
        add(filterPanel, BorderLayout.SOUTH);
        add(panelHitos, BorderLayout.EAST);

        conectarEventos();
        refrescarGrafica();
    }

    // ── Panel de filtros ──────────────────────────────────────────────────────

    private void construirFilterPanel(Color fondo, Color texto) {
        filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        filterPanel.setBackground(fondo);

        // Métrica
        comboMetrica = new JComboBox<>(new String[] {
                "Páginas Totales", "PPM (Velocidad)", "Progreso Acumulado",
                "Meta Anual", "Evolución Mensual",
                "Páginas por día de la semana", "Actividad por Hora",
                "PPM Comparativa", "Correlación: Minutos vs PPM",
                "Mapa de Consistencia"
        });
        comboMetrica.setBackground(modoOscuro ? new Color(60, 60, 60) : Color.WHITE);
        comboMetrica.setForeground(texto);

        // Selector de libro
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
                refrescarGrafica();
            }
        });

        lblLibro = crearLabel("Libro:", texto);
        fieldMinPag = new JTextField("0", 4);
        fieldFecha = new JTextField("", 8);
        lblMinPag = crearLabel("Pág >:", texto);
        lblFecha = crearLabel("Fecha:", texto);

        checkAgrupar = new JCheckBox("Agrupar", true);
        checkCapitulo = new JCheckBox("Capítulos", false);
        checkAgrupar.setBackground(fondo);
        checkAgrupar.setForeground(texto);
        checkCapitulo.setBackground(fondo);
        checkCapitulo.setForeground(texto);

        btnActualizar = new JButton("🔄 Filtros");
        JButton btnRuta = new JButton("📁 Carpeta");
        btnExportarPNG = new JButton("💾 PNG");
        JButton btnExportarCSV = new JButton("📥 CSV");
        btnVerHitos = new JButton("🏆 Récords");

        for (JButton b : new JButton[] { btnActualizar, btnRuta, btnExportarPNG, btnExportarCSV, btnVerHitos })
            configurarBotonPlano(b);
        btnVerHitos.setVisible(false);

        labelEstimacion = new JLabel("");
        labelEstimacion.setFont(new Font("SansSerif", Font.BOLD, 13));
        labelEstimacion.setForeground(modoOscuro ? new Color(100, 149, 237) : new Color(41, 128, 185));

        barraProgreso = new JProgressBar(0, 100);
        barraProgreso.setPreferredSize(new Dimension(150, 20));
        barraProgreso.setStringPainted(true);
        barraProgreso.setForeground(new Color(46, 204, 113));
        UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
        UIManager.put("ProgressBar.selectionBackground", Color.BLACK);
        SwingUtilities.updateComponentTreeUI(barraProgreso);

        // Añadir al panel
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

        // Listener del combo (visibilidad de controles)
        comboMetrica.addItemListener(e -> {
            if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED)
                return;
            actualizarVisibilidadFiltros();
            refrescarGrafica();
        });

        // Listeners de botones definidos en conectarEventos()
        btnActualizar.addActionListener(ignored -> refrescarGrafica());
        btnRuta.addActionListener(ignored -> seleccionarCarpetaExportacion());
        btnExportarCSV.addActionListener(ignored -> exportarCSV());
    }

    // ── Panel contenedor de la gráfica ────────────────────────────────────────

    private void construirContenedor() {
        container = new JPanel(new BorderLayout());
        container.setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
    }

    // ── Panel de hitos personales ─────────────────────────────────────────────

    private void construirPanelHitos(Color fondo, Color texto) {
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
        btnCerrar.addActionListener(ignored -> {
            hitosAbiertosUsuario = false;
            panelHitos.setVisible(false);
            btnVerHitos.setVisible(true);
            revalidate();
        });

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
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    private void conectarEventos() {
        btnVerHitos.addActionListener(ignored -> {
            hitosAbiertosUsuario = true;
            panelHitos.setVisible(true);
            btnVerHitos.setVisible(false);
            revalidate();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibilidad de controles según métrica
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tabla de visibilidad de controles por métrica.
     *
     * <pre>
     * Métrica                        | Libro | Pág> | Fecha | Agrup | Cap | Filtros | Barra | Hitos
     * -------------------------------|-------|------|-------|-------|-----|---------|-------|------
     * Páginas Totales                |  ✓    |  ✓   |  ✓    |  ✓    |  ✓  |   ✓     |  ✓    |  ✓
     * PPM (Velocidad)                |  ✓    |  ✓   |  ✓    |  ✓    |  ✓  |   ✓     |  ✓    |  ✓
     * Progreso Acumulado             |  ✓    |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✓    |  ✓
     * Meta Anual                     |  ✗    |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * Evolución Mensual              |  ✗    |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * Páginas por día de la semana   |  ✓*   |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * Actividad por Hora             |  ✓*   |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * PPM Comparativa                |  ✗    |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * Correlación: Minutos vs PPM    |  ✓*   |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * Mapa de Consistencia           |  ✗    |  ✗   |  ✗    |  ✗    |  ✗  |   ✗     |  ✗    |  ✗
     * </pre>
     * 
     * ✓* = soporta "Todos los libros" además del selector individual.
     */
    private void actualizarVisibilidadFiltros() {
        String sel = (String) comboMetrica.getSelectedItem();

        // ── Clasificación de la métrica ───────────────────────────────────────
        boolean esEstandar = "Páginas Totales".equals(sel) || "PPM (Velocidad)".equals(sel);
        boolean esAcumulado = "Progreso Acumulado".equals(sel);
        boolean soportaTodos = "Páginas por día de la semana".equals(sel)
                || "Actividad por Hora".equals(sel)
                || "Correlación: Minutos vs PPM".equals(sel);
        boolean muestraLibro = esEstandar || esAcumulado || soportaTodos;
        boolean muestraBarraHitos = esEstandar || esAcumulado || soportaTodos;

        // ── Actualizar lista de libros ────────────────────────────────────────
        List<String> lbros = DatabaseManager.obtenerTodosLosLibros();
        if (soportaTodos)
            lbros.addFirst("--- Todos los libros ---");
        String prevSel = libroSeleccionado;
        libroSearchField.setBooks(lbros);
        if (!soportaTodos && "--- Todos los libros ---".equals(prevSel)) {
            libroSeleccionado = lbros.isEmpty() ? null : lbros.getFirst();
        }
        if (libroSeleccionado != null)
            libroSearchField.setSelectedBook(libroSeleccionado);

        // ── Visibilidad de controles ──────────────────────────────────────────
        lblLibro.setVisible(muestraLibro);
        libroSearchField.setVisible(muestraLibro);

        lblMinPag.setVisible(esEstandar);
        fieldMinPag.setVisible(esEstandar);
        lblFecha.setVisible(esEstandar);
        fieldFecha.setVisible(esEstandar);
        checkAgrupar.setVisible(esEstandar);
        checkCapitulo.setVisible(esEstandar);
        btnActualizar.setVisible(esEstandar);

        lblProgreso.setVisible(muestraBarraHitos);
        barraProgreso.setVisible(muestraBarraHitos);

        if (muestraBarraHitos && (esEstandar || esAcumulado)) {
            panelHitos.setVisible(hitosAbiertosUsuario);
            btnVerHitos.setVisible(!hitosAbiertosUsuario);
        } else {
            panelHitos.setVisible(false);
            btnVerHitos.setVisible(false);
        }

        filterPanel.revalidate();
        filterPanel.repaint();
        revalidate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresco de la gráfica
    // ─────────────────────────────────────────────────────────────────────────

    private void refrescarGrafica() {
        String metrica = (String) comboMetrica.getSelectedItem();
        if (metrica == null)
            return;

        // Sincronizar libro seleccionado desde el campo
        if (!esMetricaComparativa(metrica)) {
            String sel = libroSearchField.getSelectedBook();
            if (sel != null)
                libroSeleccionado = sel;
        }

        // Sin libro para métricas individuales → mensaje vacío
        if (!esMetricaGlobal(metrica) && libroSeleccionado == null) {
            mostrarMensajeSinDatos("Selecciona un libro para ver su gráfica.");
            return;
        }

        int minPag = parsearEntero(fieldMinPag.getText(), 0);
        String fechaFiltro = fieldFecha.getText().trim().isEmpty()
                ? "01/01/2000"
                : fieldFecha.getText().trim();

        // Obtener datos
        GraphDataProcessor.GraphData data = GraphDataProcessor.obtener(
                metrica, libroSeleccionado,
                minPag, fechaFiltro,
                checkAgrupar.isSelected(), checkCapitulo.isSelected());

        // Sin datos para métricas estándar
        if (!esMetricaGlobal(metrica) && !esMetricaComparativa(metrica) && data.isEmpty()) {
            String msg = "Este libro aún no tiene sesiones almacenadas.";
            if ("Actividad por Hora".equals(metrica) && libroSeleccionado != null)
                msg = "Este libro no tiene registros de hora (solo fecha).";
            mostrarMensajeSinDatos(msg);
            return;
        }

        // Actualizar barra de progreso e hitos
        actualizarBarraYHitos(metrica);

        // Construir y mostrar el panel de gráfica
        JPanel panel = construirPanel(metrica, data);
        mostrarPanel(panel, metrica);
    }

    // ── Construcción del panel correcto ───────────────────────────────────────

    private JPanel construirPanel(String metrica, GraphDataProcessor.GraphData data) {
        return switch (metrica) {
            case "Mapa de Consistencia" -> {
                PanelHeatmap hp = new PanelHeatmap(data.fechas(), data.valores(), modoOscuro);
                hp.setPreferredSize(new Dimension(850, 500));
                yield hp;
            }
            case "Meta Anual" -> {
                List<model.Libro> todos = DatabaseManager.obtenerTodosLosLibrosDesde("1970-01-01");
                int terminados = (int) todos.stream()
                        .filter(l -> "Terminado".equalsIgnoreCase(l.getEstado())).count();
                PanelMetaAnual pma = new PanelMetaAnual(terminados, 12, modoOscuro);
                pma.setPreferredSize(new Dimension(850, 500));
                yield pma;
            }
            case "Correlación: Minutos vs PPM" -> {
                PanelScatter ps = new PanelScatter(data.fechas(), data.valores(), modoOscuro);
                ps.setPreferredSize(new Dimension(850, 500));
                yield ps;
            }
            case "Páginas por día de la semana", "Actividad por Hora", "Evolución Mensual" -> {
                PanelLineaSimple pls = new PanelLineaSimple(data.fechas(), data.valores(), modoOscuro);
                int w = Math.max(850, data.fechas().size() * 90 + 150);
                pls.setPreferredSize(new Dimension(w, 550));
                yield pls;
            }
            case "PPM Comparativa" -> {
                List<DataPoint> comp = DatabaseManager.obtenerPpmMediaPorLibroTerminado();
                PanelBarrasComparacion pbc = new PanelBarrasComparacion(comp, modoOscuro);
                pbc.setPreferredSize(new Dimension(850, Math.max(400, comp.size() * 55 + 100)));
                yield pbc;
            }
            case "Progreso Acumulado" -> {
                int totalPags = libroSeleccionado != null
                        ? DatabaseManager.obtenerPaginasTotales(DatabaseManager.obtenerLibroId(libroSeleccionado))
                        : 0;
                PanelLineaAcumulada pla = new PanelLineaAcumulada(
                        data.fechas(), data.valores(), totalPags, modoOscuro);
                int w = Math.min(data.fechas().size() * 120 + 150, 1200);
                pla.setPreferredSize(new Dimension(w, 550));
                yield pla;
            }
            default -> {
                PanelGrafica pg = new PanelGrafica(data.fechas(), data.valores(), metrica, modoOscuro);
                int w = Math.max(850, data.fechas().size() * 90 + 150);
                pg.setPreferredSize(new Dimension(w, 550));
                yield pg;
            }
        };
    }

    private void mostrarPanel(JPanel panel, String metrica) {
        // Reconectar listener de exportación PNG al panel actual
        for (var al : btnExportarPNG.getActionListeners())
            btnExportarPNG.removeActionListener(al);
        btnExportarPNG.addActionListener(ignored -> ExportService.exportarAPNG(panel,
                libroSeleccionado != null ? libroSeleccionado : "comparativa"));

        container.removeAll();
        JScrollPane scroll = new JScrollPane(panel);
        scroll.getViewport().setBackground(modoOscuro ? new Color(30, 30, 30) : Color.WHITE);
        scroll.setBorder(null);
        container.add(scroll, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
    }

    private void mostrarMensajeSinDatos(String mensaje) {
        container.removeAll();
        JLabel lbl = new JLabel(mensaje, SwingConstants.CENTER);
        lbl.setForeground(modoOscuro ? Color.LIGHT_GRAY : Color.GRAY);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        container.add(lbl, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
    }

    // ── Barra de progreso e hitos ─────────────────────────────────────────────

    private void actualizarBarraYHitos(String metrica) {
        boolean esGlobal = esMetricaGlobal(metrica);
        if (esGlobal || libroSeleccionado == null) {
            barraProgreso.setVisible(false);
            if (lblProgreso != null)
                lblProgreso.setVisible(false);
            labelEstimacion.setText("");
            labelEstimacion.setVisible(false);
            panelHitos.setVisible(false);
            btnVerHitos.setVisible(false);
            return;
        }

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
            double pct = DatabaseManager.obtenerPorcentajeProgreso(libroId);
            barraProgreso.setValue((int) pct);
            barraProgreso.setToolTipText("Has leído el " + String.format("%.1f", pct) + "% del libro");
            labelEstimacion.setText("");
            labelEstimacion.setVisible(false);
            cargarHitosPersonales();
        }
    }

    private void cargarHitosPersonales() {
        if (libroSeleccionado == null)
            return;
        int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
        double maxMin = DatabaseManager.obtenerSesionMasLarga(libroId);
        String diaRec = DatabaseManager.obtenerDiaMasLectura(libroId);
        double maxPpm = DatabaseManager.obtenerVelocidadMaxima(libroId);

        String cR = modoOscuro ? "#87CEFA" : "#00509E";
        String cT = modoOscuro ? "white" : "black";
        String base = "<html><div style='text-align: center; width: 190px;'>";

        lblSesionLarga.setText(base
                + "<b style='color:" + cT + ";'>⏱️ Sesión más larga</b><br>"
                + "<span style='font-size:15px;color:" + cR + ";'>"
                + String.format("%.1f", maxMin) + " min</span></div></html>");

        lblDiaRecord.setText(base
                + "<b style='color:" + cT + ";'>📅 Día récord</b><br>"
                + "<span style='font-size:13px;color:" + cR + ";'>" + diaRec + "</span></div></html>");

        lblVelocidadMax.setText(base
                + "<b style='color:" + cT + ";'>⚡ Velocidad máxima</b><br>"
                + "<span style='font-size:15px;color:" + cR + ";'>"
                + String.format("%.2f", maxPpm) + " PPM</span></div></html>");

        panelHitos.revalidate();
        panelHitos.repaint();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Acciones de botones
    // ─────────────────────────────────────────────────────────────────────────

    private void seleccionarCarpetaExportacion() {
        JFileChooser chooser = new JFileChooser(ExportService.rutaExportacion);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            ExportService.rutaExportacion = chooser.getSelectedFile().getAbsolutePath();
            utils.ConfigManager.setExportPath(ExportService.rutaExportacion);
        }
    }

    /**
     * Exporta a CSV con cabecera y datos adaptados a la métrica activa.
     *
     * <table>
     * <tr>
     * <th>Métrica</th>
     * <th>Cabecera</th>
     * <th>Nombre archivo</th>
     * </tr>
     * <tr>
     * <td>PPM Comparativa</td>
     * <td>Libro;PPM Media</td>
     * <td>PPM_Comparativa…</td>
     * </tr>
     * <tr>
     * <td>Evolución Mensual</td>
     * <td>Mes;Páginas</td>
     * <td>Evolucion_Mensual</td>
     * </tr>
     * <tr>
     * <td>Páginas por día de la semana</td>
     * <td>Día;Páginas</td>
     * <td>Paginas_Por_Dia_Semana</td>
     * </tr>
     * <tr>
     * <td>Actividad por Hora</td>
     * <td>Hora;Páginas</td>
     * <td>Actividad_Por_Hora</td>
     * </tr>
     * <tr>
     * <td>Correlación: Minutos vs PPM</td>
     * <td>Minutos;PPM</td>
     * <td>Correlacion_Minutos_PPM</td>
     * </tr>
     * <tr>
     * <td>Mapa de Consistencia</td>
     * <td>Fecha;Páginas</td>
     * <td>Mapa_Consistencia</td>
     * </tr>
     * <tr>
     * <td>Meta Anual</td>
     * <td>Libro;Estado</td>
     * <td>Meta_Anual</td>
     * </tr>
     * <tr>
     * <td>Todos los libros (métricas estándar)</td>
     * <td>Libro;Fecha;Capítulo;Páginas;Minutos;PPM;PPH</td>
     * </tr>
     * <tr>
     * <td>Libro individual</td>
     * <td>Fecha;Capítulo;Páginas;Minutos;PPM;PPH</td>
     * </tr>
     * </table>
     */
    private void exportarCSV() {
        String metrica = (String) comboMetrica.getSelectedItem();
        if (metrica == null)
            return;

        List<String[]> datos = new java.util.ArrayList<>();
        String nombreArchivo;

        switch (metrica) {
            case "PPM Comparativa" -> {
                nombreArchivo = "PPM_Comparativa_Libros_Terminados";
                datos.add(new String[] { "Libro", "PPM Media" });
                for (DataPoint p : DatabaseManager.obtenerPpmMediaPorLibroTerminado())
                    datos.add(new String[] { p.getEtiqueta(), String.format("%.2f", p.getValor()) });
            }
            case "Evolución Mensual" -> {
                nombreArchivo = "Evolucion_Mensual";
                datos.add(new String[] { "Mes", "Páginas" });
                GraphDataProcessor.GraphData d = GraphDataProcessor.obtener(
                        metrica, libroSeleccionado, 0, "", false, false);
                for (int i = 0; i < d.fechas().size(); i++)
                    datos.add(new String[] { d.fechas().get(i), String.valueOf(d.valores().get(i).intValue()) });
            }
            case "Páginas por día de la semana" -> {
                nombreArchivo = "Paginas_Por_Dia_Semana";
                datos.add(new String[] { "Día", "Páginas" });
                GraphDataProcessor.GraphData d = GraphDataProcessor.obtener(
                        metrica, libroSeleccionado, 0, "", false, false);
                for (int i = 0; i < d.fechas().size(); i++)
                    datos.add(new String[] { d.fechas().get(i), String.valueOf(d.valores().get(i).intValue()) });
            }
            case "Actividad por Hora" -> {
                nombreArchivo = "Actividad_Por_Hora";
                datos.add(new String[] { "Hora", "Páginas" });
                GraphDataProcessor.GraphData d = GraphDataProcessor.obtener(
                        metrica, libroSeleccionado, 0, "", false, false);
                for (int i = 0; i < d.fechas().size(); i++)
                    datos.add(new String[] { d.fechas().get(i), String.valueOf(d.valores().get(i).intValue()) });
            }
            case "Correlación: Minutos vs PPM" -> {
                nombreArchivo = "Correlacion_Minutos_PPM";
                datos.add(new String[] { "Minutos", "PPM" });
                GraphDataProcessor.GraphData d = GraphDataProcessor.obtener(
                        metrica, libroSeleccionado, 0, "", false, false);
                for (int i = 0; i < d.fechas().size(); i++)
                    datos.add(new String[] { d.fechas().get(i),
                            String.format("%.2f", d.valores().get(i)) });
            }
            case "Mapa de Consistencia" -> {
                nombreArchivo = "Mapa_Consistencia";
                datos.add(new String[] { "Fecha", "Páginas" });
                GraphDataProcessor.GraphData d = GraphDataProcessor.obtener(
                        metrica, libroSeleccionado, 0, "", false, false);
                for (int i = 0; i < d.fechas().size(); i++)
                    datos.add(new String[] { d.fechas().get(i), String.valueOf(d.valores().get(i).intValue()) });
            }
            case "Meta Anual" -> {
                nombreArchivo = "Meta_Anual";
                datos.add(new String[] { "Libro", "Estado" });
                for (model.Libro l : DatabaseManager.obtenerTodosLosLibrosDesde("1970-01-01"))
                    datos.add(new String[] { l.getNombre(), l.getEstado() });
            }
            default -> {
                // Métricas estándar: libro individual o "todos los libros"
                boolean todosTodos = "--- Todos los libros ---".equals(libroSeleccionado);
                int minPag = parsearEntero(fieldMinPag.getText(), 0);
                String fFiltro = fieldFecha.getText().isBlank() ? "01/01/2000" : fieldFecha.getText();
                boolean agrupar = checkAgrupar.isSelected();

                if (todosTodos) {
                    nombreArchivo = "Todos_Los_Libros_" + metrica.replace(" ", "_");
                    datos.add(new String[] { "Libro", "Fecha", "Capítulo", "Páginas", "Minutos", "PPM", "PPH" });
                    datos.addAll(DatabaseManager.obtenerDatosParaExportarTodos(fFiltro, minPag, agrupar));
                } else {
                    if (libroSeleccionado == null)
                        return;
                    nombreArchivo = libroSeleccionado;
                    datos.add(new String[] { "Fecha", "Capítulo", "Páginas", "Minutos", "PPM", "PPH" });
                    int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
                    datos.addAll(DatabaseManager.obtenerDatosParaExportar(libroId, minPag, fFiltro, agrupar));
                }
            }
        }

        ExportService.exportarDatosCSV(datos, nombreArchivo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Métricas que no necesitan libro seleccionado. */
    private static boolean esMetricaGlobal(String m) {
        return "Mapa de Consistencia".equals(m)
                || "PPM Comparativa".equals(m)
                || "Meta Anual".equals(m)
                || "Evolución Mensual".equals(m);
    }

    /** Métricas cuyo panel no usa libro (comparativas + heatmap + meta). */
    private static boolean esMetricaComparativa(String m) {
        return "Mapa de Consistencia".equals(m)
                || "PPM Comparativa".equals(m)
                || "Meta Anual".equals(m)
                || "Evolución Mensual".equals(m);
    }

    private static int parsearEntero(String texto, int valorDefecto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return valorDefecto;
        }
    }

    private void configurarBotonPlano(JButton btn) {
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(
                modoOscuro ? new Color(80, 80, 80) : Color.LIGHT_GRAY));
        btn.setBackground(modoOscuro ? new Color(60, 60, 60) : new Color(225, 225, 225));
        btn.setForeground(modoOscuro ? Color.WHITE : Color.BLACK);
    }

    private JLabel crearLabel(String texto, Color color) {
        JLabel l = new JLabel(texto);
        l.setForeground(color);
        return l;
    }
}