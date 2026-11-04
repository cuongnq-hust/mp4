using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

namespace ReviewPhimAI
{
    class Launcher
    {
        private const string GITHUB_OWNER_REPO = "cuongnq-hust/mp4";
        private const string GIT_BRANCH = "main";
        private const string GITHUB_API_COMMIT_URL = "https://api.github.com/repos/" + GITHUB_OWNER_REPO + "/commits/" + GIT_BRANCH;
        private const string GITHUB_ZIP_URL = "https://github.com/" + GITHUB_OWNER_REPO + "/archive/refs/heads/" + GIT_BRANCH + ".zip";

        [STAThread]
        static int Main(string[] args)
        {
            try
            {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | (SecurityProtocolType)768 | SecurityProtocolType.Tls;
            }
            catch { }

            string appDir = AppDomain.CurrentDomain.BaseDirectory;
            Directory.SetCurrentDirectory(appDir);

            CleanupTempFiles(appDir);

            // Chế độ kiểm tra nhanh không mở giao diện (--check)
            if (args != null && args.Length > 0 && (args[0] == "--check" || args[0] == "--build-only"))
            {
                bool updated = CheckAndApplyCloudUpdates(appDir, delegate(string s) { });
                EnsureLauncherExeUpToDate(appDir, updated);
                EnsureSystemEnvironment(appDir, delegate(string s) { });
                string javaBin = FindJavaBinDirectory();
                string javacExe = (javaBin != null && File.Exists(Path.Combine(javaBin, "javac.exe")))
                    ? Path.Combine(javaBin, "javac.exe") : "javac";
                string jarExe = (javaBin != null && File.Exists(Path.Combine(javaBin, "jar.exe")))
                    ? Path.Combine(javaBin, "jar.exe") : "jar";
                EnsureLatestBuild(appDir, javacExe, jarExe, updated, delegate(string s) { });
                return 0;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            using (SplashForm splash = new SplashForm(appDir, args))
            {
                Application.Run(splash);
            }

            return 0;
        }

        private class SplashForm : Form
        {
            private readonly string appDir;
            private readonly string[] launchArgs;
            private readonly Label lblStatus;
            private readonly ProgressBar progressBar;

            public SplashForm(string appDir, string[] args)
            {
                this.appDir = appDir;
                this.launchArgs = args;

                this.Text = "Review Phim AI Studio Pro";
                this.FormBorderStyle = FormBorderStyle.None;
                this.StartPosition = FormStartPosition.CenterScreen;
                this.Size = new Size(480, 230);
                this.BackColor = Color.FromArgb(15, 23, 42);

                string icoPath = Path.Combine(appDir, "app.ico");
                if (File.Exists(icoPath))
                {
                    try { this.Icon = new Icon(icoPath); } catch { }
                }

                Panel borderPanel = new Panel();
                borderPanel.Dock = DockStyle.Fill;
                borderPanel.Padding = new Padding(2);
                borderPanel.BackColor = Color.FromArgb(56, 189, 248);
                this.Controls.Add(borderPanel);

                Panel innerPanel = new Panel();
                innerPanel.Dock = DockStyle.Fill;
                innerPanel.BackColor = Color.FromArgb(15, 23, 42);
                borderPanel.Controls.Add(innerPanel);

                PictureBox picLogo = new PictureBox();
                picLogo.Size = new Size(68, 68);
                picLogo.Location = new Point(28, 32);
                picLogo.SizeMode = PictureBoxSizeMode.Zoom;
                string logoPath = Path.Combine(appDir, "logo.png");
                if (File.Exists(logoPath))
                {
                    try { picLogo.Image = Image.FromFile(logoPath); } catch { }
                }
                innerPanel.Controls.Add(picLogo);

                Label lblTitle = new Label();
                lblTitle.Text = "REVIEW PHIM AI STUDIO PRO";
                lblTitle.Font = new Font("Segoe UI", 15F, FontStyle.Bold);
                lblTitle.ForeColor = Color.FromArgb(56, 189, 248);
                lblTitle.AutoSize = true;
                lblTitle.Location = new Point(112, 36);
                innerPanel.Controls.Add(lblTitle);

                Label lblSub = new Label();
                lblSub.Text = "Hệ thống Tự động Dịch thuật Gemini & Lồng tiếng Phòng thu";
                lblSub.Font = new Font("Segoe UI", 9.5F, FontStyle.Regular);
                lblSub.ForeColor = Color.FromArgb(148, 163, 184);
                lblSub.AutoSize = true;
                lblSub.Location = new Point(114, 68);
                innerPanel.Controls.Add(lblSub);

                lblStatus = new Label();
                lblStatus.Text = "🔍 Đang kiểm tra bản cập nhật mới trên GitHub Cloud...";
                lblStatus.Font = new Font("Segoe UI", 9.5F, FontStyle.Regular);
                lblStatus.ForeColor = Color.FromArgb(248, 250, 252);
                lblStatus.AutoSize = false;
                lblStatus.Size = new Size(420, 24);
                lblStatus.Location = new Point(28, 132);
                innerPanel.Controls.Add(lblStatus);

                progressBar = new ProgressBar();
                progressBar.Style = ProgressBarStyle.Marquee;
                progressBar.MarqueeAnimationSpeed = 25;
                progressBar.Size = new Size(420, 14);
                progressBar.Location = new Point(28, 164);
                innerPanel.Controls.Add(progressBar);

                this.Shown += OnSplashShown;
            }

            private void UpdateStatus(string text)
            {
                if (this.IsDisposed) return;
                if (this.InvokeRequired)
                {
                    try { this.BeginInvoke(new Action<string>(UpdateStatus), text); } catch { }
                    return;
                }
                lblStatus.Text = text;
            }

            private void OnSplashShown(object sender, EventArgs e)
            {
                Thread worker = new Thread(delegate()
                {
                    try
                    {
                        bool updated = CheckAndApplyCloudUpdates(appDir, UpdateStatus);
                        EnsureLauncherExeUpToDate(appDir, updated);

                        EnsureSystemEnvironment(appDir, UpdateStatus);

                        string javaBin = FindJavaBinDirectory();
                        string javawExe = (javaBin != null && File.Exists(Path.Combine(javaBin, "javaw.exe")))
                            ? Path.Combine(javaBin, "javaw.exe") : "javaw";
                        string javacExe = (javaBin != null && File.Exists(Path.Combine(javaBin, "javac.exe")))
                            ? Path.Combine(javaBin, "javac.exe") : "javac";
                        string jarExe = (javaBin != null && File.Exists(Path.Combine(javaBin, "jar.exe")))
                            ? Path.Combine(javaBin, "jar.exe") : "jar";

                        EnsureLatestBuild(appDir, javacExe, jarExe, updated, UpdateStatus);

                        UpdateStatus("🚀 Đang mở giao diện Review Phim AI Studio Pro...");
                        Thread.Sleep(350);

                        LaunchJavaWindowless(appDir, javawExe, launchArgs);
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show("Lỗi khởi động ứng dụng: " + ex.Message, "Review Phim AI Studio",
                            MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }
                    finally
                    {
                        try
                        {
                            this.BeginInvoke(new Action(delegate() { this.Close(); }));
                        }
                        catch { }
                    }
                });
                worker.IsBackground = true;
                worker.Start();
            }
        }

        private static void CleanupTempFiles(string appDir)
        {
            string[] tempFiles = new string[] { "ReviewPhimAI.exe.old", "update_temp.zip" };
            foreach (string tf in tempFiles)
            {
                string full = Path.Combine(appDir, tf);
                if (File.Exists(full))
                {
                    try { File.Delete(full); } catch { }
                }
            }
        }

        private static bool CheckAndApplyCloudUpdates(string appDir, Action<string> onStatus)
        {
            try
            {
                onStatus("🔍 Đang kiểm tra phiên bản mới trên GitHub Cloud...");
                string localSha = GetLocalVersionSha(appDir);

                string remoteSha, commitMessage;
                if (!FetchLatestGitHubCommit(out remoteSha, out commitMessage))
                {
                    onStatus("ℹ️ Chế độ Offline - Sử dụng phiên bản hiện tại.");
                    return false;
                }

                string shortRemote = remoteSha.Length >= 7 ? remoteSha.Substring(0, 7) : remoteSha;

                if (!string.IsNullOrEmpty(localSha) && string.Equals(localSha, remoteSha, StringComparison.OrdinalIgnoreCase))
                {
                    onStatus("✅ Đã ở phiên bản mới nhất (" + shortRemote + ").");
                    return false;
                }

                if (Directory.Exists(Path.Combine(appDir, ".git")))
                {
                    string statusOut, statusErr;
                    if (RunHiddenCommand("git", "status --porcelain", appDir, 3000, out statusOut, out statusErr) == 0 &&
                        !string.IsNullOrWhiteSpace(statusOut))
                    {
                        onStatus("💻 Phát hiện code mới đang chỉnh sửa trên máy (Dev Mode) - Giữ nguyên bản Local.");
                        return false;
                    }

                    string unusedOut, unusedErr;
                    int ancCode = RunHiddenCommand("git", "merge-base --is-ancestor " + remoteSha + " HEAD", appDir, 3000, out unusedOut, out unusedErr);
                    if (ancCode == 0)
                    {
                        SaveLocalVersionSha(appDir, remoteSha);
                        onStatus("✅ Phiên bản hiện tại đã đồng bộ (" + shortRemote + ").");
                        return false;
                    }
                }

                onStatus("⬇️ Phát hiện bản mới (" + shortRemote + ")! Đang tải cập nhật tự động...");
                if (DownloadAndExtractGitHubZip(appDir))
                {
                    SaveLocalVersionSha(appDir, remoteSha);
                    if (Directory.Exists(Path.Combine(appDir, ".git")))
                    {
                        string uOut, uErr;
                        RunHiddenCommand("git", "pull --rebase --autostash origin " + GIT_BRANCH, appDir, 10000, out uOut, out uErr);
                    }
                    onStatus("✅ Đã cập nhật lên phiên bản " + shortRemote + " thành công!");
                    return true;
                }
                return false;
            }
            catch
            {
                return false;
            }
        }

        private static string GetLocalVersionSha(string appDir)
        {
            string versionFile = Path.Combine(appDir, ".version");
            if (File.Exists(versionFile))
            {
                try
                {
                    string v = File.ReadAllText(versionFile, Encoding.UTF8).Trim();
                    if (!string.IsNullOrEmpty(v)) return v;
                }
                catch { }
            }

            if (Directory.Exists(Path.Combine(appDir, ".git")))
            {
                string gitSha, gitErr;
                if (RunHiddenCommand("git", "rev-parse HEAD", appDir, 3000, out gitSha, out gitErr) == 0)
                {
                    gitSha = (gitSha ?? "").Trim();
                    if (!string.IsNullOrEmpty(gitSha))
                    {
                        SaveLocalVersionSha(appDir, gitSha);
                        return gitSha;
                    }
                }
            }

            return "";
        }

        private static void SaveLocalVersionSha(string appDir, string sha)
        {
            try
            {
                File.WriteAllText(Path.Combine(appDir, ".version"), sha.Trim(), Encoding.UTF8);
            }
            catch { }
        }

        private static bool FetchLatestGitHubCommit(out string sha, out string message)
        {
            sha = "";
            message = "";
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(GITHUB_API_COMMIT_URL);
                req.Method = "GET";
                req.UserAgent = "ReviewPhimAI-AutoUpdater/3.0";
                req.Accept = "application/vnd.github.v3+json";
                req.Timeout = 7000;
                req.ReadWriteTimeout = 7000;

                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                using (StreamReader reader = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                {
                    string json = reader.ReadToEnd();
                    Match shaMatch = Regex.Match(json, "\"sha\"\\s*:\\s*\"([0-9a-fA-F]{40})\"");
                    if (!shaMatch.Success) return false;
                    sha = shaMatch.Groups[1].Value;

                    Match msgMatch = Regex.Match(json, "\"commit\"\\s*:\\s*\\{.*?\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOptions.Singleline);
                    if (msgMatch.Success)
                    {
                        message = msgMatch.Groups[1].Value.Replace("\\n", " ").Replace("\\r", "").Trim();
                    }
                    return true;
                }
            }
            catch
            {
                return false;
            }
        }

        private static bool DownloadAndExtractGitHubZip(string appDir)
        {
            string tempZip = Path.Combine(appDir, "update_temp.zip");
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(GITHUB_ZIP_URL);
                req.Method = "GET";
                req.UserAgent = "ReviewPhimAI-AutoUpdater/3.0";
                req.Timeout = 20000;
                req.ReadWriteTimeout = 20000;

                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                using (Stream netStream = resp.GetResponseStream())
                using (FileStream fs = new FileStream(tempZip, FileMode.Create, FileAccess.Write))
                {
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = netStream.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        fs.Write(buffer, 0, read);
                    }
                }

                using (ZipArchive archive = ZipFile.OpenRead(tempZip))
                {
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        string fullName = entry.FullName.Replace('\\', '/');
                        int slashIdx = fullName.IndexOf('/');
                        if (slashIdx < 0 || slashIdx == fullName.Length - 1) continue;

                        string relPath = fullName.Substring(slashIdx + 1);
                        if (string.IsNullOrEmpty(relPath)) continue;

                        if (relPath.Equals("config.properties", StringComparison.OrdinalIgnoreCase) ||
                            relPath.Equals(".version", StringComparison.OrdinalIgnoreCase) ||
                            relPath.Equals(".env_ready", StringComparison.OrdinalIgnoreCase) ||
                            relPath.Equals("ReviewPhimAI.exe", StringComparison.OrdinalIgnoreCase) ||
                            relPath.StartsWith("Workspaces/", StringComparison.OrdinalIgnoreCase) ||
                            relPath.StartsWith(".git/", StringComparison.OrdinalIgnoreCase))
                        {
                            continue;
                        }

                        string destPath = Path.Combine(appDir, relPath.Replace('/', Path.DirectorySeparatorChar));
                        if (entry.FullName.EndsWith("/"))
                        {
                            Directory.CreateDirectory(destPath);
                            continue;
                        }

                        string parentDir = Path.GetDirectoryName(destPath);
                        if (!string.IsNullOrEmpty(parentDir))
                        {
                            Directory.CreateDirectory(parentDir);
                        }

                        entry.ExtractToFile(destPath, true);
                    }
                }

                try { File.Delete(tempZip); } catch { }
                return true;
            }
            catch
            {
                try { if (File.Exists(tempZip)) File.Delete(tempZip); } catch { }
                return false;
            }
        }

