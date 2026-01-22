/*
 * CTBadlion 0.1 - Badlion-Style Launcher
 * Team Flames / Samsoft
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.*;
import java.time.Duration;
import java.util.zip.*;

public class CTBadlion extends JFrame {
    static final Color BG = new Color(20, 20, 30);
    static final Color BG2 = new Color(30, 30, 45);
    static final Color BG3 = new Color(45, 45, 65);
    static final Color ACCENT = new Color(90, 130, 255);
    static final Color GREEN = new Color(70, 210, 110);
    static final Color WHITE = new Color(255, 255, 255);
    static final Color GRAY = new Color(180, 180, 200);

    static final Path BASE = Paths.get(System.getProperty("user.home"), ".ctbadlion");
    static final Path VERSIONS = BASE.resolve("versions");
    static final Path LIBS = BASE.resolve("libraries");
    static final Path ASSETS = BASE.resolve("assets");
    static final Path MODS_DIR = BASE.resolve("mods");
    static final Path FABRIC_DIR = BASE.resolve("fabric");
    static final String SEP = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";

    static final int THREADS = 50;
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    JTextField userField;
    JComboBox<String> verBox;
    JSlider ramSlider;
    JTextArea console;
    JProgressBar progress;
    JLabel statusLbl, ramLbl;
    JButton playBtn;
    Map<String, JCheckBox> mods = new LinkedHashMap<>();
    volatile boolean launching = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CTBadlion().setVisible(true));
    }

    public CTBadlion() {
        setTitle("CTBadlion 0.1");
        setSize(950, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        mkdirs();
        cleanAllMods(); // Fresh mods every launch

        add(createMain(), BorderLayout.CENTER);

        log("══════════════════════════════════════════════════════════");
        log("                    Team Flames");
        log("              Cracked + 50 Parallel Downloads");
        log("══════════════════════════════════════════════════════════");
        log("");
        log("[INFO] Cleaned mods folder for fresh start");
        log("[INFO] Press F5 in-game for Mod Menu");
        log("[READY] Click PLAY!");
    }

    void mkdirs() {
        try {
            Files.createDirectories(VERSIONS);
            Files.createDirectories(LIBS);
            Files.createDirectories(ASSETS.resolve("indexes"));
            Files.createDirectories(ASSETS.resolve("objects"));
            Files.createDirectories(MODS_DIR);
            Files.createDirectories(FABRIC_DIR);
        } catch (Exception ignored) {}
    }

    void cleanAllMods() {
        try {
            if (Files.exists(MODS_DIR)) {
                Files.list(MODS_DIR).forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        } catch (Exception ignored) {}
    }

    JPanel createMain() {
        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // === TOP BAR ===
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG2);
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        // Settings on left
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        settings.setBackground(BG2);

        // Username
        JPanel up = new JPanel();
        up.setBackground(BG2);
        up.setLayout(new BoxLayout(up, BoxLayout.Y_AXIS));
        JLabel ul = new JLabel("USERNAME");
        ul.setFont(new Font("SansSerif", Font.BOLD, 10));
        ul.setForeground(GRAY);
        up.add(ul);
        userField = new JTextField("Player", 10);
        userField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        userField.setBackground(BG3);
        userField.setForeground(WHITE);
        userField.setCaretColor(WHITE);
        userField.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        up.add(userField);
        settings.add(up);

        // Version
        JPanel vp = new JPanel();
        vp.setBackground(BG2);
        vp.setLayout(new BoxLayout(vp, BoxLayout.Y_AXIS));
        JLabel vl = new JLabel("VERSION");
        vl.setFont(new Font("SansSerif", Font.BOLD, 10));
        vl.setForeground(GRAY);
        vp.add(vl);
        verBox = new JComboBox<>(new String[]{"1.20.4", "1.20.1", "1.19.4", "1.18.2"});
        verBox.setFont(new Font("SansSerif", Font.PLAIN, 13));
        verBox.setBackground(BG3);
        verBox.setForeground(WHITE);
        vp.add(verBox);
        settings.add(vp);

        // RAM
        JPanel rp = new JPanel();
        rp.setBackground(BG2);
        rp.setLayout(new BoxLayout(rp, BoxLayout.Y_AXIS));
        ramLbl = new JLabel("RAM: 6GB");
        ramLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        ramLbl.setForeground(GRAY);
        rp.add(ramLbl);
        ramSlider = new JSlider(2, 16, 6);
        ramSlider.setBackground(BG2);
        ramSlider.setForeground(WHITE);
        ramSlider.addChangeListener(e -> ramLbl.setText("RAM: " + ramSlider.getValue() + "GB"));
        rp.add(ramSlider);
        settings.add(rp);

        topBar.add(settings, BorderLayout.WEST);

        // Play button on right
        playBtn = new JButton("PLAY");
        playBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        playBtn.setBackground(GREEN);
        playBtn.setForeground(WHITE);
        playBtn.setFocusPainted(false);
        playBtn.setBorderPainted(false);
        playBtn.setPreferredSize(new Dimension(100, 40));
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.addActionListener(e -> launch());
        topBar.add(playBtn, BorderLayout.EAST);

        main.add(topBar, BorderLayout.NORTH);

        // === CENTER: MODS + CONSOLE ===
        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
        center.setBackground(BG);

        // Mods panel
        JPanel modsPanel = new JPanel(new BorderLayout());
        modsPanel.setBackground(BG2);
        modsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel modsLbl = new JLabel("MODS (F5 in-game)");
        modsLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        modsLbl.setForeground(WHITE);
        modsPanel.add(modsLbl, BorderLayout.NORTH);

        JPanel modGrid = new JPanel(new GridLayout(0, 2, 8, 8));
        modGrid.setBackground(BG2);
        modGrid.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Only mods that work without Kotlin
        addMod(modGrid, "FPS Boost", "sodium", true);
        addMod(modGrid, "Performance", "lithium", true);
        addMod(modGrid, "Zoom (C)", "logical-zoom", true);
        addMod(modGrid, "Dyn Lights", "lambdynamiclights", true);
        addMod(modGrid, "Food Info", "appleskin", true);
        addMod(modGrid, "No Report", "no-chat-reports", true);
        addMod(modGrid, "Entity Cull", "entityculling", true);
        addMod(modGrid, "Better F3", "betterf3", true);

        modsPanel.add(modGrid, BorderLayout.CENTER);
        center.add(modsPanel);

        // Console panel
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBackground(BG2);
        consolePanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel consoleLbl = new JLabel("CONSOLE");
        consoleLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        consoleLbl.setForeground(WHITE);
        consolePanel.add(consoleLbl, BorderLayout.NORTH);

        console = new JTextArea();
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        console.setBackground(new Color(15, 15, 25));
        console.setForeground(new Color(100, 255, 100));
        console.setCaretColor(GREEN);
        console.setEditable(false);
        console.setLineWrap(true);
        console.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        consolePanel.add(scroll, BorderLayout.CENTER);
        center.add(consolePanel);

        main.add(center, BorderLayout.CENTER);

        // === BOTTOM BAR ===
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(BG2);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Status on left
        statusLbl = new JLabel("Ready - 50 parallel downloads");
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLbl.setForeground(GRAY);
        bottomBar.add(statusLbl, BorderLayout.WEST);

        // Progress bar in center
        progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        progress.setFont(new Font("SansSerif", Font.BOLD, 10));
        progress.setForeground(ACCENT);
        progress.setBackground(BG3);
        progress.setPreferredSize(new Dimension(180, 22));
        bottomBar.add(progress, BorderLayout.CENTER);

        // Title on right bottom
        JLabel titleLbl = new JLabel("CTBadlion 0.1");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLbl.setForeground(ACCENT);
        bottomBar.add(titleLbl, BorderLayout.EAST);

        main.add(bottomBar, BorderLayout.SOUTH);

        return main;
    }

    void addMod(JPanel p, String name, String slug, boolean on) {
        JCheckBox cb = new JCheckBox(name);
        cb.setSelected(on);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setForeground(WHITE);
        cb.setBackground(BG3);
        cb.setOpaque(true);
        cb.setFocusPainted(false);
        cb.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        mods.put(slug, cb);
        p.add(cb);
    }

    void log(String s) {
        SwingUtilities.invokeLater(() -> {
            console.append(s + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    void status(String s) { SwingUtilities.invokeLater(() -> statusLbl.setText(s)); }
    void prog(int v) { SwingUtilities.invokeLater(() -> progress.setValue(v)); }

    void launch() {
        if (launching) return;
        launching = true;
        playBtn.setEnabled(false);
        playBtn.setText("...");
        progress.setValue(0);
        cleanAllMods(); // Always clean before launch

        new Thread(() -> {
            try {
                String user = userField.getText().trim();
                if (user.isEmpty()) user = "Player" + (int)(Math.random() * 9999);
                String mcVer = (String) verBox.getSelectedItem();
                int ram = ramSlider.getValue();

                log("\n[LAUNCH] " + mcVer + " / " + user + " / " + ram + "GB");
                long t0 = System.currentTimeMillis();

                // Download mods (parallel)
                status("Downloading mods...");
                prog(5);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                futures.add(modAsync("fabric-api", mcVer));
                futures.add(modAsync("modmenu", mcVer));
                for (var e : mods.entrySet()) if (e.getValue().isSelected()) futures.add(modAsync(e.getKey(), mcVer));
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                prog(20);

                // Version JSON
                status("Getting version...");
                String manifest = fetch("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
                String verUrl = findUrl(manifest, mcVer);
                String verJson = fetch(verUrl);
                prog(25);

                Path vDir = VERSIONS.resolve(mcVer);
                Files.createDirectories(vDir);
                Path natives = vDir.resolve("natives");
                Files.createDirectories(natives);

                // Client JAR
                String clientUrl = getUrl(verJson, "client");
                Path clientJar = vDir.resolve(mcVer + ".jar");
                if (!Files.exists(clientJar)) { log("[DL] Client JAR"); dl(clientUrl, clientJar); }
                prog(35);

                // Libraries (parallel)
                status("Libraries...");
                List<String> cp = libsAsync(verJson, natives);
                prog(60);

                // Fabric (parallel)
                status("Fabric...");
                cp.addAll(0, fabricAsync(mcVer));
                prog(75);

                // Assets (parallel)
                status("Assets...");
                String assetIdx = assetsAsync(verJson);
                prog(95);

                long secs = (System.currentTimeMillis() - t0) / 1000;
                log("[DONE] Downloaded in " + secs + "s");

                // Build launch command
                List<String> cmd = new ArrayList<>();
                cmd.add(java());
                if (os().equals("osx")) cmd.add("-XstartOnFirstThread");
                cmd.add("-Xmx" + ram + "G");
                cmd.add("-XX:+UseG1GC");
                cmd.add("-Djava.library.path=" + natives);
                cmd.add("-Dfabric.gameVersion=" + mcVer);
                cmd.add("-Dfabric.loader.gameDir=" + BASE);
                cmd.add("-cp");
                cp.add(clientJar.toString());
                cmd.add(String.join(SEP, cp));
                cmd.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
                cmd.add("--username"); cmd.add(user);
                cmd.add("--uuid"); cmd.add(uuid(user));
                cmd.add("--accessToken"); cmd.add("0");
                cmd.add("--userType"); cmd.add("legacy");
                cmd.add("--version"); cmd.add(mcVer);
                cmd.add("--gameDir"); cmd.add(BASE.toString());
                cmd.add("--assetsDir"); cmd.add(ASSETS.toString());
                cmd.add("--assetIndex"); cmd.add(assetIdx);
                // Cracked server support
                cmd.add("--server"); cmd.add("");
                cmd.add("--width"); cmd.add("925");
                cmd.add("--height"); cmd.add("530");

                prog(100);
                log("[INFO] F5 = Mod Menu | Works on cracked servers");
                log("[LAUNCH] Starting Minecraft...");
                status("Playing!");

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(BASE.toFile());
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                new Thread(() -> {
                    try (var br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) log("[MC] " + line);
                    } catch (Exception ignored) {}
                }).start();

                proc.waitFor();
                log("[EXIT] Minecraft closed");

            } catch (Exception e) {
                log("[ERROR] " + e.getMessage());
                e.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    launching = false;
                    playBtn.setEnabled(true);
                    playBtn.setText("PLAY");
                    status("Ready");
                });
            }
        }).start();
    }

    CompletableFuture<Void> modAsync(String slug, String ver) {
        return CompletableFuture.runAsync(() -> {
            try (var s = Files.newDirectoryStream(MODS_DIR, slug + "*.jar")) { for (var x : s) return; } catch (Exception ignored) {}
            try {
                String api = "https://api.modrinth.com/v2/project/" + slug + "/version?game_versions=%5B%22" + ver + "%22%5D&loaders=%5B%22fabric%22%5D";
                String resp = fetch(api);
                var m = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"").matcher(resp);
                if (m.find()) {
                    String url = m.group(1);
                    String fn = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                    dl(url, MODS_DIR.resolve(fn));
                    log("[MOD] " + slug);
                }
            } catch (Exception ignored) {}
        }, pool);
    }

    List<String> libsAsync(String json, Path natives) throws Exception {
        List<String> cp = Collections.synchronizedList(new ArrayList<>());
        Set<String> added = Collections.synchronizedSet(new HashSet<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        var m = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(json);
        while (m.find()) {
            String path = m.group(1), url = m.group(2);
            if (!added.add(path)) continue;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Path f = LIBS.resolve(path);
                    Files.createDirectories(f.getParent());
                    if (!Files.exists(f)) dl(url, f);
                    cp.add(f.toString());
                } catch (Exception ignored) {}
            }, pool));
        }

        var nm = Pattern.compile("\"natives-" + os() + "\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(json);
        while (nm.find()) {
            String url = nm.group(1);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Path tmp = Files.createTempFile("n", ".jar");
                    dl(url, tmp); unzip(tmp, natives); Files.delete(tmp);
                } catch (Exception ignored) {}
            }, pool));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return cp;
    }

    List<String> fabricAsync(String ver) throws Exception {
        List<String> libs = Collections.synchronizedList(new ArrayList<>());
        String[][] fl = {
            {"net.fabricmc", "fabric-loader", "0.16.10", "https://maven.fabricmc.net/"},
            {"net.fabricmc", "intermediary", ver, "https://maven.fabricmc.net/"},
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
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String[] l : fl) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    String path = l[0].replace('.', '/') + "/" + l[1] + "/" + l[2] + "/" + l[1] + "-" + l[2] + ".jar";
                    Path lp = FABRIC_DIR.resolve(path);
                    Files.createDirectories(lp.getParent());
                    if (!Files.exists(lp)) dl(l[3] + path, lp);
                    libs.add(lp.toString());
                } catch (Exception ignored) {}
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return libs;
    }

    String assetsAsync(String json) throws Exception {
        var m = Pattern.compile("\"assetIndex\"[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(json);
        String id = "legacy", url = null;
        if (m.find()) { id = m.group(1); url = m.group(2); }
        if (url == null) return id;

        Path idx = ASSETS.resolve("indexes").resolve(id + ".json");
        String data = Files.exists(idx) ? Files.readString(idx) : fetch(url);
        if (!Files.exists(idx)) Files.writeString(idx, data);

        List<String> hashes = new ArrayList<>();
        var hm = Pattern.compile("\"hash\"\\s*:\\s*\"([a-f0-9]{40})\"").matcher(data);
        while (hm.find()) hashes.add(hm.group(1));

        int[] count = {0};
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String h : hashes) {
            Path f = ASSETS.resolve("objects").resolve(h.substring(0, 2)).resolve(h);
            if (!Files.exists(f)) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        Files.createDirectories(f.getParent());
                        dl("https://resources.download.minecraft.net/" + h.substring(0, 2) + "/" + h, f);
                        count[0]++;
                    } catch (Exception ignored) {}
                }, pool));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        if (count[0] > 0) log("[ASSETS] " + count[0] + " files");
        return id;
    }

    String fetch(String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "CTBadlion/0.1").timeout(Duration.ofSeconds(30)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    void dl(String url, Path dest) throws Exception {
        Files.createDirectories(dest.getParent());
        var req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "CTBadlion/0.1").timeout(Duration.ofSeconds(60)).build();
        http.send(req, HttpResponse.BodyHandlers.ofFile(dest));
    }

    String findUrl(String m, String v) {
        int i = m.indexOf("\"id\":\"" + v + "\"");
        if (i == -1) i = m.indexOf("\"id\": \"" + v + "\"");
        if (i == -1) return null;
        int u = m.indexOf("\"url\"", i), s = m.indexOf("\"", u + 6) + 1, e = m.indexOf("\"", s);
        return m.substring(s, e);
    }

    String getUrl(String j, String k) {
        int d = j.indexOf("\"downloads\""), p = j.indexOf("\"" + k + "\"", d), u = j.indexOf("\"url\"", p);
        int s = j.indexOf("\"", u + 6) + 1, e = j.indexOf("\"", s);
        return j.substring(s, e);
    }

    void unzip(Path jar, Path dest) throws Exception {
        try (var z = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib"))
                    Files.copy(z, dest.resolve(Paths.get(n).getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    String uuid(String name) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            d[6] = (byte)((d[6] & 0x0f) | 0x30); d[8] = (byte)((d[8] & 0x3f) | 0x80);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) { sb.append(String.format("%02x", d[i])); if (i==3||i==5||i==7||i==9) sb.append("-"); }
            return sb.toString();
        } catch (Exception e) { return UUID.randomUUID().toString(); }
    }

    String java() {
        if (os().equals("osx")) for (String p : new String[]{"/opt/homebrew/opt/openjdk/bin/java", "/opt/homebrew/opt/openjdk@21/bin/java"}) if (Files.exists(Paths.get(p))) return p;
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + (os().equals("windows") ? "java.exe" : "java");
    }

    String os() { String o = System.getProperty("os.name").toLowerCase(); return o.contains("win") ? "windows" : o.contains("mac") ? "osx" : "linux"; }
}
