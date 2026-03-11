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
    private JButton loginBtn, signupBtn, offlineBtn;
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
        statusLabel.setForeground(Color.RED);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonRow1 = new JPanel(new GridLayout(1, 2, 10, 0));
        loginBtn = new JButton("Iniciar Sesión");
        signupBtn = new JButton("Registrarse");
        buttonRow1.add(loginBtn);
        buttonRow1.add(signupBtn);

        offlineBtn = new JButton("Acceder sin conexión (Modo Invitado)");
        JPanel buttonRow2 = new JPanel(new BorderLayout());
        buttonRow2.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buttonRow2.add(offlineBtn, BorderLayout.CENTER);

        JPanel allButtonsPanel = new JPanel(new BorderLayout());
        allButtonsPanel.add(buttonRow1, BorderLayout.NORTH);
        allButtonsPanel.add(buttonRow2, BorderLayout.SOUTH);
        bottomPanel.add(allButtonsPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(mainPanel);

        loginBtn.addActionListener(e -> performLogin());
        signupBtn.addActionListener(e -> performSignup());
        offlineBtn.addActionListener(e -> performOfflineLogin());

        // Enter en cualquier campo dispara el login
        java.awt.event.ActionListener enterListener = e -> loginBtn.doClick();
        emailField.addActionListener(enterListener);
        passwordField.addActionListener(enterListener);
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
        statusLabel.setForeground(Color.BLUE);
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
        statusLabel.setForeground(Color.BLUE);
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
            x.printStackTrace();
        }
        dispose();
        if (onSuccess != null)
            onSuccess.run();
    }

    private void showError(String msg) {
        statusLabel.setForeground(Color.RED);
        statusLabel.setText("<html><center>" + msg + "</center></html>");
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        loginBtn.setEnabled(enabled);
        signupBtn.setEnabled(enabled);
    }
}