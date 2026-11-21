package sistemaFerroviario.communication.dto;

/**
 * DTO minimale del messaggio di presenza periodico inviato da una stazione.
 */
public class MessaggioHeartbeat {
	private String idStazione;
	private String timestamp;
	
	public MessaggioHeartbeat() {}
	
	public String getIdStazione() {
		return idStazione;
	}
	
	public void setIdStazione(String idStazione) {
		this.idStazione = idStazione;
	}
	
	public String getTimeStamp() {
		return timestamp;
	}
	
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
}
