package ui;

import db.DatabaseManager;
import model.Sesion;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Ventana (Diálogo) que muestra el historial completo de lectura de un libro.
 * Permite eliminar y editar sesiones específicas libremente.
 * MEJORAS:
 * - La sincronización con la nube se hace en un SwingWorker (no bloquea la UI)
 */
public class HistoryWindow extends JDialog {

    private final int libroId;
    private JTable tablaHistorial;
    private DefaultTableModel modeloTabla;

    /** Formatos de fecha aceptados en el formulario de añadir sesión. */
    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public HistoryWindow(JFrame parent, int libroId, String tituloLibro) {
        super(parent, "📜 Historial de: " + tituloLibro, true);
        this.libroId = libroId;
        setSize(750, 400);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        inicializarTabla();

        // MEJORA: sincronización en hilo secundario para no congelar la ventana
        sincronizarEnSegundoPlano();

        cargarDatos();

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnAnadir = new JButton("➕ Añadir Sesión");
        JButton btnEditar = new JButton("✏️ Editar Seleccionada");
        JButton btnEliminar = new JButton("🗑️ Eliminar Seleccionada");

        btnAnadir.addActionListener(ignored -> registrarNuevaSesion());
        btnEditar.addActionListener(ignored -> editarSesionSeleccionada());
        btnEliminar.addActionListener(ignored -> eliminarSesionSeleccionada());

        panelBotones.add(btnAnadir);
        panelBotones.add(btnEditar);
        panelBotones.add(btnEliminar);

        add(new JScrollPane(tablaHistorial), BorderLayout.CENTER);
        add(panelBotones, BorderLayout.SOUTH);
    }

