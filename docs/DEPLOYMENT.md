# Production Deployment & Hosting Guide
## Disaster Management & Alert System

This guide outlines the complete operational procedures for deploying the Disaster Management & Alert System as a live, secure web application on cloud infrastructure.

---

### 1. Prerequisites & Specifications

- **Java Runtime Environment (JRE):** OpenJDK 21 LTS or OpenJDK 25.
- **Database:** MySQL 8.0+ (Local, Docker container, AWS RDS, Railway, or Google Cloud SQL).
- **RAM Minimum:** 1 GB dedicated RAM (512 MB minimum heap for JVM).
- **Network Access:** Outbound HTTPS access on port 443 to `https://sachet.ndma.gov.in` for real-time CAP alert feeds and `https://api.open-meteo.com` (or `https://api.imd.gov.in`) for meteorological observations.

---

### 2. Environment Variables & Secret Configuration

Never commit live credentials to version control. Set the following environment variables in your cloud hosting console or production `.env` file:

```env
# Server & Runtime
PORT=8080
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC

# Managed Database (MySQL 8.0)
DB_URL=jdbc:mysql://<db-host>:3306/disaster_management?createDatabaseIfNotExist=true&useSSL=true&requireSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=disaster_admin
DB_PASSWORD=your_strong_random_db_password_here
JPA_DDL_AUTO=update

# Security & Authentication Secrets
JWT_SECRET=generate_a_cryptographically_secure_random_64_character_string_here
JWT_EXPIRATION_MS=86400000
ADMIN_STEP_UP_MINUTES=15

# Real-Time NDMA SACHET Live Feed Configuration
NDMA_RSS_URL=https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml
NDMA_SYNC_ENABLED=true
NDMA_SYNC_INTERVAL_MS=300000
NDMA_SYNC_INITIAL_DELAY_MS=5000
NDMA_SYNC_MAX_ITEMS=30

# IMD & Meteorological Weather Integration
IMD_API_URL=https://api.imd.gov.in/api
IMD_API_KEY=
IMD_JWT_TOKEN=
WEATHER_FALLBACK_URL=https://api.open-meteo.com/v1/forecast
```

---

### 3. Option A: Containerized Deployment (Docker & Docker Compose)

The repository provides a multi-stage `Dockerfile` and `docker-compose.yml` for automated container builds:

```bash
# 1. Build and start services in detached mode
docker compose up --build -d

# 2. Check container health status
docker compose ps

# 3. Follow live application logs
docker compose logs -f app
```

The application exposes:
- Web Application: `http://localhost:8080`
- Spring Boot Actuator Health Probe: `http://localhost:8080/actuator/health`

---

### 4. Option B: Cloud PaaS Deployment (Railway / Render)

#### Deploying on Railway
1. Create a new project in [Railway.app](https://railway.app).
2. Add a **MySQL** database service. Note the connection URL, username, and password.
3. Add a **GitHub Repo** service connected to your repository.
4. Add the environment variables specified in Section 2 to the service settings.
5. Railway will automatically detect Maven, build the application, run health probes against `/actuator/health`, and provision a public HTTPS URL (e.g. `https://disaster-management-production.up.railway.app`).

#### Deploying on Render
1. Create a **Web Service** on [Render.com](https://render.com).
2. Runtime: **Docker** (points to repository `Dockerfile`).
3. Set the Health Check Path to `/actuator/health`.
4. Attach environment variables from Section 2.
5. Render provides automatic SSL certificate provisioning and zero-downtime rolling deploys.

---

### 5. Option C: Dedicated Linux VPS (Ubuntu 22.04 / 24.04 LTS)

#### 1. Install Java 21 & MySQL
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk-headless mysql-server nginx certbot python3-certbot-nginx
```

#### 2. Configure Systemd Service
Create `/etc/systemd/system/disaster-management.service`:
```ini
[Unit]
Description=Disaster Management and Alert System
After=syslog.target network.target mysql.service

[Service]
User=www-data
Group=www-data
Type=simple
WorkingDirectory=/var/www/disaster-management
EnvironmentFile=/var/www/disaster-management/.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar /var/www/disaster-management/app.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable disaster-management
sudo systemctl start disaster-management
```

#### 3. Nginx Reverse Proxy with HTTPS / SSL
Create `/etc/nginx/sites-available/disaster-system.conf`:
```nginx
server {
    listen 80;
    server_name disaster.yourdomain.gov.in;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name disaster.yourdomain.gov.in;

    ssl_certificate /etc/letsencrypt/live/disaster.yourdomain.gov.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/disaster.yourdomain.gov.in/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

Issue Let's Encrypt SSL certificate:
```bash
sudo certbot --nginx -d disaster.yourdomain.gov.in
```

---

### 6. Health Checks, Telemetry & Maintenance

1. **Liveness & Readiness Probes:**
   - URL: `/actuator/health`
   - Returns components status: database connectivity (`MySQL`), disk space, SSL validity, and ping.
2. **NDMA Feed Telemetry:**
   - URL: `/api/official-alerts/status`
   - Returns live feed metrics: last sync timestamp, records imported, duplicates prevented, and active alert counts.
3. **Database Backup:**
   ```bash
   mysqldump -u root -p disaster_management > /var/backups/disaster_db_$(date +%F).sql
   ```
