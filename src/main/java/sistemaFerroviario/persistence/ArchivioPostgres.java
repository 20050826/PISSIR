package sistemaFerroviario.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import sistemaFerroviario.model.EventoTransito;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import sistemaFerroviario.service.SistemaFerroviario;

/** Salvataggio incrementale di uno snapshot JSONB dello stato centrale su PostgreSQL. */
public final class ArchivioPostgres {
    private final String url;
    private final String user;
    private final String password;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    // Quanti eventi dello storico sono gia' stati scritti nella tabella relazionale.
    private int transitiGiaInseriti = 0;

    public ArchivioPostgres() {
        url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/ferrovia");
        user = System.getenv().getOrDefault("DB_USER", "ferrovia");
        password = System.getenv().getOrDefault("DB_PASSWORD", "");
        if (password.isBlank()) throw new IllegalStateException("Impostare la variabile d'ambiente DB_PASSWORD");
    }

    public void inizializza() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             var stmt = c.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS railway_state_snapshot (id INTEGER PRIMARY KEY CHECK(id=1), payload JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            // Storico dei transiti in forma relazionale: interrogabile con SQL e senza
            // doverlo estrarre dallo snapshot JSON (che resta la fonte per il ripristino).
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS storico_transiti (id_transito TEXT PRIMARY KEY, id_treno TEXT NOT NULL, id_stazione TEXT NOT NULL, movimento TEXT, istante TIMESTAMPTZ NOT NULL)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_storico_transiti_treno ON storico_transiti(id_treno, istante)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_storico_transiti_stazione ON storico_transiti(id_stazione, istante)");
        }
    }

    public boolean esisteSnapshot() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             var st = c.prepareStatement("SELECT EXISTS(SELECT 1 FROM railway_state_snapshot WHERE id=1)");
             ResultSet rs = st.executeQuery()) {
            rs.next(); return rs.getBoolean(1);
        }
    }

    /** Rilegge lo snapshot persistito e lo deserializza nel modello dati. */
    public StatoPersistito carica() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password);
             var st = c.prepareStatement("SELECT payload::text FROM railway_state_snapshot WHERE id=1");
             ResultSet rs = st.executeQuery()) {
            if (!rs.next()) return null;
            return mapper.readValue(rs.getString(1), StatoPersistito.class);
        }
    }

    // Ordine dei lock coerente: prima il sistema, poi l'archivio.
    // Questo impedisce che il salvataggio periodico si intercalI tra
    // applicazione, commit e eventuale annullamento di un transito.
    /** Salva lo snapshot completo mantenendo serializzate le scritture al DB. */
    public void salva(SistemaFerroviario sistema) throws Exception {
        synchronized (sistema) {
            synchronized (this) {
                StatoPersistito stato = new StatoPersistito();
                stato.setTreni(new ArrayList<>(sistema.getTreni()));
                stato.setStazioni(new ArrayList<>(sistema.getStazioni()));
                stato.setPercorsi(new ArrayList<>(sistema.getPercorsi()));
                stato.setGuasti(new ArrayList<>(sistema.getGuasti()));
                stato.setTransiti(new ArrayList<>(sistema.getStoricoTransiti()));
                String json = mapper.writeValueAsString(stato);
                try (Connection c = DriverManager.getConnection(url, user, password);
                     PreparedStatement st = c.prepareStatement("INSERT INTO railway_state_snapshot(id,payload,updated_at) VALUES(1,?::jsonb,now()) ON CONFLICT(id) DO UPDATE SET payload=EXCLUDED.payload, updated_at=now()")) {
                    st.setString(1, json);
                    st.executeUpdate();
                }
                // Dopo lo snapshot: un errore sullo storico non annulla il transito gia' persistito.
                salvaStoricoTransiti(new ArrayList<>(sistema.getStoricoTransiti()));
            }
        }
    }

    /** Inserisce nella tabella relazionale solo gli eventi non ancora scritti (idempotente). */
    private void salvaStoricoTransiti(List<EventoTransito> transiti) {
        if (transitiGiaInseriti > transiti.size()) transitiGiaInseriti = transiti.size();
        if (transitiGiaInseriti == transiti.size()) return;
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement st = c.prepareStatement("INSERT INTO storico_transiti(id_transito,id_treno,id_stazione,movimento,istante) VALUES(?,?,?,?,?) ON CONFLICT (id_transito) DO NOTHING")) {
            for (EventoTransito e : transiti.subList(transitiGiaInseriti, transiti.size())) {
                st.setString(1, idTransito(e));
                st.setString(2, e.getIdTreno());
                st.setString(3, e.getIdStazione());
                st.setString(4, e.getTipoMovimento() == null ? null : e.getTipoMovimento().name());
                st.setTimestamp(5, java.sql.Timestamp.from(e.getTimeStamp()));
                st.addBatch();
            }
            st.executeBatch();
            transitiGiaInseriti = transiti.size();
        } catch (SQLException ex) {
            System.err.println("[PostgreSQL] Storico transiti non aggiornato: " + ex.getMessage());
        }
    }

    /** Chiave dell'evento: ID richiesta, oppure combinazione stabile per i vecchi eventi senza ID. */
    static String idTransito(EventoTransito e) {
        if (e.getIdRichiesta() != null && !e.getIdRichiesta().isBlank()) return e.getIdRichiesta();
        return e.getIdTreno() + "|" + e.getIdStazione() + "|" + e.getTimeStamp() + "|" + e.getTipoMovimento();
    }
}
