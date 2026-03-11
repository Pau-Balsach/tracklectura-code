package ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import db.DatabaseManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interfaz gráfica principal de la aplicación Reading Tracker PRO.
 * Gestiona la visualización, el cronómetro de lectura y la interacción con la
 * base de datos.
 */
public class ReadingTrackerGUI extends JFrame {

    // Componentes de la interfaz
    private java.util.Map<String, Image> cachePortadas = new java.util.HashMap<>();
    private BookSearchField libroSearch;
    private StateSelectorField estadoCombo;
    private JButton agregarLibroBtn, editarLibroBtn, eliminarLibroBtn, darkModeBtn, iniciarBtn, pausarBtn, terminarBtn,
            verGraficoBtn, historialBtn, correctaCoverBtn, otraCoverBtn, subirPortadaBtn;
    private JTextField capituloField, paginaInicioField, paginaFinField;
    private JLabel tiempoLabel, rachaLabel, coverLabel, lblEstimacion, lblPorcentaje; // Añadido coverLabel,
                                                                                      // lblEstimacion, lblPorcentaje
    private JPanel mainPanel, coverOptionsPanel;

    // Variables de estado y control del tiempo
    private boolean esModoOscuro = false;
    private Timer timer;
    private long tiempoInicio, tiempoAcumulado = 0;
    private boolean corriendo = false;

    // Variables para la selección de portada
    private List<String> currentCoverUrls = new ArrayList<>();
    private int currentCoverIndex = 0;

    public ReadingTrackerGUI() {
        super("📚 Reading Tracker PRO");

        // Configuración de FlatLaf para una apariencia moderna y premium
        try {
            if (utils.ConfigManager.isDarkMode()) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            // Fallback al look and feel del sistema
        }

        setSize(480, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centrar ventana en la pantalla

        // Inicialización de la interfaz
        inicializarComponentes();
        configurarEventos();
        actualizarComboLibros();
        actualizarVistaRacha();

        // Aplicar el tema guardado al iniciar
        esModoOscuro = utils.ConfigManager.isDarkMode();
        aplicarTema(esModoOscuro);

        if (libroSearch.getSelectedBook() != null) {
            cargarPortadaAsincrona(libroSearch.getSelectedBook());
        }
    }

    /**
     * Crea y organiza todos los elementos visuales en la ventana usando diferentes
     * Layouts.
     */
    private void inicializarComponentes() {
        mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Instanciación de componentes
        libroSearch = new BookSearchField();
        agregarLibroBtn = new JButton("➕ Añadir Nuevo Libro");
        editarLibroBtn = new JButton("...");
        eliminarLibroBtn = new JButton("Eliminar libro seleccionado");
        eliminarLibroBtn.setToolTipText("Eliminar el libro seleccionado y todas sus sesiones");
        darkModeBtn = new JButton("🌙");
        rachaLabel = new JLabel("🔥 Racha: 0 días", SwingConstants.RIGHT);
        rachaLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        // Create left panel for cover and status
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, 0)); // Un poco más estrecho para no robar espacio
        leftPanel.setBorder(new EmptyBorder(0, 0, 0, 15));
        leftPanel.setOpaque(false);

