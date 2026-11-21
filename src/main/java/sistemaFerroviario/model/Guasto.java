package sistemaFerroviario.model;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modello di una anomalia della rete ferroviaria e del suo ciclo di vita, da apertura a presa in carico e risoluzione.
 */
public class Guasto {

	public enum StatoGuasto{
		APERTO,
		IN_GESTIONE,
		RISOLTO
	}
	
	private final String id;
	private final String idStazione;
	private final String descrizione;
	private final Instant timestamp;
	private StatoGuasto stato;
	private final String idSensore;
	
	@JsonCreator
	public Guasto(
	        @JsonProperty("id") String id,
	        @JsonProperty("idStazione") String idStazione,
	        @JsonProperty("descrizione") String descrizione,
	        @JsonProperty("timestamp") Instant timestamp,
	        @JsonProperty("stato") StatoGuasto stato,
	        @JsonProperty("idSensore") String idSensore) {

	    if (id == null || id.isBlank()) {
	        throw new IllegalArgumentException("ID del guasto obbligatorio");
	    }

	    if (idStazione == null || idStazione.isBlank()) {
	        throw new IllegalArgumentException("ID della stazione obbligatorio");
	    }

	    if (descrizione == null || descrizione.isBlank()) {
	        throw new IllegalArgumentException("Descrizione del guasto obbligatoria");
	    }

	    this.id = id;
	    this.idStazione = idStazione;
	    this.descrizione = descrizione;
	    this.timestamp = timestamp == null ? Instant.now() : timestamp;
	    this.stato = stato == null ? StatoGuasto.APERTO : stato;
	    this.idSensore = idSensore;
	}
	
	public Guasto(String id, String idStazione, String descrizione) {
	    this(id, idStazione, descrizione, Instant.now(),
	         StatoGuasto.APERTO, null);
	}

	public Guasto(
	        String id,
	        String idStazione,
	        String descrizione,
	        Instant timestamp,
	        StatoGuasto stato) {

	    this(id, idStazione, descrizione, timestamp, stato, null);
	}
	
	public void prendiInCarico() {
		if(stato != StatoGuasto.APERTO) {
			throw new IllegalStateException("il guasto non è nello stato APERTO");
		}
		
		stato = StatoGuasto.IN_GESTIONE;
	}
	
	public void risolvi() {
		if (stato != StatoGuasto.IN_GESTIONE) {
			throw new IllegalStateException("Il guasto deve essere preso in carico prima della risoluzione");
		}
		
		stato = StatoGuasto.RISOLTO;
	}
	
	public String getId() {
		return id;
	}
	
	public String getIdSensore() {
	    return idSensore;
	}
	
	public String getIdStazione() {
		return idStazione;
	}
	
	public String getDescrizione() {
		return descrizione;
	}
	
	@JsonProperty("timestamp")
	public Instant getTimestamp() {
	    return timestamp;
	}
	
	public StatoGuasto getStato() {
		return stato;
	}
	
	@Override
	public String toString() {
	    return "Guasto{" +
	            "id='" + id + '\'' +
	            ", idStazione='" + idStazione + '\'' +
	            ", idSensore='" + idSensore + '\'' +
	            ", descrizione='" + descrizione + '\'' +
	            ", timestamp='" + timestamp + '\'' +
	            ", stato=" + stato +
	            '}';
	}
}
