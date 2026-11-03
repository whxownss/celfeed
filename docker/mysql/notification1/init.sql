-- mysqld-exporter 계정 생성 및 권한 부여
CREATE USER 'exporter'@'%' IDENTIFIED BY 'exporter_pw';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;