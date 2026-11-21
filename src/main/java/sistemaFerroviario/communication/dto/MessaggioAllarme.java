
package sistemaFerroviario.communication.dto;

/**
 * DTO del messaggio di allarme MQTT, contenente identificativo della stazione, causa e istante della segnalazione.
 */
public class MessaggioAllarme {

    private String idStazione;
    private String idGuasto;
    private String descrizione;
    private String timestamp;
    private String evento;

    public MessaggioAllarme() {
        // Costruttore vuoto necessario a Jackson.
    }

    public MessaggioAllarme(
            String idStazione,
            String idGuasto,
            String descrizione,
            String timestamp,
            String evento) {

        this.idStazione = idStazione;
        this.idGuasto = idGuasto;
        this.descrizione = descrizione;
        this.timestamp = timestamp;
        this.evento = evento;
    }

    public String getIdStazione() {
        return idStazione;
    }

    public void setIdStazione(String idStazione) {
        this.idStazione = idStazione;
    }

    public String getIdGuasto() {
        return idGuasto;
    }

    public void setIdGuasto(String idGuasto) {
        this.idGuasto = idGuasto;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }
}

