# 🌐 ReconEngine Enterprise — Deployment Guide

This project is packaged as a **Unified Full-Stack Deployment**.
The React frontend is pre-built and bundled directly inside the Spring Boot executable application.
This means:
- **1 single service to deploy** (No separate frontend hosting needed).
- **0 CORS errors** (Frontend and backend share the same origin).
- **Zero-config database** (H2 in-memory in PostgreSQL compatibility mode out-of-the-box, or connect any PostgreSQL database by supplying `DATABASE_URL`).
- **Dynamic Port support** (Works automatically with Render, Railway, Fly.io, AWS, Heroku).

---

## 🚀 Option 1: 1-Click Deployment on Render.com (Recommended & Free)

1. Push your code to GitHub (already completed).
2. Go to [https://dashboard.render.com/](https://dashboard.render.com/) and click **New +** ➔ **Web Service**.
3. Select your repository: `kantinilesh/Multi-Source-Payment-Reconciliation-Agent`.
4. Configure the service:
   - **Name**: `recon-engine` (or your choice)
   - **Region**: Any (e.g. Oregon or Frankfurt)
   - **Runtime**: **Docker** (Render will automatically detect `Dockerfile`)
   - **Instance Type**: **Free**
5. Click **Create Web Service**.
6. Render will automatically:
   - Build the React SPA with Node 20.
   - Build the Spring Boot application with Java 17.
   - Package everything into an Alpine container.
   - Launch your live website at `https://recon-engine-xxxx.onrender.com`!

---

## 🚂 Option 2: 1-Click Deployment on Railway.app

1. Go to [https://railway.app/new](https://railway.app/new).
2. Click **Deploy from GitHub repo**.
3. Select `kantinilesh/Multi-Source-Payment-Reconciliation-Agent`.
4. Railway will automatically detect `Dockerfile` / `railway.json` and start the deployment.
5. Once built, go to the service settings ➔ **Networking** ➔ Click **Generate Domain**.
6. Your app is live at `https://recon-engine-xxxx.up.railway.app`!

---

## 🐳 Option 3: Local or VPS Production Deployment with Docker

If you want to run the full production stack locally or on a VPS (DigitalOcean, AWS EC2, Linode):

```bash
# Build and run both the app and PostgreSQL in background:
docker compose up -d --build

# View live logs:
docker compose logs -f

# Open your browser:
http://localhost:8080
```

---

## 💻 Option 4: Local Standalone Production JAR (Without Docker)

You can also run the production JAR directly on any machine with Java 17+:

```bash
# 1. Build the production frontend:
cd frontend && npm install && npm run build && cd ..

# 2. Package into a single executable JAR:
mvn clean package -DskipTests

# 3. Run the application:
java -jar target/recon-backend-0.1.0.jar
```
Open `http://localhost:8080` in your browser! Both frontend and backend are live.
