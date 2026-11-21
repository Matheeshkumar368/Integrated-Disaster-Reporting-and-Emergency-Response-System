<!-- 3D DYNAMIC HEADER BANNER -->
<div align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:0f2027,50:203a43,100:2c5364&height=260&section=header&text=DISASTER%20RESPONSE%20SYSTEM&fontSize=38&fontColor=00f2fe&animation=fadeIn&fontAlignY=38&desc=Integrated%20Disaster%20Reporting%20%26%20Emergency%20Response%20Coordination&descAlignY=58&descSize=16&descColor=ffffff" width="100%" />
</div>

<!-- 3D TYPING ANIMATION SUBTITLE -->
<div align="center">
  <a href="https://github.com/Matheeshkumar368/Integrated-Disaster-Reporting-and-Emergency-Response-System">
    <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=20&duration=3000&pause=1000&color=00F2FE&center=true&vCenter=true&multiline=true&width=750&height=70&lines=Multi-Role+Emergency+Management+%7C+Real-Time+GPS+Telemetry;Spring+Boot+3.4+%7C+Java+21+LTS+%7C+MySQL+8.0+%7C+NDMA+SACHET+CAP" alt="Typing SVG" />
  </a>
</div>

<!-- 3D ELEVATED TECH STACK BADGES -->
<div align="center">
  <img src="https://img.shields.io/badge/Build-Passing-00e676?style=for-the-badge&logo=githubactions&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-21%20LTS-ff6d00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4%20%2F%204.0-6db33f?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/MySQL-8.0-00758f?style=for-the-badge&logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/Security-JWT%20%2B%202FA%20PIN-d50000?style=for-the-badge&logo=springsecurity&logoColor=white" />
  <img src="https://img.shields.io/badge/NDMA%20SACHET-Live%20CAP%20Feed-00b0ff?style=for-the-badge&logo=rss&logoColor=white" />
</div>

<br/>

<!-- 3D HERO SHOWCASE CARD (Upload your generated 3D image to your repo and link it here!) -->
<div align="center">
  <table>
    <tr>
      <td align="center">
        <!-- Replace the src below with the path to your uploaded 3D diorama image, e.g., docs/3d-architecture-showcase.png -->
        <img src="docs/3d-architecture-showcase.png" alt="3D System Architecture Diorama" width="85%" style="border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);" />
        <br/>
        <sub><b>🧊 3D Ecosystem Overview:</b> Citizen Distress PWA ➔ Field Responder GPS Telemetry ➔ Central NDMA Command</sub>
      </td>
    </tr>
  </table>
</div>

---

## 🌐 1. Project Overview
The **Integrated Disaster Reporting and Emergency Response Coordination System** is a unified full-stack web and Progressive Web Application (PWA) built on **Java 21**, **Spring Boot**, **MySQL 8.0**, and **Leaflet GIS** to bridge critical communication gaps during catastrophic natural disasters and civilian emergencies.

---

## 🚨 2. Problem Statement vs. 3D Ecosystem Solution

<table>
  <tr>
    <th width="50%">⚠️ Traditional Bottlenecks</th>
    <th width="50%">🛡️ Our Real-Time Solution</th>
  </tr>
  <tr>
    <td><b>Civilian Vulnerability:</b> Trapped citizens struggle to send exact GPS coordinates and media evidence.</td>
    <td><b>Dual-Action Emergency SOS:</b> 600Hz–1200Hz Web Audio warble siren + locked hardware GPS beacon transmission.</td>
  </tr>
  <tr>
    <td><b>Fragmented Dispatch:</b> Control rooms lack real-time visibility into responder proximity and workload.</td>
    <td><b>Smart Dispatch Engine:</b> AI-assisted proximity heuristics matching zone specialization and responder status.</td>
  </tr>
  <tr>
    <td><b>Stale Telemetry:</b> Units operate with static coordinates without live victim routing.</td>
    <td><b>Live Hardware GPS Radar:</b> Continuous <code>watchPosition()</code> streaming with OSRM turn-by-turn routing.</td>
  </tr>
  <tr>
    <td><b>Delayed Alerts:</b> National meteorological warnings remain siloed from affected citizens.</td>
    <td><b>NDMA SACHET CAP Sync:</b> Automated 5-minute XML RSS ingestion cataloging live state-level alerts.</td>
  </tr>
</table>

---

## 👥 3. Three-Tier Role Architecture

<div align="center">
<table>
  <tr>
    <td align="center" width="33%">
      <h3>📱 Citizen</h3>
      <code>ROLE_Citizen</code>
      <hr/>
      <p align="left">
        • Geotagged photo/video distress reporting<br/>
        • Audible SOS siren & silent GPS beacon<br/>
        • Live responder tracking on Safety Radar<br/>
        • Offline-ready NDMA/NDRF survival videos
      </p>
    </td>
    <td align="center" width="33%">
      <h3>🚑 Responder</h3>
      <code>ROLE_Responder</code>
      <hr/>
      <p align="left">
        • Zone-isolated field operations console<br/>
        • Live GPS, speed, heading & battery stream<br/>
        • 5-Stage Mission Lifecycle tracker<br/>
        • Duty mode switching with load safeguards
      </p>
    </td>
    <td align="center" width="33%">
      <h3>🛰️ Command Admin</h3>
      <code>ROLE_Admin</code>
      <hr/>
      <p align="left">
        • Central Live Sector Operations Radar<br/>
        • Smart responder dispatch & assignment<br/>
        • NDMA SACHET ingestion & broadcasts<br/>
        • 6-digit Step-Up 2FA Security PIN
      </p>
    </td>
  </tr>
</table>
</div>

---

## 🏗️ 4. System Architecture Flow

```mermaid
flowchart TD
    subgraph Clients ["🌐 Multi-Role Client Layer (PWA / Web)"]
        C1["📱 Citizen Portal<br/>(SOS & Reporting)"]
        C2["🚑 Responder Console<br/>(Live GPS Telemetry)"]
        C3["🛰️ Admin Command<br/>(Central Radar & 2FA)"]
    end

    subgraph Security ["🔐 Security & API Gateway"]
        SEC["Spring Security + JWT (HMAC SHA-256) + Step-Up 2FA PIN"]
    end

    subgraph Services ["⚙️ Spring Boot 3.4 / Java 21 Core Services"]
        S1["CitizenReportService & Spatial Deduplicator (150m)"]
        S2["ResponderTrackingService & OSRM Router"]
        S3["SmartAssignmentService & Proximity Engine"]
        S4["NdmaSyncService (Scheduled CAP v1.2 Cron)"]
    end

    subgraph Data ["🗄️ Persistence & External Feeds"]
        DB[("MySQL 8.0<br/>(HikariCP Pool)")]
        EXT["☁️ NDMA SACHET RSS & OSRM Engine"]
    end

    C1 & C2 & C3 -->|HTTPS / REST| SEC
    SEC --> S1 & S2 & S3 & S4
    S1 & S2 & S3 & S4 <--> DB
    S2 & S4 <--> EXT
