# PRODUCTION CLOUD DEPLOYMENT & HOSTING GUIDE
## Disaster Management & Emergency Response System (Enterprise Full-Stack Edition)

**Target Environment:** Linux Container (Ubuntu 22.04 / 24.04 LTS) | Docker & Docker Compose  
**Runtime:** Eclipse Temurin OpenJDK 21 / 25 | MySQL 8.0 Engine  
**Security:** TLS 1.3 HTTPS, Reverse Proxy (Nginx / Cloudflare), Non-Root Container Execution  
**Status:** **DEPLOYMENT READY (Zero Open Dependencies)**

---

## 1. HOSTING ARCHITECTURE & PROVIDER EVALUATION

Before choosing a cloud provider, evaluators and deployment engineers must consider the 24/7 background nature of disaster alerting (continuous NDMA CAP 1.2 RSS polling and live GPS telemetry):

### Free-Tier vs Paid Production Realities:

| Hosting Provider | Tier / Plan | Estimated Monthly Cost | Disaster Management Compatibility & Critical Limitations | Recommendation |
| :--- | :--- | :---: | :--- | :---: |
| **Render** | Free Web Service | **$0** | **NOT RECOMMENDED:** Spins down after 15 minutes of inactivity. Cold start takes 50+ seconds. Background NDMA sync stops when inactive. Database limited to 1GB and deleted after 30 days. | Prototyping only |
| **Railway** | Starter / Trial | **$0 - $5** | **LIMITED:** 30-day $5 credit limit. Shuts down once credit is exhausted. | Testing only |
| **Hetzner Cloud / DigitalOcean** | Dedicated Linux VPS (2 vCPU, 4GB RAM) | **$6 - $12 / month** | **STRONGLY RECOMMENDED:** 24/7 continuous uptime, persistent SSD storage, dedicated IP, automated Let's Encrypt SSL, full Docker Compose support, zero cold starts. | **BEST VALUE** |
| **Railway (Hobby)** | Developer Paid Tier | **$5 - $10 / month** | **RECOMMENDED FOR EASY PAAS:** Automated Git deployment, managed MySQL with automated daily backups, zero server administration. | **EASIEST PAAS** |
| **AWS / Google Cloud** | Cloud Run / ECS + Cloud SQL | **$45 - $80 / month** | **ENTERPRISE SCALE:** Multi-zone high availability, automated database replication, enterprise SLA, auto-scaling under civilian SOS surges. | **ENTERPRISE** |

> [!IMPORTANT]
> **Production Recommendation:** For production deployment or final showcase demonstration, deploy using **Docker Compose** on a **$6-$12/month Linux VPS (Hetzner, DigitalOcean, or AWS EC2)**. This guarantees persistent MySQL data, uninterrupted NDMA RSS cron synchronization, and zero cold-start delays.

---

## 2. PRODUCTION ENVIRONMENT VARIABLES

The application reads configuration from environment variables defined in `.env` (or cloud secrets manager):

| Environment Variable | Production Example | Required? | Purpose & Description |
| :--- | :--- | :---: | :--- |
| `PORT` | `8080` | Yes | HTTP port the Spring Boot server listens on. |
| `SPRING_PROFILES_ACTIVE` | `prod` | Yes | Activates `application-prod.properties` profile. |
| `DB_URL` | `jdbc:mysql://mysql:3306/disaster_management?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC` | Yes | JDBC connection string to MySQL database. |
| `DB_USERNAME` | `disaster_admin` | Yes | MySQL database user with `CRUD` privileges. |
| `DB_PASSWORD` | `[STRONG_GENERATED_PASSWORD]` | Yes | Strong MySQL password (never commit to git). |
| `JWT_SECRET` | `[64_HEX_CHARACTERS_CRYPTOGRAPHIC_SECRET]` | Yes | HMAC-SHA256 signature secret for user authentication. |
| `JWT_EXPIRATION_MS` | `86400000` | No | Token lifetime in milliseconds (defaults to 24 hours). |
| `ADMIN_STEP_UP_MINUTES` | `15` | No | Lifetime of 2FA Step-Up authorization tokens. |
| `MEDIA_UPLOAD_DIR` | `/app/uploads/incident-media` | Yes | Persistent folder for civilian and responder photos. |
| `NDMA_RSS_URL` | `https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml` | Yes | Official Government of India CAP 1.2 RSS feed URL. |
| `NDMA_SYNC_ENABLED` | `true` | Yes | Activates background automated ingestion scheduler. |
| `NDMA_SYNC_INTERVAL_MS` | `300000` | No | Ingestion frequency (300,000 ms = every 5 minutes). |
| `OSRM_URL` | `https://router.project-osrm.org/route/v1/driving` | No | Turn-by-turn routing calculation API. |

---

## 3. DOCKER & DOCKER COMPOSE DEPLOYMENT

