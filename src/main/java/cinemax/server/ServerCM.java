/*
Costantini Marco , 762497 , VA
Colombo Davide , 760761 , VA
Bonza Lorenzo , 760667 , VA
Piloni Luca , 762374 , VA
*/
package cinemax.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server centrale dell'applicazione CineMax.
 * Gestisce richieste concorrenti da client tramite Socket TCP e interagisce
 * con il database PostgreSQL dbCM garantendo consistenza transazionale.
 */
public class ServerCM {

    private static final int DEFAULT_PORT = 12345;
    private static final String ALFANUMERICO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    /**
     * Punto di ingresso del server CineMax.
     * Richiede i parametri di connessione a PostgreSQL e avvia il ServerSocket.
     *
     * @param args argomenti da riga di comando (non utilizzati)
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== AVVIO SERVER CINEMAX ===");
        System.out.print("Host PostgreSQL (invio per localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        System.out.print("Username PostgreSQL (invio per postgres): ");
        dbUser = scanner.nextLine().trim();
        if (dbUser.isEmpty()) dbUser = "postgres";

        System.out.print("Password PostgreSQL: ");
        dbPassword = scanner.nextLine().trim();

        System.out.print("Porta TCP Server (invio per 12345): ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? DEFAULT_PORT : Integer.parseInt(portStr);

        dbUrl = "jdbc:postgresql://" + host + ":5432/dbCM";

        // Verifica preliminare della connessione al database
        try (Connection testConn = getConnection()) {
            System.out.println("Connessione a PostgreSQL (dbCM) riuscita!");
        } catch (SQLException e) {
            System.err.println("Errore di connessione al database: " + e.getMessage());
            return;
        }

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ServerCM in ascolto sulla porta " + port + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuovo client connesso: " + clientSocket.getRemoteSocketAddress());
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Errore del ServerSocket: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Crea una nuova connessione JDBC verso il database dbCM.
     *
     * @return oggetto Connection attivo
     * @throws SQLException in caso di errore di connessione
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Calcola l'hash SHA-256 esadecimale di una stringa.
     *
     * @param input testo in chiaro
     * @return stringa esadecimale SHA-256
     */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Errore algoritmo SHA-256", e);
        }
    }

    /**
     * Genera un codice univoco alfanumerico di 8 caratteri per le prenotazioni.
     *
     * @return codice di 8 caratteri maiuscoli
     */
    private static String generaCodiceUnivoco() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALFANUMERICO.charAt(RANDOM.nextInt(ALFANUMERICO.length())));
        }
        return sb.toString();
    }

    /**
     * Gestore dedicato a ciascun client connesso al server.
     */
    private static class ClientHandler implements Runnable {

        private final Socket socket;

        /**
         * Costruisce un handler per il socket del client specificato.
         *
         * @param socket socket del client connesso
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                String richiesta;
                while ((richiesta = in.readLine()) != null) {
                    if (richiesta.equalsIgnoreCase("QUIT")) {
                        out.println("OK|Arrivederci");
                        break;
                    }
                    elaboraRichiesta(richiesta, out);
                }
            } catch (IOException e) {
                System.out.println("Client disconnesso: " + socket.getRemoteSocketAddress());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void elaboraRichiesta(String richiesta, PrintWriter out) {
            String[] parti = richiesta.split("\\|", -1);
            String comando = parti[0].toUpperCase();

            try {
                switch (comando) {
                    case "LOGIN":
                        gestisciLogin(parti, out);
                        break;
                    case "REGISTER":
                        gestisciRegistrazione(parti, out);
                        break;
                    case "CERCA_PROIEZIONI":
                        gestisciCercaProiezioni(parti, out);
                        break;
                    case "PRENOTA":
                        gestisciPrenotazione(parti, out);
                        break;
                    case "MIE_PRENOTAZIONI":
                        gestisciMiePrenotazioni(parti, out);
                        break;
                    case "CANCELLA_PRENOTAZIONE":
                        gestisciCancellaPrenotazione(parti, out);
                        break;
                    case "AGGIUNGI_PROIEZIONE":
                        gestisciAggiungiProiezione(parti, out);
                        break;
                    case "ELIMINA_PROIEZIONE":
                        gestisciEliminaProiezione(parti, out);
                        break;
                    case "VERIFICA_CODICE":
                        gestisciVerificaCodice(parti, out);
                        break;
                    case "STATISTICHE_PROIEZIONE":
                        gestisciStatisticheProiezione(parti, out);
                        break;
                    default:
                        out.println("ERR|Comando non riconosciuto: " + comando);
                }
            } catch (Exception e) {
                out.println("ERR|Errore interno server: " + e.getMessage());
            }
        }

        private void gestisciLogin(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 3) {
                out.println("ERR|Parametri login mancanti");
                return;
            }
            String username = parti[1].trim();
            String passwordHash = sha256(parti[2]);

            String sql = "SELECT ruolo, nome, cognome FROM utenti WHERE username = ? AND password_cifrata = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, passwordHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.println("OK|" + rs.getString("ruolo") + "|" + rs.getString("nome") + "|" + rs.getString("cognome"));
                    } else {
                        out.println("ERR|Credenziali non valide");
                    }
                }
            }
        }

        private void gestisciRegistrazione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 7) {
                out.println("ERR|Parametri registrazione mancanti");
                return;
            }
            String username = parti[1].trim();
            String nome = parti[2].trim();
            String cognome = parti[3].trim();
            String passwordHash = sha256(parti[4]);
            String dataNascitaStr = parti[5].trim();
            String domicilio = parti[6].trim();

            if (username.isEmpty() || nome.isEmpty() || cognome.isEmpty() || parti[4].isEmpty() || domicilio.isEmpty()) {
                out.println("ERR|Compilare tutti i campi obbligatori");
                return;
            }

            String sql = "INSERT INTO utenti (username, nome, cognome, password_cifrata, data_nascita, luogo_domicilio, ruolo) "
                       + "VALUES (?, ?, ?, ?, ?, ?, 'CLIENTE')";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, nome);
                ps.setString(3, cognome);
                ps.setString(4, passwordHash);
                if (dataNascitaStr.isEmpty()) {
                    ps.setNull(5, java.sql.Types.DATE);
                } else {
                    ps.setDate(5, Date.valueOf(dataNascitaStr));
                }
                ps.setString(6, domicilio);
                ps.executeUpdate();
                out.println("OK|Registrazione completata con successo");
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    out.println("ERR|Username gia esistente");
                } else {
                    out.println("ERR|Errore registrazione: " + e.getMessage());
                }
            } catch (IllegalArgumentException e) {
                out.println("ERR|Formato data non valido (usare YYYY-MM-DD)");
            }
        }

        private void gestisciCercaProiezioni(String[] parti, PrintWriter out) throws SQLException {
            String titolo = parti.length > 1 ? parti[1].trim() : "";
            String genere = parti.length > 2 ? parti[2].trim() : "";
            String data = parti.length > 3 ? parti[3].trim() : "";
            boolean soloFuture = parti.length > 4 && "1".equals(parti[4].trim());

            StringBuilder sql = new StringBuilder(
                "SELECT p.id_proiezione, f.titolo, f.genere, f.regista, f.anno, f.durata_minuti, f.eta_minima, " +
                "p.data_ora_proiezione, p.prezzo_biglietto, p.posti_disponibili " +
                "FROM proiezioni p JOIN film f ON p.id_film = f.id_film WHERE 1=1 "
            );

            if (!titolo.isEmpty()) sql.append("AND LOWER(f.titolo) LIKE LOWER(?) ");
            if (!genere.isEmpty()) sql.append("AND LOWER(f.genere) LIKE LOWER(?) ");
            if (!data.isEmpty()) sql.append("AND DATE(p.data_ora_proiezione) = ? ");
            if (soloFuture) sql.append("AND p.data_ora_proiezione >= CURRENT_TIMESTAMP ");

            sql.append("ORDER BY p.data_ora_proiezione ASC LIMIT 100");

            List<String> righe = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (!titolo.isEmpty()) ps.setString(idx++, "%" + titolo + "%");
                if (!genere.isEmpty()) ps.setString(idx++, "%" + genere + "%");
                if (!data.isEmpty()) ps.setDate(idx++, Date.valueOf(data));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String riga = String.join(";",
                            String.valueOf(rs.getInt("id_proiezione")),
                            rs.getString("titolo").replace(";", ","),
                            rs.getString("genere").replace(";", ","),
                            rs.getString("regista").replace(";", ","),
                            String.valueOf(rs.getInt("anno")),
                            String.valueOf(rs.getInt("durata_minuti")),
                            String.valueOf(rs.getInt("eta_minima")),
                            rs.getTimestamp("data_ora_proiezione").toString(),
                            rs.getBigDecimal("prezzo_biglietto").toPlainString(),
                            String.valueOf(rs.getInt("posti_disponibili"))
                        );
                        righe.add(riga);
                    }
                }
            } catch (IllegalArgumentException e) {
                out.println("ERR|Formato data non valido (usare YYYY-MM-DD)");
                return;
            }

            out.println("OK|" + righe.size());
            for (String r : righe) {
                out.println(r);
            }
        }

        private void gestisciPrenotazione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 4) {
                out.println("ERR|Parametri prenotazione mancanti");
                return;
            }
            String username = parti[1].trim();
            int idProiezione = Integer.parseInt(parti[2].trim());
            int numBiglietti = Integer.parseInt(parti[3].trim());

            if (numBiglietti <= 0) {
                out.println("ERR|Il numero di biglietti deve essere maggiore di zero");
                return;
            }

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Recupera data di nascita del cliente
                    Date dataNascita = null;
                    try (PreparedStatement psUser = conn.prepareStatement("SELECT data_nascita FROM utenti WHERE username = ?")) {
                        psUser.setString(1, username);
                        try (ResultSet rsUser = psUser.executeQuery()) {
                            if (!rsUser.next()) {
                                conn.rollback();
                                out.println("ERR|Utente non trovato");
                                return;
                            }
                            dataNascita = rsUser.getDate("data_nascita");
                        }
                    }

                    // 2. Blocca la riga della proiezione per evitare overbooking concorrente
                    String sqlLock = "SELECT p.posti_disponibili, p.prezzo_biglietto, p.data_ora_proiezione, f.eta_minima "
                                   + "FROM proiezioni p JOIN film f ON p.id_film = f.id_film "
                                   + "WHERE p.id_proiezione = ? FOR UPDATE OF p";
                    int postiDisponibili;
                    double prezzoUnitario;
                    Timestamp dataOraProj;
                    int etaMinima;

                    try (PreparedStatement psLock = conn.prepareStatement(sqlLock)) {
                        psLock.setInt(1, idProiezione);
                        try (ResultSet rs = psLock.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                out.println("ERR|Proiezione inesistente");
                                return;
                            }
                            postiDisponibili = rs.getInt("posti_disponibili");
                            prezzoUnitario = rs.getDouble("prezzo_biglietto");
                            dataOraProj = rs.getTimestamp("data_ora_proiezione");
                            etaMinima = rs.getInt("eta_minima");
                        }
                    }

                    // 3. Controllo eta minima
                    if (etaMinima > 0) {
                        if (dataNascita == null) {
                            conn.rollback();
                            out.println("ERR|Data di nascita obbligatoria per film VM" + etaMinima);
                            return;
                        }
                        LocalDate nascita = dataNascita.toLocalDate();
                        LocalDate dataFilm = dataOraProj.toLocalDateTime().toLocalDate();
                        int eta = Period.between(nascita, dataFilm).getYears();
                        if (eta < etaMinima) {
                            conn.rollback();
                            out.println("ERR|Eta minima non raggiunta (richiesti " + etaMinima + " anni)");
                            return;
                        }
                    }

                    // 4. Controllo disponibilita posti
                    if (postiDisponibili < numBiglietti) {
                        conn.rollback();
                        out.println("ERR|Posti insufficienti (disponibili: " + postiDisponibili + ")");
                        return;
                    }

                    // 5. Scala i posti disponibili
                    try (PreparedStatement psUpdate = conn.prepareStatement(
                            "UPDATE proiezioni SET posti_disponibili = posti_disponibili - ? WHERE id_proiezione = ?")) {
                        psUpdate.setInt(1, numBiglietti);
                        psUpdate.setInt(2, idProiezione);
                        psUpdate.executeUpdate();
                    }

                    // 6. Genera codice univoco e inserisce prenotazione
                    String codice = generaCodiceUnivoco();
                    double costoTotale = Math.round(prezzoUnitario * numBiglietti * 100.0) / 100.0;

                    String sqlInsert = "INSERT INTO prenotazioni (codice_univoco, username_cliente, id_proiezione, numero_biglietti, costo_unitario, costo_totale) "
                                     + "VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement psIns = conn.prepareStatement(sqlInsert)) {
                        psIns.setString(1, codice);
                        psIns.setString(2, username);
                        psIns.setInt(3, idProiezione);
                        psIns.setInt(4, numBiglietti);
                        psIns.setDouble(5, prezzoUnitario);
                        psIns.setDouble(6, costoTotale);
                        psIns.executeUpdate();
                    }

                    conn.commit();
                    out.println("OK|" + codice + "|" + String.format("%.2f", costoTotale));
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }

        private void gestisciMiePrenotazioni(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 2) {
                out.println("ERR|Username mancante");
                return;
            }
            String username = parti[1].trim();
            String sql = "SELECT pr.codice_univoco, f.titolo, p.data_ora_proiezione, pr.numero_biglietti, pr.costo_unitario, pr.costo_totale "
                       + "FROM prenotazioni pr "
                       + "JOIN proiezioni p ON pr.id_proiezione = p.id_proiezione "
                       + "JOIN film f ON p.id_film = f.id_film "
                       + "WHERE pr.username_cliente = ? ORDER BY p.data_ora_proiezione DESC";

            List<String> righe = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        righe.add(String.join(";",
                            rs.getString("codice_univoco"),
                            rs.getString("titolo").replace(";", ","),
                            rs.getTimestamp("data_ora_proiezione").toString(),
                            String.valueOf(rs.getInt("numero_biglietti")),
                            rs.getBigDecimal("costo_unitario").toPlainString(),
                            rs.getBigDecimal("costo_totale").toPlainString()
                        ));
                    }
                }
            }

            out.println("OK|" + righe.size());
            for (String r : righe) {
                out.println(r);
            }
        }

        private void gestisciCancellaPrenotazione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 3) {
                out.println("ERR|Parametri cancellazione mancanti");
                return;
            }
            String username = parti[1].trim();
            String codice = parti[2].trim().toUpperCase();

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int idProiezione;
                    int numBiglietti;
                    String sqlSelect = "SELECT id_proiezione, numero_biglietti FROM prenotazioni WHERE codice_univoco = ? AND username_cliente = ? FOR UPDATE";
                    try (PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
                        ps.setString(1, codice);
                        ps.setString(2, username);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                out.println("ERR|Prenotazione non trovata per questo utente");
                                return;
                            }
                            idProiezione = rs.getInt("id_proiezione");
                            numBiglietti = rs.getInt("numero_biglietti");
                        }
                    }

                    try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM prenotazioni WHERE codice_univoco = ?")) {
                        psDel.setString(1, codice);
                        psDel.executeUpdate();
                    }

                    try (PreparedStatement psUpd = conn.prepareStatement(
                            "UPDATE proiezioni SET posti_disponibili = posti_disponibili + ? WHERE id_proiezione = ?")) {
                        psUpd.setInt(1, numBiglietti);
                        psUpd.setInt(2, idProiezione);
                        psUpd.executeUpdate();
                    }

                    conn.commit();
                    out.println("OK|Prenotazione " + codice + " cancellata e " + numBiglietti + " posti ripristinati");
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }

        private void gestisciAggiungiProiezione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 9) {
                out.println("ERR|Parametri proiezione mancanti");
                return;
            }
            String titolo = parti[1].trim();
            String genere = parti[2].trim();
            String regista = parti[3].trim();
            int anno = Integer.parseInt(parti[4].trim());
            int durata = Integer.parseInt(parti[5].trim());
            int etaMinima = Integer.parseInt(parti[6].trim());
            String dataOraStr = parti[7].trim();
            if (dataOraStr.length() == 16) dataOraStr += ":00";
            double prezzo = Double.parseDouble(parti[8].trim());

            String sqlFilm = "INSERT INTO film (titolo, genere, regista, anno, durata_minuti, eta_minima) "
                           + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (titolo) DO UPDATE SET titolo = EXCLUDED.titolo RETURNING id_film";
            String sqlProj = "INSERT INTO proiezioni (id_film, data_ora_proiezione, prezzo_biglietto, posti_disponibili) "
                           + "VALUES (?, ?, ?, 200) RETURNING id_proiezione";

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int idFilm;
                    try (PreparedStatement psFilm = conn.prepareStatement(sqlFilm)) {
                        psFilm.setString(1, titolo);
                        psFilm.setString(2, genere);
                        psFilm.setString(3, regista);
                        psFilm.setInt(4, anno);
                        psFilm.setInt(5, durata);
                        psFilm.setInt(6, etaMinima);
                        try (ResultSet rs = psFilm.executeQuery()) {
                            rs.next();
                            idFilm = rs.getInt(1);
                        }
                    }

                    int idProj;
                    try (PreparedStatement psProj = conn.prepareStatement(sqlProj)) {
                        psProj.setInt(1, idFilm);
                        psProj.setTimestamp(2, Timestamp.valueOf(dataOraStr));
                        psProj.setDouble(3, prezzo);
                        try (ResultSet rs = psProj.executeQuery()) {
                            rs.next();
                            idProj = rs.getInt(1);
                        }
                    }

                    conn.commit();
                    out.println("OK|Proiezione inserita con ID " + idProj);
                } catch (SQLException e) {
                    conn.rollback();
                    if ("23505".equals(e.getSQLState())) {
                        out.println("ERR|Esiste gia una proiezione programmata in questa data e ora");
                    } else {
                        out.println("ERR|Errore inserimento proiezione: " + e.getMessage());
                    }
                } catch (IllegalArgumentException e) {
                    conn.rollback();
                    out.println("ERR|Formato data/ora non valido (usare YYYY-MM-DD HH:MM:SS)");
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }

        private void gestisciEliminaProiezione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 2) {
                out.println("ERR|ID proiezione mancante");
                return;
            }
            int idProiezione = Integer.parseInt(parti[1].trim());
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM proiezioni WHERE id_proiezione = ?")) {
                ps.setInt(1, idProiezione);
                int righe = ps.executeUpdate();
                if (righe > 0) {
                    out.println("OK|Proiezione " + idProiezione + " eliminata");
                } else {
                    out.println("ERR|Proiezione non trovata");
                }
            } catch (SQLException e) {
                if ("23503".equals(e.getSQLState())) {
                    out.println("ERR|Impossibile eliminare: esistono gia prenotazioni per questa proiezione");
                } else {
                    out.println("ERR|Errore eliminazione: " + e.getMessage());
                }
            }
        }

        private void gestisciVerificaCodice(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 2) {
                out.println("ERR|Codice prenotazione mancante");
                return;
            }
            String codice = parti[1].trim().toUpperCase();
            String sql = "SELECT pr.codice_univoco, u.username, u.nome, u.cognome, f.titolo, p.data_ora_proiezione, pr.numero_biglietti, pr.costo_totale "
                       + "FROM prenotazioni pr "
                       + "JOIN utenti u ON pr.username_cliente = u.username "
                       + "JOIN proiezioni p ON pr.id_proiezione = p.id_proiezione "
                       + "JOIN film f ON p.id_film = f.id_film "
                       + "WHERE pr.codice_univoco = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codice);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String info = String.join(";",
                            rs.getString("codice_univoco"),
                            rs.getString("username"),
                            rs.getString("nome") + " " + rs.getString("cognome"),
                            rs.getString("titolo").replace(";", ","),
                            rs.getTimestamp("data_ora_proiezione").toString(),
                            String.valueOf(rs.getInt("numero_biglietti")),
                            rs.getBigDecimal("costo_totale").toPlainString()
                        );
                        out.println("OK|" + info);
                    } else {
                        out.println("ERR|Codice prenotazione non valido o inesistente");
                    }
                }
            }
        }

        private void gestisciStatisticheProiezione(String[] parti, PrintWriter out) throws SQLException {
            if (parti.length < 2) {
                out.println("ERR|ID proiezione mancante");
                return;
            }
            int idProiezione = Integer.parseInt(parti[1].trim());
            String sql = "SELECT f.titolo, p.data_ora_proiezione, p.posti_disponibili, "
                       + "COALESCE(SUM(pr.numero_biglietti), 0) AS venduti, "
                       + "COALESCE(SUM(pr.costo_totale), 0.00) AS incasso "
                       + "FROM proiezioni p "
                       + "JOIN film f ON p.id_film = f.id_film "
                       + "LEFT JOIN prenotazioni pr ON p.id_proiezione = pr.id_proiezione "
                       + "WHERE p.id_proiezione = ? "
                       + "GROUP BY f.titolo, p.data_ora_proiezione, p.posti_disponibili";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idProiezione);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String info = String.join(";",
                            rs.getString("titolo").replace(";", ","),
                            rs.getTimestamp("data_ora_proiezione").toString(),
                            String.valueOf(rs.getInt("posti_disponibili")),
                            String.valueOf(rs.getInt("venduti")),
                            rs.getBigDecimal("incasso").toPlainString()
                        );
                        out.println("OK|" + info);
                    } else {
                        out.println("ERR|Proiezione non trovata");
                    }
                }
            }
        }
    }
}