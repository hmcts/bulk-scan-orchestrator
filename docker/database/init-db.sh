#!/usr/bin/env bash

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE USER bulk_scan_orchestrator;
    CREATE DATABASE bulk_scan_orchestrator
        WITH OWNER = bulk_scan_orchestrator
        ENCODING ='UTF-8'
        CONNECTION LIMIT = -1;
EOSQL

psql -v ON_ERROR_STOP=1 --dbname=bulk_scan_orchestrator --username "$POSTGRES_USER" <<-EOSQL
    CREATE SCHEMA bulk_scan_orchestrator AUTHORIZATION bulk_scan_orchestrator;
EOSQL

psql -v ON_ERROR_STOP=1 --dbname=bulk_scan_orchestrator --username "$POSTGRES_USER" <<-EOSQL
    CREATE EXTENSION lo;
EOSQL
