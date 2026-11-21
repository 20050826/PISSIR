
package sistemaFerroviario.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modello di un sensore di stazione, con stato di attivazione, rilevamento e anomalia.
 */
public class Sensore {

    private final String id;
    private volatile boolean attivo;
    private volatile boolean inAnomalia;
    
    @JsonCreator
    public Sensore(
            @JsonProperty("id") String id,
            @JsonProperty("attivo") boolean attivo,
            @JsonProperty("inAnomalia") boolean inAnomalia) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                "ID del sensore obbligatorio"
            );
        }

        this.id = id;
        this.attivo = attivo;
        this.inAnomalia = inAnomalia;
    }

    public Sensore(String id, boolean attivo) {
        this(id, attivo, false);
    }
    
    public boolean rilevaPassaggio() {
        if (!attivo || inAnomalia) {
            return false;
        }

        System.out.println(
            "Sensore " + id + ": passaggio rilevato"
        );

        return true;
    }

    public void attiva() {
        attivo = true;
    }

    public void disattiva() {
        attivo = false;
    }

    public void segnalaAnomalia() {
        inAnomalia = true;
        System.out.println(
            "ATTENZIONE: anomalia rilevata dal sensore " + id
        );
    }

    public void ripristina() {
        inAnomalia = false;
        System.out.println(
            "Sensore " + id + ": anomalia ripristinata"
        );
    }

    public boolean isInAnomalia() {
        return inAnomalia;
    }
    public String getId() {
        return id;
    }

    public boolean isAttivo() {
        return attivo;
    }
    
}