        private static void EnsureLauncherExeUpToDate(string appDir, bool forceCheck)
        {
            try
            {
                string csFile = Path.Combine(appDir, "Launcher.cs");
                string exeFile = Path.Combine(appDir, "ReviewPhimAI.exe");
                string icoFile = Path.Combine(appDir, "app.ico");
                string cscExe = @"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe";

                if (!File.Exists(csFile) || !File.Exists(cscExe) || !File.Exists(exeFile)) return;
                if (!forceCheck && File.GetLastWriteTimeUtc(csFile) <= File.GetLastWriteTimeUtc(exeFile)) return;

                string newExe = Path.Combine(appDir, "ReviewPhimAI.exe.new");
                string oldExe = Path.Combine(appDir, "ReviewPhimAI.exe.old");
                string iconArg = File.Exists(icoFile) ? " /win32icon:\"" + icoFile + "\"" : "";
                string cscArgs = "/nologo /optimize+ /target:winexe" + iconArg +
                                 " /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll" +
                                 " /out:\"" + newExe + "\" \"" + csFile + "\"";
                string outStr, errStr;
                int code = RunHiddenCommand(cscExe, cscArgs, appDir, 15000, out outStr, out errStr);
                if (code == 0 && File.Exists(newExe))
                {
                    if (File.Exists(oldExe)) { try { File.Delete(oldExe); } catch { } }
                    File.Move(exeFile, oldExe);
                    File.Move(newExe, exeFile);
                }
            }
            catch { }
        }

