/*
Costantini Marco , 762497 , VA
Colombo Davide , 760761 , VA
Bonza Lorenzo , 760667 , VA
Piloni Luca , 762374 , VA
*/

package cinemax.server;


import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Utility da eseguire una sola volta per importare i dati da proiezioni.csv
 * nelle tabelle normalizzate film e proiezioni di PostgreSQL.
 */
public class ImportatoreCSV {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Host DB (scrivi localhost): ");
        String host = scanner.nextLine().trim();
        System.out.print("Username PostgreSQL (scrivi postgres): ");
        String user = scanner.nextLine().trim();
        System.out.print("Password PostgreSQL: ");
        String password = scanner.nextLine().trim();

        String url = "jdbc:postgresql://" + host + ":5432/dbCM";
        String sqlFilm = "INSERT INTO film (titolo, genere, regista, anno, durata_minuti, eta_minima) "
                       + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (titolo) DO UPDATE SET titolo = EXCLUDED.titolo RETURNING id_film";
        String sqlProiezione = "INSERT INTO proiezioni (id_film, data_ora_proiezione, prezzo_biglietto, posti_disponibili) "
                             + "VALUES (?, ?, ?, 200) ON CONFLICT (data_ora_proiezione) DO NOTHING";

        Map<String, Integer> cacheFilm = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             BufferedReader br = new BufferedReader(new FileReader("proiezioni.csv"));
             PreparedStatement psFilm = conn.prepareStatement(sqlFilm);
             PreparedStatement psProj = conn.prepareStatement(sqlProiezione)) {

            conn.setAutoCommit(false);
            br.readLine(); // Salta l'intestazione
            int count = 0;
            String linea;

            while ((linea = br.readLine()) != null) {
                String[] dati = linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (dati.length < 8) continue;

                String dataOra = dati[0].trim().replaceAll("^\"|\"$", "");
                String titolo = dati[1].trim().replaceAll("^\"|\"$", "");
                String genere = dati[2].trim().replaceAll("^\"|\"$", "");
                String regista = dati[3].trim().replaceAll("^\"|\"$", "");
                int anno = Integer.parseInt(dati[4].trim());
                int durata = Integer.parseInt(dati[5].trim());
                int etaMinima = Integer.parseInt(dati[6].trim());
                double prezzo = Double.parseDouble(dati[7].trim());

                Integer idFilm = cacheFilm.get(titolo);
                if (idFilm == null) {
                    psFilm.setString(1, titolo);
                    psFilm.setString(2, genere);
                    psFilm.setString(3, regista);
                    psFilm.setInt(4, anno);
                    psFilm.setInt(5, durata);
                    psFilm.setInt(6, etaMinima);
                    try (ResultSet rs = psFilm.executeQuery()) {
                        if (rs.next()) {
                            idFilm = rs.getInt(1);
                            cacheFilm.put(titolo, idFilm);
                        }
                    }
                }

                psProj.setInt(1, idFilm);
                psProj.setTimestamp(2, Timestamp.valueOf(dataOra));
                psProj.setDouble(3, prezzo);
                psProj.addBatch();
                count++;
            }

            psProj.executeBatch();
            conn.commit();
            System.out.println("Fatto! Film inseriti: " + cacheFilm.size() + " | Proiezioni caricate: " + count);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}