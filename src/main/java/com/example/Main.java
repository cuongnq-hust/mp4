package com.example;

import com.example.ffmpeg.FFmpegManager;
import com.example.googleAI.GeminiTranslator;
import com.example.tts.EdgeTTSManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    // Bảng màu Cinema Dark Studio hiện đại
    private static final Color BG_DARK = new Color(15, 23, 42);       // #0F172A
    private static final Color CARD_BG = new Color(30, 41, 59);       // #1E293B
    private static final Color INPUT_BG = new Color(51, 65, 85);      // #334155
    private static final Color BORDER_COLOR = new Color(71, 85, 105); // #475569
    private static final Color ACCENT_CYAN = new Color(56, 189, 248); // #38BDF8
    private static final Color ACCENT_GOLD = new Color(250, 204, 21); // #FACC15
    private static final Color ACCENT_GREEN = new Color(34, 197, 94); // #22C55E
    private static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    private static final Color TEXT_MUTED = new Color(148, 163, 184);

    private static final String[] STUDIO_PRESETS = {
            "1. Nam Trầm Điện Ảnh (YouTube Review Kiếm Hiệp / Hành Động - Bass Ấm)",
            "2. Nữ TikTok / CapCut Sáng (Review Ngôn Tình / Drama / Tổng Tài)",
            "3. Nam TikTok Tốc Độ (Tóm Tắt Phim Nhanh, Rõ Nét)",
            "4. Gemini AI Nam Truyền Cảm (Giọng Charon - Đa Cảm Xúc Thế Hệ Mới)",
            "5. Gemini AI Nữ Truyền Cảm (Giọng Kore - Tự Nhiên Như Người Thật)"
    };

    private static JFrame mainFrame;
    private static JRadioButton rbFullMode;
    private static JRadioButton rbResumeMode;
    private static JTextField txtVideoInput;
    private static JTextField txtExtraFile;
    private static JLabel lblExtraFile;
    private static JButton btnBrowseExtra;
    private static JComboBox<String> cmbVoicePreset;
    private static JCheckBox chkShowViSub;
    private static JCheckBox chkPauseForReview;
    private static JCheckBox chkKeepOriginalAudio;
    private static JLabel lblApiKeyStatus;
    private static JProgressBar progressBar;
    private static JLabel lblStepStatus;
    private static JTextArea txtLog;
    private static JButton btnStart;
    private static JButton btnOpenVideo;
    private static JButton btnOpenFolder;

    private static volatile File lastOutputVideoFile = null;
    private static volatile File lastWorkspaceDir = null;
    private static volatile boolean isProcessing = false;

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(Main::createAndShowStudioGUI);
    }

    private static void createAndShowStudioGUI() {
        mainFrame = new JFrame("Review Phim AI Studio Pro - Tự Động Dịch & Lồng Tiếng Phòng Thu");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1020, 760);
        mainFrame.setMinimumSize(new Dimension(920, 680));
        mainFrame.setLocationRelativeTo(null);

        List<Image> appIcons = loadOrCreateAppIcons();
        if (!appIcons.isEmpty()) {
            mainFrame.setIconImages(appIcons);
        }

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(BG_DARK);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        // 1. HEADER THƯƠNG HIỆU & CÀI ĐẶT API KEY
        root.add(buildHeaderPanel(appIcons), BorderLayout.NORTH);

        // 2. KHU VỰC TRUNG TÂM: BẢNG CẤU HÌNH + NHẬT KÝ TRỰC TIẾP
        JPanel centerPanel = new JPanel(new BorderLayout(14, 14));
        centerPanel.setOpaque(false);
        centerPanel.add(buildConfigCard(), BorderLayout.NORTH);
        centerPanel.add(buildLogCard(), BorderLayout.CENTER);
        root.add(centerPanel, BorderLayout.CENTER);

        // 3. THANH TIẾN TRÌNH & NÚT ĐIỀU KHIỂN
        root.add(buildFooterActionPanel(), BorderLayout.SOUTH);

        mainFrame.setContentPane(root);
        redirectSystemStreamsToGUI();

        refreshApiKeyBadge();
        System.out.println("✨ Chào mừng bạn đến với Review Phim AI Studio Pro!");
        System.out.println("👉 Hãy chọn file video (.mp4) hoặc dán link YouTube, chọn giọng đọc Studio và bấm [🚀 BẮT ĐẦU XỬ LÝ VIDEO].");

        mainFrame.setVisible(true);
    }

    private static JPanel buildHeaderPanel(List<Image> appIcons) {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel brandLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandLeft.setOpaque(false);

        JLabel lblLogo = new JLabel();
        BufferedImage logoImg = createVectorLogoImage(52);
        File logoFile = new File("logo.png");
        if (logoFile.exists()) {
            try {
                Image raw = ImageIO.read(logoFile);
                if (raw != null) {
                    lblLogo.setIcon(new ImageIcon(raw.getScaledInstance(52, 52, Image.SCALE_SMOOTH)));
                }
            } catch (Exception ignored) {
            }
        }
        if (lblLogo.getIcon() == null) {
            lblLogo.setIcon(new ImageIcon(logoImg));
        }
        brandLeft.add(lblLogo);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);
        JLabel lblTitle = new JLabel("REVIEW PHIM AI STUDIO PRO");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 21));
        lblTitle.setForeground(ACCENT_CYAN);

        JLabel lblSubtitle = new JLabel("Bóc băng AI • Dịch thuật Gemini Pro • Lồng tiếng YouTube / TikTok & Khớp Khẩu Hình Tự Động");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSubtitle.setForeground(TEXT_MUTED);

        titleBox.add(lblTitle);
        titleBox.add(lblSubtitle);
        brandLeft.add(titleBox);

        header.add(brandLeft, BorderLayout.WEST);

        // Góc phải: Trạng thái API Key + Nút đổi Key
        JPanel apiBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        apiBox.setOpaque(false);

        lblApiKeyStatus = new JLabel("🔑 Đang kiểm tra Key...");
        lblApiKeyStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblApiKeyStatus.setForeground(ACCENT_GREEN);

        JButton btnConfigKey = createStyledButton("⚙️ Cài đặt Gemini Key", new Color(71, 85, 105), TEXT_PRIMARY);
        btnConfigKey.addActionListener(e -> openApiKeyDialog());

        apiBox.add(lblApiKeyStatus);
        apiBox.add(btnConfigKey);
        header.add(apiBox, BorderLayout.EAST);

        return header;
    }

    private static JPanel buildConfigCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Dòng 0: Chế độ chạy
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        card.add(createFieldLabel("Chế độ chạy:"), gbc);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        modePanel.setOpaque(false);
        rbFullMode = new JRadioButton("🎬 Chạy tự động từ A - Z (Tải/Nhập Video ➔ Bóc băng ➔ Dịch Gemini ➔ Lồng tiếng)", true);
        rbResumeMode = new JRadioButton("⏩ Chỉ Lồng tiếng & Render (Dùng file bản dịch review_translation.txt có sẵn)", false);
        styleRadioButton(rbFullMode);
        styleRadioButton(rbResumeMode);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbFullMode);
        bg.add(rbResumeMode);
        modePanel.add(rbFullMode);
        modePanel.add(rbResumeMode);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        card.add(modePanel, gbc);

        // Dòng 1: Nguồn Video (.mp4 hoặc link YouTube)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        card.add(createFieldLabel("Nguồn Video:"), gbc);

        txtVideoInput = createStyledTextField("Kéo thả file .mp4 vào đây, bấm Chọn Video, hoặc dán trực tiếp Link YouTube...");
        File defaultTestVideo = new File("test_chinese.mp4");
        if (defaultTestVideo.exists()) {
            txtVideoInput.setText(defaultTestVideo.getAbsolutePath());
        }

        // Hỗ trợ kéo thả file video trực tiếp vào ô nhập
        enableFileDragAndDrop(txtVideoInput);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        card.add(txtVideoInput, gbc);

        JButton btnBrowseVideo = createStyledButton("📂 Chọn File Video", new Color(37, 99, 235), TEXT_PRIMARY);
        btnBrowseVideo.addActionListener(e -> chooseVideoFile());
        gbc.gridx = 2;
        gbc.weightx = 0;
        card.add(btnBrowseVideo, gbc);

        // Dòng 2: File Từ điển (Chế độ A-Z) hoặc File review_translation.txt (Chế độ Chỉ lồng tiếng)
        gbc.gridx = 0;
        gbc.gridy = 2;
        lblExtraFile = createFieldLabel("Từ điển (Tùy chọn):");
        card.add(lblExtraFile, gbc);

        txtExtraFile = createStyledTextField("");
        txtExtraFile.setToolTipText("Có thể bỏ trống, hoặc chọn file từ điển tên riêng (.txt)");
        enableFileDragAndDrop(txtExtraFile);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        card.add(txtExtraFile, gbc);

        btnBrowseExtra = createStyledButton("📄 Chọn File .txt", new Color(71, 85, 105), TEXT_PRIMARY);
        btnBrowseExtra.addActionListener(e -> chooseExtraTxtFile());
        gbc.gridx = 2;
        gbc.weightx = 0;
        card.add(btnBrowseExtra, gbc);

        rbFullMode.addActionListener(e -> updateModeUI());
        rbResumeMode.addActionListener(e -> updateModeUI());

        // Dòng 3: Giọng đọc Phòng thu AI
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        card.add(createFieldLabel("Giọng đọc Studio:"), gbc);

        cmbVoicePreset = new JComboBox<>(STUDIO_PRESETS);
        cmbVoicePreset.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cmbVoicePreset.setBackground(Color.WHITE);
        cmbVoicePreset.setForeground(new Color(15, 23, 42));
        cmbVoicePreset.setPreferredSize(new Dimension(400, 34));
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        card.add(cmbVoicePreset, gbc);

        // Dòng 4: Các tùy chọn xử lý
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        card.add(createFieldLabel("Tùy chọn xuất:"), gbc);

        JPanel optPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        optPanel.setOpaque(false);

        chkShowViSub = new JCheckBox("📝 Phụ đề Tiếng Việt + Che kín 100% chữ Trung gốc", true);
        chkPauseForReview = new JCheckBox("⏸️ Duyệt & sửa bản dịch trên giao diện trước khi lồng tiếng", false);
        chkKeepOriginalAudio = new JCheckBox("🔊 Giữ âm thanh gốc làm nền nhỏ (12%)", false);

        styleCheckBox(chkShowViSub);
        styleCheckBox(chkPauseForReview);
        styleCheckBox(chkKeepOriginalAudio);

        optPanel.add(chkShowViSub);
        optPanel.add(chkPauseForReview);
        optPanel.add(chkKeepOriginalAudio);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        card.add(optPanel, gbc);

        return card;
    }

    private static JPanel buildLogCard() {
        JPanel logCard = new JPanel(new BorderLayout(0, 8));
        logCard.setBackground(CARD_BG);
        logCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(10, 14, 12, 14)
        ));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel lblLogTitle = new JLabel("📡 TIẾN TRÌNH XỬ LÝ TRỰC TIẾP (LIVE STUDIO MONITOR)");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLogTitle.setForeground(ACCENT_GOLD);
        topBar.add(lblLogTitle, BorderLayout.WEST);

        JButton btnClearLog = createStyledButton("🧹 Xóa nhật ký", new Color(51, 65, 85), TEXT_MUTED);
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnClearLog.addActionListener(e -> txtLog.setText(""));
        topBar.add(btnClearLog, BorderLayout.EAST);

        logCard.add(topBar, BorderLayout.NORTH);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setBackground(new Color(10, 15, 29));
        txtLog.setForeground(new Color(226, 232, 240));
        txtLog.setCaretColor(ACCENT_CYAN);
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        txtLog.setMargin(new Insets(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(txtLog);
        scroll.setBorder(new LineBorder(new Color(30, 41, 59), 1));
        logCard.add(scroll, BorderLayout.CENTER);

        return logCard;
    }

    private static JPanel buildFooterActionPanel() {
        JPanel footer = new JPanel(new BorderLayout(14, 10));
        footer.setBackground(CARD_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Thanh trạng thái + ProgressBar
        JPanel progBox = new JPanel(new BorderLayout(0, 6));
        progBox.setOpaque(false);

        lblStepStatus = new JLabel("Sẵn sàng xử lý video.");
        lblStepStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStepStatus.setForeground(TEXT_PRIMARY);
        progBox.add(lblStepStatus, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setPreferredSize(new Dimension(300, 22));
        progressBar.setForeground(new Color(16, 185, 129));
        progBox.add(progressBar, BorderLayout.CENTER);

        footer.add(progBox, BorderLayout.CENTER);

        // Cụm nút hành động bên phải
        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnGroup.setOpaque(false);

        btnOpenFolder = createStyledButton("📁 Mở Thư Mục Xuất", new Color(51, 65, 85), TEXT_PRIMARY);
        btnOpenFolder.setEnabled(false);
        btnOpenFolder.addActionListener(e -> {
            if (lastWorkspaceDir != null && lastWorkspaceDir.exists()) {
                try {
                    Desktop.getDesktop().open(lastWorkspaceDir);
                } catch (Exception ignored) {
                }
            }
        });

        btnOpenVideo = createStyledButton("▶️ Xem Video Kết Quả", new Color(14, 165, 233), TEXT_PRIMARY);
        btnOpenVideo.setEnabled(false);
        btnOpenVideo.addActionListener(e -> {
            if (lastOutputVideoFile != null && lastOutputVideoFile.exists()) {
                try {
                    Desktop.getDesktop().open(lastOutputVideoFile);
                } catch (Exception ignored) {
                }
            }
        });

        btnStart = createStyledButton("🚀 BẮT ĐẦU XỬ LÝ VIDEO", new Color(22, 163, 74), Color.WHITE);
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnStart.setPreferredSize(new Dimension(235, 42));
        btnStart.addActionListener(e -> startVideoPipelineThread());

        btnGroup.add(btnOpenFolder);
        btnGroup.add(btnOpenVideo);
        btnGroup.add(btnStart);

        footer.add(btnGroup, BorderLayout.EAST);
        return footer;
    }

    private static void updateModeUI() {
        boolean resume = rbResumeMode.isSelected();
        if (resume) {
            lblExtraFile.setText("File Bản dịch (.txt):");
            txtExtraFile.setToolTipText("Chọn file review_translation.txt đã dịch trước đó");
            chkPauseForReview.setSelected(true);
        } else {
            lblExtraFile.setText("Từ điển (Tùy chọn):");
            txtExtraFile.setToolTipText("Có thể bỏ trống, hoặc chọn file từ điển tên riêng (.txt)");
        }
    }

    private static void chooseVideoFile() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Chọn Video gốc cần dịch & lồng tiếng");
        chooser.setFileFilter(new FileNameExtensionFilter("Video Files (*.mp4, *.mkv, *.mov)", "mp4", "mkv", "mov", "avi"));
        if (chooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            txtVideoInput.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static void chooseExtraTxtFile() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
        if (chooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            txtExtraFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static void openApiKeyDialog() {
        String currentKey = readConfiguredApiKey();
        JTextField txtKey = new JTextField(currentKey, 38);
        txtKey.setFont(new Font("Consolas", Font.PLAIN, 14));

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("<html>Nhập <b>Google Gemini API Key</b> (tự động lưu vào <code>config.properties</code>):</html>"), BorderLayout.NORTH);
        panel.add(txtKey, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(mainFrame, panel, "Cài đặt Gemini API Key",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String newKey = txtKey.getText().trim();
            if (!newKey.isEmpty()) {
                saveApiKeyToConfig(newKey);
                refreshApiKeyBadge();
                System.out.println("🔑 Đã cập nhật Gemini API Key vào config.properties thành công.");
            }
        }
    }

    private static String readConfiguredApiKey() {
        File cfg = new File("config.properties");
        if (cfg.exists()) {
            try (FileInputStream fis = new FileInputStream(cfg)) {
                Properties prop = new Properties();
                prop.load(fis);
                String k = prop.getProperty("gemini.api.key", "").trim();
                if (!k.isEmpty() && !k.equals("YOUR_API_KEY_HERE")) {
                    return k;
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static void saveApiKeyToConfig(String key) {
        try {
            Properties prop = new Properties();
            File cfg = new File("config.properties");
            if (cfg.exists()) {
                try (FileInputStream fis = new FileInputStream(cfg)) {
                    prop.load(fis);
                }
            }
            prop.setProperty("gemini.api.key", key.trim());
            try (FileOutputStream fos = new FileOutputStream(cfg)) {
                prop.store(fos, "Google Gemini API Configuration");
            }
        } catch (Exception ignored) {
        }
    }

    private static void refreshApiKeyBadge() {
        String key = readConfiguredApiKey();
        if (!key.isEmpty()) {
            String masked = key.length() > 10
                    ? key.substring(0, 6) + "..." + key.substring(key.length() - 4)
                    : "Đã lưu";
            lblApiKeyStatus.setText("🔑 Gemini Key: " + masked);
            lblApiKeyStatus.setForeground(ACCENT_GREEN);
        } else {
            lblApiKeyStatus.setText("⚠️ Chưa cấu hình Gemini Key");
            lblApiKeyStatus.setForeground(ACCENT_GOLD);
        }
    }

    private static void updateProgressUI(int percent, String statusText) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(Math.max(0, Math.min(100, percent)));
            lblStepStatus.setText(statusText);
        });
    }

    // =========================================================================
    // LUỒNG XỬ LÝ CHÍNH (CHẠY BACKGROUND ĐỂ GIAO DIỆN LUÔN MƯỢT MÀ)
    // =========================================================================
    private static void startVideoPipelineThread() {
        if (isProcessing) {
            return;
        }

        final boolean isResumeMode = rbResumeMode.isSelected();
        final String rawVideoSource = txtVideoInput.getText().trim();
        final String extraFileSource = txtExtraFile.getText().trim();
        final String selectedPreset = (String) cmbVoicePreset.getSelectedItem();
        final boolean showViSub = chkShowViSub.isSelected();
        final boolean pauseForReview = chkPauseForReview.isSelected();
        final boolean keepOriginalAudio = chkKeepOriginalAudio.isSelected();

        if (rawVideoSource.isEmpty() || rawVideoSource.startsWith("Kéo thả")) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Vui lòng chọn file Video (.mp4) hoặc dán đường dẫn YouTube vào ô Nguồn Video!",
                    "Thiếu nguồn Video", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isResumeMode && (extraFileSource.isEmpty() || !new File(extraFileSource).exists())) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Ở chế độ Chỉ Lồng Tiếng, vui lòng chọn file 'review_translation.txt' có sẵn!",
                    "Thiếu file bản dịch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!isResumeMode && readConfiguredApiKey().isEmpty()) {
            openApiKeyDialog();
            if (readConfiguredApiKey().isEmpty()) {
                return;
            }
        }

        isProcessing = true;
        btnStart.setEnabled(false);
        btnStart.setText("⏳ ĐANG XỬ LÝ VIDEO...");
        btnOpenVideo.setEnabled(false);

        new Thread(() -> {
            long programStartTime = System.currentTimeMillis();
            try {
                runPipeline(isResumeMode, rawVideoSource, extraFileSource, selectedPreset,
                        showViSub, pauseForReview, keepOriginalAudio, programStartTime);
            } finally {
                isProcessing = false;
                SwingUtilities.invokeLater(() -> {
                    btnStart.setEnabled(true);
                    btnStart.setText("🚀 BẮT ĐẦU XỬ LÝ VIDEO");
                });
            }
        }, "Studio-Pipeline-Worker").start();
    }

    private static void runPipeline(boolean isResumeMode,
                                    String rawVideoSource,
                                    String extraFileSource,
                                    String selectedPreset,
                                    boolean showViSub,
                                    boolean pauseForReview,
                                    boolean keepOriginalAudio,
                                    long programStartTime) {
        try {
            System.out.println("\n==================================================================");
            System.out.println("🎬 BẮT ĐẦU DÂY CHUYỀN REVIEW PHIM AI STUDIO PRO");
            System.out.println("==================================================================");

            String relativeWorkspacePath;
            String inputVideoPath;
            String reviewFilePath;

            if (isResumeMode) {
                File txtFile = new File(extraFileSource);
                reviewFilePath = txtFile.getAbsolutePath();
                relativeWorkspacePath = txtFile.getParent() + File.separator;
                inputVideoPath = rawVideoSource;
                lastWorkspaceDir = txtFile.getParentFile();
                SwingUtilities.invokeLater(() -> btnOpenFolder.setEnabled(true));
                System.out.println("⏩ Chế độ Chỉ Lồng Tiếng - Sử dụng Workspace: " + relativeWorkspacePath);
            } else {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                relativeWorkspacePath = "Workspaces/Video_" + timestamp + "/";
                File workspaceDir = new File(relativeWorkspacePath);
                if (!workspaceDir.exists()) workspaceDir.mkdirs();
                lastWorkspaceDir = workspaceDir.getAbsoluteFile();
                SwingUtilities.invokeLater(() -> btnOpenFolder.setEnabled(true));

                if (rawVideoSource.startsWith("http://") || rawVideoSource.startsWith("https://")) {
                    updateProgressUI(5, "Bước 1/5: Đang tải video từ YouTube...");
                    inputVideoPath = relativeWorkspacePath + "YT_Source.mp4";
                    System.out.println("⏳ Đang kết nối và tải video từ YouTube: " + rawVideoSource);
                    ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--no-check-certificate", "-f",
                            "bestvideo[vcodec^=avc1]+bestaudio[ext=m4a]/mp4", "--merge-output-format", "mp4", "-o",
                            inputVideoPath, rawVideoSource);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("[download]") || line.contains("[Merger]")) {
                                System.out.println("   -> " + line);
                            }
                        }
                    }
                    p.waitFor();
                    System.out.println("✅ Tải video YouTube thành công!");
                } else {
                    File localVideo = new File(rawVideoSource);
                    if (!localVideo.exists()) {
                        updateProgressUI(0, "❌ Không tìm thấy file video đầu vào!");
                        System.out.println("❌ Lỗi: File video không tồn tại: " + rawVideoSource);
                        return;
                    }
                    inputVideoPath = localVideo.getAbsolutePath();
                }
                reviewFilePath = relativeWorkspacePath + "review_translation.txt";
            }

            String extractedAudio = relativeWorkspacePath + "audio_temp.wav";
            String outputMp4 = relativeWorkspacePath + "output_final.mp4";
            String srtFilePath = relativeWorkspacePath + "vi_sub.srt";
            String concatListPath = relativeWorkspacePath + "concat_list.txt";
            String finalAudioPath = relativeWorkspacePath + "final_vietnamese_track.wav";

            File audioChunksDir = new File(relativeWorkspacePath + "audio_chunks");
            if (!audioChunksDir.exists()) audioChunksDir.mkdirs();

            String glossary = "";
            if (!isResumeMode && !extraFileSource.isEmpty() && new File(extraFileSource).exists()) {
                try {
                    glossary = new String(Files.readAllBytes(Paths.get(extraFileSource)), StandardCharsets.UTF_8);
                    System.out.println("📘 Đã nạp từ điển tùy chọn: " + extraFileSource);
                } catch (Exception ignored) {
                }
            }

            List<String> audioSegments = new ArrayList<>();
            Map<Integer, String> vietnameseTranslatedMap = new HashMap<>();

            if (!isResumeMode) {
                updateProgressUI(12, "Bước 1/5: Đang tách âm thanh gốc từ video...");
                FFmpegManager.extractAudio(inputVideoPath, extractedAudio);

                System.out.println("\n[1.5/5] Chia nhỏ âm thanh chuẩn bị bóc băng...");
                File audioPartsDir = new File(relativeWorkspacePath + "audio_parts");
                if (!audioPartsDir.exists()) audioPartsDir.mkdirs();
                FFmpegManager.splitAudio(extractedAudio, relativeWorkspacePath + "audio_parts/part_%03d.wav", 300);

                updateProgressUI(25, "Bước 2/5: Đang bóc tách âm thanh sang văn bản bằng Google Gemini Audio...");
                System.out.println("\n[2/5] Bóc tách âm thanh sang văn bản bằng Google Gemini Audio...");
                File[] partFiles = audioPartsDir.listFiles((dir, name) -> name.startsWith("part_") && name.endsWith(".wav"));
                if (partFiles != null) {
                    java.util.Arrays.sort(partFiles);
                    double currentOffset = 0.0;
                    for (int pIdx = 0; pIdx < partFiles.length; pIdx++) {
                        File part = partFiles[pIdx];
                        audioSegments.addAll(GeminiTranslator.transcribeAudio(part.getAbsolutePath(), currentOffset));
                        currentOffset += FFmpegManager.getAudioDuration(part.getAbsolutePath());
                        int prog = 25 + (int) ((20.0 * (pIdx + 1)) / Math.max(1, partFiles.length));
                        updateProgressUI(prog, "Bước 2/5: Đã bóc băng phần " + (pIdx + 1) + "/" + partFiles.length);
                    }
                }

                updateProgressUI(48, "Bước 3/5: Đang dịch thuật khớp khẩu hình bằng Google Gemini...");
                System.out.println("\n[3/5] Dịch thuật khớp khẩu hình bằng Google Gemini...");
                int batchSize = 30;
                for (int i = 0; i < audioSegments.size(); i += batchSize) {
                    Map<Integer, String> currentChunk = new LinkedHashMap<>();
                    Map<Integer, Double> currentDurations = new HashMap<>();
                    int endIdx = Math.min(i + batchSize, audioSegments.size());
                    for (int j = i; j < endIdx; j++) {
                        String[] segParts = audioSegments.get(j).split("\\|");
                        double dur = Math.max(0.5, Double.parseDouble(segParts[1]) - Double.parseDouble(segParts[0]));
                        currentChunk.put(j, segParts[2]);
                        currentDurations.put(j, dur);
                    }

                    StringBuilder prevContext = new StringBuilder();
                    for (int k = Math.max(0, i - 5); k < i; k++) {
                        String orig = audioSegments.get(k).split("\\|")[2];
                        String trans = vietnameseTranslatedMap.getOrDefault(k, "");
                        if (!trans.isEmpty()) {
                            prevContext.append(orig).append(" -> ").append(trans).append("\n");
                        }
                    }

                    boolean success = false;
                    while (!success) {
                        try {
                            System.out.println("   -> 🌐 Gemini đang dịch cụm câu " + i + " -> " + (endIdx - 1) + " / " + audioSegments.size() + "...");
                            vietnameseTranslatedMap.putAll(GeminiTranslator.translateBatch(
                                    currentChunk, currentDurations, prevContext.toString(), glossary, GeminiTranslator.MODEL_PRO));
                            success = true;
                        } catch (Exception e) {
                            System.out.println("   -> ⚠️ Lỗi kết nối Gemini API. Đang thử lại sau 5 giây... (" + e.getMessage() + ")");
                            Thread.sleep(5000);
                        }
                    }
                    int prog = 48 + (int) ((15.0 * endIdx) / Math.max(1, audioSegments.size()));
                    updateProgressUI(prog, "Bước 3/5: Đã dịch " + endIdx + "/" + audioSegments.size() + " câu thoại");
                    Thread.sleep(1000);
                }

                try (FileWriter rw = new FileWriter(reviewFilePath, StandardCharsets.UTF_8)) {
                    for (int i = 0; i < audioSegments.size(); i++) {
                        String[] p = audioSegments.get(i).split("\\|");
                        rw.write(i + "|||" + p[0] + "|||" + p[1] + "|||" + p[2] + "|||" + vietnameseTranslatedMap.getOrDefault(i, "") + "\n");
                    }
                }

                if (pauseForReview) {
                    updateProgressUI(64, "⏸️ Đang chờ bạn duyệt bản dịch trên cửa sổ trình biên tập...");
                    if (!showBuiltInTranslationEditor(reviewFilePath, "Duyệt & Chỉnh Sửa Bản Dịch Trước Khi Lồng Tiếng")) {
                        updateProgressUI(0, "⏹️ Đã hủy quá trình xử lý theo yêu cầu người dùng.");
                        System.out.println("⏹️ Người dùng đã hủy quá trình xử lý ở bước duyệt bản dịch.");
                        return;
                    }
                }
            } else {
                List<String> lines = Files.readAllLines(Paths.get(reviewFilePath), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] p = line.split("\\|\\|\\|", -1);
                    if (p.length >= 5) {
                        audioSegments.add(p[1].trim() + "|" + p[2].trim() + "|" + p[3].trim());
                    }
                }
            }

            // Kiểm tra tính hợp lệ của bản dịch (không còn sót chữ Hán)
            boolean dataIsValid = false;
            while (!dataIsValid) {
                System.out.println("\n[3.6/5] Đang nạp và kiểm tra tính hợp lệ của bản dịch...");
                vietnameseTranslatedMap.clear();
                List<String> lines = Files.readAllLines(Paths.get(reviewFilePath), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] p = line.split("\\|\\|\\|", -1);
                    if (p.length >= 5) {
                        int id = Integer.parseInt(p[0].trim());
                        vietnameseTranslatedMap.put(id, p[4].trim());
                    }
                }

                boolean hasError = false;
                for (int i = 0; i < audioSegments.size(); i++) {
                    String text = vietnameseTranslatedMap.getOrDefault(i, "").trim();
                    if (text.matches(".*[\\u4e00-\\u9fa5].*")) {
                        System.out.println("   ⚠️ Câu " + i + " còn sót chữ Hán: " + text);
                        hasError = true;
                    }
                }

                if (hasError) {
                    if (!showBuiltInTranslationEditor(reviewFilePath, "⚠️ Phát hiện câu còn sót chữ Hán - Vui lòng chỉnh lại rồi bấm Lưu")) {
                        updateProgressUI(0, "⏹️ Đã dừng chương trình.");
                        return;
                    }
                } else {
                    System.out.println("✅ Bản dịch sạch sẽ, 100% tiếng Việt!");
                    dataIsValid = true;
                }
            }

            // Cấu hình Preset Giọng đọc Phòng thu
            double videoDuration = FFmpegManager.getAudioDuration(inputVideoPath);
            boolean useGeminiTTS = false;
            String geminiVoice = "Charon";
            String edgeVoice = "vi-VN-NamMinhNeural";
            String baseRate = "+5%";
            String basePitch = "-8Hz";
            String studioEqMode = "deep";

            if (selectedPreset != null) {
                if (selectedPreset.startsWith("1")) {
                    edgeVoice = "vi-VN-NamMinhNeural";
                    baseRate = "+5%";
                    basePitch = "-8Hz";
                    studioEqMode = "deep";
                } else if (selectedPreset.startsWith("2")) {
                    edgeVoice = "vi-VN-HoaiMyNeural";
                    baseRate = "+8%";
                    basePitch = "+6Hz";
                    studioEqMode = "bright";
                } else if (selectedPreset.startsWith("3")) {
                    edgeVoice = "vi-VN-NamMinhNeural";
                    baseRate = "+12%";
                    basePitch = "+2Hz";
                    studioEqMode = "bright";
                } else if (selectedPreset.startsWith("4")) {
                    useGeminiTTS = true;
                    geminiVoice = "Charon";
                    edgeVoice = "vi-VN-NamMinhNeural";
                    baseRate = "+5%";
                    basePitch = "-8Hz";
                    studioEqMode = "deep";
                } else if (selectedPreset.startsWith("5")) {
                    useGeminiTTS = true;
                    geminiVoice = "Kore";
                    edgeVoice = "vi-VN-HoaiMyNeural";
                    baseRate = "+8%";
                    basePitch = "+6Hz";
                    studioEqMode = "bright";
                }
            }

            // 4. LỒNG TIẾNG PHÒNG THU
            updateProgressUI(66, "Bước 4/5: Đang tổng hợp giọng đọc Studio AI...");
            System.out.println("\n[4/5] 🚀 Đang tạo giọng đọc theo Preset: " + selectedPreset + "...");

            for (int i = 0; i < audioSegments.size(); i++) {
                String text = vietnameseTranslatedMap.get(i);
                String chunkRawPath = relativeWorkspacePath + "audio_chunks/chunk_" + i + ".mp3";

                if (text == null || !text.matches(".*[\\p{L}\\p{N}].*")) {
                    continue;
                }
                try {
                    if (useGeminiTTS) {
                        try {
                            GeminiTranslator.generateGeminiTTS(text, chunkRawPath, geminiVoice);
                        } catch (Exception geminiEx) {
                            System.out.println("   -> ⚡ Gemini TTS chạm giới hạn tốc độ, tự động dùng Preset Studio tương đương cho câu " + i + "...");
                            EdgeTTSManager.generateAudio(text, chunkRawPath, edgeVoice, baseRate, basePitch);
                        }
                    } else {
                        try {
                            EdgeTTSManager.generateAudio(text, chunkRawPath, edgeVoice, baseRate, basePitch);
                        } catch (Exception edgeEx) {
                            System.out.println("   -> ⚡ Edge-TTS bận, tự động dùng Gemini TTS dự phòng cho câu " + i + "...");
                            GeminiTranslator.generateGeminiTTS(text, chunkRawPath, geminiVoice);
                        }
                    }
                    System.out.println("   -> 🎤 [Hoàn thành] Câu " + (i + 1) + " / " + audioSegments.size());
                } catch (Exception e) {
                    System.out.println("   -> ❌ Lỗi tạo giọng câu " + i + ": " + e.getMessage());
                }

                int prog = 66 + (int) ((20.0 * (i + 1)) / Math.max(1, audioSegments.size()));
                updateProgressUI(prog, "Bước 4/5: Đã lồng tiếng " + (i + 1) + "/" + audioSegments.size() + " câu");
            }

            // 5. KHỚP KHẨU HÌNH & RENDER VIDEO
            updateProgressUI(88, "Bước 5/5: Đang khớp khẩu hình từng câu & Render Video Studio...");
            System.out.println("\n[5/5] Cắt khoảng lặng thừa, khớp thời lượng từng câu & Hòa âm Phòng thu...");

            try (FileWriter srt = new FileWriter(srtFilePath, StandardCharsets.UTF_8);
                 FileWriter con = new FileWriter(concatListPath, StandardCharsets.UTF_8)) {

                long timeMs = 0;
                int srtIdx = 1;

                for (int i = 0; i < audioSegments.size(); i++) {
                    String[] p = audioSegments.get(i).split("\\|");
                    File rawAudio = new File(relativeWorkspacePath + "audio_chunks/chunk_" + i + ".mp3");

                    if (rawAudio.exists() && rawAudio.length() > 100) {
                        double origStartSec = Double.parseDouble(p[0]);
                        double origEndSec = Double.parseDouble(p[1]);
                        long startMs = (long) (origStartSec * 1000);

                        if (startMs > timeMs + 15) {
                            String silenceWavPath = relativeWorkspacePath + "audio_chunks/s_" + i + ".wav";
                            FFmpegManager.createSilenceWav((startMs - timeMs) / 1000.0, silenceWavPath);

                            String absoluteSilencePath = new File(silenceWavPath).getAbsolutePath().replace("\\", "/");
                            con.write("file '" + absoluteSilencePath + "'\n");
                            timeMs = startMs;
                        }

                        double currentCursorSec = timeMs / 1000.0;
                        double nextOrigStartSec = (i + 1 < audioSegments.size())
                                ? Double.parseDouble(audioSegments.get(i + 1).split("\\|")[0])
                                : (videoDuration > 0 ? videoDuration : origEndSec + 2.0);

                        double maxSlotDuration = Math.max(0.35, nextOrigStartSec - currentCursorSec - 0.02);
                        double origDuration = Math.max(0.35, origEndSec - origStartSec);

                        String fittedWavPath = relativeWorkspacePath + "audio_chunks/chunk_" + i + ".wav";
                        double actualDurSec = FFmpegManager.trimAndFitAudioToWav(
                                rawAudio.getAbsolutePath(),
                                fittedWavPath,
                                origDuration,
                                maxSlotDuration
                        );

                        String absoluteChunkWavPath = new File(fittedWavPath).getAbsolutePath().replace("\\", "/");
                        con.write("file '" + absoluteChunkWavPath + "'\n");

                        long durMs = (long) (actualDurSec * 1000);
                        srt.write(srtIdx++ + "\n"
                                + formatSrtTime(timeMs / 1000.0) + " --> " + formatSrtTime((timeMs + durMs) / 1000.0) + "\n"
                                + vietnameseTranslatedMap.get(i) + "\n\n");
                        timeMs += durMs;
                    }
                }

                long totalVideoMs = (long) (videoDuration * 1000);
                if (totalVideoMs > timeMs + 50) {
                    String tailSilencePath = relativeWorkspacePath + "audio_chunks/s_tail.wav";
                    FFmpegManager.createSilenceWav((totalVideoMs - timeMs) / 1000.0, tailSilencePath);
                    String absoluteTailPath = new File(tailSilencePath).getAbsolutePath().replace("\\", "/");
                    con.write("file '" + absoluteTailPath + "'\n");
                }
            }

            FFmpegManager.concatAudioFiles(concatListPath, finalAudioPath);
            updateProgressUI(93, "Bước 5/5: Đang xuất file MP4 hoàn chỉnh (Studio EQ + Phụ đề)...");

            File finalVideoFile = new File(outputMp4).getAbsoluteFile();
            FFmpegManager.mergeFinalVideoWithSub(
                    new File(inputVideoPath).getAbsolutePath(),
                    new File(finalAudioPath).getAbsolutePath(),
                    new File(srtFilePath).getAbsolutePath(),
                    finalVideoFile.getAbsolutePath(),
                    keepOriginalAudio, showViSub, studioEqMode
            );

            lastOutputVideoFile = finalVideoFile;
            long totalSeconds = (System.currentTimeMillis() - programStartTime) / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            System.out.println("\n🎉 HOÀN THÀNH XUẤT SẮC! Sản phẩm được lưu tại:\n👉 " + finalVideoFile.getAbsolutePath());
            System.out.println("⏱️ TỔNG THỜI GIAN XỬ LÝ: " + minutes + " phút " + seconds + " giây.");

            updateProgressUI(100, "✅ Hoàn tất! Video đã sẵn sàng (" + minutes + "p " + seconds + "s) - Bấm [▶️ Xem Video Kết Quả]");
            SwingUtilities.invokeLater(() -> btnOpenVideo.setEnabled(true));

        } catch (Exception e) {
            updateProgressUI(0, "❌ Có lỗi xảy ra: " + e.getMessage());
            System.out.println("❌ LỖI HỆ THỐNG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TRÌNH DUYỆT & CHỈNH SỬA BẢN DỊCH TRỰC TIẾP TRÊN GIAO DIỆN (KHÔNG CẦN NOTEPAD)
    // =========================================================================
    private static boolean showBuiltInTranslationEditor(String reviewFilePath, String dialogTitle) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean approved = new AtomicBoolean(false);

        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog(mainFrame, dialogTitle, true);
            dialog.setSize(860, 580);
            dialog.setLocationRelativeTo(mainFrame);
            dialog.setLayout(new BorderLayout(10, 10));

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBackground(BG_DARK);
            content.setBorder(new EmptyBorder(14, 14, 14, 14));

            JLabel lblHint = new JLabel("<html><b>Hướng dẫn:</b> Bạn có thể chỉnh sửa trực tiếp lời dịch tiếng Việt (cột cuối cùng sau dấu <code>|||</code>) rồi bấm <b>Lưu & Tiếp Tục</b>.</html>");
            lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblHint.setForeground(ACCENT_CYAN);
            content.add(lblHint, BorderLayout.NORTH);

            JTextArea editor = new JTextArea();
            editor.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            editor.setBackground(new Color(15, 23, 42));
            editor.setForeground(TEXT_PRIMARY);
            editor.setCaretColor(ACCENT_GOLD);
            editor.setMargin(new Insets(10, 10, 10, 10));

            try {
                String raw = new String(Files.readAllBytes(Paths.get(reviewFilePath)), StandardCharsets.UTF_8);
                editor.setText(raw);
                editor.setCaretPosition(0);
            } catch (Exception ignored) {
            }

            content.add(new JScrollPane(editor), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottom.setOpaque(false);

            JButton btnCancel = createStyledButton("❌ Hủy Bỏ", new Color(185, 28, 28), Color.WHITE);
            btnCancel.addActionListener(e -> {
                approved.set(false);
                dialog.dispose();
                latch.countDown();
            });

            JButton btnSave = createStyledButton("✅ Lưu Bản Dịch & Tiếp Tục Lồng Tiếng", new Color(22, 163, 74), Color.WHITE);
            btnSave.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btnSave.addActionListener(e -> {
                try {
                    Files.write(Paths.get(reviewFilePath), editor.getText().getBytes(StandardCharsets.UTF_8));
                    approved.set(true);
                } catch (Exception ex) {
                    System.out.println("❌ Không thể lưu file bản dịch: " + ex.getMessage());
                }
                dialog.dispose();
                latch.countDown();
            });

            bottom.add(btnCancel);
            bottom.add(btnSave);
            content.add(bottom, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    latch.countDown();
                }
            });
            dialog.setVisible(true);
        });

        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return approved.get();
    }

    // =========================================================================
    // CÁC HÀM TIỆN ÍCH GIAO DIỆN & VẼ LOGO VECTOR
    // =========================================================================
    private static void redirectSystemStreamsToGUI() {
        OutputStream guiStream = new OutputStream() {
            private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                buffer.write(b);
                if (b == '\n') {
                    flushBuffer();
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                buffer.write(b, off, len);
                for (int i = off; i < off + len; i++) {
                    if (b[i] == '\n') {
                        flushBuffer();
                        break;
                    }
                }
            }

            private void flushBuffer() {
                final String text = buffer.toString(StandardCharsets.UTF_8);
                buffer.reset();
                SwingUtilities.invokeLater(() -> {
                    if (txtLog != null) {
                        txtLog.append(text);
                        txtLog.setCaretPosition(txtLog.getDocument().getLength());
                    }
                });
            }
        };

        PrintStream ps = new PrintStream(guiStream, true, StandardCharsets.UTF_8);
        System.setOut(ps);
        System.setErr(ps);
    }

    private static void enableFileDragAndDrop(JTextField targetField) {
        new DropTarget(targetField, new java.awt.dnd.DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        targetField.setText(droppedFiles.get(0).getAbsolutePath());
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static List<Image> loadOrCreateAppIcons() {
        List<Image> icons = new ArrayList<>();
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        File logoFile = new File("logo.png");
        BufferedImage base = null;
        if (logoFile.exists()) {
            try {
                base = ImageIO.read(logoFile);
            } catch (Exception ignored) {
            }
        }
        for (int sz : sizes) {
            if (base != null) {
                icons.add(base.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
            } else {
                icons.add(createVectorLogoImage(sz));
            }
        }
        return icons;
    }

    private static BufferedImage createVectorLogoImage(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float s = size;
        float pad = s * 0.05f;
        RoundRectangle2D.Float rect = new RoundRectangle2D.Float(pad, pad, s - 2 * pad, s - 2 * pad, s * 0.45f, s * 0.45f);
        g.setPaint(new GradientPaint(0, 0, new Color(22, 27, 64), s, s, new Color(88, 28, 135)));
        g.fill(rect);

        g.setColor(new Color(56, 189, 248, 190));
        g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.04f)));
        g.draw(rect);

        int[] px = {(int) (s * 0.34f), (int) (s * 0.34f), (int) (s * 0.74f)};
        int[] py = {(int) (s * 0.26f), (int) (s * 0.74f), (int) (s * 0.50f)};
        g.setPaint(new GradientPaint(s * 0.3f, s * 0.25f, ACCENT_CYAN, s * 0.75f, s * 0.75f, ACCENT_GOLD));
        g.fillPolygon(px, py, 3);

        g.dispose();
        return img;
    }

    private static JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(TEXT_PRIMARY);
        return lbl;
    }

    private static JTextField createStyledTextField(String initialText) {
        JTextField tf = new JTextField(initialText);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBackground(INPUT_BG);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ACCENT_CYAN);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return tf;
    }

    private static JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        return btn;
    }

    private static void styleRadioButton(JRadioButton rb) {
        rb.setOpaque(false);
        rb.setFont(new Font("Segoe UI", Font.BOLD, 13));
        rb.setForeground(TEXT_PRIMARY);
        rb.setFocusPainted(false);
    }

    private static void styleCheckBox(JCheckBox cb) {
        cb.setOpaque(false);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cb.setForeground(TEXT_PRIMARY);
        cb.setFocusPainted(false);
    }

    public static String formatSrtTime(double s) {
        return String.format(Locale.US, "%02d:%02d:%02d,%03d",
                (int) (s / 3600), (int) ((s % 3600) / 60), (int) (s % 60), (int) ((s % 1) * 1000));
    }
}