        // =====================================================================
        // TỰ ĐỘNG KIỂM TRA & CÀI ĐẶT MÔI TRƯỜNG HỆ THỐNG (JAVA + FFMPEG + PYTHON + AI LIBS)
        // =====================================================================
        private static void EnsureSystemEnvironment(string appDir, Action<string> onStatus)
        {
            try
            {
                RefreshProcessPath(appDir);

                string envReadyFile = Path.Combine(appDir, ".env_ready");
                string javaBin = FindJavaBinDirectory();
                string ffmpegDir = FindFfmpegDirectory(appDir);
                string pythonExe = FindRealPythonExe();

                // Nếu máy đã có đủ Java + FFmpeg + Python và đã đánh dấu .env_ready -> Bỏ qua ngay (0ms)
                if (File.Exists(envReadyFile) && !string.IsNullOrEmpty(javaBin) && !string.IsNullOrEmpty(ffmpegDir) && !string.IsNullOrEmpty(pythonExe))
                {
                    return;
                }

                string uOut, uErr;

                // 1. KIỂM TRA & TỰ ĐỘNG CÀI ĐẶT JAVA JDK 21
                if (string.IsNullOrEmpty(javaBin) && RunHiddenCommand("javaw", "-version", appDir, 4000, out uOut, out uErr) != 0)
                {
                    onStatus("📦 Máy chưa có Java - Đang tự động tải & cài đặt Java 21 (Winget)...");
                    int jCode = RunHiddenCommand("winget", "install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-package-agreements --accept-source-agreements", appDir, 360000, out uOut, out uErr);
                    if (jCode != 0)
                    {
                        RunHiddenCommand("winget", "install --id Microsoft.OpenJDK.21 -e --silent --accept-package-agreements --accept-source-agreements", appDir, 360000, out uOut, out uErr);
                    }
                    RefreshProcessPath(appDir);
                    javaBin = FindJavaBinDirectory();
                }

                // 2. KIỂM TRA & TỰ ĐỘNG CÀI ĐẶT FFMPEG
                if (string.IsNullOrEmpty(ffmpegDir) && RunHiddenCommand("ffmpeg", "-version", appDir, 4000, out uOut, out uErr) != 0)
                {
                    onStatus("🎬 Máy chưa có FFmpeg - Đang tự động tải & cài đặt FFmpeg...");
                    RunHiddenCommand("winget", "install --id Gyan.FFmpeg -e --silent --accept-package-agreements --accept-source-agreements", appDir, 360000, out uOut, out uErr);
                    RefreshProcessPath(appDir);
                    ffmpegDir = FindFfmpegDirectory(appDir);
                }

                // 3. KIỂM TRA & TỰ ĐỘNG CÀI ĐẶT PYTHON 3
                if (string.IsNullOrEmpty(pythonExe))
                {
                    onStatus("🐍 Máy chưa có Python 3 - Đang tự động tải & cài đặt Python 3.11...");
                    RunHiddenCommand("winget", "install --id Python.Python.3.11 -e --silent --accept-package-agreements --accept-source-agreements", appDir, 360000, out uOut, out uErr);
                    RefreshProcessPath(appDir);
                    pythonExe = FindRealPythonExe();
                }

                // 4. KIỂM TRA & TỰ ĐỘNG CÀI ĐẶT BỘ THƯ VIỆN AI (faster-whisper, edge-tts, yt-dlp)
                if (!string.IsNullOrEmpty(pythonExe))
                {
                    bool aiLibsReady = ArePythonAiLibrariesInstalled(pythonExe, appDir);
                    if (!aiLibsReady)
                    {
                        onStatus("🧠 Đang tự động cài đặt thư viện AI (faster-whisper, edge-tts, yt-dlp)...");
                        RunHiddenCommand(pythonExe, "-m pip install --upgrade pip --quiet", appDir, 120000, out uOut, out uErr);
                        RunHiddenCommand(pythonExe, "-m pip install faster-whisper edge-tts yt-dlp --disable-pip-version-check", appDir, 600000, out uOut, out uErr);
                        RefreshProcessPath(appDir);
                        aiLibsReady = ArePythonAiLibrariesInstalled(pythonExe, appDir);
                    }

                    if (aiLibsReady && (!string.IsNullOrEmpty(javaBin) || RunHiddenCommand("javaw", "-version", appDir, 4000, out uOut, out uErr) == 0))
                    {
                        try { File.WriteAllText(envReadyFile, "READY", Encoding.UTF8); } catch { }
                    }
                }
            }
            catch { }
        }

