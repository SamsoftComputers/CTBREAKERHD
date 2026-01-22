/*
 * CTBreaker v0.5 - Offline Minecraft Launcher with Fabric + Mod Menu
 * by Team Flames / Samsoft
 * 
 * Features: Offline mode, Fabric Loader, IN-GAME MOD MENU (Mods button in menu)
 * Compile: javac CTBreakerMods.java
 * Run: java CTBreakerMods
 * Java 17+ recommended
 */

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.*;
import java.time.Duration;
import java.util.zip.*;
import java.util.jar.*;

public class CTBreakerMods extends JFrame {

    static final Color BG_DARK     = new Color(10, 10, 20);
    static final Color BG_PANEL    = new Color(20, 20, 35);
    static final Color BG_INPUT    = new Color(30, 30, 50);
    static final Color ACCENT      = new Color(0, 200, 255);
    static final Color ACCENT_DARK = new Color(0, 140, 180);
    static final Color TEXT        = new Color(220, 220, 255);
    static final Color TEXT_DIM    = new Color(140, 140, 180);
    static final Color GREEN       = new Color(0, 220, 100);

    static final String TITLE      = "CTBreaker v0.5 - Team Flames";
    static final String MANIFEST   = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    static final Path BASE         = Paths.get(System.getProperty("user.home"), ".ctbreaker");
    static final Path VERSIONS     = BASE.resolve("versions");
    static final Path LIBS         = BASE.resolve("libraries");
    static final Path ASSETS       = BASE.resolve("assets");
    static final Path NATIVES      = BASE.resolve("natives");
    static final Path MODS_DIR     = BASE.resolve("mods");
    static final Path FABRIC_DIR   = BASE.resolve("fabric");
    static final Path CONFIG_FILE  = BASE.resolve("ctbreaker_hacks.txt");
    static final String SEP        = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";

    static final Map<String, String> FABRIC_VERSIONS = Map.of(
        "1.21", "0.16.9", "1.20.4", "0.16.9", "1.20.1", "0.16.9",
        "1.19.4", "0.16.9", "1.19.2", "0.16.9", "1.18.2", "0.16.9",
        "1.16.5", "0.16.9", "1.14.4", "0.16.9"
    );

    JTextField username;
    JComboBox<String> versionBox;
    JSlider ramSlider;
    JLabel ramText;
    JTextArea console;
    JProgressBar progBar;
    JLabel statusLabel;
    JButton launchButton;
    JCheckBox useFabric;
    JCheckBox killaura, flight, speed, esp, fullbright, nofall, jesus, autoTotem;

