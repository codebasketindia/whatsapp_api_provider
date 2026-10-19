-- Run as MySQL root:  sudo mysql < deploy/mysql/setup.sql
-- Then set a strong password:  ALTER USER 'cbwa_app'@'localhost' IDENTIFIED BY '...';
CREATE DATABASE IF NOT EXISTS cbwa CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'cbwa_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON cbwa.* TO 'cbwa_app'@'localhost';
-- Backup user (read-only + lock for consistent dumps)
CREATE USER IF NOT EXISTS 'cbwa_backup'@'localhost' IDENTIFIED BY 'CHANGE_ME_BACKUP_PASSWORD';
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES, EVENT, PROCESS ON *.* TO 'cbwa_backup'@'localhost';
FLUSH PRIVILEGES;