        private static bool ArePythonAiLibrariesInstalled(string pythonExe, string appDir)
        {
            try
            {
                if (File.Exists(pythonExe))
                {
                    string pyDir = Path.GetDirectoryName(pythonExe);
                    if (!string.IsNullOrEmpty(pyDir))
                    {
                        string sitePkgs = Path.Combine(pyDir, "Lib", "site-packages");
                        string scriptsDir = Path.Combine(pyDir, "Scripts");
                        if (Directory.Exists(Path.Combine(sitePkgs, "faster_whisper")) &&
                            File.Exists(Path.Combine(scriptsDir, "edge-tts.exe")))
                        {
                            return true;
                        }
                    }
                }
            }
            catch { }

            string uOut, uErr;
            return RunHiddenCommand(pythonExe, "-c \"import faster_whisper, edge_tts\"", appDir, 15000, out uOut, out uErr) == 0;
        }

        private static void RefreshProcessPath(string appDir)
        {
            try
            {
                string currentPath = Environment.GetEnvironmentVariable("PATH", EnvironmentVariableTarget.Process) ?? "";
                string machinePath = "";
                string userPath = "";
                try { machinePath = Environment.GetEnvironmentVariable("PATH", EnvironmentVariableTarget.Machine) ?? ""; } catch { }
                try { userPath = Environment.GetEnvironmentVariable("PATH", EnvironmentVariableTarget.User) ?? ""; } catch { }

                StringBuilder extra = new StringBuilder();

                try
                {
                    string javaBin = FindJavaBinDirectory();
                    if (!string.IsNullOrEmpty(javaBin)) extra.Append(javaBin).Append(";");
                }
                catch { }

                try
                {
                    string ffmpegDir = FindFfmpegDirectory(appDir);
                    if (!string.IsNullOrEmpty(ffmpegDir)) extra.Append(ffmpegDir).Append(";");
                }
                catch { }

                try
                {
                    string pyExe = FindRealPythonExe();
                    if (!string.IsNullOrEmpty(pyExe) && File.Exists(pyExe))
                    {
                        string pyDir = Path.GetDirectoryName(pyExe);
                        if (!string.IsNullOrEmpty(pyDir))
                        {
                            extra.Append(pyDir).Append(";");
                            string scriptsDir = Path.Combine(pyDir, "Scripts");
                            if (Directory.Exists(scriptsDir)) extra.Append(scriptsDir).Append(";");
                        }
                    }
                }
                catch { }

                try
                {
                    string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                    string wingetLinks = Path.Combine(localAppData, "Microsoft", "WinGet", "Links");
                    if (Directory.Exists(wingetLinks)) extra.Append(wingetLinks).Append(";");
                }
                catch { }

                string combined = extra.ToString() + currentPath + ";" + userPath + ";" + machinePath;
                Environment.SetEnvironmentVariable("PATH", combined, EnvironmentVariableTarget.Process);
            }
            catch { }
        }