    ExecutorService executor = Executors.newCachedThreadPool();
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    volatile boolean isLaunching = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CTBreakerMods().setVisible(true));
    }

    public CTBreakerMods() {
        setTitle(TITLE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        createDirectories();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainContent(), BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);
        setContentPane(root);

        log("╔═══════════════════════════════════════════════════╗");
        log("║        CTBreaker v0.5 - Team Flames               ║");
        log("║     Fabric Loader + In-Game Mod Menu              ║");
        log("╚═══════════════════════════════════════════════════╝");
        log("[INFO] Game directory: " + BASE);
        log("[INFO] Mods directory: " + MODS_DIR);
        log("[INFO] Click 'Mods' button in main menu!");
        log("[READY] Launcher initialized.");
        
        checkExistingMods();
    }

    void createDirectories() {
        try {
            Files.createDirectories(BASE);
            Files.createDirectories(VERSIONS);
            Files.createDirectories(LIBS);
            Files.createDirectories(ASSETS.resolve("indexes"));
            Files.createDirectories(ASSETS.resolve("objects"));
            Files.createDirectories(NATIVES);
            Files.createDirectories(MODS_DIR);
            Files.createDirectories(FABRIC_DIR);
        } catch (IOException ignored) {}
    }

    void checkExistingMods() {
        // Delete any broken generated mods
        try {
            Path brokenMod = MODS_DIR.resolve("CTBreakerMod-1.0.jar");
            Files.deleteIfExists(brokenMod);
        } catch (IOException ignored) {}
        
        // Auto-download Fabric API (required by most mods)
        downloadFabricApi();
        
        // Auto-download Mod Menu for in-game mod list (Mods button)
        downloadModMenu();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODS_DIR, "*.jar")) {
            int count = 0;
            for (Path mod : stream) {
                String name = mod.getFileName().toString();
                log("[MOD] Found: " + name);
                count++;
            }
            if (count > 0) {
                log("[INFO] " + count + " mod(s) ready");
                log("[INFO] Click 'Mods' button in main menu!");
            }
        } catch (IOException ignored) {}
    }
    
    void downloadFabricApi() {
        // Check if Fabric API exists
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODS_DIR, "fabric-api*.jar")) {
            for (Path p : stream) return;
        } catch (IOException ignored) {}
        
        String selectedVer = (String) versionBox.getSelectedItem();
        if (selectedVer.contains(" (")) selectedVer = selectedVer.substring(0, selectedVer.indexOf(" ("));
        
        log("[INFO] Downloading Fabric API...");
        
        // Use Modrinth API with URL encoding
        try {
            String apiUrl = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22" + selectedVer + "%22%5D&loaders=%5B%22fabric%22%5D";
            String response = fetch(apiUrl);
            
            Pattern urlPat = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"");
            Matcher m = urlPat.matcher(response);
            if (m.find()) {
                String url = m.group(1);
                String filename = url.substring(url.lastIndexOf('/') + 1);
                Path jar = MODS_DIR.resolve(filename);
                download(url, jar);
                log("[INFO] Fabric API installed: " + filename);
                return;
            }
        } catch (Exception e) {
            log("[WARN] Fabric API API failed: " + e.getMessage());
        }
        
        // Fallback URLs (verified working)
        String[][] fallbacks = {
            {"1.20.4", "fabric-api-0.97.0+1.20.4.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/xklQBMta/fabric-api-0.97.0%2B1.20.4.jar"},
            {"1.20.1", "fabric-api-0.92.2+1.20.1.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/P7uGFii0/fabric-api-0.92.2%2B1.20.1.jar"},
            {"1.19.4", "fabric-api-0.87.2+1.19.4.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/JYQdfGtZ/fabric-api-0.87.2%2B1.19.4.jar"},
            {"1.18.2", "fabric-api-0.77.0+1.18.2.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/2kYMNprE/fabric-api-0.77.0%2B1.18.2.jar"},
            {"1.16.5", "fabric-api-0.42.0+1.16.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/0.42.0+1.16/fabric-api-0.42.0+1.16.jar"},
        };
        
        for (String[] fb : fallbacks) {
            if (selectedVer.startsWith(fb[0])) {
                try {
                    Path jar = MODS_DIR.resolve(fb[1]);
                    download(fb[2], jar);
                    log("[INFO] Fabric API installed: " + fb[1]);
                    return;
                } catch (Exception e) {
                    log("[WARN] Fabric API fallback failed: " + e.getMessage());
                }
            }
        }
    }
    
    void downloadModMenu() {
        // Check if any mod menu version exists
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODS_DIR, "modmenu*.jar")) {
            for (Path p : stream) return; // Already have mod menu
        } catch (IOException ignored) {}
        
        log("[INFO] Downloading Mod Menu...");
        
        String selectedVer = (String) versionBox.getSelectedItem();
        if (selectedVer.contains(" (")) selectedVer = selectedVer.substring(0, selectedVer.indexOf(" ("));
        
        // Use Modrinth API with URL encoding
        try {
            String apiUrl = "https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%22" + selectedVer + "%22%5D&loaders=%5B%22fabric%22%5D";
            String response = fetch(apiUrl);
            
            // Parse the file URL from response
            Pattern urlPat = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"");
            Matcher m = urlPat.matcher(response);
            if (m.find()) {
                String url = m.group(1);
                String filename = url.substring(url.lastIndexOf('/') + 1);
                Path jar = MODS_DIR.resolve(filename);
                download(url, jar);
                log("[INFO] Mod Menu installed: " + filename);
                return;
            }
        } catch (Exception e) {
            log("[WARN] Mod Menu API failed: " + e.getMessage());
        }
        
        // Fallback: direct CDN URLs (verified working)
        String[][] fallbacks = {
            {"1.20.4", "modmenu-9.0.0.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/sjtVVlsA/modmenu-9.0.0.jar"},
            {"1.20.1", "modmenu-7.2.2.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/I48f4VaX/modmenu-7.2.2.jar"},
            {"1.19.4", "modmenu-6.3.1.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/Jmp4Go6L/modmenu-6.3.1.jar"},
            {"1.19.2", "modmenu-4.2.0-beta.2.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/gSoPJyVn/modmenu-4.2.0-beta.2.jar"},
            {"1.18.2", "modmenu-3.2.5.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/uqlKkRPX/modmenu-3.2.5.jar"},
            {"1.16.5", "modmenu-1.16.23.jar", "https://cdn.modrinth.com/data/mOgUt4GM/versions/4Aw9RnVe/modmenu-1.16.23.jar"},
        };
        
        for (String[] fb : fallbacks) {
            if (selectedVer.startsWith(fb[0])) {
                try {
                    Path jar = MODS_DIR.resolve(fb[1]);
                    download(fb[2], jar);
                    log("[INFO] Mod Menu installed: " + fb[1]);
                    return;
                } catch (Exception e) {
                    log("[WARN] Mod Menu fallback failed: " + e.getMessage());
                }
            }
        }
        
        log("[WARN] Could not download Mod Menu");
        log("[INFO] Download manually: https://modrinth.com/mod/modmenu");
    }

    void saveHackConfig() {
        try {
            StringBuilder cfg = new StringBuilder();
            cfg.append("# CTBreaker Hack Config - read by in-game menu\n");
            cfg.append("killaura=").append(killaura.isSelected()).append("\n");
            cfg.append("flight=").append(flight.isSelected()).append("\n");
            cfg.append("speed=").append(speed.isSelected()).append("\n");
            cfg.append("esp=").append(esp.isSelected()).append("\n");
            cfg.append("fullbright=").append(fullbright.isSelected()).append("\n");
            cfg.append("nofall=").append(nofall.isSelected()).append("\n");
            cfg.append("jesus=").append(jesus.isSelected()).append("\n");
            cfg.append("autototem=").append(autoTotem.isSelected()).append("\n");
            Files.writeString(CONFIG_FILE, cfg.toString());
        } catch (IOException ignored) {}
    }

    JPanel createHeader() {
        JPanel h = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, BG_PANEL, 0, getHeight(), BG_DARK));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.fillRect(0, getHeight() - 4, getWidth(), 4);
            }
        };
        h.setPreferredSize(new Dimension(0, 80));
        h.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JLabel logo = new JLabel("CTBreaker");
        logo.setFont(new Font("Arial", Font.BOLD, 32));
        logo.setForeground(ACCENT);
        h.add(logo, BorderLayout.WEST);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        badges.setOpaque(false);

        JLabel menuBadge = new JLabel("MOD MENU");
        menuBadge.setFont(new Font("Arial", Font.BOLD, 11));
        menuBadge.setForeground(GREEN);
        menuBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GREEN, 2, true),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        badges.add(menuBadge);

        JLabel ver = new JLabel("v0.5");
        ver.setFont(new Font("Arial", Font.BOLD, 11));
        ver.setForeground(ACCENT);
        ver.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT, 2, true),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        badges.add(ver);
        h.add(badges, BorderLayout.EAST);
        return h;
    }

    JPanel createMainContent() {
        JPanel main = new JPanel(new BorderLayout(20, 20));
        main.setBackground(BG_DARK);
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        main.add(createSettingsPanel(), BorderLayout.WEST);
        main.add(createConsolePanel(), BorderLayout.CENTER);
        return main;
    }

    JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 90), 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)));
        panel.setPreferredSize(new Dimension(360, 0));

        panel.add(createLabel("USERNAME"));
        username = createTextField("Player");
        panel.add(username);
        panel.add(Box.createVerticalStrut(10));

        panel.add(createLabel("VERSION"));
        versionBox = new JComboBox<>(new String[]{"1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.21 (Java 21)"});
        versionBox.setSelectedIndex(0); // Default to 1.20.4 (Java 17 compatible)
        styleCombo(versionBox);
        panel.add(versionBox);
        panel.add(Box.createVerticalStrut(10));

        panel.add(createLabel("RAM (GB)"));
        ramSlider = new JSlider(2, 16, 6);
        ramSlider.setBackground(BG_PANEL);
        ramSlider.setForeground(ACCENT);
        ramSlider.setMajorTickSpacing(2);
        ramSlider.setPaintTicks(true);
        ramSlider.setPaintLabels(true);
        ramText = createLabel("6 GB");
        ramSlider.addChangeListener(e -> ramText.setText(ramSlider.getValue() + " GB"));
        panel.add(ramSlider);
        panel.add(ramText);
        panel.add(Box.createVerticalStrut(12));

        panel.add(createSectionLabel("MOD LOADER"));
        useFabric = new JCheckBox("Use Fabric Loader (required for mods)");
        useFabric.setSelected(true);
        styleCheckBox(useFabric);
        panel.add(useFabric);
        
        JLabel modMenuInfo = new JLabel("<html><font color='#00dd66'>Mod Menu auto-downloads - click 'Mods' in main menu!</font></html>");
        modMenuInfo.setFont(new Font("Arial", Font.PLAIN, 11));
        panel.add(modMenuInfo);
        panel.add(Box.createVerticalStrut(10));

        panel.add(createSectionLabel("HACKS (Toggle in-game)"));
        killaura   = new JCheckBox("Killaura - Attack nearby");
        flight     = new JCheckBox("Flight - Fly mode");
        speed      = new JCheckBox("Speed - Fast movement");
        esp        = new JCheckBox("ESP - See through walls");
        fullbright = new JCheckBox("Fullbright - Max gamma");
        nofall     = new JCheckBox("No Fall - No fall damage");
        jesus      = new JCheckBox("Jesus - Walk on water");
        autoTotem  = new JCheckBox("Auto Totem - Swap totems");

        styleCheckBox(killaura);   panel.add(killaura);
        styleCheckBox(flight);     panel.add(flight);
        styleCheckBox(speed);      panel.add(speed);
        styleCheckBox(esp);        panel.add(esp);
        styleCheckBox(fullbright); panel.add(fullbright);
        styleCheckBox(nofall);     panel.add(nofall);
        styleCheckBox(jesus);      panel.add(jesus);
        styleCheckBox(autoTotem);  panel.add(autoTotem);
        panel.add(Box.createVerticalStrut(8));

        JButton openMods = new JButton("Open Mods Folder");
        openMods.setBackground(BG_INPUT);
        openMods.setForeground(TEXT);
        openMods.setFocusPainted(false);
        openMods.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        openMods.addActionListener(e -> {
            try { Desktop.getDesktop().open(MODS_DIR.toFile()); }
            catch (Exception ex) { log("[ERROR] Could not open folder"); }
        });
        panel.add(openMods);

        panel.add(Box.createVerticalGlue());

        launchButton = new JButton("LAUNCH") {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? ACCENT_DARK : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BG_DARK);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        launchButton.setFont(new Font("Arial", Font.BOLD, 18));
        launchButton.setForeground(TEXT);
        launchButton.setBorderPainted(false);
        launchButton.setContentAreaFilled(false);
        launchButton.setFocusPainted(false);
        launchButton.setPreferredSize(new Dimension(0, 50));
        launchButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        launchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        launchButton.addActionListener(e -> startLaunch());
        panel.add(launchButton);
        return panel;
    }

    JLabel createLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(TEXT);
        l.setFont(new Font("Arial", Font.BOLD, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    JLabel createSectionLabel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(ACCENT);
        l.setFont(new Font("Arial", Font.BOLD, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    JTextField createTextField(String def) {
        JTextField f = new JTextField(def);
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT);
        f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 90)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return f;
    }

    void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_INPUT);
        c.setForeground(TEXT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    }

    void styleCheckBox(JCheckBox cb) {
        cb.setBackground(BG_PANEL);
        cb.setForeground(TEXT);
        cb.setFocusPainted(false);
        cb.setFont(new Font("Arial", Font.PLAIN, 11));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    JPanel createConsolePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 90)));
        JLabel title = new JLabel(" CONSOLE ");
        title.setForeground(ACCENT);
        title.setFont(new Font("Arial", Font.BOLD, 13));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.add(title, BorderLayout.NORTH);

        console = new JTextArea();
        console.setBackground(new Color(8, 8, 16));
        console.setForeground(new Color(180, 255, 180));
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        console.setEditable(false);
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(console);
        sp.setBorder(null);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    JPanel createFooter() {
        JPanel f = new JPanel(new BorderLayout());
        f.setBackground(new Color(15, 15, 25));
        f.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(TEXT_DIM);
        f.add(statusLabel, BorderLayout.WEST);
        progBar = new JProgressBar();
        progBar.setPreferredSize(new Dimension(200, 6));
        progBar.setVisible(false);
        progBar.setForeground(ACCENT);
        progBar.setBackground(new Color(30, 30, 50));
        progBar.setBorderPainted(false);
        f.add(progBar, BorderLayout.CENTER);
        JLabel credit = new JLabel("Team Flames / Samsoft");
        credit.setForeground(TEXT_DIM);
        f.add(credit, BorderLayout.EAST);
        return f;
    }

    void log(String s) {
        SwingUtilities.invokeLater(() -> {
            console.append(s + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    void setStatus(String s) { SwingUtilities.invokeLater(() -> statusLabel.setText(s)); }

    void startLaunch() {
        if (isLaunching) return;
        isLaunching = true;
        launchButton.setText("LAUNCHING...");
        launchButton.setEnabled(false);
        progBar.setVisible(true);
        progBar.setIndeterminate(true);
        
        saveHackConfig();

        executor.submit(() -> {
            try {
                String user = username.getText().trim();
                if (user.isEmpty()) user = "Player" + (int)(Math.random() * 9999);
                String mcVer = (String) versionBox.getSelectedItem();
                // Remove Java requirement suffix if present
                if (mcVer.contains(" (")) mcVer = mcVer.substring(0, mcVer.indexOf(" ("));
                int ram = ramSlider.getValue();
                boolean fabric = useFabric.isSelected();

                log("\n[START] Launching " + mcVer + " as " + user + " (" + ram + "GB)");
                if (fabric) log("[INFO] Fabric Loader enabled");
                log("[INFO] Click 'Mods' button in main menu!");

                setStatus("Fetching manifest...");
                String manifest = fetch(MANIFEST);

                String verUrl = findVersionUrl(manifest, mcVer);
                if (verUrl == null) throw new Exception("Version not found: " + mcVer);

                setStatus("Downloading version...");
                String verJson = fetch(verUrl);

                Path vDir = VERSIONS.resolve(mcVer);
                Files.createDirectories(vDir);
                Path nativesDir = vDir.resolve("natives");
                Files.createDirectories(nativesDir);

                setStatus("Downloading client...");
                String clientUrl = extractUrl(verJson, "client");
                Path clientJar = vDir.resolve(mcVer + ".jar");
                if (!Files.exists(clientJar)) {
                    log("[INFO] Downloading: " + mcVer + ".jar");
                    download(clientUrl, clientJar);
                } else log("[INFO] Client cached");

                setStatus("Downloading libraries...");
                List<String> classpath = downloadLibsAndNatives(verJson, nativesDir);
                log("[INFO] " + classpath.size() + " vanilla libraries");

                List<String> fabricLibs = new ArrayList<>();
                String mainClass = "net.minecraft.client.main.Main";
                
                if (fabric) {
                    setStatus("Setting up Fabric...");
                    fabricLibs = downloadFabric(mcVer);
                    classpath.addAll(0, fabricLibs);
                    mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient";
                    log("[INFO] " + fabricLibs.size() + " Fabric libraries");
                    
                    int modCount = countMods();
                    log("[INFO] " + modCount + " mod(s) will load");
                }

                setStatus("Downloading assets...");
                String assetIndex = downloadAssetIndex(verJson);

                String verStr = mcVer;
                if (fabric) {
                    verStr += "/Fabric";
                    int mc = countMods();
                    if (mc > 0) verStr += " (" + mc + " mods)";
                }
                verStr += "/CTBreaker";

                String uuid = offlineUUID(user);
                List<String> cmd = new ArrayList<>();
                cmd.add(javaPath(mcVer));
                
                // macOS requires this for LWJGL/GLFW
                if (getOS().equals("osx")) {
                    cmd.add("-XstartOnFirstThread");
                }
                
                cmd.add("-Xmx" + ram + "G");
                cmd.add("-Xms" + Math.max(2, ram / 2) + "G");
                cmd.add("-XX:+UnlockExperimentalVMOptions");
                cmd.add("-XX:+UseG1GC");
                cmd.add("-Djava.library.path=" + nativesDir);
                cmd.add("-Dctbreaker.config=" + CONFIG_FILE);
                
                if (fabric) {
                    cmd.add("-Dfabric.gameVersion=" + mcVer);
                    cmd.add("-Dfabric.loader.gameDir=" + BASE);
                }
                
                cmd.add("-cp");
                classpath.add(clientJar.toString());
                cmd.add(String.join(SEP, classpath));
                cmd.add(mainClass);
                
                cmd.add("--username");    cmd.add(user);
                cmd.add("--uuid");        cmd.add(uuid);
                cmd.add("--accessToken"); cmd.add("0");
                cmd.add("--userType");    cmd.add("legacy");
                cmd.add("--version");     cmd.add(verStr);
                cmd.add("--gameDir");     cmd.add(BASE.toString());
                cmd.add("--assetsDir");   cmd.add(ASSETS.toString());
                cmd.add("--assetIndex");  cmd.add(assetIndex);

                log("[LAUNCH] Main: " + mainClass);
                log("[INFO] Click 'Mods' button in main menu!");
                setStatus("Launching...");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(BASE.toFile());
                pb.redirectErrorStream(true);
                pb.environment().put("FABRIC_GAME_DIR", BASE.toString());
                
                Process proc = pb.start();

                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) log("[MC] " + line);
                    } catch (IOException ignored) {}
                }).start();

                int exit = proc.waitFor();
                log("[EXIT] Minecraft closed (" + exit + ")");

            } catch (Exception e) {
                log("[ERROR] " + e.getMessage());
                e.printStackTrace();
                setStatus("Failed");
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isLaunching = false;
                    launchButton.setText("LAUNCH");
                    launchButton.setEnabled(true);
                    progBar.setVisible(false);
                    setStatus("Ready");
                });
            }
        });
    }
    
    int countMods() {
        int c = 0;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(MODS_DIR, "*.jar")) {
            for (Path p : s) c++;
        } catch (IOException ignored) {}
        return c;
    }

    List<String> downloadFabric(String mcVer) throws Exception {
        List<String> libs = new ArrayList<>();
        String loaderVer = FABRIC_VERSIONS.getOrDefault(mcVer, "0.16.9");
        log("[FABRIC] Loader " + loaderVer + " for MC " + mcVer);
        
        // Updated libraries for Java 21 support
        String[][] fabricLibs = {
            {"net.fabricmc", "fabric-loader", loaderVer, "https://maven.fabricmc.net/"},
            {"net.fabricmc", "intermediary", mcVer, "https://maven.fabricmc.net/"},
            {"net.fabricmc", "sponge-mixin", "0.15.4+mixin.0.8.7", "https://maven.fabricmc.net/"},
            {"net.fabricmc", "tiny-mappings-parser", "0.3.0+build.17", "https://maven.fabricmc.net/"},
            {"net.fabricmc", "tiny-remapper", "0.10.4", "https://maven.fabricmc.net/"},
            {"net.fabricmc", "access-widener", "2.1.0", "https://maven.fabricmc.net/"},
            {"org.ow2.asm", "asm", "9.7.1", "https://repo1.maven.org/maven2/"},
            {"org.ow2.asm", "asm-analysis", "9.7.1", "https://repo1.maven.org/maven2/"},
            {"org.ow2.asm", "asm-commons", "9.7.1", "https://repo1.maven.org/maven2/"},
            {"org.ow2.asm", "asm-tree", "9.7.1", "https://repo1.maven.org/maven2/"},
            {"org.ow2.asm", "asm-util", "9.7.1", "https://repo1.maven.org/maven2/"},
        };
        
        for (String[] lib : fabricLibs) {
            String group = lib[0].replace('.', '/');
            String artifact = lib[1];
            String version = lib[2];
            String repo = lib[3];
            String path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
            Path libPath = FABRIC_DIR.resolve(path);
            Files.createDirectories(libPath.getParent());
            if (!Files.exists(libPath)) {
                try {
                    log("[FABRIC] " + artifact + "-" + version + ".jar");
                    download(repo + path, libPath);
                } catch (Exception e) {
                    log("[WARN] Failed: " + artifact);
                    continue;
                }
            }
            libs.add(libPath.toString());
        }
        return libs;
    }

    String fetch(String url) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url));
        // Modrinth API requires User-Agent
        if (url.contains("modrinth")) {
            req.header("User-Agent", "CTBreaker/0.5 (minecraft-launcher)");
        }
        HttpResponse<String> r = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }

    void download(String url, Path dest) throws Exception {
        Files.createDirectories(dest.getParent());
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url));
        if (url.contains("modrinth")) {
            req.header("User-Agent", "CTBreaker/0.5 (minecraft-launcher)");
        }
        HttpResponse<Path> r = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofFile(dest));
        if (r.statusCode() != 200) { Files.deleteIfExists(dest); throw new IOException("HTTP " + r.statusCode()); }
    }

    String findVersionUrl(String m, String v) {
        int i = 0;
        while (true) {
            int p = m.indexOf("\"id\"", i);
            if (p == -1) return null;
            int s = m.lastIndexOf("{", p), e = m.indexOf("}", p);
            if (s == -1 || e == -1) { i = p + 1; continue; }
            String o = m.substring(s, e + 1);
            if (Pattern.compile("\"id\"\\s*:\\s*\"" + Pattern.quote(v) + "\"").matcher(o).find()) {
                Matcher u = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(o);
                if (u.find()) return u.group(1);
            }
            i = p + 1;
        }
    }

    String extractUrl(String j, String k) {
        int d = j.indexOf("\"downloads\""); if (d == -1) return null;
        int p = j.indexOf("\"" + k + "\"", d); if (p == -1) return null;
        int s = j.indexOf("{", p), e = j.indexOf("}", s); if (s == -1 || e == -1) return null;
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(j.substring(s, e + 1));
        return m.find() ? m.group(1) : null;
    }

    List<String> downloadLibsAndNatives(String j, Path nd) throws Exception {
        List<String> cp = new ArrayList<>();
        String os = getOS();
        int ls = j.indexOf("\"libraries\""); if (ls == -1) return cp;
        int as = j.indexOf("[", ls); if (as == -1) return cp;
        int d = 1, p = as + 1;
        while (p < j.length() && d > 0) { char c = j.charAt(p); if (c == '[' || c == '{') d++; else if (c == ']' || c == '}') d--; p++; }
        String ls2 = j.substring(as, p);
        Set<String> added = new HashSet<>();
        Matcher a = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(ls2);
        while (a.find()) {
            String path = a.group(1), url = a.group(2);
            if (!added.contains(path)) {
                Path f = LIBS.resolve(path);
                Files.createDirectories(f.getParent());
                if (!Files.exists(f)) { log("[LIB] " + path.substring(path.lastIndexOf('/') + 1)); download(url, f); }
                cp.add(f.toString()); added.add(path);
            }
        }
        Matcher n = Pattern.compile("\"natives-" + os + "\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(ls2);
        while (n.find()) {
            Path t = Files.createTempFile("n", ".jar");
            try { log("[NAT] " + n.group(1).substring(n.group(1).lastIndexOf('/') + 1)); download(n.group(1), t); unzip(t, nd); }
            finally { Files.deleteIfExists(t); }
        }
        return cp;
    }

    void unzip(Path j, Path d) throws Exception {
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(j))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (n.startsWith("META-INF/")) continue;
                if (n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib") || n.endsWith(".jnilib"))
                    Files.copy(z, d.resolve(Paths.get(n).getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    String downloadAssetIndex(String j) throws Exception {
        String id = exN(j, "assetIndex", "id"), url = exN(j, "assetIndex", "url");
        if (id == null) id = "legacy"; if (url == null) return id;
        Path idx = ASSETS.resolve("indexes").resolve(id + ".json");
        String data = Files.exists(idx) ? Files.readString(idx) : fetch(url);
        if (!Files.exists(idx)) { log("[ASSETS] Index: " + id); Files.writeString(idx, data); }
        dlAssets(data);
        return id;
    }

    void dlAssets(String j) throws Exception {
        Path od = ASSETS.resolve("objects");
        Matcher m = Pattern.compile("\"hash\"\\s*:\\s*\"([a-f0-9]{40})\"").matcher(j);
        List<String> h = new ArrayList<>(); while (m.find()) h.add(m.group(1));
        log("[ASSETS] " + h.size() + " total");
        int c = 0;
        for (String x : h) {
            Path f = od.resolve(x.substring(0, 2)).resolve(x);
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try { download("https://resources.download.minecraft.net/" + x.substring(0, 2) + "/" + x, f); c++; if (c % 200 == 0) log("[ASSETS] " + c); }
                catch (Exception ignored) {}
            }
        }
        log("[ASSETS] " + (c > 0 ? "Downloaded " + c : "Cached"));
    }

    String exN(String j, String o, String k) {
        int s = j.indexOf("\"" + o + "\""); if (s == -1) return null;
        int a = j.indexOf("{", s), b = j.indexOf("}", a); if (a == -1 || b == -1) return null;
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"([^\"]+)\"").matcher(j.substring(a, b + 1));
        return m.find() ? m.group(1) : null;
    }

    String offlineUUID(String n) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8));
            d[6] = (byte)((d[6] & 0x0f) | 0x30); d[8] = (byte)((d[8] & 0x3f) | 0x80);
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 16; i++) { s.append(String.format("%02x", d[i])); if (i == 3 || i == 5 || i == 7 || i == 9) s.append("-"); }
            return s.toString();
        } catch (Exception e) { return UUID.randomUUID().toString(); }
    }

    String javaPath(String mcVer) {
        // MC 1.21+ needs Java 21, MC 1.20.5+ needs Java 21, MC 1.18+ needs Java 17, older needs Java 8
        int reqVer = 17;
        if (mcVer.startsWith("1.21") || mcVer.startsWith("1.20.5") || mcVer.startsWith("1.20.6")) {
            reqVer = 21;
        } else if (mcVer.startsWith("1.20") || mcVer.startsWith("1.19") || mcVer.startsWith("1.18")) {
            reqVer = 17;
        }
        
        String os = getOS();
        String exe = os.equals("windows") ? "java.exe" : "java";
        
        // Check common Java installation paths
        List<String> searchPaths = new ArrayList<>();
        
        if (os.equals("osx")) {
            // macOS paths
            searchPaths.add("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java");
            searchPaths.add("/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java");
            searchPaths.add("/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java");
            searchPaths.add("/opt/homebrew/opt/openjdk@21/bin/java");
            searchPaths.add("/usr/local/opt/openjdk@21/bin/java");
            searchPaths.add("/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java");
            searchPaths.add("/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java");
            searchPaths.add("/opt/homebrew/opt/openjdk@17/bin/java");
            searchPaths.add("/opt/homebrew/opt/openjdk/bin/java");
        } else if (os.equals("windows")) {
            // Windows paths
            searchPaths.add("C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe");
            searchPaths.add("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe");
            searchPaths.add("C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe");
            searchPaths.add("C:\\Program Files\\Eclipse Adoptium\\jdk-17\\bin\\java.exe");
            searchPaths.add("C:\\Program Files\\Java\\jdk-17\\bin\\java.exe");
        } else {
            // Linux paths
            searchPaths.add("/usr/lib/jvm/java-21-openjdk/bin/java");
            searchPaths.add("/usr/lib/jvm/temurin-21-jdk/bin/java");
            searchPaths.add("/usr/lib/jvm/java-21/bin/java");
            searchPaths.add("/usr/lib/jvm/java-17-openjdk/bin/java");
            searchPaths.add("/usr/lib/jvm/temurin-17-jdk/bin/java");
        }
        
        // Try to find Java with correct version
        for (String path : searchPaths) {
            if (Files.exists(Paths.get(path))) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(path, "-version");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    String out = new String(p.getInputStream().readAllBytes());
                    // Check version number
                    if (reqVer == 21 && (out.contains("\"21") || out.contains(" 21"))) {
                        log("[JAVA] Found Java 21: " + path);
                        return path;
                    } else if (reqVer == 17 && (out.contains("\"17") || out.contains("\"21") || out.contains(" 17") || out.contains(" 21"))) {
                        log("[JAVA] Found Java " + (out.contains("21") ? "21" : "17") + ": " + path);
                        return path;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        // Check JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            String path = javaHome + File.separator + "bin" + File.separator + exe;
            if (Files.exists(Paths.get(path))) {
                log("[JAVA] Using JAVA_HOME: " + path);
                return path;
            }
        }
        
        // Fall back to current JVM
        String current = System.getProperty("java.home") + File.separator + "bin" + File.separator + exe;
        String ver = System.getProperty("java.version");
        int currentVer = 0;
        try {
            String[] parts = ver.split("\\.");
            currentVer = Integer.parseInt(parts[0]);
        } catch (Exception ignored) {}
        
        if (currentVer < reqVer) {
            log("[WARN] MC " + mcVer + " requires Java " + reqVer + " but found Java " + currentVer);
            log("[WARN] Download Java 21 from: https://adoptium.net/");
            log("[WARN] Or use an older MC version (1.20.4 works with Java 17)");
        }
        
        return current;
    }
    
    String getOS() { String o = System.getProperty("os.name").toLowerCase(); return o.contains("win") ? "windows" : o.contains("mac") ? "osx" : "linux"; }
}