    /**
     * Lanza la sincronización con la nube en segundo plano.
     * Cuando termina, recarga los datos de la tabla.
     */
    private void sincronizarEnSegundoPlano() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (DatabaseManager.getService() != null) {
                    DatabaseManager.getService().sincronizarConNube();
                }
                return null;
            }

            @Override
            protected void done() {
                cargarDatos(); // refrescar tabla con datos actualizados
            }
        }.execute();
    }

    private void inicializarTabla() {
        String[] columnas = { "ID", "Fecha", "Capítulo", "P. Inicio", "P. Fin", "Páginas", "Mins", "PPM" };
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaHistorial = new JTable(modeloTabla);

        // Ocultar columna ID
        tablaHistorial.getColumnModel().getColumn(0).setMinWidth(0);
        tablaHistorial.getColumnModel().getColumn(0).setMaxWidth(0);
        tablaHistorial.getColumnModel().getColumn(0).setWidth(0);
    }

    private void cargarDatos() {
        modeloTabla.setRowCount(0);
        List<Sesion> listaSesiones = DatabaseManager.obtenerSesionesPorLibro(libroId);
        if (listaSesiones.isEmpty()) return;

        listaSesiones.sort((s1, s2) -> Integer.compare(s2.getPaginaInicio(), s1.getPaginaInicio()));

        for (Sesion s : listaSesiones) {
            Object[] fila = {
                    s.getId(),
                    s.getFecha(),
                    s.getCapitulo(),
                    s.getPaginaInicio(),
                    s.getPaginaFin(),
                    s.getPaginasLeidas(),
                    String.format("%.1f", s.getMinutos()),
                    String.format("%.1f", s.getPpm())
            };
            modeloTabla.addRow(fila);
        }
    }

    private void eliminarSesionSeleccionada() {
        int filaSel = tablaHistorial.getSelectedRow();
        if (filaSel == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Selecciona una fila primero.");
            return;
        }

        int sessionId = (int) modeloTabla.getValueAt(filaSel, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "¿Seguro que quieres borrar esta sesión?", "Confirmar",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean ok = DatabaseManager.eliminarSesion(sessionId);
            cargarDatos();
            if (!ok) {
                boolean sigueEnTabla = false;
                for (int i = 0; i < modeloTabla.getRowCount(); i++) {
                    if ((int) modeloTabla.getValueAt(i, 0) == sessionId) {
                        sigueEnTabla = true;
                        break;
                    }
                }
                if (sigueEnTabla) JOptionPane.showMessageDialog(this, "❌ Error al borrar.");
            }
        }
    }

    private void editarSesionSeleccionada() {
        int filaSel = tablaHistorial.getSelectedRow();
        if (filaSel == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Selecciona una fila primero.");
            return;
        }

        int sessionId  = (int) modeloTabla.getValueAt(filaSel, 0);
        String fechaActual = (String) modeloTabla.getValueAt(filaSel, 1);
        String capActual   = (String) modeloTabla.getValueAt(filaSel, 2);
        String iniActual   = modeloTabla.getValueAt(filaSel, 3).toString();
        String finActual   = modeloTabla.getValueAt(filaSel, 4).toString();
        String minActual   = modeloTabla.getValueAt(filaSel, 6).toString().replace(",", ".");

        // ── Formulario persistente con JDialog ──────────────────────────────────
        JDialog dialog = new JDialog((JDialog) null, "✏️ Editar Sesión", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        JTextField fFecha = new JTextField(fechaActual != null ? fechaActual : "");
        JTextField fCap   = new JTextField(capActual != null ? capActual : "");
        JTextField fIni   = new JTextField(iniActual);
        JTextField fFin   = new JTextField(finActual);
        JTextField fMin   = new JTextField(minActual);

        // Etiqueta de error roja, inicialmente en blanco
        JLabel lblError = new JLabel(" ");
        lblError.setForeground(new Color(200, 50, 50));
        lblError.setFont(lblError.getFont().deriveFont(Font.ITALIC, 11f));
        lblError.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        // Panel de campos con el mismo layout que añadir sesión
        JPanel camposPanel = new JPanel(new GridBagLayout());
        camposPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        String[] etiquetas = {
            "📅 Fecha (dd/MM/yyyy HH:mm):",
            "🔖 Capítulo / Relato:",
            "📄 Página de inicio:",
            "📄 Página de fin:",
            "⏱️ Minutos leídos:",
        };
        JTextField[] campos = { fFecha, fCap, fIni, fFin, fMin };

        for (int i = 0; i < campos.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            camposPanel.add(new JLabel(etiquetas[i]), gc);

            gc.gridx = 1; gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            campos[i].setPreferredSize(new Dimension(180, 26));
            camposPanel.add(campos[i], gc);
        }

        // Panel de error + botones
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 10, 8));
        bottomPanel.add(lblError, BorderLayout.NORTH);

        JPanel botonesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnGuardar  = new JButton("💾 Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        botonesPanel.add(btnCancelar);
        botonesPanel.add(btnGuardar);
        bottomPanel.add(botonesPanel, BorderLayout.SOUTH);

        dialog.add(camposPanel, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        // ── Acción Cancelar ──────────────────────────────────────────────────────
        btnCancelar.addActionListener(ignored -> dialog.dispose());

        // ── Acción Guardar (valida sin cerrar si hay error) ──────────────────────
        btnGuardar.addActionListener(ignored -> {
            lblError.setText(" ");

            // 1) Validar fecha
            String fechaTexto = fFecha.getText().trim();
            if (esFechaInvalida(fechaTexto)) {
                lblError.setText("⚠️ Formato de fecha incorrecto. Usa: dd/MM/yyyy HH:mm");
                fFecha.requestFocusInWindow();
                fFecha.selectAll();
                dialog.pack();
                return;
            }

            // 2) Validar numéricos
            int nIni, nFin;
            double nMin;
            try {
                nIni = Integer.parseInt(fIni.getText().trim());
                nFin = Integer.parseInt(fFin.getText().trim());
                nMin = Double.parseDouble(fMin.getText().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                lblError.setText("⚠️ Páginas y minutos deben ser números válidos.");
                dialog.pack();
                return;
            }

            // 3) Validación de lógica de sesión
            String errorLogica = utils.ReadingCalculator.validarSesion(nIni, nFin, nMin);
            if (errorLogica != null) {
                lblError.setText("⚠️ " + errorLogica);
                dialog.pack();
                return;
            }

            // 4) Guardar
            String nCap  = fCap.getText().trim();
            int nPags    = utils.ReadingCalculator.calcularPaginasLeidas(nIni, nFin);
            double nPpm  = utils.ReadingCalculator.calcularPPM(nPags, nMin);
            double nPph  = utils.ReadingCalculator.calcularPPH(nPpm);

            if (DatabaseManager.actualizarSesionCompleta(sessionId, nIni, nFin, nPags, nMin, nPpm, nPph, nCap, fechaTexto)) {
                dialog.dispose();
                cargarDatos();
            } else {
                lblError.setText("❌ Error al actualizar en la base de datos.");
                dialog.pack();
            }
        });

        // Pulsar Enter en cualquier campo activa Guardar
        for (JTextField campo : campos) {
            campo.addActionListener(ignored -> btnGuardar.doClick());
        }

        dialog.setVisible(true);
    }

    private void registrarNuevaSesion() {
        int ultimaPag = DatabaseManager.obtenerUltimaPagina(libroId);
        String fechaDefecto = LocalDateTime.now().format(FMT_ENTRADA);

        // ── Formulario persistente con JDialog ──────────────────────────────────
        JDialog dialog = new JDialog((JDialog) null, "➕ Registrar Nueva Sesión", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        // Campos del formulario
        JTextField fFecha = new JTextField(fechaDefecto);
        JTextField fCap   = new JTextField();
        JTextField fIni   = new JTextField(String.valueOf(ultimaPag));
        JTextField fFin   = new JTextField();
        JTextField fMin   = new JTextField();

        // Etiqueta de error (roja, inicialmente invisible)
        JLabel lblError = new JLabel(" ");
        lblError.setForeground(new Color(200, 50, 50));
        lblError.setFont(lblError.getFont().deriveFont(Font.ITALIC, 11f));
        lblError.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        // Panel de campos
        JPanel camposPanel = new JPanel(new GridBagLayout());
        camposPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        String[][] filas = {
            {"📅 Fecha (dd/MM/yyyy HH:mm):", null},
            {"🔖 Capítulo / Relato:",        null},
            {"📄 Página de inicio:",          null},
            {"📄 Página de fin:",             null},
            {"⏱️ Minutos leídos:",           null},
        };
        JTextField[] campos = { fFecha, fCap, fIni, fFin, fMin };

        for (int i = 0; i < campos.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            camposPanel.add(new JLabel(filas[i][0]), gc);

            gc.gridx = 1; gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            campos[i].setPreferredSize(new Dimension(180, 26));
            camposPanel.add(campos[i], gc);
        }

        // Panel de error + botones
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 10, 8));
        bottomPanel.add(lblError, BorderLayout.NORTH);

        JPanel botonesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnGuardar  = new JButton("💾 Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        botonesPanel.add(btnCancelar);
        botonesPanel.add(btnGuardar);
        bottomPanel.add(botonesPanel, BorderLayout.SOUTH);

        dialog.add(camposPanel, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        // ── Acción Cancelar ──────────────────────────────────────────────────────
        btnCancelar.addActionListener(ignored -> dialog.dispose());

        // ── Acción Guardar (valida sin cerrar si hay error) ──────────────────────
        btnGuardar.addActionListener(ignored -> {
            lblError.setText(" "); // limpiar error anterior

            // 1) Validar fecha
            String fechaTexto = fFecha.getText().trim();
            if (esFechaInvalida(fechaTexto)) {
                lblError.setText("⚠️ Formato de fecha incorrecto. Usa: dd/MM/yyyy HH:mm");
                fFecha.requestFocusInWindow();
                fFecha.selectAll();
                dialog.pack();
                return;
            }

            // 2) Validar numéricos
            int nIni, nFin;
            double nMin;
            try {
                nIni = Integer.parseInt(fIni.getText().trim());
                nFin = Integer.parseInt(fFin.getText().trim());
                nMin = Double.parseDouble(fMin.getText().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                lblError.setText("⚠️ Páginas y minutos deben ser números válidos.");
                dialog.pack();
                return;
            }

            // 3) Validación de lógica de sesión
            String errorLogica = utils.ReadingCalculator.validarSesion(nIni, nFin, nMin);
            if (errorLogica != null) {
                lblError.setText("⚠️ " + errorLogica);
                dialog.pack();
                return;
            }

            // 4) Guardar
            String cap   = fCap.getText().trim();
            int nPags    = utils.ReadingCalculator.calcularPaginasLeidas(nIni, nFin);
            double nPpm  = utils.ReadingCalculator.calcularPPM(nPags, nMin);
            double nPph  = utils.ReadingCalculator.calcularPPH(nPpm);

            if (DatabaseManager.insertarSesionManual(libroId, fechaTexto, cap, nIni, nFin, nPags, nMin, nPpm, nPph)) {
                dialog.dispose();
                cargarDatos();
                JOptionPane.showMessageDialog(this, "✅ Sesión añadida.");
            } else {
                lblError.setText("❌ Error al guardar en la base de datos.");
                dialog.pack();
            }
        });

        // Pulsar Enter en cualquier campo activa Guardar
        for (JTextField campo : campos) {
            campo.addActionListener(ignored -> btnGuardar.doClick());
        }

        dialog.setVisible(true);
    }

    /**
     * Devuelve {@code true} si la cadena NO corresponde al formato de fecha esperado.
     * Acepta "dd/MM/yyyy HH:mm" con hora, o "dd/MM/yyyy" sin hora.
     */
    private boolean esFechaInvalida(String texto) {
        if (texto == null || texto.isBlank()) return true;
        try {
            LocalDateTime.parse(texto, FMT_ENTRADA);
            return false;
        } catch (DateTimeParseException e1) {
            try {
                // También aceptar sin hora (solo fecha)
                java.time.LocalDate.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return false;
            } catch (DateTimeParseException e2) {
                return true;
            }
        }
    }
}