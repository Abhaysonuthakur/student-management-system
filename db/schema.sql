-- ============================================================
-- Project 1: Student Management System
-- File   : db/schema.sql
-- Purpose: Create the database and the students table.
-- Run    : mysql -u root -p < db/schema.sql
-- ============================================================

-- 1. Create the database (a container that holds tables).
--    IF NOT EXISTS makes this script safe to run twice.
CREATE DATABASE IF NOT EXISTS student_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 2. Tell MySQL "all following statements apply to this database".
USE student_management;

-- 3. Drop the old table so this script can be re-run from scratch.
--    DANGER: this deletes all rows. Fine for learning, never for production.
DROP TABLE IF EXISTS students;

-- 4. Create the table that stores one row per student.
CREATE TABLE students (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    phone      VARCHAR(15)  NOT NULL,
    age        INT          NOT NULL,
    course     VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_students      PRIMARY KEY (id),
    CONSTRAINT uq_students_email UNIQUE (email),
    CONSTRAINT chk_students_age  CHECK (age BETWEEN 15 AND 100)
) ENGINE = InnoDB;
