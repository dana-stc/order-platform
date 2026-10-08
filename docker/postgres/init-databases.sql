-- One database per service: no service can read another one's tables (database-per-service).
CREATE DATABASE orders;
CREATE DATABASE inventory;
CREATE DATABASE payments;
CREATE DATABASE notifications;
