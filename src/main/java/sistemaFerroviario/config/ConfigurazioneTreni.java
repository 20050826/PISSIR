
package sistemaFerroviario.config;

import sistemaFerroviario.model.Treno;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Gestisce la configurazione dei treni
 * tramite il file config/treni.properties.
 */
public final class ConfigurazioneTreni {

    private final Properties proprieta = new Properties();

    private ConfigurazioneTreni() {
    }

    /**
     * Carica la configurazione dei treni.
     */
    public static ConfigurazioneTreni carica()
            throws IOException {

        Path file = Path.of("config", "treni.properties");

        ConfigurazioneTreni configurazione =
                new ConfigurazioneTreni();

        try (Reader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {

            configurazione.proprieta.load(reader);
        }

        if (configurazione.idTreni().isEmpty()) {
            throw new IllegalStateException(
                    "Nessun treno configurato.");
        }

        return configurazione;
    }

    /**
     * Restituisce gli ID dei treni configurati.
     */
    public List<String> idTreni() {

        String elenco = proprieta.getProperty("treni", "");

        return Arrays.stream(elenco.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Restituisce la stazione iniziale di un treno.
     */
    public String stazioneIniziale(String id) {

        verificaId(id);

        return proprietaObbligatoria(
                id + ".stazioneIniziale");
    }

    /**
     * Restituisce il percorso iniziale di un treno.
     */
    public String idPercorso(String id) {

        verificaId(id);

        return proprietaObbligatoria(
                id + ".percorso");
    }

    /**
     * Crea un nuovo treno con il percorso configurato.
     *
     * Da utilizzare solo quando il treno non è
     * già presente nello stato recuperato da PostgreSQL.
     */
    public Treno creaTreno(String id) {

        String stazione = stazioneIniziale(id);
        String percorso = idPercorso(id);

        Treno treno = new Treno(
                id,
                stazione,
                null,
                null
        );

        treno.impostaPercorso(percorso);

        return treno;
    }

    private void verificaId(String id) {

        if (id == null || !idTreni().contains(id)) {
            throw new IllegalArgumentException(
                    "Treno non configurato: " + id);
        }
    }

    private String proprietaObbligatoria(String chiave) {

        String valore = proprieta.getProperty(chiave);

        if (valore == null || valore.isBlank()) {
            throw new IllegalStateException(
                    "Proprietà mancante o vuota: " + chiave);
        }

        return valore.trim();
    }
}