        private static string FindFfmpegDirectory(string appDir)
        {
            if (File.Exists(Path.Combine(appDir, "ffmpeg.exe"))) return appDir;

            string uOut, uErr;
            if (RunHiddenCommand("where.exe", "ffmpeg", appDir, 3000, out uOut, out uErr) == 0 && !string.IsNullOrWhiteSpace(uOut))
            {
                string firstLine = uOut.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)[0].Trim();
                if (File.Exists(firstLine)) return Path.GetDirectoryName(firstLine);
            }

            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string wingetLinks = Path.Combine(localAppData, "Microsoft", "WinGet", "Links");
            if (File.Exists(Path.Combine(wingetLinks, "ffmpeg.exe"))) return wingetLinks;

            string wingetPkgs = Path.Combine(localAppData, "Microsoft", "WinGet", "Packages");
            if (Directory.Exists(wingetPkgs))
            {
                try
                {
                    string[] ffFiles = Directory.GetFiles(wingetPkgs, "ffmpeg.exe", SearchOption.AllDirectories);
                    if (ffFiles.Length > 0) return Path.GetDirectoryName(ffFiles[0]);
                }
                catch { }
            }

            if (Directory.Exists(@"C:\ffmpeg\bin") && File.Exists(@"C:\ffmpeg\bin\ffmpeg.exe"))
            {
                return @"C:\ffmpeg\bin";
            }

