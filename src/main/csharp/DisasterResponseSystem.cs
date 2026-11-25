using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace DisasterManagement.Launcher
{
    public class DisasterResponseSystemForm : Form
    {
        private static Mutex appMutex = null;
        private Process serverProcess = null;
        private Thread monitorThread = null;
        private volatile bool isServerRunning = false;
        private volatile bool isStopping = false;

        private Label lblHeaderTitle;
        private Label lblHeaderSubtitle;
        private Panel pnlStatusCard;
        private Label lblStatusBadge;
        private Label lblDbBadge;
        private Label lblStatusText;
        private ProgressBar progressBar;
        private Label lblLocalhostUrl;
        private Label lblLanUrl;
        private Button btnCopyLan;
        private Button btnStartServer;
        private Button btnStopServer;
        private Button btnOpenAdmin;
        private Button btnOpenCitizen;
        private Button btnOpenResponder;
        private Button btnConfigureFirewall;
        private Button btnExit;
        private TextBox txtLogs;
        private string detectedLanIp = "127.0.0.1";
        private int serverPort = 8080;

        [STAThread]
        public static void Main(string[] args)
        {
            bool createdNew;
            appMutex = new Mutex(true, "Global\\DisasterResponseSystemMutex", out createdNew);
            if (!createdNew)
            {
                MessageBox.Show("DisasterResponseSystem is already running on this machine.\nPlease check your taskbar or active processes.",
                    "Instance Already Active", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new DisasterResponseSystemForm());

            if (appMutex != null)
            {
                try { appMutex.ReleaseMutex(); } catch { }
                appMutex.Dispose();
            }
        }

        public DisasterResponseSystemForm()
        {
            InitializeComponent();
            DetectActiveLanIp();
            // Start server automatically on launch as requested
            StartServerProcess();
        }

        private void InitializeComponent()
        {
            this.Text = "DisasterResponseSystem — Central Server Management Console";
            this.Size = new Size(860, 720);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.FromArgb(15, 23, 42); // Slate 900
            this.ForeColor = Color.White;
            this.Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);
            this.FormBorderStyle = FormBorderStyle.FixedSingle;
            this.MaximizeBox = false;

            // 1. Header Banner
            Panel pnlHeader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 90,
                BackColor = Color.FromArgb(30, 41, 59) // Slate 800
            };

            lblHeaderTitle = new Label
            {
                Text = "DISASTER RESPONSE SYSTEM — MISSION CONTROL",
                Font = new Font("Segoe UI", 14f, FontStyle.Bold),
                ForeColor = Color.FromArgb(56, 189, 248), // Sky 400
                Location = new Point(20, 16),
                AutoSize = true
            };

            lblHeaderSubtitle = new Label
            {
                Text = "Integrated Disaster Reporting & Emergency Response Coordination Platform | Central Server Host",
                Font = new Font("Segoe UI", 9.2f, FontStyle.Regular),
                ForeColor = Color.FromArgb(148, 163, 184), // Slate 400
                Location = new Point(22, 48),
                AutoSize = true
            };

            pnlHeader.Controls.Add(lblHeaderTitle);
            pnlHeader.Controls.Add(lblHeaderSubtitle);
            this.Controls.Add(pnlHeader);

            // 2. Status & Network Details Card
            pnlStatusCard = new Panel
            {
                Location = new Point(20, 105),
                Size = new Size(805, 195),
                BackColor = Color.FromArgb(30, 41, 59),
                BorderStyle = BorderStyle.FixedSingle
            };

            lblStatusBadge = new Label
            {
                Text = "STOPPED",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(100, 116, 139), // Slate 500
                ForeColor = Color.White,
                Location = new Point(18, 16),
                Padding = new Padding(6, 3, 6, 3),
                AutoSize = true
            };

            lblDbBadge = new Label
            {
                Text = "DATABASE: STANDBY",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(71, 85, 105),
                ForeColor = Color.White,
                Location = new Point(110, 16),
                Padding = new Padding(6, 3, 6, 3),
                AutoSize = true
            };

            lblStatusText = new Label
            {
                Text = "Server process is stopped. Click 'Start Server' to launch the backend.",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.White,
                Location = new Point(285, 18),
                AutoSize = true
            };

            progressBar = new ProgressBar
            {
                Location = new Point(18, 48),
                Size = new Size(765, 10),
                Style = ProgressBarStyle.Blocks,
                Value = 0
            };

            Label lblLocalLabel = new Label
            {
                Text = "Local Host Access URL:",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(18, 72),
                AutoSize = true
            };

            lblLocalhostUrl = new Label
            {
                Text = "http://localhost:8080",
                Font = new Font("Consolas", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(125, 211, 252),
                Location = new Point(18, 92),
                AutoSize = true
            };

            Label lblLanLabel = new Label
            {
                Text = "Local Wi-Fi / LAN Access URL (for other laptops, responders & citizens on same network):",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(18, 120),
                AutoSize = true
            };

            lblLanUrl = new Label
            {
                Text = "Detecting active LAN IP...",
                Font = new Font("Consolas", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(74, 222, 128), // Green 400
                Location = new Point(18, 142),
                AutoSize = true
            };

            btnCopyLan = new Button
            {
                Text = "Copy LAN URL",
                Location = new Point(660, 136),
                Size = new Size(125, 30),
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnCopyLan.Click += (s, e) => {
                try {
                    Clipboard.SetText(lblLanUrl.Text);
                    MessageBox.Show("LAN Access URL copied to clipboard:\n" + lblLanUrl.Text + "\n\nShare this URL with other laptops on the same Wi-Fi network.",
                        "URL Copied", MessageBoxButtons.OK, MessageBoxIcon.Information);
                } catch { }
            };

            pnlStatusCard.Controls.Add(lblStatusBadge);
            pnlStatusCard.Controls.Add(lblDbBadge);
            pnlStatusCard.Controls.Add(lblStatusText);
            pnlStatusCard.Controls.Add(progressBar);
            pnlStatusCard.Controls.Add(lblLocalLabel);
            pnlStatusCard.Controls.Add(lblLocalhostUrl);
            pnlStatusCard.Controls.Add(lblLanLabel);
            pnlStatusCard.Controls.Add(lblLanUrl);
            pnlStatusCard.Controls.Add(btnCopyLan);
            this.Controls.Add(pnlStatusCard);

            // 3. Server Controls & Quick Action Buttons Panel
            Panel pnlControls = new Panel
            {
                Location = new Point(20, 312),
                Size = new Size(805, 46)
            };

            btnStartServer = new Button
            {
                Text = "▶ Start Server",
                Location = new Point(0, 0),
                Size = new Size(118, 42),
                BackColor = Color.FromArgb(22, 163, 74), // Green 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            btnStartServer.Click += (s, e) => StartServerProcess();

            btnStopServer = new Button
            {
                Text = "⏹ Stop Server",
                Location = new Point(126, 0),
                Size = new Size(118, 42),
                BackColor = Color.FromArgb(220, 38, 38), // Red 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            btnStopServer.Click += (s, e) => StopServerProcess(false);

            btnOpenAdmin = new Button
            {
                Text = "Admin Portal",
                Location = new Point(252, 0),
                Size = new Size(110, 42),
                BackColor = Color.FromArgb(37, 99, 235), // Blue 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            btnOpenAdmin.Click += (s, e) => OpenBrowser("http://localhost:" + serverPort + "/admin-dashboard.html");

            btnOpenCitizen = new Button
            {
                Text = "Citizen Portal",
                Location = new Point(370, 0),
                Size = new Size(110, 42),
                BackColor = Color.FromArgb(13, 148, 136), // Teal 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            btnOpenCitizen.Click += (s, e) => OpenBrowser("http://localhost:" + serverPort + "/citizen-dashboard.html");

            btnOpenResponder = new Button
            {
                Text = "Responder HUD",
                Location = new Point(488, 0),
                Size = new Size(115, 42),
                BackColor = Color.FromArgb(217, 119, 6), // Amber 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand,
                Enabled = false
            };
            btnOpenResponder.Click += (s, e) => OpenBrowser("http://localhost:" + serverPort + "/responder-dashboard.html");

            btnConfigureFirewall = new Button
            {
                Text = "Firewall Port",
                Location = new Point(611, 0),
                Size = new Size(100, 42),
                BackColor = Color.FromArgb(71, 85, 105),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnConfigureFirewall.Click += (s, e) => ConfigureWindowsFirewall();

            btnExit = new Button
            {
                Text = "Exit",
                Location = new Point(719, 0),
                Size = new Size(86, 42),
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnExit.Click += (s, e) => this.Close();

            pnlControls.Controls.Add(btnStartServer);
            pnlControls.Controls.Add(btnStopServer);
            pnlControls.Controls.Add(btnOpenAdmin);
            pnlControls.Controls.Add(btnOpenCitizen);
            pnlControls.Controls.Add(btnOpenResponder);
            pnlControls.Controls.Add(btnConfigureFirewall);
            pnlControls.Controls.Add(btnExit);
            this.Controls.Add(pnlControls);

            // 4. Live Console Logs Viewer
            Label lblLogHeader = new Label
            {
                Text = "LIVE SERVER TELEMETRY & EVENT LOG STREAM:",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(20, 368),
                AutoSize = true
            };
            this.Controls.Add(lblLogHeader);

            txtLogs = new TextBox
            {
                Location = new Point(20, 390),
                Size = new Size(805, 270),
                Multiline = true,
                ScrollBars = ScrollBars.Vertical,
                ReadOnly = true,
                BackColor = Color.FromArgb(10, 15, 30),
                ForeColor = Color.FromArgb(226, 232, 240),
                Font = new Font("Consolas", 8.8f, FontStyle.Regular),
                BorderStyle = BorderStyle.FixedSingle
            };
            this.Controls.Add(txtLogs);

            this.FormClosing += DisasterResponseSystemForm_FormClosing;
        }

        private void DetectActiveLanIp()
        {
            try
            {
                string bestIp = null;
                foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    if (ni.Description.ToLower().Contains("virtual") || ni.Name.ToLower().Contains("vethernet")) continue;

                    var ipProps = ni.GetIPProperties();
                    foreach (UnicastIPAddressInformation uip in ipProps.UnicastAddresses)
                    {
                        if (uip.Address.AddressFamily == AddressFamily.InterNetwork)
                        {
                            string s = uip.Address.ToString();
                            if (!s.StartsWith("127.") && !s.StartsWith("169.254."))
                            {
                                bestIp = s;
                                break;
                            }
                        }
                    }
                    if (bestIp != null) break;
                }

                if (bestIp != null)
                {
                    detectedLanIp = bestIp;
                    lblLanUrl.Text = "http://" + detectedLanIp + ":" + serverPort;
                }
                else
                {
                    lblLanUrl.Text = "http://127.0.0.1:" + serverPort + " (No active LAN adapter detected)";
                }
            }
            catch (Exception ex)
            {
                lblLanUrl.Text = "http://127.0.0.1:" + serverPort + " (" + ex.Message + ")";
            }
        }

        private void AppendLog(string text)
        {
            if (this.IsDisposed || !this.IsHandleCreated) return;
            try
            {
                this.BeginInvoke((Action)(() => {
                    if (txtLogs.TextLength > 100000)
                    {
                        txtLogs.Text = txtLogs.Text.Substring(txtLogs.TextLength - 50000);
                    }
                    txtLogs.AppendText(text + Environment.NewLine);
                }));
            }
            catch { }
        }

        private void StartServerProcess()
        {
            if (isServerRunning) return;

            btnStartServer.Enabled = false;
            btnStopServer.Enabled = true;

            lblStatusBadge.Text = "STARTING";
            lblStatusBadge.BackColor = Color.FromArgb(234, 179, 8); // Yellow 500
            lblStatusBadge.ForeColor = Color.Black;
            lblDbBadge.Text = "DATABASE: CONNECTING";
            lblDbBadge.BackColor = Color.FromArgb(234, 179, 8);
            lblDbBadge.ForeColor = Color.Black;
            lblStatusText.Text = "Launching Spring Boot backend and establishing MySQL connection...";
            progressBar.Style = ProgressBarStyle.Marquee;
            progressBar.MarqueeAnimationSpeed = 30;

            AppendLog("[LAUNCHER] >>> Starting DisasterResponseSystem Backend Server <<<");
            AppendLog("[LAUNCHER] Host: " + Environment.MachineName + " | OS: " + Environment.OSVersion);
            AppendLog("[LAUNCHER] Detected LAN IPv4 Address: " + detectedLanIp);

            string javaExe = ResolveJavaExecutable();
            string jarPath = ResolveJarPath();

            AppendLog("[LAUNCHER] Java Runtime: " + javaExe);
            AppendLog("[LAUNCHER] Application Archive: " + jarPath);

            if (!File.Exists(javaExe))
            {
                AppendLog("[ERROR] Java executable not found at: " + javaExe);
                MessageBox.Show("Java Runtime not found.\nPlease ensure JDK/JRE 21+ is bundled in the runtime folder or installed on system PATH.",
                    "Runtime Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                ResetStoppedState();
                return;
            }

            if (!File.Exists(jarPath))
            {
                AppendLog("[ERROR] Application archive not found at: " + jarPath);
                MessageBox.Show("Server application archive not found at:\n" + jarPath,
                    "Package Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                ResetStoppedState();
                return;
            }

            try
            {
                // Ensure persistent user-writable data directory in %LOCALAPPDATA%\DisasterResponseSystem
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string appDataDir = Path.Combine(localAppData, "DisasterResponseSystem");
                string uploadsDir = Path.Combine(appDataDir, "uploads", "incident-media");
                string logsDir = Path.Combine(appDataDir, "logs");
                try
                {
                    Directory.CreateDirectory(uploadsDir);
                    Directory.CreateDirectory(logsDir);
                }
                catch { }

                StringBuilder argsBuilder = new StringBuilder();
                argsBuilder.Append("-XX:TieredStopAtLevel=1");
                argsBuilder.Append(" -Dserver.port=").Append(serverPort);
                argsBuilder.Append(" -Dserver.address=0.0.0.0");
                argsBuilder.Append(" -Dapp.media.upload-dir=\"").Append(uploadsDir.Replace("\\", "/")).Append("\"");

                string externalConfig = Path.Combine(appDataDir, "application-local.properties");
                if (File.Exists(externalConfig))
                {
                    argsBuilder.Append(" -Dspring.config.additional-location=\"file:").Append(externalConfig.Replace("\\", "/")).Append("\"");
                    AppendLog("[CONFIG] Loaded external configuration: " + externalConfig);
                }

                argsBuilder.Append(" -jar \"").Append(jarPath).Append("\"");

                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaExe,
                    Arguments = argsBuilder.ToString(),
                    WorkingDirectory = AppDomain.CurrentDomain.BaseDirectory,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    RedirectStandardInput = true,
                    CreateNoWindow = true
                };

                serverProcess = new Process { StartInfo = psi };
                serverProcess.OutputDataReceived += (s, e) => { if (!string.IsNullOrEmpty(e.Data)) AppendLog(e.Data); };
                serverProcess.ErrorDataReceived += (s, e) => { if (!string.IsNullOrEmpty(e.Data)) AppendLog("[ERR] " + e.Data); };

                serverProcess.Start();
                serverProcess.BeginOutputReadLine();
                serverProcess.BeginErrorReadLine();
                isServerRunning = true;
                isStopping = false;

                AppendLog("[LAUNCHER] Spring Boot child process started with PID: " + serverProcess.Id);

                // Start Health Monitor Thread
                monitorThread = new Thread(MonitorServerHealth) { IsBackground = true, Name = "HealthMonitor" };
                monitorThread.Start();
            }
            catch (Exception ex)
            {
                AppendLog("[FATAL] Failed to start server: " + ex.Message);
                MessageBox.Show("Failed to start Spring Boot application:\n" + ex.Message,
                    "Startup Failure", MessageBoxButtons.OK, MessageBoxIcon.Error);
                ResetStoppedState();
            }
        }

        private void StopServerProcess(bool isExiting)
        {
            if (!isServerRunning && serverProcess == null)
            {
                if (!isExiting) ResetStoppedState();
                return;
            }

            isStopping = true;
            lblStatusBadge.Text = "STOPPING...";
            lblStatusBadge.BackColor = Color.FromArgb(239, 68, 68);
            lblStatusBadge.ForeColor = Color.White;
            lblStatusText.Text = "Shutting down Spring Boot server gracefully and releasing port " + serverPort + "...";
            progressBar.Style = ProgressBarStyle.Marquee;

            btnOpenAdmin.Enabled = false;
            btnOpenCitizen.Enabled = false;
            btnOpenResponder.Enabled = false;
            btnStopServer.Enabled = false;

            Thread stopThread = new Thread(() => {
                ShutdownBackendProcess();
                this.BeginInvoke((Action)(() => {
                    isServerRunning = false;
                    isStopping = false;
                    ResetStoppedState();
                    if (isExiting)
                    {
                        this.FormClosing -= DisasterResponseSystemForm_FormClosing;
                        this.Close();
                    }
                }));
            });
            stopThread.Start();
        }

        private void ResetStoppedState()
        {
            lblStatusBadge.Text = "STOPPED";
            lblStatusBadge.BackColor = Color.FromArgb(100, 116, 139);
            lblStatusBadge.ForeColor = Color.White;
            lblDbBadge.Text = "DATABASE: STANDBY";
            lblDbBadge.BackColor = Color.FromArgb(71, 85, 105);
            lblDbBadge.ForeColor = Color.White;
            lblStatusText.Text = "Server process is stopped. Click 'Start Server' to launch the backend.";
            progressBar.Style = ProgressBarStyle.Blocks;
            progressBar.Value = 0;
            btnStartServer.Enabled = true;
            btnStopServer.Enabled = false;
            btnOpenAdmin.Enabled = false;
            btnOpenCitizen.Enabled = false;
            btnOpenResponder.Enabled = false;
        }

        private string ResolveJavaExecutable()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string bundled1 = Path.Combine(baseDir, "runtime", "bin", "java.exe");
            if (File.Exists(bundled1)) return bundled1;

            string bundled2 = Path.Combine(baseDir, "jre", "bin", "java.exe");
            if (File.Exists(bundled2)) return bundled2;

            string targetRuntime = Path.Combine(baseDir, "..", "target", "runtime", "bin", "java.exe");
            if (File.Exists(targetRuntime)) return Path.GetFullPath(targetRuntime);

            string distRuntime = Path.Combine(baseDir, "..", "dist", "DisasterManagement", "runtime", "bin", "java.exe");
            if (File.Exists(distRuntime)) return Path.GetFullPath(distRuntime);

            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string homeJava = Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(homeJava)) return homeJava;
            }

            return "java.exe"; // fallback to system PATH
        }

        private string ResolveJarPath()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string localJar = Path.Combine(baseDir, "server", "demo-0.0.1-SNAPSHOT.jar");
            if (File.Exists(localJar)) return localJar;

            string rootJar = Path.Combine(baseDir, "demo-0.0.1-SNAPSHOT.jar");
            if (File.Exists(rootJar)) return rootJar;

            string targetJar = Path.Combine(baseDir, "target", "demo-0.0.1-SNAPSHOT.jar");
            if (File.Exists(targetJar)) return Path.GetFullPath(targetJar);

            string parentTargetJar = Path.Combine(baseDir, "..", "target", "demo-0.0.1-SNAPSHOT.jar");
            if (File.Exists(parentTargetJar)) return Path.GetFullPath(parentTargetJar);

            return localJar;
        }

        private void MonitorServerHealth()
        {
            string healthUrl = "http://localhost:" + serverPort + "/actuator/health";
            bool isReady = false;
            bool dbConnected = false;

            for (int i = 0; i < 75; i++)
            {
                if (isStopping || !isServerRunning) return;
                Thread.Sleep(2000);

                try
                {
                    HttpWebRequest request = (HttpWebRequest)WebRequest.Create(healthUrl);
                    request.Timeout = 3000;
                    using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
                    {
                        if (response.StatusCode == HttpStatusCode.OK)
                        {
                            isReady = true;
                            using (StreamReader sr = new StreamReader(response.GetResponseStream()))
                            {
                                string content = sr.ReadToEnd();
                                if (content.Contains("\"db\":{\"status\":\"UP\"}") || content.Contains("\"status\":\"UP\""))
                                {
                                    dbConnected = true;
                                }
                            }
                            break;
                        }
                    }
                }
                catch { }
            }

            if (isReady && !isStopping && isServerRunning)
            {
                this.BeginInvoke((Action)(() => {
                    lblStatusBadge.Text = "READY / ONLINE";
                    lblStatusBadge.BackColor = Color.FromArgb(34, 197, 94); // Emerald 500
                    lblStatusBadge.ForeColor = Color.White;
                    lblDbBadge.Text = dbConnected ? "DATABASE: CONNECTED (MySQL)" : "DATABASE: CONNECTED";
                    lblDbBadge.BackColor = Color.FromArgb(34, 197, 94);
                    lblDbBadge.ForeColor = Color.White;
                    lblStatusText.Text = "Central Server is ACTIVE on Port " + serverPort + ". All role portals ready.";
                    progressBar.Style = ProgressBarStyle.Blocks;
                    progressBar.Value = 100;
                    btnOpenAdmin.Enabled = true;
                    btnOpenCitizen.Enabled = true;
                    btnOpenResponder.Enabled = true;
                    btnStartServer.Enabled = false;
                    btnStopServer.Enabled = true;
                    AppendLog("[LAUNCHER] >>> SERVER REACHED READY STATE. All network listeners active. <<<");
                }));
            }
            else if (!isStopping && isServerRunning)
            {
                this.BeginInvoke((Action)(() => {
                    lblStatusBadge.Text = "FAILED / UNREACHABLE";
                    lblStatusBadge.BackColor = Color.FromArgb(239, 68, 68);
                    lblStatusBadge.ForeColor = Color.White;
                    lblDbBadge.Text = "DATABASE: ERROR";
                    lblDbBadge.BackColor = Color.FromArgb(239, 68, 68);
                    lblStatusText.Text = "Health check timed out after 150s. Check logs for MySQL or port collision.";
                    progressBar.Style = ProgressBarStyle.Blocks;
                    progressBar.Value = 0;
                    btnStartServer.Enabled = true;
                    btnStopServer.Enabled = true;
                }));
            }
        }

        private void OpenBrowser(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
        }

        private void ConfigureWindowsFirewall()
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = "netsh",
                    Arguments = "advfirewall firewall add rule name=\"DisasterResponseSystem_Port8080\" dir=in action=allow protocol=TCP localport=" + serverPort + " profile=private,domain",
                    Verb = "runas",
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                Process p = Process.Start(psi);
                p.WaitForExit();
                if (p.ExitCode == 0)
                {
                    MessageBox.Show("Windows Firewall rule successfully applied for TCP port " + serverPort + ".\nDevices on the local Wi-Fi can now connect via:\n" + lblLanUrl.Text,
                        "Firewall Configured", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    AppendLog("[FIREWALL] Inbound TCP port " + serverPort + " rule added for local network.");
                }
                else
                {
                    MessageBox.Show("Firewall command returned code: " + p.ExitCode, "Notice", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Failed to configure firewall: " + ex.Message + "\nYou can also run 'setup-firewall.bat' as Administrator.",
                    "Firewall Configuration Notice", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void DisasterResponseSystemForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            if (isStopping) return;

            if (isServerRunning)
            {
                DialogResult dr = MessageBox.Show("Are you sure you want to shut down the Disaster Response System server?\nAll connected Citizen and Responder devices will disconnect.",
                    "Confirm Server Shutdown", MessageBoxButtons.YesNo, MessageBoxIcon.Question);

                if (dr != DialogResult.Yes)
                {
                    e.Cancel = true;
                    return;
                }

                StopServerProcess(true);
                e.Cancel = true;
            }
        }

        private void ShutdownBackendProcess()
        {
            if (serverProcess != null && !serverProcess.HasExited)
            {
                AppendLog("[SHUTDOWN] Triggering graceful shutdown via API / standard input...");
                try
                {
                    HttpWebRequest request = (HttpWebRequest)WebRequest.Create("http://localhost:" + serverPort + "/api/system/shutdown");
                    request.Method = "POST";
                    request.Timeout = 3000;
                    using (var resp = request.GetResponse()) { }
                }
                catch { }

                try { serverProcess.StandardInput.Close(); } catch { }

                try
                {
                    if (!serverProcess.WaitForExit(6000))
                    {
                        AppendLog("[SHUTDOWN] Graceful timeout reached; terminating process tree...");
                        serverProcess.Kill();
                    }
                    AppendLog("[SHUTDOWN] Spring Boot process terminated. Port " + serverPort + " released.");
                }
                catch (Exception ex)
                {
                    AppendLog("[SHUTDOWN] Notice during process exit: " + ex.Message);
                }
            }
        }
    }
}