        // Cover setup (Corregido: más pequeña, fondo sutil, texto centrado)
        coverLabel = new JLabel();
        coverLabel.setPreferredSize(new Dimension(120, 170)); // Tamaño más pequeño y proporcionado
        coverLabel.setOpaque(true); // Permitimos fondo para el marcador de posición
        coverLabel.setHorizontalAlignment(SwingConstants.CENTER);
        coverLabel.setVerticalAlignment(SwingConstants.CENTER); // Texto en el centro exacto
        coverLabel.setText("Sin Portada");
        coverLabel.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150, 50))); // Borde muy suave

        // Botones de Portada
        correctaCoverBtn = createStyledButton("✓", new Color(46, 204, 113), Color.WHITE, false);
        correctaCoverBtn.setToolTipText("Portada correcta");
        otraCoverBtn = createStyledButton("↻", new Color(52, 152, 219), Color.WHITE, false);
        otraCoverBtn.setToolTipText("Buscar otra");
        subirPortadaBtn = createStyledButton("Subir Portada", new Color(149, 165, 166), Color.WHITE, false);

        // Agrupación de botones
        // 1. Panel de opciones que SÍ se oculta (solo el ✓ y ↻)
        coverOptionsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        coverOptionsPanel.setOpaque(false);
        coverOptionsPanel.add(correctaCoverBtn);
        coverOptionsPanel.add(otraCoverBtn);
        coverOptionsPanel.setVisible(false); // Esto respeta tu código original

        // 2. Contenedor de todos los botones (El de "Subir Portada" queda fuera del
        // panel oculto)
        JPanel botonesPortadaPanel = new JPanel(new BorderLayout(0, 5));
        botonesPortadaPanel.setOpaque(false);
        botonesPortadaPanel.add(coverOptionsPanel, BorderLayout.NORTH);
        botonesPortadaPanel.add(subirPortadaBtn, BorderLayout.SOUTH); // Siempre visible y justo debajo

        // Panel para agrupar portada y botones
        JPanel coverGroupPanel = new JPanel(new BorderLayout(0, 5)); // 5px de separación para que quede pegado pero
                                                                     // respire
        coverGroupPanel.setOpaque(false);
        coverGroupPanel.add(coverLabel, BorderLayout.CENTER);
        coverGroupPanel.add(botonesPortadaPanel, BorderLayout.SOUTH);

        // Selector de estado (deshabilitado hasta que se seleccione un libro)
        estadoCombo = new StateSelectorField();
        estadoCombo.setEnabled(false);
        estadoCombo.setPreferredSize(new Dimension(140, 30));
        JPanel estadoPanel = new JPanel(new BorderLayout());
        estadoPanel.setOpaque(false);

        estadoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Estado del libro"),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        estadoPanel.add(estadoCombo, BorderLayout.CENTER);

        // Wrapper superior para empujar todo hacia arriba
        // 20px de separación vertical entre el grupo de portada y el selector de estado
        JPanel leftTopWrapper = new JPanel(new BorderLayout(0, 20));
        leftTopWrapper.setOpaque(false);
        leftTopWrapper.add(coverGroupPanel, BorderLayout.NORTH);
        leftTopWrapper.add(estadoPanel, BorderLayout.CENTER);

        // Añadimos el contenedor al panel izquierdo
        leftPanel.add(leftTopWrapper, BorderLayout.NORTH);

        capituloField = new JTextField();
        paginaInicioField = new JTextField();
        paginaFinField = new JTextField();

        iniciarBtn = new JButton("▶ INICIAR");
        pausarBtn = new JButton("⏸ PAUSAR");
        terminarBtn = new JButton("⏹ TERMINAR");
        verGraficoBtn = new JButton("📊 VER PROGRESO");
        historialBtn = new JButton("📜 EDITAR REGISTROS");

        pausarBtn.setEnabled(false); // Deshabilitado hasta que se inicie el cronómetro

        // Panel superior: Selección y gestión de libros
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 2, 5, 2);

        // FILA 0: Título a la izquierda, Racha a la derecha
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        topPanel.add(new JLabel("📖 Selecciona tu lectura:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0; // El botón/racha no necesita estirarse
        topPanel.add(rachaLabel, gbc); // <-- Racha arriba a la derecha

        // FILA 1: Buscador de libros y Botón Modo Oscuro
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        topPanel.add(libroSearch, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        topPanel.add(darkModeBtn, gbc); // <-- Movemos el botón de modo oscuro aquí

        // FILA 2: Botones añadir y editar
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        topPanel.add(agregarLibroBtn, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        topPanel.add(editarLibroBtn, gbc);

        // FILA 3: Botón eliminar libro (debajo de añadir)
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        topPanel.add(eliminarLibroBtn, gbc);
        gbc.gridwidth = 1;

        // Instanciar labels antes de añadirlos
        tiempoLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        tiempoLabel.setFont(new Font("Monospaced", Font.BOLD, 40));

        // --- PANEL CENTRAL: Formulario ---
        JPanel centerPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        centerPanel.setOpaque(false);
        centerPanel.add(new JLabel("🔖 Capítulo / Relato:"));
        centerPanel.add(capituloField);
        centerPanel.add(new JLabel("📄 Página inicio:"));
        centerPanel.add(paginaInicioField);
        centerPanel.add(new JLabel("📄 Página fin:"));
        centerPanel.add(paginaFinField);

        // Panel inferior: Cronómetro y Botones
        JPanel botPanel = new JPanel(new BorderLayout(10, 10));
        botPanel.setOpaque(false);

        // Sub-panel para el tiempo (Grande y abajo)
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

        // Sub-panel para botones
        JPanel buttonsGrid = new JPanel(new GridLayout(3, 2, 8, 8));
        buttonsGrid.setOpaque(false);
        buttonsGrid.add(iniciarBtn);
        buttonsGrid.add(pausarBtn);
        buttonsGrid.add(terminarBtn);
        buttonsGrid.add(verGraficoBtn);
        buttonsGrid.add(historialBtn);

        botPanel.add(buttonsGrid, BorderLayout.CENTER);

        // Ensamblaje final de la ventana principal
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(botPanel, BorderLayout.SOUTH);
        add(mainPanel);
    }

    private void updateButtonStates() {
        iniciarBtn.setEnabled(!corriendo);
        pausarBtn.setEnabled(corriendo);
    }

    /**
     * Aplica los colores correspondientes al modo claro u oscuro en toda la
     * interfaz.
     */
    private void aplicarTema(boolean oscuro) {
        try {
            if (oscuro) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Color bg = oscuro ? new Color(30, 30, 30) : new Color(245, 245, 245);
        Color fg = oscuro ? Color.WHITE : Color.BLACK;
        Color inputBg = oscuro ? new Color(50, 50, 50) : Color.WHITE;

        mainPanel.setBackground(bg);
        darkModeBtn.setText(oscuro ? "☀️" : "🌙");

        // Recorre todos los componentes para forzar los colores personalizados
        cambiarColorRecursivo(mainPanel, bg, fg, inputBg, oscuro);

        // REQUISITO: Refrescar la racha al final para que los colores de meta
        // (verde/naranja)
        // no sean sobrescritos por el tema general.
        actualizarVistaRacha();
    }

    /**
     * Método auxiliar para iterar sobre los componentes de un contenedor y aplicar
     * estilos específicos.
     */
    private void cambiarColorRecursivo(Container container, Color bg, Color fg, Color inputBg, boolean oscuro) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton) {
                JButton b = (JButton) c;
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
                } else if (c != lblEstimacion && c != lblPorcentaje) {
                    c.setForeground(fg);
                }
            }
            if (c instanceof Container) {
                cambiarColorRecursivo((Container) c, bg, fg, inputBg, oscuro);
            }
        }
    }

    /**
     * Define el comportamiento (ActionListeners) de todos los botones y campos
     * interactivos.
     */
    private void configurarEventos() {
        // Timer que actualiza la etiqueta del tiempo cada segundo
        timer = new Timer(1000, e -> actualizarCronometro());

        // Evento de cambio en estadoCombo
        estadoCombo.setOnSelectionChanged(nuevoEstado -> {
            String libro = libroSearch.getSelectedBook();
            if (libro != null && estadoCombo.isEnabled()) {
                int id = DatabaseManager.obtenerLibroId(libro);
                DatabaseManager.actualizarEstadoLibro(id, nuevoEstado);
            }
        });

        // Alternar entre modo claro y oscuro
        darkModeBtn.addActionListener(e -> {
            esModoOscuro = !esModoOscuro;
            utils.ConfigManager.setDarkMode(esModoOscuro);
            aplicarTema(esModoOscuro);
        });

        // Callback del buscador de libros
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
                aplicarTema(esModoOscuro);
                cargarPortadaAsincrona(libro);
                capituloField.requestFocusInWindow();
            }
        });

        // Control del cronómetro: Iniciar
        iniciarBtn.addActionListener(e -> {
            corriendo = true;
            tiempoInicio = System.currentTimeMillis();
            timer.start();
            iniciarBtn.setEnabled(false);
            pausarBtn.setEnabled(true);
            aplicarTema(esModoOscuro);
        });

        // Control del cronómetro: Pausar
        pausarBtn.addActionListener(e -> {
            tiempoAcumulado += System.currentTimeMillis() - tiempoInicio;
            timer.stop();
            corriendo = false;
            iniciarBtn.setEnabled(true);
            pausarBtn.setEnabled(false);
            aplicarTema(esModoOscuro);
        });

        // Finaliza y guarda la lectura en la base de datos
        terminarBtn.addActionListener(e -> {
            procesarTerminarSesion();
            aplicarTema(esModoOscuro);
        });

        // Registrar un nuevo libro
        agregarLibroBtn.addActionListener(e -> {
            String nombre = JOptionPane.showInputDialog(this, "Nombre del nuevo libro:");
            if (nombre != null && !nombre.trim().isEmpty()) {
                String pagsStr = JOptionPane.showInputDialog(this, "Número total de páginas (opcional):", "0");
                int pags = 0;
                try {
                    pags = Integer.parseInt(pagsStr);
                } catch (Exception ex) {
                }

                DatabaseManager.guardarLibro(nombre.trim(), pags);
                actualizarComboLibros();
                libroSearch.setSelectedBook(nombre.trim());
                // Disparar la carga de portada y datos del nuevo libro
                int id = DatabaseManager.obtenerLibroId(nombre.trim());
                paginaInicioField.setText(String.valueOf(DatabaseManager.obtenerUltimaPaginaLeida(id)));
                cargarPortadaAsincrona(nombre.trim());
            }
        });

        // Editar la información general del libro seleccionado (ej. total de páginas)
        editarLibroBtn.addActionListener(e -> {
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

        // Eliminar el libro seleccionado (con confirmación)
        eliminarLibroBtn.addActionListener(e -> {
            String libro = libroSearch.getSelectedBook();
            if (libro == null) {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
                return;
            }
            int confirmacion = JOptionPane.showConfirmDialog(this,
                    "¿Estás seguro de que quieres eliminar \"" + libro + "\"?\n\n"
                            + "Se eliminarán también todas sus sesiones de lectura.\n"
                            + "Esta acción no se puede deshacer.",
                    "Confirmar eliminación",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
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

        // Abre la ventana de estadísticas y gráficas
        verGraficoBtn.addActionListener(e -> {
            String libro = libroSearch.getSelectedBook();
            new GraphWindow(esModoOscuro, libro).setVisible(true);
        });

        // Abre el modal para editar los datos de las sesiónes guardadas
        historialBtn.addActionListener(e -> {
            String libroSeleccionado = libroSearch.getSelectedBook();
            if (libroSeleccionado != null) {
                // 1. Obtenemos el ID del libro
                int libroId = DatabaseManager.obtenerLibroId(libroSeleccionado);

                // 2. Abrimos la ventana del historial
                HistoryWindow hw = new HistoryWindow(this, libroId, libroSeleccionado);
                hw.setVisible(true);

                // 3. Al cerrar el historial, refrescamos la racha y la página actual
                // por si el usuario borró o editó algo importante
                actualizarVistaRacha();
                actualizarEstimacion();
                // Opcional: actualizar el campo de página de inicio con la última sesión
                int ultimaPag = DatabaseManager.obtenerUltimaPagina(libroId);
                paginaInicioField.setText(String.valueOf(ultimaPag));
            } else {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
            }
        });

        // Configurar meta diaria al hacer clic en la racha
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

        otraCoverBtn.addActionListener(e -> {
            if (!currentCoverUrls.isEmpty()) {
                currentCoverIndex = (currentCoverIndex + 1) % currentCoverUrls.size();
                mostrarPortadaLocal(currentCoverUrls.get(currentCoverIndex));
            }
        });

        correctaCoverBtn.addActionListener(e -> {
            String libro = libroSearch.getSelectedBook();
            if (libro != null && !currentCoverUrls.isEmpty()) {
                int libroId = DatabaseManager.obtenerLibroId(libro);
                String selectedUrl = currentCoverUrls.get(currentCoverIndex);
                DatabaseManager.guardarCoverUrl(libroId, selectedUrl);

                // Ocultar botones tras guardar
                coverOptionsPanel.setVisible(false);
                JOptionPane.showMessageDialog(this, "Portada guardada correctamente.");
            }
        });

        subirPortadaBtn.addActionListener(e -> {
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

                String localPath = utils.FileUtil.saveBookCover(selectedFile, libroId);
                if (localPath != null) {
                    DatabaseManager.guardarCoverUrl(libroId, localPath);
                    cachePortadas.clear(); // Limpiamos todo el caché para forzar recarga absoluta
                    cargarPortadaAsincrona(libro);

                    // Forzar a Swing a redibujar todo el panel lateral
                    coverLabel.revalidate();
                    coverLabel.repaint();
                    mainPanel.revalidate();
                    mainPanel.repaint();

                    JOptionPane.showMessageDialog(this, "Portada actualizada correctamente.");
                } else {
                    JOptionPane.showMessageDialog(this, "Error al guardar la imagen local.");
                }
            }
        });
    }

    /**
     * Calcula y formatea el tiempo transcurrido para mostrarlo en formato HH:MM:SS.
     */
    private void actualizarCronometro() {
        long t = tiempoAcumulado + (corriendo ? System.currentTimeMillis() - tiempoInicio : 0);
        long s = t / 1000;
        tiempoLabel.setText(String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
    }

    /**
     * Refresca la lista desplegable consultando los libros registrados en la base
     * de datos.
     */
    private void actualizarComboLibros() {
        List<String> libros = DatabaseManager.obtenerTodosLosLibros();
        libroSearch.setBooks(libros);
    }

    /**
     * Reinicia el cronómetro y los controles asociados sin borrar los campos de
     * texto.
     */
    private void resetearSesionLocal() {
        if (timer != null)
            timer.stop();
        corriendo = false;
        tiempoAcumulado = 0;
        tiempoLabel.setText("00:00:00");
        updateButtonStates();
    }

    /**
     * Carga la portada del libro en un hilo secundario para no bloquear la
     * interfaz.
     */
    private void cargarPortadaAsincrona(String tituloLibro) {
        coverLabel.setIcon(null);
        coverLabel.setText("Buscando...");
        coverOptionsPanel.setVisible(false);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private Image finalImage = null;
            private boolean isSavedCover = false;

            @Override
            protected Void doInBackground() throws Exception {
                int libroId = DatabaseManager.obtenerLibroId(tituloLibro);
                String savedUrl = DatabaseManager.obtenerCoverUrl(libroId);

                if (savedUrl != null && !savedUrl.isEmpty()) {
                    isSavedCover = true;
                    if (cachePortadas.containsKey(savedUrl)) {
                        finalImage = cachePortadas.get(savedUrl);
                    } else {
                        java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(savedUrl);
                        if (img != null) {
                            finalImage = img.getScaledInstance(120, 170, Image.SCALE_SMOOTH);
                            cachePortadas.put(savedUrl, finalImage);
                        }
                    }
                } else {
                    currentCoverUrls = utils.BookCoverService.fetchCoverUrls(tituloLibro);
                    currentCoverIndex = 0;
                    if (!currentCoverUrls.isEmpty()) {
                        String firstUrl = currentCoverUrls.get(0);
                        if (cachePortadas.containsKey(firstUrl)) {
                            finalImage = cachePortadas.get(firstUrl);
                        } else {
                            java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(firstUrl);
                            if (img != null) {
                                finalImage = img.getScaledInstance(120, 170, Image.SCALE_SMOOTH);
                                cachePortadas.put(firstUrl, finalImage);
                            }
                        }
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
                            // Desactivamos el botón "Otra" si solo hay 1
                            otraCoverBtn.setEnabled(currentCoverUrls.size() > 1);
                        }

                        // Forzar repintado del label
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
        if (cachePortadas.containsKey(url)) {
            Image img = cachePortadas.get(url);
            coverLabel.setText("");
            coverLabel.setIcon(new ImageIcon(img));
            return;
        }

        coverLabel.setIcon(null);
        coverLabel.setText("Cargando...");

        SwingWorker<Image, Void> worker = new SwingWorker<>() {
            @Override
            protected Image doInBackground() throws Exception {
                java.awt.image.BufferedImage img = utils.BookCoverService.downloadImage(url);
                if (img != null) {
                    return img.getScaledInstance(120, 170, Image.SCALE_SMOOTH);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    Image resultImage = get();
                    cachePortadas.put(url, resultImage);

                    if (resultImage != null) {
                        coverLabel.setText("");
                        coverLabel.setIcon(new ImageIcon(resultImage));
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
        int restantes = Math.max(0, totales - leidas);

        // Actualizar Porcentaje
        if (totales > 0) {
            double porc = (leidas / (double) totales) * 100;
            lblPorcentaje.setText(String.format("Progreso: %.1f%%", porc));
            if (porc >= 100)
                lblPorcentaje.setForeground(new Color(46, 204, 113));
            else
                lblPorcentaje.setForeground(new Color(70, 130, 180));
        } else {
            lblPorcentaje.setText("Progreso: (Total págs. desc.)");
        }

        // Actualizar Estimación
        if (restantes <= 0 && totales > 0) {
            lblEstimacion.setText("¡Libro terminado!");
            return;
        }

        double pph = DatabaseManager.obtenerPromedioPPH(id);
        if (pph <= 0) {
            lblEstimacion.setText("Tiempo est. restante: Necesitas más sesiones");
            return;
        }

        double horas = restantes / pph;
        int h = (int) horas;
        int m = (int) Math.round((horas - h) * 60);

        if (h > 0) {
            lblEstimacion.setText(String.format("Tiempo est. restante: %dh %dm", h, m));
        } else {
            lblEstimacion.setText(String.format("Tiempo est. restante: %dm", m));
        }
    }

    /**
     * Valida los datos introducidos, realiza los cálculos de velocidad (PPM/PPH)
     * y guarda la sesión en la base de datos.
     */
    private void procesarTerminarSesion() {
        try {
            int pIni = Integer.parseInt(paginaInicioField.getText().trim());
            int pFin = Integer.parseInt(paginaFinField.getText().trim());
            String tituloLibro = libroSearch.getSelectedBook();
            if (tituloLibro == null) {
                JOptionPane.showMessageDialog(this, "⚠️ Selecciona un libro primero.");
                return;
            }
            int pagsLeidas = pFin - pIni;

            if (pagsLeidas < 0) {
                JOptionPane.showMessageDialog(this, "⚠️ La página final no puede ser menor que la inicial.");
                return;
            }

            // Cálculo del tiempo total en minutos
            long totalMs = tiempoAcumulado + (corriendo ? System.currentTimeMillis() - tiempoInicio : 0);
            double mins = totalMs / 60000.0;

            // Cálculo de métricas: Páginas Por Minuto (PPM) y Páginas Por Hora (PPH)
            double ppm = 0;
            double pph = 0;
            if (mins > 0.01) { // Evitamos dividir por cero en sesiones accidentalmente cortas
                ppm = pagsLeidas / mins;
                pph = ppm * 60;
            } else {
                JOptionPane.showMessageDialog(this, "⚠️ La sesión es demasiado corta para guardarse.");
                return;
            }

            int id = DatabaseManager.obtenerLibroId(tituloLibro);
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            // Persistencia en la base de datos
            DatabaseManager.guardarSesion(id, capituloField.getText(), pIni, pFin,
                    pagsLeidas, mins, ppm, pph, fecha);

            // Mostramos un resumen al usuario
            String mensajeResumen = String.format(
                    "📚 RESUMEN DE LECTURA\n" +
                            "---------------------------\n" +
                            "📖 Título: %s\n" +
                            "⏱️ Tiempo: %.2f min\n" +
                            "📄 Páginas: %d\n" +
                            "🚀 Velocidad: %.2f ppm\n" +
                            "📅 Fecha: %s\n" +
                            "---------------------------",
                    tituloLibro, mins, pagsLeidas, ppm, fecha);

            JOptionPane.showMessageDialog(this, mensajeResumen, "Sesión Finalizada", JOptionPane.INFORMATION_MESSAGE);

            // Preparación de la UI para la siguiente lectura
            resetearSesionLocal();
            paginaInicioField.setText(String.valueOf(pFin));
            paginaFinField.setText("");
            capituloField.setText("");
            actualizarVistaRacha();
            actualizarEstimacion();
            JOptionPane.showMessageDialog(this, "✅ Sesión guardada.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    /**
     * Crea un botón estilizado con los colores y bordes especificados.
     */
    private JButton createStyledButton(String text, Color bg, Color fg, boolean isBordered) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(isBordered);
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

        if (leidasHoy >= meta && meta > 0) {
            rachaLabel.setForeground(new Color(46, 204, 113)); // Verde (Meta cumplida)
        } else {
            rachaLabel.setForeground(new Color(255, 140, 0)); // Naranja (En progreso)
        }
    }
}