            return null;
        }

        private static string FindRealPythonExe()
        {
            try
            {
                string localPrograms = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Python");
                string[] roots = new string[] { localPrograms, @"C:\Program Files\Python312", @"C:\Program Files\Python311", @"C:\Program Files\Python310", @"C:\Python311" };
                foreach (string r in roots)
                {
                    if (!Directory.Exists(r)) continue;
                    if (File.Exists(Path.Combine(r, "python.exe"))) return Path.Combine(r, "python.exe");
                    try
                    {
                        string[] subDirs = Directory.GetDirectories(r, "Python3*");
                        Array.Sort(subDirs);
                        for (int i = subDirs.Length - 1; i >= 0; i--)
                        {
                            string cand = Path.Combine(subDirs[i], "python.exe");
                            if (File.Exists(cand)) return cand;
                        }
                    }
                    catch { }
                }
            }
            catch { }

            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string uOut, uErr;
            if (RunHiddenCommand("where.exe", "python", baseDir, 3000, out uOut, out uErr) == 0 && !string.IsNullOrWhiteSpace(uOut))
            {
                string[] lines = uOut.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
                foreach (string line in lines)
                {
                    string p = line.Trim();
                    if (File.Exists(p) && p.IndexOf("WindowsApps", StringComparison.OrdinalIgnoreCase) < 0)
                    {
                        return p;
                    }
                }
            }

            if (RunHiddenCommand("python", "--version", baseDir, 3000, out uOut, out uErr) == 0 &&
                ((uOut != null && uOut.Contains("Python 3")) || (uErr != null && uErr.Contains("Python 3"))))
            {
                return "python";
            }

            return null;
        }

