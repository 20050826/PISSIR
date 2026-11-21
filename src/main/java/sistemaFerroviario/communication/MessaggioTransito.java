package sistemaFerroviario.communication;

import sistemaFerroviario.model.TipoMovimento;

/**
 * Oggetto serializzabile che trasporta i dati essenziali di un rilevamento ferroviario su MQTT.
 */
public class MessaggioTransito {
	
	private String idTreno;
	private String idStazione;
	private String timestamp;
    private String idRichiesta;
	// null mantiene compatibilita con i messaggi di transito precedenti.
	private Boolean rilevato;
	private TipoMovimento tipoMovimento;
	
	public MessaggioTransito() {
	}
	
    public String getIdRichiesta() { return idRichiesta; }
    public void setIdRichiesta(String idRichiesta) { this.idRichiesta = idRichiesta; }

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
	
	public Boolean getRilevato() {
		return rilevato;
	}
	public void setRilevato(Boolean rilevato) {
		this.rilevato = rilevato;
	}

    public TipoMovimento getTipoMovimento() { return tipoMovimento; }
    public void setTipoMovimento(TipoMovimento tipoMovimento) { this.tipoMovimento = tipoMovimento; }

	public String getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
}
