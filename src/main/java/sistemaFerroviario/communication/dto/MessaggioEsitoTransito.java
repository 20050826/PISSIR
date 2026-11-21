
package sistemaFerroviario.communication.dto;

import sistemaFerroviario.model.TipoMovimento;

/**
 * DTO della risposta della centrale a una richiesta di transito: consenso o blocco con motivazione.
 */
public class MessaggioEsitoTransito {

    private String idTreno;
    private String idStazione;
    private String esito;
    private String motivo;
    private String timestamp;
    private String idRichiesta;
    private TipoMovimento tipoMovimento;

    public MessaggioEsitoTransito() {
    }

    public MessaggioEsitoTransito(
            String idTreno,
            String idStazione,
            String esito,
            String motivo,
            String timestamp) {

        this.idTreno = idTreno;
        this.idStazione = idStazione;
        this.esito = esito;
        this.motivo = motivo;
        this.timestamp = timestamp;
    }

    public String getIdRichiesta() { return idRichiesta; }
    public void setIdRichiesta(String idRichiesta) { this.idRichiesta = idRichiesta; }

    public TipoMovimento getTipoMovimento() { return tipoMovimento; }
    public void setTipoMovimento(TipoMovimento tipoMovimento) { this.tipoMovimento = tipoMovimento; }

    public String getIdTreno() {
        return idTreno;
    }

    public void setIdTreno(String idTreno) {
        this.idTreno = idTreno;
    }

    public String getIdStazione() {
        return idStazione;
    }

    public void setIdStazione(String idStazione) {
        this.idStazione = idStazione;
    }

    public String getEsito() {
        return esito;
    }

    public void setEsito(String esito) {
        this.esito = esito;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