        private static void EnsureLatestBuild(string appDir, string javacExe, string jarExe, bool forceRebuild, Action<string> onStatus)
        {
            string srcDir = Path.Combine(appDir, "src", "main", "java");
            string classesDir = Path.Combine(appDir, "target", "classes");
            string mainClassFile = Path.Combine(classesDir, "com", "example", "Main.class");
            string jarFile = Path.Combine(appDir, "ReviewPhimAI.jar");

            if (!Directory.Exists(srcDir)) return;

            string[] javaFiles = Directory.GetFiles(srcDir, "*.java", SearchOption.AllDirectories);
            if (javaFiles.Length == 0) return;

            bool needsCompile = forceRebuild || !File.Exists(mainClassFile) || !File.Exists(jarFile);
            if (!needsCompile)
            {
                DateTime lastBuildTime = File.GetLastWriteTimeUtc(mainClassFile);
                foreach (string jf in javaFiles)
                {
                    if (File.GetLastWriteTimeUtc(jf) > lastBuildTime)
                    {
                        needsCompile = true;
                        break;
                    }
                }
            }

            if (!needsCompile) return;

            string verOut, verErr;
            if (RunHiddenCommand(javacExe, "-version", appDir, 5000, out verOut, out verErr) != 0)
            {
                return;
            }

            onStatus("⚙️ Đang biên dịch & tối ưu hóa mã nguồn mới nhất...");
            Directory.CreateDirectory(classesDir);

            string modInfo1 = Path.Combine(classesDir, "module-info.class");
            if (File.Exists(modInfo1)) { try { File.Delete(modInfo1); } catch { } }

            StringBuilder argsBuilder = new StringBuilder();
            argsBuilder.Append("-encoding UTF-8 -cp \"lib/*;target/classes;ReviewPhimAI.jar\" -d \"target/classes\"");
            foreach (string jf in javaFiles)
            {
                argsBuilder.Append(" \"").Append(jf).Append("\"");
            }

            string compOut, compErr;
            int compileCode = RunHiddenCommand(javacExe, argsBuilder.ToString(), appDir, 30000, out compOut, out compErr);
            if (compileCode == 0 && (File.Exists(jarExe) || jarExe == "jar"))
            {
                string jarOut, jarErr;
                RunHiddenCommand(jarExe, "cfe \"ReviewPhimAI.jar\" com.example.Main -C \"target/classes\" .", appDir, 15000, out jarOut, out jarErr);
            }
        }

