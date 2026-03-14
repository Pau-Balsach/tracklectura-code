package ui;

import db.DatabaseManager;
import model.Sesion;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Ventana (Diálogo) que muestra el historial completo de lectura de un libro.
 * Permite eliminar y editar sesiones específicas libremente.
 */
public class HistoryWindow extends JDialog {

    private int libroId;
    private JTable tablaHistorial;
    private DefaultTableModel modeloTabla;
    private List<Sesion> listaSesiones;

    public HistoryWindow(JFrame parent, int libroId, String tituloLibro) {
        super(parent, "📜 Historial de: " + tituloLibro, true); // Modal
        this.libroId = libroId;
        setSize(750, 400);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        inicializarTabla();
        if (DatabaseManager.getService() != null) {
            DatabaseManager.getService().sincronizarConNube();
        }
        cargarDatos();

        // Dentro del constructor HistoryWindow
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnAnadir = new JButton("➕ Añadir Sesión"); // Nuevo botón
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

    private void inicializarTabla() {
        String[] columnas = { "ID", "Fecha", "Capítulo", "P. Inicio", "P. Fin", "Páginas", "Mins", "PPM" };
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Evitamos edición directa, usamos el botón
            }
        };
        tablaHistorial = new JTable(modeloTabla);

        // Ocultamos la columna del ID internamente, pero la mantenemos en el modelo
        // para lógica
        tablaHistorial.getColumnModel().getColumn(0).setMinWidth(0);
        tablaHistorial.getColumnModel().getColumn(0).setMaxWidth(0);
        tablaHistorial.getColumnModel().getColumn(0).setWidth(0);
    }

    // Dentro de HistoryWindow.java asegúrate de que cargarDatos() esté así:
    private void cargarDatos() {
        modeloTabla.setRowCount(0);
        listaSesiones = DatabaseManager.obtenerSesionesPorLibro(libroId);

        if (listaSesiones.isEmpty()) {
            return;
        }

        // Ordenar por fecha de más reciente a más antiguo
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
            cargarDatos(); // Siempre refrescar, independientemente del resultado
            if (!ok) {
                // Solo mostrar error si la sesión sigue apareciendo en la tabla local
                // (puede que ya se hubiera borrado de la BD remota en un intento anterior)
                boolean sigueEnTabla = false;
                for (int i = 0; i < modeloTabla.getRowCount(); i++) {
                    if ((int) modeloTabla.getValueAt(i, 0) == sessionId) {
                        sigueEnTabla = true;
                        break;
                    }
                }
                if (sigueEnTabla) {
                    JOptionPane.showMessageDialog(this, "❌ Error al borrar.");
                }
            }
        }
    }

    private void editarSesionSeleccionada() {
        int filaSel = tablaHistorial.getSelectedRow();
        if (filaSel == -1) {
            JOptionPane.showMessageDialog(this, "⚠️ Selecciona una fila primero.");
            return;
        }

        // Obtener valores actuales de la tabla
        int sessionId = (int) modeloTabla.getValueAt(filaSel, 0);
        String capActual = (String) modeloTabla.getValueAt(filaSel, 2);
        String iniActual = modeloTabla.getValueAt(filaSel, 3).toString();
        String finActual = modeloTabla.getValueAt(filaSel, 4).toString();
        // Extraemos los minutos (ojo: quitamos el formato String si es necesario)
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

                if (nFin < nIni) {
                    JOptionPane.showMessageDialog(this, "⚠️ La página final no puede ser menor que la inicial.");
                    return;
                }
                if (nMin <= 0) {
                    JOptionPane.showMessageDialog(this, "⚠️ El tiempo debe ser mayor a 0.");
                    return;
                }

                // RECALCULO DE MÉTRICAS
                int nPags = nFin - nIni;
                double nPpm = nPags / nMin;
                double nPph = nPpm * 60;

                // Llamada al método actualizado de la base de datos
                if (DatabaseManager.actualizarSesionCompleta(sessionId, nIni, nFin, nPags, nMin, nPpm, nPph, nCap)) {
                    cargarDatos(); // Refrescar tabla
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

        // Campos del formulario
        JTextField fFecha = new JTextField(
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        JTextField fCap = new JTextField();
        JTextField fIni = new JTextField(String.valueOf(ultimaPag));
        JTextField fFin = new JTextField();
        JTextField fMin = new JTextField();

        Object[] formulario = {
                "📅 Fecha (dd/MM/yyyy HH:mm):", fFecha,
                "🔖 Capítulo:", fCap,
                "📄 Página de Inicio:", fIni,
                "📄 Página de Fin:", fFin,
                "⏱️ Minutos leídos:", fMin
        };

        int opcion = JOptionPane.showConfirmDialog(this, formulario, "➕ Registrar Nueva Sesión",
                JOptionPane.OK_CANCEL_OPTION);

        if (opcion == JOptionPane.OK_OPTION) {
            try {
                String fecha = fFecha.getText().trim();
                String cap = fCap.getText().trim();
                int nIni = Integer.parseInt(fIni.getText().trim());
                int nFin = Integer.parseInt(fFin.getText().trim());
                double nMin = Double.parseDouble(fMin.getText().trim().replace(",", "."));

                // Validaciones básicas
                if (nFin < nIni || nMin <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "⚠️ Revisa los datos: Las páginas o el tiempo no son coherentes.");
                    return;
                }

                // Cálculos automáticos
                int nPags = nFin - nIni;
                double nPpm = nPags / nMin;
                double nPph = nPpm * 60;

                // Guardar en BD (Necesitas crear este método en DatabaseManager)
                if (DatabaseManager.insertarSesionManual(libroId, fecha, cap, nIni, nFin, nPags, nMin, nPpm,
                        nPph)) {
                    cargarDatos();
                    // Opcional: Avisar a la ventana principal para refrescar racha
                    JOptionPane.showMessageDialog(this, "✅ Sesión añadida.");
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Error al guardar en la base de datos.");
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "⚠️ Por favor, introduce números válidos.");
            }
        }
    }
}
