package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Componente de búsqueda de libros con autocompletado en tiempo real.
 * Reemplaza el JComboBox con un campo de texto que filtra la lista de libros
 * conforme el usuario escribe.
 */
public class BookSearchField extends JPanel {

    private final JTextField searchField;
    private final JButton dropdownBtn;
    private final JPopupMenu popup;
    private final JList<String> resultList;
    private final DefaultListModel<String> listModel;

    private List<String> allBooks = new ArrayList<>();
    private String selectedBook = null;
    private boolean isAdjusting = false;
    private Consumer<String> onSelectionChanged;

    // Colores y estilos (se actualizan con el tema)
    private Color bgColor = Color.WHITE;
    private Color fgColor = Color.BLACK;
    private Color selectionBg = new Color(70, 130, 180);

    public BookSearchField() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        // --- Campo de texto ---
        searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setToolTipText("Escribe para buscar un libro...");
        add(searchField, BorderLayout.CENTER);

        // --- Botón desplegable (▼) ---
        dropdownBtn = new JButton("▼");
        dropdownBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        dropdownBtn.setFocusable(false);
        dropdownBtn.setMargin(new Insets(0, 4, 0, 4));
        dropdownBtn.setToolTipText("Ver todos los libros");
        add(dropdownBtn, BorderLayout.EAST);

        // --- Lista de resultados ---
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFixedCellHeight(28);
        resultList.setCellRenderer(new BookListCellRenderer());

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // --- Popup ---
        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        popup.add(scrollPane, BorderLayout.CENTER);
        popup.setFocusable(false);

