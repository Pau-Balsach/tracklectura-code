package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Componente personalizado para seleccionar el estado, inspirado en
 * BookSearchField.
 * Evita los problemas de renderizado de FlatLaf con JComboBox.
 */
public class StateSelectorField extends JPanel {

    private final JLabel selectedStateLabel;
    private final JButton dropdownBtn;
    private final JPopupMenu popup;
    private final JList<String> resultList;
    private final DefaultListModel<String> listModel;

    private final List<String> states = Arrays.asList("Por leer", "Leyendo", "Terminado", "Abandonado");
    private String selectedState = "Por leer";
    private Consumer<String> onSelectionChanged;

    private Color bgColor = Color.WHITE;
    private Color fgColor = Color.BLACK;
    private Color selectionBg = new Color(70, 130, 180);

    public StateSelectorField() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(true);
        setBackground(bgColor);
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        selectedStateLabel = new JLabel("  " + selectedState);
        selectedStateLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectedStateLabel.setOpaque(false);
        add(selectedStateLabel, BorderLayout.CENTER);

        dropdownBtn = new JButton("▼");
        dropdownBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        dropdownBtn.setFocusable(false);
        dropdownBtn.setMargin(new Insets(0, 4, 0, 4));
        dropdownBtn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        add(dropdownBtn, BorderLayout.EAST);

        listModel = new DefaultListModel<>();
        for (String s : states)
            listModel.addElement(s);

        resultList = new JList<>(listModel);
        resultList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFixedCellHeight(28);
        resultList.setCellRenderer(new StateListCellRenderer());

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        popup.add(scrollPane, BorderLayout.CENTER);
        popup.setFocusable(false);

        configurarEventos();
    }

    private void configurarEventos() {
        MouseAdapter togglePopup = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isEnabled()) {
                    togglePopup();
                }
            }
        };
        selectedStateLabel.addMouseListener(togglePopup);
        dropdownBtn.addActionListener(e -> {
            if (isEnabled())
                togglePopup();
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String sel = resultList.getSelectedValue();
                if (sel != null) {
                    setSelectedItem(sel);
                    popup.setVisible(false);
                    if (onSelectionChanged != null) {
                        onSelectionChanged.accept(sel);
                    }
                }
            }
        });
    }

    private void togglePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
        } else {
            int ancho = getWidth();
            // Asegurar que el ancho mínimo se respeta
            ancho = Math.max(ancho, 140);
            int alto = states.size() * 28 + 4;
            popup.setPreferredSize(new Dimension(ancho, alto));
            resultList.setSelectedValue(selectedState, true);
            popup.show(this, 0, getHeight());
        }
    }

    public void setSelectedItem(String state) {
        if (states.contains(state)) {
            selectedState = state;
            selectedStateLabel.setText("  " + state);
        }
    }

    public String getSelectedItem() {
        return selectedState;
    }

    public void setOnSelectionChanged(Consumer<String> callback) {
        this.onSelectionChanged = callback;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        dropdownBtn.setEnabled(enabled);
        selectedStateLabel.setEnabled(enabled);

        // Estilo visual cuando está deshabilitado
        if (!enabled) {
            setBackground(bgColor.darker());
        } else {
            setBackground(bgColor);
        }
    }

    public void applyTheme(Color bg, Color fg, Color inputBg) {
        this.bgColor = inputBg;
        this.fgColor = fg;

        if (isEnabled()) {
            setBackground(inputBg);
        } else {
            setBackground(inputBg.darker());
        }

        setBorder(BorderFactory.createLineBorder(fg.equals(Color.WHITE) ? Color.GRAY : Color.LIGHT_GRAY));

        selectedStateLabel.setForeground(fg);
        dropdownBtn.setBackground(inputBg);
        dropdownBtn.setForeground(fg);
        dropdownBtn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        resultList.setBackground(inputBg);
        resultList.setForeground(fg);
        resultList.setSelectionBackground(selectionBg);
        resultList.setSelectionForeground(Color.WHITE);

        popup.setBackground(inputBg);
        SwingUtilities.updateComponentTreeUI(popup);
    }

    private class StateListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(4, 10, 4, 10));

            String icon = "📌 ";
            if ("Leyendo".equals(value))
                icon = "📖 ";
            else if ("Terminado".equals(value))
                icon = "✅ ";
            else if ("Abandonado".equals(value))
                icon = "❌ ";

            label.setText(icon + value);

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