package ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import db.DatabaseManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interfaz gráfica principal de Reading Tracker PRO.
 * MEJORAS aplicadas:
 * - {@link SessionTimer} gestiona el cronómetro (separación de
 * responsabilidades)
 * - LRU cache para portadas (máx. 30 entradas) evita crecimiento ilimitado de
 * memoria
 * - {@link WindowAdapter} avisa si se cierra con una sesión en marcha
 * - Indicador visual de sincronización pendiente en la barra de título
 * - Cálculos PPM/PPH delegados a {@link utils.ReadingCalculator}
 */
public class ReadingTrackerGUI extends JFrame {

    // ── MEJORA: LRU cache limitado a 30 portadas ──────────────────────────────
    private static final int MAX_PORTADAS_CACHE = 30;
    private final Map<String, Image> cachePortadas = new LinkedHashMap<>(MAX_PORTADAS_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_PORTADAS_CACHE;
        }
    };

    // ── MEJORA: Cronómetro extraído a su propia clase ─────────────────────────
    private final SessionTimer sessionTimer;

    // Componentes de la interfaz
    private BookSearchField libroSearch;
    private StateSelectorField estadoCombo;
    private JButton agregarLibroBtn, editarLibroBtn, eliminarLibroBtn, darkModeBtn,
            iniciarBtn, pausarBtn, terminarBtn, verGraficoBtn, historialBtn,
            correctaCoverBtn, otraCoverBtn, subirPortadaBtn;
    private JTextField capituloField, paginaInicioField, paginaFinField;
    private JLabel tiempoLabel, rachaLabel, coverLabel, lblEstimacion, lblPorcentaje;
    private JPanel mainPanel, coverOptionsPanel;

    private boolean esModoOscuro = false;

    // Selección de portada
    private List<String> currentCoverUrls = new ArrayList<>();
    private int currentCoverIndex = 0;

    // Indicador de sync pendiente (actualizado periódicamente)
    private final Timer syncCheckTimer;

    public ReadingTrackerGUI() {
        super("📚 Reading Tracker PRO");

        try {
            if (utils.ConfigManager.isDarkMode())
                FlatDarkLaf.setup();
            else
                FlatLightLaf.setup();
        } catch (Exception ignored) {
        }

        setSize(480, 720);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // MEJORA: controlar el cierre manualmente

        // ── MEJORA: WindowListener para detectar cronómetro activo al cerrar ─────
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (sessionTimer != null && sessionTimer.isCorriendo()) {
                    int opcion = JOptionPane.showOptionDialog(
                            ReadingTrackerGUI.this,
                            "⏱️ Tienes una sesión de lectura en marcha.\n¿Qué quieres hacer?",
                            "Sesión activa",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            new String[] { "Guardar y salir", "Salir sin guardar", "Cancelar" },
                            "Cancelar");
                    if (opcion == 0) {
                        // Guardar sesión y salir
                        procesarTerminarSesion();
                        salirAplicacion();
                    } else if (opcion == 1) {
                        salirAplicacion();
                    }
                    // opcion == 2 o cerrar el diálogo → no hacer nada
                } else {
                    salirAplicacion();
                }
            }
        });

        setLocationRelativeTo(null);

        // Inicializar cronómetro
        sessionTimer = new SessionTimer(tiempo -> tiempoLabel.setText(tiempo));

        inicializarComponentes();
        configurarEventos();
        actualizarComboLibros();
        actualizarVistaRacha();

        esModoOscuro = utils.ConfigManager.isDarkMode();
        aplicarTema(esModoOscuro);

        if (libroSearch.getSelectedBook() != null) {
            cargarPortadaAsincrona(libroSearch.getSelectedBook());
        }

        // ── MEJORA: Comprueba cada 60 s si hay sync pendiente y actualiza el título
        syncCheckTimer = new Timer(60_000, ignored -> actualizarTituloConSyncStatus());
        syncCheckTimer.start();
        actualizarTituloConSyncStatus(); // Primera comprobación inmediata
    }

    private void salirAplicacion() {
        if (syncCheckTimer != null)
            syncCheckTimer.stop();
        dispose();
        System.exit(0);
    }

    /**
     * Actualiza el título de la ventana indicando si hay datos pendientes de subir.
     */
    private void actualizarTituloConSyncStatus() {
        boolean pendiente = DatabaseManager.haySincronizacionPendiente();
        if (pendiente) {
            setTitle("📚 Reading Tracker PRO  ⏳ (sincronización pendiente)");
        } else if (utils.ConfigManager.isOfflineMode()) {
            setTitle("📚 Reading Tracker PRO  🔌 (modo offline)");
        } else {
            setTitle("📚 Reading Tracker PRO");
        }
    }

    private void inicializarComponentes() {
        mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        libroSearch = new BookSearchField();
        agregarLibroBtn = new JButton("➕ Añadir Nuevo Libro");
        editarLibroBtn = new JButton("...");
        eliminarLibroBtn = new JButton("Eliminar libro seleccionado");
        eliminarLibroBtn.setToolTipText("Eliminar el libro seleccionado y todas sus sesiones");
        darkModeBtn = new JButton("🌙");
        rachaLabel = new JLabel("🔥 Racha: 0 días", SwingConstants.RIGHT);
        rachaLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, 0));
        leftPanel.setBorder(new EmptyBorder(0, 0, 0, 15));
        leftPanel.setOpaque(false);

        coverLabel = new JLabel();
        coverLabel.setPreferredSize(new Dimension(120, 170));
        coverLabel.setOpaque(true);
        coverLabel.setHorizontalAlignment(SwingConstants.CENTER);
        coverLabel.setVerticalAlignment(SwingConstants.CENTER);
        coverLabel.setText("Sin Portada");
        coverLabel.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150, 50)));

        correctaCoverBtn = createStyledButton("✓", new Color(46, 204, 113));
        correctaCoverBtn.setToolTipText("Portada correcta");
        otraCoverBtn = createStyledButton("↻", new Color(52, 152, 219));
        otraCoverBtn.setToolTipText("Buscar otra");
        subirPortadaBtn = createStyledButton("Subir Portada", new Color(149, 165, 166));

        coverOptionsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        coverOptionsPanel.setOpaque(false);
        coverOptionsPanel.add(correctaCoverBtn);
        coverOptionsPanel.add(otraCoverBtn);
        coverOptionsPanel.setVisible(false);

        JPanel botonesPortadaPanel = new JPanel(new BorderLayout(0, 5));
        botonesPortadaPanel.setOpaque(false);
        botonesPortadaPanel.add(coverOptionsPanel, BorderLayout.NORTH);
        botonesPortadaPanel.add(subirPortadaBtn, BorderLayout.SOUTH);

        JPanel coverGroupPanel = new JPanel(new BorderLayout(0, 5));
        coverGroupPanel.setOpaque(false);
        coverGroupPanel.add(coverLabel, BorderLayout.CENTER);
        coverGroupPanel.add(botonesPortadaPanel, BorderLayout.SOUTH);

        estadoCombo = new StateSelectorField();
        estadoCombo.setEnabled(false);
        estadoCombo.setPreferredSize(new Dimension(140, 30));
        JPanel estadoPanel = new JPanel(new BorderLayout());
        estadoPanel.setOpaque(false);
        estadoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Estado del libro"),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        estadoPanel.add(estadoCombo, BorderLayout.CENTER);

        JPanel leftTopWrapper = new JPanel(new BorderLayout(0, 20));
        leftTopWrapper.setOpaque(false);
        leftTopWrapper.add(coverGroupPanel, BorderLayout.NORTH);
        leftTopWrapper.add(estadoPanel, BorderLayout.CENTER);
        leftPanel.add(leftTopWrapper, BorderLayout.NORTH);

        capituloField = new JTextField();
        paginaInicioField = new JTextField();
        paginaFinField = new JTextField();

        iniciarBtn = new JButton("▶ INICIAR");
        pausarBtn = new JButton("⏸ PAUSAR");
        terminarBtn = new JButton("⏹ TERMINAR");
        verGraficoBtn = new JButton("📊 VER PROGRESO");
        historialBtn = new JButton("📜 EDITAR REGISTROS");
        pausarBtn.setEnabled(false);

        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 2, 5, 2);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        topPanel.add(new JLabel("📖 Selecciona tu lectura:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        topPanel.add(rachaLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        topPanel.add(libroSearch, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        topPanel.add(darkModeBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        topPanel.add(agregarLibroBtn, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        topPanel.add(editarLibroBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        topPanel.add(eliminarLibroBtn, gbc);
        gbc.gridwidth = 1;

        tiempoLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        tiempoLabel.setFont(new Font("Monospaced", Font.BOLD, 40));

        JPanel centerPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        centerPanel.setOpaque(false);
        centerPanel.add(new JLabel("🔖 Capítulo / Relato:"));
        centerPanel.add(capituloField);
        centerPanel.add(new JLabel("📄 Página inicio:"));
        centerPanel.add(paginaInicioField);
        centerPanel.add(new JLabel("📄 Página fin:"));
        centerPanel.add(paginaFinField);

        JPanel botPanel = new JPanel(new BorderLayout(10, 10));
        botPanel.setOpaque(false);

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        timePanel.setOpaque(false);
        timePanel.add(new JLabel("⏱️"));
        timePanel.add(tiempoLabel);

        lblPorcentaje = new JLabel("Progreso: --%", SwingConstants.CENTER);
        lblPorcentaje.setFont(new Font("SansSerif", Font.BOLD, 14));
        lblPorcentaje.setForeground(new Color(70, 130, 180));

        lblEstimacion = new JLabel("Tiempo est. restante: --", SwingConstants.CENTER);
        lblEstimacion.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lblEstimacion.setForeground(Color.GRAY);

        JPanel timeContainer = new JPanel(new BorderLayout(5, 5));
        timeContainer.setOpaque(false);
        timeContainer.add(lblPorcentaje, BorderLayout.NORTH);
        timeContainer.add(timePanel, BorderLayout.CENTER);
        timeContainer.add(lblEstimacion, BorderLayout.SOUTH);
        botPanel.add(timeContainer, BorderLayout.NORTH);

        JPanel buttonsGrid = new JPanel(new GridLayout(3, 2, 8, 8));
        buttonsGrid.setOpaque(false);
        buttonsGrid.add(iniciarBtn);
        buttonsGrid.add(pausarBtn);
        buttonsGrid.add(terminarBtn);
        buttonsGrid.add(verGraficoBtn);
        buttonsGrid.add(historialBtn);
        botPanel.add(buttonsGrid, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(botPanel, BorderLayout.SOUTH);
        add(mainPanel);
    }

    private void updateButtonStates() {
        iniciarBtn.setEnabled(!sessionTimer.isCorriendo());
        pausarBtn.setEnabled(sessionTimer.isCorriendo());
    }

    private void aplicarTema(boolean oscuro) {
        try {
            if (oscuro)
                UIManager.setLookAndFeel(new FlatDarkLaf());
            else
                UIManager.setLookAndFeel(new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ex) {
            System.err.println("[ReadingTrackerGUI] Error al cambiar tema: " + ex.getMessage());
        }

        Color bg = oscuro ? new Color(30, 30, 30) : new Color(245, 245, 245);
        Color fg = oscuro ? Color.WHITE : Color.BLACK;
        Color inputBg = oscuro ? new Color(50, 50, 50) : Color.WHITE;

        mainPanel.setBackground(bg);
        darkModeBtn.setText(oscuro ? "☀️" : "🌙");

        cambiarColorRecursivo(mainPanel, bg, fg, inputBg, oscuro);
        actualizarVistaRacha();
    }

    private void actualizarColoresUI() {
        Color bg = esModoOscuro ? new Color(30, 30, 30) : new Color(245, 245, 245);
        Color fg = esModoOscuro ? Color.WHITE : Color.BLACK;
        Color inputBg = esModoOscuro ? new Color(50, 50, 50) : Color.WHITE;
        cambiarColorRecursivo(mainPanel, bg, fg, inputBg, esModoOscuro);
    }

    private void cambiarColorRecursivo(Container container, Color bg, Color fg, Color inputBg, boolean oscuro) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton b) {
                b.setOpaque(true);
                b.setContentAreaFilled(true);
                b.setBorder(BorderFactory.createLineBorder(oscuro ? new Color(60, 60, 60) : Color.LIGHT_GRAY));
                if (b.isEnabled()) {
                    b.setBackground(oscuro ? new Color(60, 60, 60) : new Color(225, 225, 225));
                    b.setForeground(fg);
                } else {
                    b.setBackground(oscuro ? new Color(45, 45, 45) : new Color(240, 240, 240));
                    b.setForeground(oscuro ? Color.GRAY : Color.LIGHT_GRAY);
                }
            } else if (c instanceof BookSearchField bsf) {
                bsf.applyTheme(bg, fg, inputBg);
            } else if (c instanceof StateSelectorField ssf) {
                ssf.applyTheme(bg, fg, inputBg);
            } else if (c instanceof JTextField) {
                c.setBackground(inputBg);
                c.setForeground(fg);
                ((JTextField) c).setCaretColor(fg);
                ((JTextField) c).setBorder(BorderFactory.createLineBorder(oscuro ? Color.GRAY : Color.LIGHT_GRAY));
            } else if (c instanceof JLabel) {
                if (c == coverLabel) {
                    c.setBackground(oscuro ? new Color(45, 45, 45) : new Color(235, 235, 235));
                    c.setForeground(oscuro ? Color.LIGHT_GRAY : Color.GRAY);
                } else if (c != lblEstimacion && c != lblPorcentaje && c != rachaLabel) {
                    c.setForeground(fg);
                }
            }
            if (c instanceof Container) {
                cambiarColorRecursivo((Container) c, bg, fg, inputBg, oscuro);
            }
        }
    }

    private void configurarEventos() {
        estadoCombo.setOnSelectionChanged(nuevoEstado -> {
            String libro = libroSearch.getSelectedBook();
            if (libro != null && estadoCombo.isEnabled()) {
                int id = DatabaseManager.obtenerLibroId(libro);
                DatabaseManager.actualizarEstadoLibro(id, nuevoEstado);
            }
        });

        darkModeBtn.addActionListener(ignored -> {
            esModoOscuro = !esModoOscuro;
            utils.ConfigManager.setDarkMode(esModoOscuro);
            aplicarTema(esModoOscuro);
        });

        libroSearch.setOnSelectionChanged(libro -> {
            if (libro != null) {
                int id = DatabaseManager.obtenerLibroId(libro);
                estadoCombo.setEnabled(false);
                String estado = DatabaseManager.obtenerEstadoLibro(id);
                if (estado == null || estado.isEmpty())
                    estado = "Por leer";
                estadoCombo.setSelectedItem(estado);
                estadoCombo.setEnabled(true);
                paginaInicioField.setText(String.valueOf(DatabaseManager.obtenerUltimaPaginaLeida(id)));
                actualizarEstimacion();
                resetearSesionLocal();
                actualizarColoresUI();
                cargarPortadaAsincrona(libro);
                capituloField.requestFocusInWindow();
            }
        });

        // ── MEJORA: Iniciar/Pausar usan SessionTimer ──────────────────────────────
        iniciarBtn.addActionListener(ignored -> {
            sessionTimer.iniciar();
            iniciarBtn.setEnabled(false);
            pausarBtn.setEnabled(true);
            actualizarColoresUI();
        });

        pausarBtn.addActionListener(ignored -> {
            sessionTimer.pausar();
            iniciarBtn.setEnabled(true);
            pausarBtn.setEnabled(false);
            actualizarColoresUI();
        });

        terminarBtn.addActionListener(ignored -> {
            procesarTerminarSesion();
            actualizarColoresUI();
        });

        agregarLibroBtn.addActionListener(ignored -> {
            String nombre = JOptionPane.showInputDialog(this, "Nombre del nuevo libro:");
            if (nombre != null && !nombre.trim().isEmpty()) {
                String pagsStr = JOptionPane.showInputDialog(this, "Número total de páginas (opcional):", "0");
                int pags = 0;
                try {
                    pags = Integer.parseInt(pagsStr);
                } catch (NumberFormatException ex) {
                    /* valor por defecto 0 */ }
                DatabaseManager.guardarLibro(nombre.trim(), pags);
                actualizarComboLibros();
                libroSearch.setSelectedBook(nombre.trim());
                int id = DatabaseManager.obtenerLibroId(nombre.trim());
                paginaInicioField.setText(String.valueOf(DatabaseManager.obtenerUltimaPaginaLeida(id)));
                cargarPortadaAsincrona(nombre.trim());
                actualizarTituloConSyncStatus();
            }
        });

        editarLibroBtn.addActionListener(ignored -> {
            String libro = libroSearch.getSelectedBook();
            if (libro == null)
                return;
            String pagsStr = JOptionPane.showInputDialog(this, "Editar total de páginas para '" + libro + "':");
            if (pagsStr != null) {
                try {
                    int nuevasPags = Integer.parseInt(pagsStr);
                    int libroId = DatabaseManager.obtenerLibroId(libro);
                    DatabaseManager.actualizarPaginasTotales(libroId, nuevasPags);
                    JOptionPane.showMessageDialog(this, "✅ Total de páginas actualizado.");
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "❌ Por favor, introduce un número válido.");
                }
            }
        });

        eliminarLibroBtn.addActionListener(ignored -> {
            String libro = libroSearch.getSelectedBook();
            if (libro == null) {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
                return;
            }
            int confirmacion = JOptionPane.showConfirmDialog(this,
                    "¿Estás seguro de que quieres eliminar \"" + libro + "\"?\n\n"
                            + "Se eliminarán también todas sus sesiones de lectura.\n"
                            + "Esta acción no se puede deshacer.",
                    "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmacion == JOptionPane.YES_OPTION) {
                int libroId = DatabaseManager.obtenerLibroId(libro);
                if (DatabaseManager.eliminarLibro(libroId)) {
                    actualizarComboLibros();
                    libroSearch.setSelectedBook(null);
                    coverLabel.setIcon(null);
                    coverLabel.setText("Sin Portada");
                    estadoCombo.setEnabled(false);
                    paginaInicioField.setText("");
                    paginaFinField.setText("");
                    capituloField.setText("");
                    actualizarVistaRacha();
                    actualizarEstimacion();
                    JOptionPane.showMessageDialog(this, "✅ Libro eliminado correctamente.");
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Error al eliminar el libro.");
                }
            }
        });

        verGraficoBtn.addActionListener(ignored -> {
            String libro = libroSearch.getSelectedBook();
            new GraphWindow(esModoOscuro, libro).setVisible(true);
        });

        historialBtn.addActionListener(ignored -> {
            String libroSeleccionado = libroSearch.getSelectedBook();
            if (libroSeleccionado != null) {
                int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);
                HistoryWindow hw = new HistoryWindow(this, libroId, libroSeleccionado);
                hw.setVisible(true);
                actualizarVistaRacha();
                actualizarEstimacion();
                int ultimaPag = DatabaseManager.obtenerUltimaPagina(libroId);
                paginaInicioField.setText(String.valueOf(ultimaPag));
                actualizarTituloConSyncStatus();
            } else {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
            }
        });

        rachaLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rachaLabel.setToolTipText("Haz clic para cambiar tu meta diaria");
        rachaLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                String metaStr = JOptionPane.showInputDialog(ReadingTrackerGUI.this,
                        "¿Cuántas páginas quieres leer al día?", utils.ConfigManager.getDailyGoal());
                if (metaStr != null) {
                    try {
                        int meta = Integer.parseInt(metaStr);
                        utils.ConfigManager.setDailyGoal(meta);
                        actualizarVistaRacha();
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(ReadingTrackerGUI.this, "⚠️ Introduce un número válido.");
                    }
                }
            }
        });

        otraCoverBtn.addActionListener(ignored -> {
            if (!currentCoverUrls.isEmpty()) {
                currentCoverIndex = (currentCoverIndex + 1) % currentCoverUrls.size();
                mostrarPortadaLocal(currentCoverUrls.get(currentCoverIndex));
            }
        });

        correctaCoverBtn.addActionListener(ignored -> {
            String libro = libroSearch.getSelectedBook();
            if (libro != null && !currentCoverUrls.isEmpty()) {
                int libroId = DatabaseManager.obtenerLibroId(libro);
                String selectedUrl = currentCoverUrls.get(currentCoverIndex);
                DatabaseManager.guardarCoverUrl(libroId, selectedUrl);
                coverOptionsPanel.setVisible(false);
                JOptionPane.showMessageDialog(this, "Portada guardada correctamente.");
            }
        });

        subirPortadaBtn.addActionListener(ignored -> {
            String libro = libroSearch.getSelectedBook();
            if (libro == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un libro primero.");
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Seleccionar Portada para: " + libro);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Imágenes (JPG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                int libroId = DatabaseManager.obtenerLibroId(libro);
                String fileName = selectedFile.getName();
                String extension = fileName.contains(".")
                        ? fileName.substring(fileName.lastIndexOf('.') + 1)
                        : "jpg";

                subirPortadaBtn.setEnabled(false);
                subirPortadaBtn.setText("Subiendo...");

                new SwingWorker<String, Void>() {
                    @Override
                    protected String doInBackground() {
                        if (!utils.ConfigManager.isOfflineMode()) {
                            String remoteUrl = utils.BookCoverService.subirPortadaASupabase(selectedFile, libroId,
                                    extension);
                            if (remoteUrl != null)
                                return remoteUrl;
                            System.err.println("[GUI] Fallo al subir a Supabase, usando copia local como fallback.");
                        }
                        return utils.FileUtil.saveBookCover(selectedFile, libroId);
                    }

                    @Override
                    protected void done() {
                        subirPortadaBtn.setEnabled(true);
                        subirPortadaBtn.setText("Subir Portada");
                        try {
                            String coverUrl = get();
                            if (coverUrl != null) {
                                DatabaseManager.guardarCoverUrl(libroId, coverUrl);
                                cachePortadas.remove(coverUrl); // forzar recarga
                                cargarPortadaAsincrona(libro);
                                coverLabel.revalidate();
                                coverLabel.repaint();
                                mainPanel.revalidate();
                                mainPanel.repaint();
                                if (!coverUrl.startsWith("http")) {
                                    JOptionPane.showMessageDialog(ReadingTrackerGUI.this,
                                            "⚠️ Portada guardada solo en este equipo.\n" +
                                                    "No se pudo subir a la nube.",
                                            "Portada local", JOptionPane.WARNING_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(ReadingTrackerGUI.this,
                                            "✅ Portada subida a la nube.\nEstarás disponible desde cualquier equipo.");
                                }
                            } else {
                                JOptionPane.showMessageDialog(ReadingTrackerGUI.this,
                                        "❌ No se pudo guardar la portada.");
                            }
                        } catch (Exception ex) {
                            subirPortadaBtn.setEnabled(true);
                            subirPortadaBtn.setText("Subir Portada");
                            JOptionPane.showMessageDialog(ReadingTrackerGUI.this,
                                    "❌ Error inesperado: " + ex.getMessage());
                        }
                    }
                }.execute();
            }
        });
    }

    private void actualizarComboLibros() {
        List<String> libros = DatabaseManager.obtenerTodosLosLibros();
        libroSearch.setBooks(libros);
    }

    private void resetearSesionLocal() {
        sessionTimer.reiniciar();
        tiempoLabel.setText("00:00:00");
        updateButtonStates();
    }

    private void cargarPortadaAsincrona(String tituloLibro) {
        coverLabel.setIcon(null);
        coverLabel.setText("Buscando...");
        coverOptionsPanel.setVisible(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private Image finalImage = null;
            private boolean isSavedCover = false;

            @Override
            protected Void doInBackground() {
                int libroId = DatabaseManager.obtenerLibroId(tituloLibro);
                String savedUrl = DatabaseManager.obtenerCoverUrl(libroId);

                if (savedUrl != null && !savedUrl.isEmpty()) {
                    isSavedCover = true;
                    finalImage = cachePortadas.computeIfAbsent(savedUrl, url -> {
                        java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(url);
                        return img != null ? img.getScaledInstance(120, 170, Image.SCALE_SMOOTH) : null;
                    });
                } else {
                    currentCoverUrls = utils.BookCoverService.fetchCoverUrls(tituloLibro);
                    currentCoverIndex = 0;
                    if (!currentCoverUrls.isEmpty()) {
                        String firstUrl = currentCoverUrls.getFirst();
                        finalImage = cachePortadas.computeIfAbsent(firstUrl, url -> {
                            java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(url);
                            return img != null ? img.getScaledInstance(120, 170, Image.SCALE_SMOOTH) : null;
                        });
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (finalImage != null) {
                        coverLabel.setText("");
                        coverLabel.setIcon(new ImageIcon(finalImage));
                        if (!isSavedCover && !currentCoverUrls.isEmpty()) {
                            coverOptionsPanel.setVisible(true);
                            otraCoverBtn.setEnabled(currentCoverUrls.size() > 1);
                        }
                        coverLabel.revalidate();
                        coverLabel.repaint();
                    } else {
                        coverLabel.setText("Sin portada");
                    }
                } catch (Exception e) {
                    coverLabel.setText("Error");
                }
            }
        };
        worker.execute();
    }

    private void mostrarPortadaLocal(String url) {
        Image cached = cachePortadas.get(url);
        if (cached != null) {
            coverLabel.setText("");
            coverLabel.setIcon(new ImageIcon(cached));
            return;
        }

        coverLabel.setIcon(null);
        coverLabel.setText("Cargando...");

        new SwingWorker<Image, Void>() {
            @Override
            protected Image doInBackground() {
                java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(url);
                return img != null ? img.getScaledInstance(120, 170, Image.SCALE_SMOOTH) : null;
            }

            @Override
            protected void done() {
                try {
                    Image resultImage = get();
                    if (resultImage != null)
                        cachePortadas.put(url, resultImage);
                    coverLabel.setText("");
                    coverLabel.setIcon(resultImage != null ? new ImageIcon(resultImage) : null);
                    if (resultImage == null)
                        coverLabel.setText("Sin portada");
                } catch (Exception e) {
                    coverLabel.setText("Error");
                }
            }
        }.execute();
    }

    private void actualizarEstimacion() {
        String libro = libroSearch.getSelectedBook();
        if (libro == null) {
            lblEstimacion.setText("Tiempo est. restante: --");
            lblPorcentaje.setText("Progreso: --%");
            return;
        }
        int id = DatabaseManager.obtenerLibroId(libro);
        int totales = DatabaseManager.obtenerPaginasTotales(id);
        int leidas = DatabaseManager.obtenerUltimaPaginaLeida(id);

        // MEJORA: Cálculo delegado a ReadingCalculator
        double porc = utils.ReadingCalculator.calcularPorcentaje(leidas, totales);
        if (porc >= 0) {
            lblPorcentaje.setText(String.format("Progreso: %.1f%%", porc));
            lblPorcentaje.setForeground(porc >= 100 ? new Color(46, 204, 113) : new Color(70, 130, 180));
        } else {
            lblPorcentaje.setText("Progreso: (Total págs. desc.)");
        }

        int restantes = Math.max(0, totales - leidas);
        if (restantes <= 0 && totales > 0) {
            lblEstimacion.setText("¡Libro terminado!");
            return;
        }

        double pph = DatabaseManager.obtenerPromedioPPH(id);
        String estimacion = utils.ReadingCalculator.estimarTiempoRestante(restantes, pph);
        if (estimacion != null) {
            lblEstimacion.setText("Tiempo est. restante: " + estimacion);
        } else {
            lblEstimacion.setText("Tiempo est. restante: Necesitas más sesiones");
        }
    }

    private void procesarTerminarSesion() {
        try {
            int pIni = Integer.parseInt(paginaInicioField.getText().trim());
            int pFin = Integer.parseInt(paginaFinField.getText().trim());
            String tituloLibro = libroSearch.getSelectedBook();
            if (tituloLibro == null) {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
                return;
            }

            double mins = sessionTimer.getTotalMinutos();
            int pagsLeidas = utils.ReadingCalculator.calcularPaginasLeidas(pIni, pFin);

            // MEJORA: Validación centralizada
            String error = utils.ReadingCalculator.validarSesion(pIni, pFin, mins);
            if (error != null) {
                JOptionPane.showMessageDialog(this, "⚠️ " + error);
                return;
            }

            double ppm = utils.ReadingCalculator.calcularPPM(pagsLeidas, mins);
            double pph = utils.ReadingCalculator.calcularPPH(ppm);

            int id = DatabaseManager.obtenerLibroId(tituloLibro);
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            DatabaseManager.guardarSesion(id, capituloField.getText(), pIni, pFin, pagsLeidas, mins, ppm, pph, fecha);

            String mensajeResumen = """
                    📚 RESUMEN DE LECTURA
                    ---------------------------
                    📖 Título: %s
                    ⏱️ Tiempo: %.2f min
                    📄 Páginas: %d
                    🚀 Velocidad: %.2f ppm
                    📅 Fecha: %s
                    ---------------------------"""
                    .formatted(tituloLibro, mins, pagsLeidas, ppm, fecha);
            JOptionPane.showMessageDialog(this, mensajeResumen, "Sesión Finalizada", JOptionPane.INFORMATION_MESSAGE);

            resetearSesionLocal();
            paginaInicioField.setText(String.valueOf(pFin));
            paginaFinField.setText("");
            capituloField.setText("");
            actualizarVistaRacha();
            actualizarEstimacion();
            actualizarTituloConSyncStatus();
            JOptionPane.showMessageDialog(this, "✅ Sesión guardada.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "⚠️ Introduce páginas válidas (números enteros).");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void actualizarVistaRacha() {
        int racha = DatabaseManager.obtenerRachaActual();
        int meta = utils.ConfigManager.getDailyGoal();
        int leidasHoy = DatabaseManager.obtenerPaginasLeidasHoy();

        rachaLabel.setText(String.format("🎯 Meta: %d/%d | 🔥 Racha: %d %s",
                leidasHoy, meta, racha, (racha == 1 ? "día" : "días")));
        rachaLabel.setForeground(leidasHoy >= meta && meta > 0
                ? new Color(46, 204, 113)
                : new Color(255, 140, 0));
    }
}