DROP TABLE IF EXISTS prenotazioni CASCADE;
DROP TABLE IF EXISTS proiezioni CASCADE;
DROP TABLE IF EXISTS film CASCADE;
DROP TABLE IF EXISTS utenti CASCADE;

CREATE TABLE film (
    id_film SERIAL PRIMARY KEY,
    titolo VARCHAR(255) NOT NULL UNIQUE,
    genere VARCHAR(100) NOT NULL,
    regista VARCHAR(150) NOT NULL,
    anno INT NOT NULL CHECK (anno > 1880),
    durata_minuti INT NOT NULL CHECK (durata_minuti > 0),
    eta_minima INT NOT NULL DEFAULT 0 CHECK (eta_minima >= 0)
);

CREATE TABLE proiezioni (
    id_proiezione SERIAL PRIMARY KEY,
    id_film INT NOT NULL REFERENCES film(id_film) ON DELETE RESTRICT,
    data_ora_proiezione TIMESTAMP NOT NULL UNIQUE,
    prezzo_biglietto NUMERIC(6, 2) NOT NULL CHECK (prezzo_biglietto >= 0),
    posti_disponibili INT NOT NULL DEFAULT 200 CHECK (posti_disponibili >= 0 AND posti_disponibili <= 200)
);

CREATE TABLE utenti (
    username VARCHAR(50) PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cognome VARCHAR(100) NOT NULL,
    password_cifrata VARCHAR(255) NOT NULL,
    data_nascita DATE,
    luogo_domicilio VARCHAR(150) NOT NULL,
    ruolo VARCHAR(20) NOT NULL CHECK (ruolo IN ('CLIENTE', 'PROIEZIONISTA', 'BIGLIETTAIO'))
);

CREATE TABLE prenotazioni (
    codice_univoco VARCHAR(8) PRIMARY KEY,
    username_cliente VARCHAR(50) NOT NULL REFERENCES utenti(username) ON DELETE CASCADE,
    id_proiezione INT NOT NULL REFERENCES proiezioni(id_proiezione) ON DELETE RESTRICT,
    numero_biglietti INT NOT NULL CHECK (numero_biglietti > 0),
    costo_unitario NUMERIC(6, 2) NOT NULL,
    costo_totale NUMERIC(8, 2) NOT NULL
);

INSERT INTO utenti (username, nome, cognome, password_cifrata, data_nascita, luogo_domicilio, ruolo) VALUES
('proj1', 'Mario', 'Rossi', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1985-04-12', 'Varese', 'PROIEZIONISTA'),
('proj2', 'Luigi', 'Verdi', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1990-09-23', 'Como', 'PROIEZIONISTA'),
('bigliettaio1', 'Anna', 'Bianchi', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1995-01-15', 'Varese', 'BIGLIETTAIO'),
('bigliettaio2', 'Marco', 'Neri', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1992-07-08', 'Gallarate', 'BIGLIETTAIO'),
('bigliettaio3', 'Sara', 'Gialli', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', NULL, 'Busto Arsizio', 'BIGLIETTAIO'),
('bigliettaio4', 'Luca', 'Blu', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1998-11-30', 'Saronno', 'BIGLIETTAIO'),
('bigliettaio5', 'Elena', 'Viola', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', '1993-03-19', 'Como', 'BIGLIETTAIO');