-- Script de inicialización de la base de datos CRASA
CREATE DATABASE IF NOT EXISTS CRASA;
USE CRASA;

-- Tabla de familias de productos
CREATE TABLE IF NOT EXISTS families (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mark_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de marcas
CREATE TABLE IF NOT EXISTS marks (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de compañías
CREATE TABLE IF NOT EXISTS companies (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de usuarios/vendedores
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(50) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de clientes
CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(255) PRIMARY KEY,
    customer_code VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    vendedor_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (vendedor_id) REFERENCES users(id)
);

-- Tabla de productos
CREATE TABLE IF NOT EXISTS products (
    code VARCHAR(255) PRIMARY KEY,
    description TEXT,
    price DECIMAL(15,2),
    family_id VARCHAR(255) NOT NULL,
    mark_id VARCHAR(255),
    company_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (mark_id) REFERENCES marks(id),
    FOREIGN KEY (company_id) REFERENCES companies(id)
);

-- Tabla de archivos procesados
CREATE TABLE IF NOT EXISTS archivo_procesado (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) UNIQUE NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    fecha DATETIME NOT NULL
);

-- Tabla de ventas
CREATE TABLE IF NOT EXISTS venta (
    id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(15,2) NOT NULL,
    total DECIMAL(15,2) NOT NULL,
    fecha DATETIME NOT NULL,
    archivo_id BIGINT,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (product_id) REFERENCES products(code),
    FOREIGN KEY (archivo_id) REFERENCES archivo_procesado(id)
);

-- Tabla de relación muchos a muchos entre customers y products
CREATE TABLE IF NOT EXISTS customer_products (
    customer_id VARCHAR(255),
    product_id VARCHAR(255),
    PRIMARY KEY (customer_id, product_id),
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (product_id) REFERENCES products(code)
);

-- Insertar datos iniciales
INSERT IGNORE INTO families (id, name) VALUES ('GEN001', 'General');
INSERT IGNORE INTO marks (id, name) VALUES ('GENERAL', 'General');
INSERT IGNORE INTO companies (id, name) VALUES ('GENERAL', 'General');
INSERT IGNORE INTO users (id, name, role) VALUES ('ADMIN001', 'Administrador', 'ADMIN');