        configurarEventos();
    }

    private void configurarEventos() {
        // Filtrar mientras el usuario escribe
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filtrar();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filtrar();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filtrar();
            }
        });

        // Botón ▼: toggle mostrar todos los libros / cerrar
        dropdownBtn.addActionListener(e -> {
            if (popup.isVisible()) {
                popup.setVisible(false);
            } else {
                filtrarMostrandoTodos();
            }
        });

        // Navegar con teclado (↑ ↓ Enter Escape)
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        if (!popup.isVisible())
                            filtrarMostrandoTodos();
                        int next = Math.min(resultList.getSelectedIndex() + 1, listModel.size() - 1);
                        resultList.setSelectedIndex(next);
                        resultList.ensureIndexIsVisible(next);
                    }
                    case KeyEvent.VK_UP -> {
                        int prev = Math.max(resultList.getSelectedIndex() - 1, 0);
                        resultList.setSelectedIndex(prev);
                        resultList.ensureIndexIsVisible(prev);
                    }
                    case KeyEvent.VK_ENTER -> {
                        String sel = resultList.getSelectedValue();
                        if (sel != null)
                            seleccionarLibro(sel);
                        else if (listModel.size() == 1)
                            seleccionarLibro(listModel.get(0));
                    }
                    case KeyEvent.VK_ESCAPE -> {
                        popup.setVisible(false);
                        // Restaurar texto del libro seleccionado al cerrar con Esc
                        if (selectedBook != null)
                            searchField.setText(selectedBook);
                    }
                }
            }
        });

        // Clic en la lista → seleccionar
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String sel = resultList.getSelectedValue();
                if (sel != null)
                    seleccionarLibro(sel);
            }
        });

        // Abrir popup al hacer foco (muestra lista completa si campo vacío)
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                filtrar();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Esperar un poco antes de cerrar (permite clic en la lista)
                Timer t = new Timer(150, ev -> {
                    if (!resultList.hasFocus()) {
                        popup.setVisible(false);
                        // Si el campo tiene texto parcial que no coincide con un libro, restaurar
                        if (selectedBook != null && !searchField.getText().equals(selectedBook)) {
                            searchField.setText(selectedBook);
                        }
                    }
                });
                t.setRepeats(false);
                t.start();
            }
        });
    }

    /** Filtra ocultando el popup si el campo está vacío (modo escritura). */
    private void filtrar() {
        if (isAdjusting)
            return;

        // Si el popup estaba abierto en modo "todos", cerrarlo antes de filtrar
        // No hacemos nada especial: el mismo popup se actualiza con los resultados
        // filtrados
        String query = searchField.getText().trim().toLowerCase();
        listModel.clear();

        // Si el campo está vacío y no hay libro seleccionado, cerrar popup
        if (query.isEmpty() && selectedBook == null) {
            popup.setVisible(false);
            return;
        }

        boolean hayCoincidencias = false;
        for (String book : allBooks) {
            if (query.isEmpty() || book.toLowerCase().contains(query)) {
                listModel.addElement(book);
                hayCoincidencias = true;
            }
        }

        if (!hayCoincidencias) {
            popup.setVisible(false);
        } else {
            // Si ya estaba visible (modo desplegable) solo actualizamos el contenido
            actualizarOShowPopup();
            if (selectedBook != null) {
                int idx = listModel.indexOf(selectedBook);
                if (idx >= 0)
                    resultList.setSelectedIndex(idx);
            }
        }
    }

    /** Muestra todos los libros sin filtro (modo desplegable del botón ▼). */
    private void filtrarMostrandoTodos() {
        if (isAdjusting)
            return;
        listModel.clear();
        for (String book : allBooks)
            listModel.addElement(book);
        if (listModel.isEmpty())
            return;

        if (selectedBook != null) {
            int idx = listModel.indexOf(selectedBook);
            if (idx >= 0) {
                resultList.setSelectedIndex(idx);
                resultList.ensureIndexIsVisible(idx);
            }
        }
        actualizarOShowPopup();
    }

    /**
     * Si el popup ya está visible, lo actualiza en sitio.
     * Si no, abre uno nuevo. Siempre en el EDT via invokeLater para
     * que el ancho del panel esté disponible.
     */
    private void actualizarOShowPopup() {
        if (!isShowing() || listModel.isEmpty())
            return;

        SwingUtilities.invokeLater(() -> {
            if (listModel.isEmpty())
                return;
            int ancho = getWidth();
            int alto = Math.min(listModel.size() * 28 + 4, 220);
            popup.setPreferredSize(new Dimension(ancho, alto));

            if (!popup.isVisible()) {
                popup.show(BookSearchField.this, 0, getHeight());
            } else {
                popup.pack();
                popup.revalidate();
                popup.repaint();
            }
        });
    }

    private void seleccionarLibro(String libro) {
        isAdjusting = true;
        selectedBook = libro;
        searchField.setText(libro);
        popup.setVisible(false);
        isAdjusting = false;
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(libro);
        }
    }

    // ---- API pública ----

    /** Carga la lista completa de libros disponibles */
    public void setBooks(List<String> books) {
        this.allBooks = new ArrayList<>(books);
    }

    /** Devuelve el libro actualmente seleccionado (puede ser null) */
    public String getSelectedBook() {
        return selectedBook;
    }

    /** Selecciona un libro por nombre sin disparar el evento */
    public void setSelectedBook(String libro) {
        this.selectedBook = libro;
        isAdjusting = true;
        searchField.setText(libro != null ? libro : "");
        isAdjusting = false;
    }

    /** Registra el callback que se llama al seleccionar un libro */
    public void setOnSelectionChanged(Consumer<String> callback) {
        this.onSelectionChanged = callback;
    }

    /** Aplica colores del tema claro/oscuro al componente */
    public void applyTheme(Color bg, Color fg, Color inputBg) {
        this.bgColor = inputBg;
        this.fgColor = fg;
        searchField.setBackground(inputBg);
        searchField.setForeground(fg);
        searchField.setCaretColor(fg);
        searchField.setBorder(BorderFactory.createLineBorder(
                fg.equals(Color.WHITE) ? Color.GRAY : Color.LIGHT_GRAY));

        dropdownBtn.setBackground(inputBg);
        dropdownBtn.setForeground(fg);
        dropdownBtn.setOpaque(true);
        dropdownBtn.setBorder(BorderFactory.createLineBorder(
                fg.equals(Color.WHITE) ? Color.GRAY : Color.LIGHT_GRAY));

        resultList.setBackground(inputBg);
        resultList.setForeground(fg);
        resultList.setSelectionBackground(selectionBg);
        resultList.setSelectionForeground(Color.WHITE);

        popup.setBackground(inputBg);
        SwingUtilities.updateComponentTreeUI(popup);
    }

    /** Renderer personalizado para los items de la lista */
    private class BookListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(4, 10, 4, 10));
            label.setText("📖 " + value);
            if (isSelected) {
                label.setBackground(selectionBg);
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(bgColor);
                label.setForeground(fgColor);
            }
            return label;
        }
    }
}