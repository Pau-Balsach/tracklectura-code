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
 *
 * MEJORAS:
 * - La sincronización con la nube se hace en un SwingWorker (no bloquea la UI)
 * - Validación de formato de fecha antes de guardar
 * - Mensaje claro al intentar editar sin seleccionar fila
 */
public class HistoryWindow extends JDialog {

    private int libroId;
    private JTable tablaHistorial;
    private DefaultTableModel modeloTabla;
    private List<Sesion> listaSesiones;

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

        btnAnadir.addActionListener(e -> añadirNuevaSesion());
        btnEditar.addActionListener(e -> editarSesionSeleccionada());
        btnEliminar.addActionListener(e -> eliminarSesionSeleccionada());

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
        listaSesiones = DatabaseManager.obtenerSesionesPorLibro(libroId);
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

        int sessionId = (int) modeloTabla.getValueAt(filaSel, 0);
        String capActual = (String) modeloTabla.getValueAt(filaSel, 2);
        String iniActual = modeloTabla.getValueAt(filaSel, 3).toString();
        String finActual = modeloTabla.getValueAt(filaSel, 4).toString();
        String minActual = modeloTabla.getValueAt(filaSel, 6).toString().replace(",", ".");

        JTextField fCap = new JTextField(capActual);
        JTextField fIni = new JTextField(iniActual);
        JTextField fFin = new JTextField(finActual);
        JTextField fMin = new JTextField(minActual);

        Object[] formulario = {
                "🔖 Capítulo:", fCap,
                "📄 Página de Inicio:", fIni,
                "📄 Página de Fin:", fFin,
                "⏱️ Minutos leídos:", fMin
        };

        int opcion = JOptionPane.showConfirmDialog(this, formulario, "✏️ Corregir Sesión",
                JOptionPane.OK_CANCEL_OPTION);

        if (opcion == JOptionPane.OK_OPTION) {
            try {
                int nIni = Integer.parseInt(fIni.getText().trim());
                int nFin = Integer.parseInt(fFin.getText().trim());
                double nMin = Double.parseDouble(fMin.getText().trim().replace(",", "."));
                String nCap = fCap.getText().trim();

                // MEJORA: Usar ReadingCalculator para validación
                String error = utils.ReadingCalculator.validarSesion(nIni, nFin, nMin);
                if (error != null) {
                    JOptionPane.showMessageDialog(this, "⚠️ " + error);
                    return;
                }

                int nPags = utils.ReadingCalculator.calcularPaginasLeidas(nIni, nFin);
                double nPpm = utils.ReadingCalculator.calcularPPM(nPags, nMin);
                double nPph = utils.ReadingCalculator.calcularPPH(nPpm);

                if (DatabaseManager.actualizarSesionCompleta(sessionId, nIni, nFin, nPags, nMin, nPpm, nPph, nCap)) {
                    cargarDatos();
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Error al actualizar en la base de datos.");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "⚠️ Introduce valores numéricos válidos.");
            }
        }
    }

    private void añadirNuevaSesion() {
        int ultimaPag = DatabaseManager.obtenerUltimaPagina(libroId);

        // Fecha con formato validado
        String fechaDefecto = LocalDateTime.now().format(FMT_ENTRADA);

        JTextField fFecha = new JTextField(fechaDefecto);
        JTextField fCap = new JTextField();
        JTextField fIni = new JTextField(String.valueOf(ultimaPag));
        JTextField fFin = new JTextField();
        JTextField fMin = new JTextField();

        // Hint de formato junto al campo de fecha
        JLabel lblFechaHint = new JLabel("<html><small>Formato: dd/MM/yyyy HH:mm</small></html>");
        lblFechaHint.setForeground(Color.GRAY);

        Object[] formulario = {
                "📅 Fecha:", fFecha,
                lblFechaHint, new JLabel(""),
                "🔖 Capítulo:", fCap,
                "📄 Página de Inicio:", fIni,
                "📄 Página de Fin:", fFin,
                "⏱️ Minutos leídos:", fMin
        };

        int opcion = JOptionPane.showConfirmDialog(this, formulario, "➕ Registrar Nueva Sesión",
                JOptionPane.OK_CANCEL_OPTION);

        if (opcion == JOptionPane.OK_OPTION) {
            // MEJORA: Validar formato de fecha antes de intentar guardar
            String fechaTexto = fFecha.getText().trim();
            if (!esFechaValida(fechaTexto)) {
                JOptionPane.showMessageDialog(this,
                        "⚠️ Formato de fecha incorrecto.\n" +
                                "Usa el formato: dd/MM/yyyy HH:mm\n" +
                                "Ejemplo: " + fechaDefecto,
                        "Fecha inválida", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                String cap = fCap.getText().trim();
                int nIni = Integer.parseInt(fIni.getText().trim());
                int nFin = Integer.parseInt(fFin.getText().trim());
                double nMin = Double.parseDouble(fMin.getText().trim().replace(",", "."));

                // MEJORA: Usar ReadingCalculator para validación y cálculo
                String error = utils.ReadingCalculator.validarSesion(nIni, nFin, nMin);
                if (error != null) {
                    JOptionPane.showMessageDialog(this, "⚠️ " + error);
                    return;
                }

                int nPags = utils.ReadingCalculator.calcularPaginasLeidas(nIni, nFin);
                double nPpm = utils.ReadingCalculator.calcularPPM(nPags, nMin);
                double nPph = utils.ReadingCalculator.calcularPPH(nPpm);

                if (DatabaseManager.insertarSesionManual(libroId, fechaTexto, cap, nIni, nFin, nPags, nMin, nPpm, nPph)) {
                    cargarDatos();
                    JOptionPane.showMessageDialog(this, "✅ Sesión añadida.");
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Error al guardar en la base de datos.");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "⚠️ Por favor, introduce números válidos en páginas y minutos.");
            }
        }
    }

    /**
     * Valida que una cadena de texto corresponde al formato de fecha esperado.
     * Acepta "dd/MM/yyyy HH:mm" con hora, o "dd/MM/yyyy" sin hora.
     */
    private boolean esFechaValida(String texto) {
        if (texto == null || texto.isBlank()) return false;
        try {
            LocalDateTime.parse(texto, FMT_ENTRADA);
            return true;
        } catch (DateTimeParseException e1) {
            try {
                // También aceptar sin hora (solo fecha)
                java.time.LocalDate.parse(texto, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return true;
            } catch (DateTimeParseException e2) {
                return false;
            }
        }
    }
}