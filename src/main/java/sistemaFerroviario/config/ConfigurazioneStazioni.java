
package sistemaFerroviario.config;

import sistemaFerroviario.model.Sensore;
import sistemaFerroviario.model.Stazione;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Carica le stazioni e i relativi sensori
 * dal file config/stazioni.properties.
 */
public final class ConfigurazioneStazioni {

    private final Properties proprieta = new Properties();

    private ConfigurazioneStazioni() {
    }

    /**
     * Legge il file di configurazione delle stazioni.
     */
    public static ConfigurazioneStazioni carica()
            throws IOException {

        Path file = Path.of("config", "stazioni.properties");

        ConfigurazioneStazioni configurazione =
                new ConfigurazioneStazioni();

        try (Reader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            configurazione.proprieta.load(reader);
        }

        if (configurazione.idStazioni().isEmpty()) {
            throw new IllegalStateException(
                    "Nessuna stazione configurata.");
        }

        return configurazione;
    }

    /**
     * Restituisce gli ID delle stazioni configurate.
     */
    public List<String> idStazioni() {

        String elenco = proprieta.getProperty("stazioni", "");

        return Arrays.stream(elenco.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Crea una stazione con i suoi sensori.
     *
     * @param id identificativo della stazione
     * @param operativa stato iniziale della stazione
     */
    public Stazione creaStazione(
            String id, boolean operativa) {

        if (!idStazioni().contains(id)) {
            throw new IllegalArgumentException(
                    "Stazione non configurata: " + id);
        }

        String nome = proprieta.getProperty(id + ".nome");
        String sensori = proprieta.getProperty(id + ".sensori");

        if (nome == null || nome.isBlank()
                || sensori == null || sensori.isBlank()) {
            throw new IllegalStateException(
                    "Configurazione incompleta: " + id);
        }

        Stazione stazione = new Stazione(id, nome.trim());
        stazione.impostaOperativa(operativa);

        for (String idSensore : sensori.split(",")) {

            idSensore = idSensore.trim();

            if (!idSensore.isEmpty()) {
                stazione.aggiungiSensori(
                        new Sensore(idSensore, operativa));
            }
        }

        if (stazione.getSensori().isEmpty()) {
            throw new IllegalStateException(
                    "Nessun sensore configurato: " + id);
        }

        return stazione;
    }
}