        private static void LaunchJavaWindowless(string appDir, string javawExe, string[] args)
        {
            StringBuilder javaArgs = new StringBuilder();
            javaArgs.Append("-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ");
            javaArgs.Append("-cp \"target/classes;lib/*;ReviewPhimAI.jar\" com.example.Main");

            if (args != null)
            {
                foreach (string a in args)
                {
                    javaArgs.Append(" \"").Append(a).Append("\"");
                }
            }

            ProcessStartInfo psi = new ProcessStartInfo
            {
                FileName = javawExe,
                Arguments = javaArgs.ToString(),
                WorkingDirectory = appDir,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            Process.Start(psi);
        }

        private static string FindJavaBinDirectory()
        {
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string bin = Path.Combine(javaHome, "bin");
                if (File.Exists(Path.Combine(bin, "javaw.exe")) || File.Exists(Path.Combine(bin, "java.exe")))
                {
                    return bin;
                }
            }

            string localPrograms = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Eclipse Adoptium");
            string[] searchRoots = new string[]
            {
                @"C:\Program Files\Java",
                @"C:\Program Files\Eclipse Adoptium",
                @"C:\Program Files\Microsoft",
                localPrograms,
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".jdks")
            };

            foreach (string root in searchRoots)
            {
                if (Directory.Exists(root))
                {
                    string[] dirs = Directory.GetDirectories(root);
                    Array.Sort(dirs);
                    for (int i = dirs.Length - 1; i >= 0; i--)
                    {
                        string bin = Path.Combine(dirs[i], "bin");
                        if (File.Exists(Path.Combine(bin, "javaw.exe")) || File.Exists(Path.Combine(bin, "java.exe")))
                        {
                            return bin;
                        }
                    }
                }
            }

            return null;
        }

        private static int RunHiddenCommand(string fileName, string arguments, string workDir, int timeoutMs, out string stdout, out string stderr)
        {
            stdout = "";
            stderr = "";
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = fileName,
                    Arguments = arguments,
                    WorkingDirectory = workDir,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    StandardOutputEncoding = Encoding.UTF8,
                    StandardErrorEncoding = Encoding.UTF8
                };

                using (Process p = Process.Start(psi))
                {
                    StringBuilder outSb = new StringBuilder();
                    StringBuilder errSb = new StringBuilder();
                    p.OutputDataReceived += (s, e) => { if (e.Data != null) outSb.AppendLine(e.Data); };
                    p.ErrorDataReceived += (s, e) => { if (e.Data != null) errSb.AppendLine(e.Data); };
                    p.BeginOutputReadLine();
                    p.BeginErrorReadLine();

                    if (!p.WaitForExit(timeoutMs))
                    {
                        try { p.Kill(); } catch { }
                        return -1;
                    }
                    p.WaitForExit();
                    stdout = outSb.ToString();
                    stderr = errSb.ToString();
                    return p.ExitCode;
                }
            }
            catch (Exception ex)
            {
                stderr = ex.Message;
                return -1;
            }
        }
    }
}
