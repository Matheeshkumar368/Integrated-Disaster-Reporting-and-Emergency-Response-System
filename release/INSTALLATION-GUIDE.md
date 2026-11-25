# DISASTER RESPONSE SYSTEM — INSTALLATION & DEPLOYMENT GUIDE

**System Name:** Integrated Disaster Reporting and Emergency Response Coordination System  
**Package:** `DisasterResponseSystem-Setup.exe` / `release/app-image/`  
**Target Platform:** Windows 10 / Windows 11 (64-bit)  
**Host Architecture:** Standalone Central Server Node (Laptop A) with Multi-Device Wi-Fi/LAN Access  

---

## 1. System Requirements

### Host Laptop (Server Node):
- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **RAM:** 4 GB minimum (8 GB recommended)
- **Storage:** 500 MB free space for application, runtime and media uploads
- **Network:** Connected to local Wi-Fi or Ethernet router
- **Database Prerequisite:** MySQL Community Server 8.0 or higher (or XAMPP / WampServer MySQL)
- **Java / JDK:** **NOT REQUIRED** (A minimal, self-contained Java 25 runtime is embedded inside the package)
- **IDE / Maven / Node.js:** **NOT REQUIRED**

### Client Devices (Citizens & Responders):
- Any device (Laptop, Tablet, Android/iOS smartphone) connected to the same Wi-Fi
- Any modern web browser (Google Chrome, Microsoft Edge, Mozilla Firefox, Safari)

---

## 2. MySQL Database Setup (One-Time Prerequisite)

The application uses real MySQL for persistent storage of incidents, alerts, telemetry, chat messages, and user records.

1. Install MySQL Server 8.0+ if not already installed.
2. Start the MySQL service:
   ```cmd
   net start MySQL80
   ```
3. Default connection expected by the application:
   - **Host:** `localhost`
   - **Port:** `3306`
   - **Database:** `disaster_management` (automatically created by the application if it does not exist)
   - **Username:** `root`
   - **Password:** `root` (or configured in `server/application.properties`)

> **Custom Credentials & External Configuration:**  
> The system automatically supports external configuration without modifying packaged application files.
> You can place an `application-local.properties` file in `%LOCALAPPDATA%\DisasterResponseSystem\`:
> ```properties
> spring.datasource.url=jdbc:mysql://localhost:3306/disaster_management?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
> spring.datasource.username=root
> spring.datasource.password=your_mysql_password
> ```
> Alternatively, edit `server/application.properties` in the installation directory.
> All uploaded incident media is stored persistently under `%LOCALAPPDATA%\DisasterResponseSystem\uploads\incident-media\`, safely outside `Program Files`.

---

## 3. Installation Options

### Option A: Standard Windows Installer (Recommended)
1. Locate `DisasterResponseSystem-Setup.exe` in the `release/` directory.
2. Double-click `DisasterResponseSystem-Setup.exe` to run the setup wizard.
3. Choose the destination directory (Default: `%LOCALAPPDATA%\Programs\DisasterResponseSystem`).
4. Click **Install**. The wizard copies all required application files, runtime libraries, and creates desktop and start menu shortcuts.
5. On the final screen, check **Launch Disaster Response System** and click **Finish**.

### Option B: Portable Application Image (Zero Installation)
1. Copy the entire `release/app-image/` folder to any location on your host computer (e.g. `C:\DisasterResponseSystem` or an external USB drive).
2. Double-click `DisasterResponseSystem.exe` to open the central control panel directly.

---

## 4. Running the Application

1. Double-click **Disaster Response System** on your desktop or inside the application directory.
2. The **DisasterResponseSystem Console** will open:
   - Displays the detected host machine name, OS, and active LAN IPv4 address (e.g. `http://192.168.1.105:8080`).
   - The status badge will show `STARTING` while Spring Boot and the database connection initialize.
   - Within 15–20 seconds, the status badge will illuminate green: **`READY / ONLINE`**, and the database indicator will show **`DATABASE: CONNECTED (MySQL)`**.
3. Click any of the role portal buttons to open them in your default browser:
   - **Admin Portal:** `http://localhost:8080/admin-dashboard.html`
   - **Citizen Portal:** `http://localhost:8080/citizen-dashboard.html`
   - **Responder HUD:** `http://localhost:8080/responder-dashboard.html`

---

## 5. Built-In Pre-Seeded Evaluation Accounts

| Role | Email | Password | Access Capabilities |
| :--- | :--- | :--- | :--- |
| **Central Admin** | `admin@disaster.gov.in` | `admin123` | Full Mission Control, NDMA Ingestion, Emergency Broadcasts (PIN: `123456`), Responder Dispatch, Live Radar |
| **Citizen 1** | `citizen@gmail.com` | `citizen123` | SOS Reporting, Photo/Video Evidence Upload, Comms Room, Warnings & Alerts |
| **Citizen 2** | `citizen2@gmail.com` | `citizen123` | Secondary civilian account for multi-user verification |
| **Responder 1** | `responder@disaster.gov.in` | `responder123` | Tactical HUD, Incident Acceptance, Field GPS Telemetry, Incident Resolution |

---

## 6. Stopping the Server & Uninstallation

### Stopping the Server:
- In the DisasterResponseSystem Console, click **⏹ Stop Server**.
- Spring Boot releases TCP port 8080 gracefully.
- Clicking **Exit** or closing the window cleanly stops all background processes with **zero orphaned Java processes**.

### Uninstallation:
- Open **Windows Settings** &rarr; **Apps & Features** &rarr; **Disaster Response System** &rarr; **Uninstall**.
- Alternatively, run `Uninstall.exe` in the application directory.
- All shortcuts, registry keys, and runtime files are cleanly removed.
