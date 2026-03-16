package ui;

import db.SupabaseAuthService;
import db.DatabaseManager;
import javax.swing.*;
import java.awt.*;

/**
 * Ventana de inicio de sesión y registro de usuarios en Supabase.
 * Soporta modo invitado (offline).
 */
public class LoginWindow extends JFrame {

    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton loginBtn, signupBtn;
    private JLabel statusLabel;

    private final Runnable onSuccess;

    public LoginWindow(Runnable onSuccess) {
        super("Supabase Login - TrackLectura");
        this.onSuccess = onSuccess;

        setSize(430, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Iniciar Sesión", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(4, 1, 5, 2));
        formPanel.add(new JLabel("Correo Electrónico:"));
        emailField = new JTextField(utils.ConfigManager.getSavedEmail());
        emailField.setMargin(new Insets(2, 5, 2, 5));
        formPanel.add(emailField);
        
        formPanel.add(new JLabel("Contraseña:"));
        passwordField = new JPasswordField(utils.ConfigManager.getSavedPassword());
        passwordField.setMargin(new Insets(2, 5, 2, 5));
        formPanel.add(passwordField);
        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(getErrorColor());
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonRow1 = new JPanel(new GridLayout(1, 2, 10, 0));
        loginBtn = new JButton("Iniciar Sesión");
        signupBtn = new JButton("Registrarse");
        buttonRow1.add(loginBtn);
        buttonRow1.add(signupBtn);

        JButton offlineBtn = new JButton("Acceder sin conexión (Modo Invitado)");
        JPanel buttonRow2 = new JPanel(new BorderLayout());
        buttonRow2.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buttonRow2.add(offlineBtn, BorderLayout.CENTER);

        JPanel allButtonsPanel = new JPanel(new BorderLayout());
        allButtonsPanel.add(buttonRow1, BorderLayout.NORTH);
        allButtonsPanel.add(buttonRow2, BorderLayout.SOUTH);
        bottomPanel.add(allButtonsPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(mainPanel);

        loginBtn.addActionListener(ignored -> performLogin());
        signupBtn.addActionListener(ignored -> performSignup());
        offlineBtn.addActionListener(ignored -> performOfflineLogin());

        // Enter en cualquier campo dispara el login
        java.awt.event.ActionListener enterListener = ignored -> loginBtn.doClick();
        emailField.addActionListener(enterListener);
        passwordField.addActionListener(enterListener);

        aplicarTema(utils.ConfigManager.isDarkMode());
    }

    private void performOfflineLogin() {
        utils.ConfigManager.setOfflineMode(true);
        dispose();
        if (onSuccess != null)
            onSuccess.run();
    }

    private void performLogin() {
        String email = emailField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (email.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("Por favor, rellena todos los campos.");
            return;
        }

        setButtonsEnabled(false);
        statusLabel.setForeground(getInfoColor());
        statusLabel.setText("Conectando...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return SupabaseAuthService.login(email, pass);
            }

            @Override
            protected void done() {
                try {
                    String error = get();
                    if (error == null) {
                        onLoginSuccess(email, pass);
                    } else {
                        showError(error);
                    }
                } catch (Exception ex) {
                    showError("Error fatal.");
                }
            }
        }.execute();
    }

    private void performSignup() {
        String email = emailField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (email.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("Por favor, rellena todos los campos.");
            return;
        }

        setButtonsEnabled(false);
        statusLabel.setForeground(getInfoColor());
        statusLabel.setText("Registrando...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return SupabaseAuthService.signup(email, pass);
            }

            @Override
            protected void done() {
                try {
                    String error = get();
                    if (error == null) {
                        if (SupabaseAuthService.getCurrentAccessToken() != null) {
                            onLoginSuccess(email, pass);
                        } else {
                            statusLabel.setForeground(new Color(46, 204, 113));
                            statusLabel
                                    .setText("<html><center>Revisa tu email para verificar la cuenta.</center></html>");
                            setButtonsEnabled(true);
                        }
                    } else {
                        showError(error);
                    }
                } catch (Exception ex) {
                    showError("Error fatal.");
                }
            }
        }.execute();
    }

    /**
     * Inicializa la base de datos local, limpia datos de otros usuarios y
     * sincroniza en segundo plano.
     */
    private void onLoginSuccess(String email, String pass) {
        utils.ConfigManager.setOfflineMode(false);
        utils.ConfigManager.setSavedEmail(email);
        utils.ConfigManager.setSavedPassword(pass);
        try {
            DatabaseManager.inicializar();
            String uid = SupabaseAuthService.getCurrentUserId();

            db.DatabaseService localDb = DatabaseManager.getService();
            localDb.limpiarDatosDeOtrosUsuarios(uid);

            db.DatabaseService remoteDb = new db.PostgresDatabaseService();
            remoteDb.registrarUsuario(SupabaseAuthService.getCurrentUserEmail());

            new Thread(() -> DatabaseManager.getService().sincronizarConNube()).start();
        } catch (Exception x) {
            System.err.println("Error en onLoginSuccess: " + x.getMessage());
        }
        dispose();
        if (onSuccess != null)
            onSuccess.run();
    }

    private void showError(String msg) {
        statusLabel.setForeground(getErrorColor());
        statusLabel.setText("<html><center>" + msg + "</center></html>");
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        loginBtn.setEnabled(enabled);
        signupBtn.setEnabled(enabled);
    }

    private void aplicarTema(boolean oscuro) {
        Color bg = oscuro ? new Color(30, 30, 30) : new Color(245, 245, 245);
        Color fg = oscuro ? Color.WHITE : Color.BLACK;
        Color inputBg = oscuro ? new Color(50, 50, 50) : Color.WHITE;

        getContentPane().setBackground(bg);
        cambiarColorRecursivo(this.getContentPane(), bg, fg, inputBg, oscuro);
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
            } else if (c instanceof JTextField) {
                c.setBackground(inputBg);
                c.setForeground(fg);
                ((JTextField) c).setCaretColor(fg);
                ((JTextField) c).setBorder(BorderFactory.createLineBorder(oscuro ? Color.GRAY : Color.LIGHT_GRAY));
            } else if (c instanceof JLabel) {
                if (c != statusLabel) {
                    c.setForeground(fg);
                }
            } else if (c instanceof JPanel) {
                c.setBackground(bg);
            }
            if (c instanceof Container) {
                cambiarColorRecursivo((Container) c, bg, fg, inputBg, oscuro);
            }
        }
    }

    private Color getInfoColor() {
        return utils.ConfigManager.isDarkMode() ? new Color(100, 180, 255) : Color.BLUE;
    }

    private Color getErrorColor() {
        return utils.ConfigManager.isDarkMode() ? new Color(255, 100, 100) : Color.RED;
    }
}