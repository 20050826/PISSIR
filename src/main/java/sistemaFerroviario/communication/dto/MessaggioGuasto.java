
package sistemaFerroviario.communication.dto;

/**
 * DTO usato per trasmettere una segnalazione di guasto con gli identificativi della stazione e del sensore.
 */
public class MessaggioGuasto {

    private String id;
    private String idStazione;
    private String idSensore;
    private String descrizione;
    private String timestamp;

    public MessaggioGuasto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdStazione() {
        return idStazione;
    }

    public void setIdStazione(String idStazione) {
        this.idStazione = idStazione;
    }

    public String getIdSensore() {
        return idSensore;
    }

    public void setIdSensore(String idSensore) {
        this.idSensore = idSensore;
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
}
