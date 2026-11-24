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
    public class DisasterServerForm : Form
    {
        private static Mutex appMutex = null;
        private Process serverProcess = null;
        private Thread monitorThread = null;
        private volatile bool isStopping = false;

        private Label lblHeaderTitle;
        private Label lblHeaderSubtitle;
        private Panel pnlStatusCard;
        private Label lblStatusBadge;
        private Label lblStatusText;
        private ProgressBar progressBar;
        private Label lblLocalhostUrl;
        private Label lblLanUrl;
        private Button btnCopyLan;
        private Button btnOpenAdmin;
        private Button btnOpenCitizen;
        private Button btnOpenResponder;
        private Button btnConfigureFirewall;
        private Button btnStopExit;
        private TextBox txtLogs;
        private string detectedLanIp = "127.0.0.1";
        private int serverPort = 8080;

        [STAThread]
        public static void Main(string[] args)
        {
            bool createdNew;
            appMutex = new Mutex(true, "Global\\DisasterManagementCentralServerMutex", out createdNew);
            if (!createdNew)
            {
                MessageBox.Show("Disaster Management Central Server is already running on this machine.\nCheck your taskbar or system tray.",
                    "Instance Already Active", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new DisasterServerForm());

            if (appMutex != null)
            {
                try { appMutex.ReleaseMutex(); } catch { }
                appMutex.Dispose();
            }
        }

        public DisasterServerForm()
        {
            InitializeComponent();
            DetectActiveLanIp();
            StartServerProcess();
        }

        private void InitializeComponent()
        {
            this.Text = "Disaster Management Central Server - Standalone Host";
            this.Size = new Size(820, 680);
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
                Height = 85,
                BackColor = Color.FromArgb(30, 41, 59) // Slate 800
            };

            lblHeaderTitle = new Label
            {
                Text = "DISASTER MANAGEMENT CENTRAL SERVER",
                Font = new Font("Segoe UI", 13.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(56, 189, 248), // Sky 400
                Location = new Point(20, 15),
                AutoSize = true
            };

            lblHeaderSubtitle = new Label
            {
                Text = "Standalone Host Node | Real-Time Multi-Device LAN Emergency Operations",
                Font = new Font("Segoe UI", 9f, FontStyle.Regular),
                ForeColor = Color.FromArgb(148, 163, 184), // Slate 400
                Location = new Point(22, 45),
                AutoSize = true
            };

            pnlHeader.Controls.Add(lblHeaderTitle);
            pnlHeader.Controls.Add(lblHeaderSubtitle);
            this.Controls.Add(pnlHeader);

            // 2. Status & Network Details Card
            pnlStatusCard = new Panel
            {
                Location = new Point(20, 100),
                Size = new Size(765, 175),
                BackColor = Color.FromArgb(30, 41, 59),
                BorderStyle = BorderStyle.FixedSingle
            };

            lblStatusBadge = new Label
            {
                Text = "INITIALIZING",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(234, 179, 8), // Yellow 500
                ForeColor = Color.Black,
                Location = new Point(18, 18),
                Padding = new Padding(6, 3, 6, 3),
                AutoSize = true
            };

            lblStatusText = new Label
            {
                Text = "Starting embedded Spring Boot server and verifying MySQL connectivity...",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.White,
                Location = new Point(130, 20),
                AutoSize = true
            };

            progressBar = new ProgressBar
            {
                Location = new Point(18, 50),
                Size = new Size(725, 10),
                Style = ProgressBarStyle.Marquee,
                MarqueeAnimationSpeed = 30
            };

            Label lblLocalLabel = new Label
            {
                Text = "Local Host Access:",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(18, 75),
                AutoSize = true
            };

            lblLocalhostUrl = new Label
            {
                Text = "http://localhost:8080",
                Font = new Font("Consolas", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(125, 211, 252),
                Location = new Point(18, 95),
                AutoSize = true
            };

            Label lblLanLabel = new Label
            {
                Text = "Local Wi-Fi / LAN Access URL (for other laptops & mobile devices):",
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
                Location = new Point(18, 140),
                AutoSize = true
            };

            btnCopyLan = new Button
            {
                Text = "Copy LAN URL",
                Location = new Point(625, 134),
                Size = new Size(118, 28),
                BackColor = Color.FromArgb(51, 65, 85),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnCopyLan.Click += (s, e) => {
                try {
                    Clipboard.SetText(lblLanUrl.Text);
                    MessageBox.Show("LAN Access URL copied to clipboard:\n" + lblLanUrl.Text + "\n\nShare this URL with Citizen and Responder laptops on the same Wi-Fi.",
                        "URL Copied", MessageBoxButtons.OK, MessageBoxIcon.Information);
                } catch { }
            };

            pnlStatusCard.Controls.Add(lblStatusBadge);
            pnlStatusCard.Controls.Add(lblStatusText);
            pnlStatusCard.Controls.Add(progressBar);
            pnlStatusCard.Controls.Add(lblLocalLabel);
            pnlStatusCard.Controls.Add(lblLocalhostUrl);
            pnlStatusCard.Controls.Add(lblLanLabel);
            pnlStatusCard.Controls.Add(lblLanUrl);
            pnlStatusCard.Controls.Add(btnCopyLan);
            this.Controls.Add(pnlStatusCard);

            // 3. Quick Action Buttons Panel
            Panel pnlButtons = new Panel
            {
                Location = new Point(20, 285),
                Size = new Size(765, 42)
            };

            btnOpenAdmin = new Button
            {
                Text = "Admin Command",
                Location = new Point(0, 0),
                Size = new Size(140, 38),
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
                Location = new Point(150, 0),
                Size = new Size(140, 38),
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
                Location = new Point(300, 0),
                Size = new Size(140, 38),
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
                Text = "Open Firewall Port",
                Location = new Point(450, 0),
                Size = new Size(150, 38),
                BackColor = Color.FromArgb(71, 85, 105),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnConfigureFirewall.Click += (s, e) => ConfigureWindowsFirewall();

            btnStopExit = new Button
            {
                Text = "Stop Server & Exit",
                Location = new Point(610, 0),
                Size = new Size(155, 38),
                BackColor = Color.FromArgb(220, 38, 38), // Red 600
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnStopExit.Click += (s, e) => this.Close();

            pnlButtons.Controls.Add(btnOpenAdmin);
            pnlButtons.Controls.Add(btnOpenCitizen);
            pnlButtons.Controls.Add(btnOpenResponder);
            pnlButtons.Controls.Add(btnConfigureFirewall);
            pnlButtons.Controls.Add(btnStopExit);
            this.Controls.Add(pnlButtons);

            // 4. Live Console Logs Viewer
            Label lblLogHeader = new Label
            {
                Text = "LIVE SERVER TELEMETRY & EVENT LOG:",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(20, 338),
                AutoSize = true
            };
            this.Controls.Add(lblLogHeader);

            txtLogs = new TextBox
            {
                Location = new Point(20, 360),
                Size = new Size(765, 260),
                Multiline = true,
                ScrollBars = ScrollBars.Vertical,
                ReadOnly = true,
                BackColor = Color.FromArgb(10, 15, 30),
                ForeColor = Color.FromArgb(226, 232, 240),
                Font = new Font("Consolas", 8.5f, FontStyle.Regular),
                BorderStyle = BorderStyle.FixedSingle
            };
            this.Controls.Add(txtLogs);

            this.FormClosing += DisasterServerForm_FormClosing;
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
            AppendLog("[LAUNCHER] Initializing Standalone Disaster Management Host Server...");
            AppendLog("[LAUNCHER] Host Machine: " + Environment.MachineName + " | OS: " + Environment.OSVersion);
            AppendLog("[LAUNCHER] Detected LAN IPv4 Address: " + detectedLanIp);

            // Locate Java executable
            string javaExe = ResolveJavaExecutable();
            AppendLog("[LAUNCHER] Resolved Java Runtime: " + javaExe);

            // Locate Server JAR
            string jarPath = ResolveJarPath();
            AppendLog("[LAUNCHER] Resolved Backend Archive: " + jarPath);

            if (!File.Exists(javaExe))
            {
                MessageBox.Show("Java Runtime not found.\nPlease ensure JDK/JRE 21+ is installed or bundled in the runtime folder.",
                    "Runtime Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            if (!File.Exists(jarPath))
            {
                MessageBox.Show("Server application archive not found at:\n" + jarPath,
                    "Package Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaExe,
                    Arguments = "-XX:TieredStopAtLevel=1 -Dserver.port=" + serverPort + " -Dserver.address=0.0.0.0 -jar \"" + jarPath + "\"",
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
                AppendLog("[LAUNCHER] Spring Boot child process started with PID: " + serverProcess.Id);

                // Start Health Monitor Thread
                monitorThread = new Thread(MonitorServerHealth) { IsBackground = true, Name = "HealthMonitor" };
                monitorThread.Start();
            }
            catch (Exception ex)
            {
                AppendLog("[FATAL] Failed to start server process: " + ex.Message);
                MessageBox.Show("Failed to start Spring Boot application:\n" + ex.Message,
                    "Startup Failure", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private string ResolveJavaExecutable()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string bundled1 = Path.Combine(baseDir, "runtime", "bin", "java.exe");
            if (File.Exists(bundled1)) return bundled1;

            string bundled2 = Path.Combine(baseDir, "jre", "bin", "java.exe");
            if (File.Exists(bundled2)) return bundled2;

            string targetRuntime = Path.Combine(baseDir, "..", "..", "target", "runtime", "bin", "java.exe");
            if (File.Exists(targetRuntime)) return Path.GetFullPath(targetRuntime);

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

            string devJar = Path.Combine(baseDir, "..", "..", "target", "demo-0.0.1-SNAPSHOT.jar");
            if (File.Exists(devJar)) return Path.GetFullPath(devJar);

            return localJar;
        }

        private void MonitorServerHealth()
        {
            string healthUrl = "http://localhost:" + serverPort + "/actuator/health";
            bool isReady = false;

            for (int i = 0; i < 60; i++)
            {
                if (isStopping) return;
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
                            break;
                        }
                    }
                }
                catch { }
            }

            if (isReady && !isStopping)
            {
                this.BeginInvoke((Action)(() => {
                    lblStatusBadge.Text = "READY / ONLINE";
                    lblStatusBadge.BackColor = Color.FromArgb(34, 197, 94); // Emerald 500
                    lblStatusBadge.ForeColor = Color.White;
                    lblStatusText.Text = "Central Server is ACTIVE on Port " + serverPort + ". All role portals ready.";
                    progressBar.Style = ProgressBarStyle.Blocks;
                    progressBar.Value = 100;
                    btnOpenAdmin.Enabled = true;
                    btnOpenCitizen.Enabled = true;
                    btnOpenResponder.Enabled = true;
                    AppendLog("[LAUNCHER] >>> SERVER REACHED READY STATE. All network listeners active. <<<");
                }));
            }
            else if (!isStopping)
            {
                this.BeginInvoke((Action)(() => {
                    lblStatusBadge.Text = "FAILED / UNREACHABLE";
                    lblStatusBadge.BackColor = Color.FromArgb(239, 68, 68);
                    lblStatusBadge.ForeColor = Color.White;
                    lblStatusText.Text = "Health check failed after 120s. Check terminal logs for database or port issues.";
                    progressBar.Style = ProgressBarStyle.Blocks;
                    progressBar.Value = 0;
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
                    Arguments = "advfirewall firewall add rule name=\"DisasterManagement_Port8080\" dir=in action=allow protocol=TCP localport=" + serverPort + " profile=private,domain",
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

        private void DisasterServerForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            if (isStopping) return;

            DialogResult dr = MessageBox.Show("Are you sure you want to shut down the Disaster Management Central Server?\nAll connected Citizen and Responder devices will disconnect.",
                "Confirm Server Shutdown", MessageBoxButtons.YesNo, MessageBoxIcon.Question);

            if (dr != DialogResult.Yes)
            {
                e.Cancel = true;
                return;
            }

            isStopping = true;
            lblStatusBadge.Text = "STOPPING...";
            lblStatusBadge.BackColor = Color.FromArgb(239, 68, 68);
            lblStatusText.Text = "Initiating graceful shutdown of Spring Boot and database pools...";
            progressBar.Style = ProgressBarStyle.Marquee;
            btnOpenAdmin.Enabled = false;
            btnOpenCitizen.Enabled = false;
            btnOpenResponder.Enabled = false;
            btnStopExit.Enabled = false;

            Thread stopThread = new Thread(() => {
                ShutdownBackendProcess();
                this.BeginInvoke((Action)(() => {
                    this.FormClosing -= DisasterServerForm_FormClosing;
                    this.Close();
                }));
            });
            stopThread.Start();
            e.Cancel = true;
        }

        private void ShutdownBackendProcess()
        {
            if (serverProcess != null && !serverProcess.HasExited)
            {
                AppendLog("[SHUTDOWN] Closing standard input stream to trigger parent-guardian shutdown...");
                try { serverProcess.StandardInput.Close(); } catch { }

                try
                {
                    if (!serverProcess.WaitForExit(8000))
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
