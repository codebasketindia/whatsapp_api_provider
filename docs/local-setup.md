# Local Development Setup (no Docker)

## 1. Install
| Tool | Version | Ubuntu | macOS (Homebrew) | Windows |
|---|---|---|---|---|
| Java | 21 (LTS) | `sudo apt install openjdk-21-jdk` | `brew install openjdk@21` | Temurin 21 MSI |
| Maven | 3.9+ | `sudo apt install maven` | `brew install maven` | maven.apache.org zip |
| MySQL | 8.0 | `sudo apt install mysql-server` | `brew install mysql@8.0` | MySQL Installer 8.0 |
| Node.js | 22 LTS | NodeSource `setup_22.x` | `brew install node@22` | nodejs.org LTS |

## 2. Database
```bash
sudo mysql -e "CREATE DATABASE cbwa CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'cbwa_app'@'localhost' IDENTIFIED BY 'cbwa_dev_pass';
GRANT ALL ON cbwa.* TO 'cbwa_app'@'localhost';
CREATE DATABASE cbwa_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL ON cbwa_test.* TO 'cbwa_app'@'localhost'; FLUSH PRIVILEGES;"
```

## 3. Backend
```bash
cd backend
mvn spring-boot:run            # dev profile: http://localhost:8080
```
The **dev** profile has built-in development-only secrets and creates the super admin
`admin@cbwa.local` / `Admin@12345`. OTP codes are printed in the console (dev only) so you can test 2FA and
forgot-password without SMTP. Flyway creates all tables on first start.

Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health

Tests (real MySQL, uses the `cbwa_test` database; Testcontainers is used automatically when the variable is
not set and Docker is available):
```bash
CBWA_TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/cbwa_test mvn test
```

## 4. Frontend
```bash
cd frontend
cp .env.example .env           # VITE_API_BASE_URL=/api (proxied to :8080 by Vite)
npm install
npm run dev                    # http://localhost:5173
npm run build                  # production build in dist/
npx vitest run                 # unit tests
```

## 5. Trying Meta & Razorpay locally
Meta webhooks and Embedded Signup need a public HTTPS URL. Use a tunnel (e.g. `cloudflared tunnel --url
http://localhost:8080`) and set `CBWA_PUBLIC_BASE_URL` to the tunnel URL; register
`<tunnel>/api/webhooks/whatsapp` in the Meta app. For Razorpay use test keys and the test webhook URL.