The project root contains a hardened multi-stage `Dockerfile` and `docker-compose.yml`:

### Step 1: Clone Repository & Create `.env`
On your production Linux server:
```bash
# Clone project repository
git clone <repository_url> /opt/disaster-management
cd /opt/disaster-management

# Create production environment file from template
cp .env.example .env
nano .env
```

Ensure `.env` contains secure values:
```ini
DB_PASSWORD=SecureProductionPassword2026!
JWT_SECRET=c3f1e98d249a0b168923f81e3540a97c413b5d2e7a08b9c6d123456789abcdef
PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### Step 2: Build and Launch Containers
```bash
# Launch containers in background with auto-restart
docker compose up -d --build
```

### Step 3: Verify Container Health
```bash
# Check running container statuses
docker compose ps

# Check Spring Boot live logs
docker compose logs -f app

# Probe actuator health check
curl -f http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

---

## 4. HTTPS & NGINX REVERSE PROXY CONFIGURATION

In a live public deployment, Nginx handles SSL/TLS termination, HTTP/2 multiplexing, and WebSocket/SSE proxy buffering:

### `/etc/nginx/sites-available/disaster-management`
```nginx
server {
    listen 80;
    server_name disaster.example.gov.in;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name disaster.example.gov.in;

    # SSL Certificates (managed via Certbot / Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/disaster.example.gov.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/disaster.example.gov.in/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Max Upload Size (for incident photo/video evidence)
    client_max_body_size 55M;

    # Root Reverse Proxy to Spring Boot Container
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # CRITICAL: Disable Buffering for Server-Sent Events (SSE) Live Telemetry
    location /api/tracking/stream/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # SSE streaming directives
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_read_timeout 86400s;
    }
}
```

Enable the configuration and obtain free SSL via Certbot:
```bash
sudo ln -s /etc/nginx/sites-available/disaster-management /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d disaster.example.gov.in
```

---

## 5. DATABASE MIGRATIONS & INITIALIZATION

* **Initial Schema Setup:** `DATABASE_SETUP.sql` contains table definitions, indices, and constraints.
* **Docker Initialization:** When `docker-compose up` runs for the first time, MySQL executes `DATABASE_SETUP.sql` automatically via the `/docker-entrypoint-initdb.d/` volume mount.
* **Hibernate Entity Evolution:** In `application-prod.properties`, `spring.jpa.hibernate.ddl-auto=update` is enabled. Spring Boot automatically manages non-destructive column additions (such as `availability_status` on `users`) without dropping existing disaster records.

---

## 6. PERSISTENT MEDIA STORAGE & FILE UPLOADS

* Civilian distress photos and responder field evidence are saved to the persistent volume `uploads/incident-media`.
* In `docker-compose.yml`, this directory is mapped to the host file system:
  ```yaml
  volumes:
    - ./uploads:/app/uploads
  ```
* This ensures that images and videos remain intact even if containers are destroyed, updated, or rebuilt.

---

## 7. AUTOMATED BACKUP & DISASTER RECOVERY

### Automated Daily Database Backup Script (`/opt/backup-disaster-db.sh`)
```bash
#!/bin/bash
BACKUP_DIR="/var/backups/disaster_db"
DATE=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"

# Execute compressed MySQL dump from inside container
docker exec disaster_mysql_db mysqldump -u root -p"$DB_PASSWORD" disaster_management | gzip > "$BACKUP_DIR/disaster_db_$DATE.sql.gz"

# Retain backups for 14 days; purge older files
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +14 -exec rm {} \;

echo "Backup completed: $BACKUP_DIR/disaster_db_$DATE.sql.gz"
```

Configure in cron:
```bash
# Run backup daily at 02:00 AM
0 2 * * * /opt/backup-disaster-db.sh >> /var/log/backup.log 2>&1
```

### Database Restoration Procedure
```bash
# To restore from a backup:
gunzip < /var/backups/disaster_db/disaster_db_20261101.sql.gz | docker exec -i disaster_mysql_db mysql -u root -p"$DB_PASSWORD" disaster_management
```
* **Recovery Time Objective (RTO):** $< 15$ minutes.
* **Recovery Point Objective (RPO):** $< 24$ hours (or $< 1$ hour with binary logs enabled).

---

## 8. MONITORING, LOGGING & HEALTH METRICS

* **Health Probe:** `GET /actuator/health` returns status code `200 OK` when the database, disk, and connection pools are operational.
* **Container Health:** The Docker daemon probes `http://localhost:8080/actuator/health` every 30 seconds. If 3 consecutive failures occur, the container is marked unhealthy and restarted.
* **Uptime Monitoring:** Configure an external uptime monitor (such as Uptime Kuma or Better Stack) to ping `https://disaster.example.gov.in/actuator/health` every 60 seconds.

