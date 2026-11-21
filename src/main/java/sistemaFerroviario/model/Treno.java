package sistemaFerroviario.model;
import com.fasterxml.jackson.annotation.JsonCreator; 
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modello dello stato del convoglio: posizione, prossima stazione, percorso assegnato, ritardi e istanti di viaggio.
 */
public class Treno {
	private final String id;
    // Assegnazione persistente del percorso (compatibile con snapshot vecchi).
    private String idPercorso;
    private int indiceTappa;
	private StatoTreno stato;
	private String stazionePrecedente;
	private String prossimaStazione;
	private java.time.Instant istanteArrivoStazione;
    // Previsioni e ritardo calcolati dalla Centrale Operativa sui transiti MQTT.
    private java.time.Instant arrivoProgrammato;
    private java.time.Instant arrivoStimato;
    private java.time.Instant ultimoArrivoEffettivo;
    private long ritardoSecondi;
	@JsonCreator
	public Treno(
	        @JsonProperty("id") String id,
	        @JsonProperty("prossimaStazione") String prossimaStazione,
	        @JsonProperty("stato") StatoTreno stato,
	        @JsonProperty("stazionePrecedente") String stazionePrecedente,
	        @JsonProperty("istanteArrivoStazione") java.time.Instant istanteArrivoStazione,
            @JsonProperty("arrivoProgrammato") java.time.Instant arrivoProgrammato,
            @JsonProperty("arrivoStimato") java.time.Instant arrivoStimato,
            @JsonProperty("ultimoArrivoEffettivo") java.time.Instant ultimoArrivoEffettivo,
            @JsonProperty("ritardoSecondi") Long ritardoSecondi,
            @JsonProperty("idPercorso") String idPercorso,
            @JsonProperty("indiceTappa") Integer indiceTappa) {

	    if (id == null || id.isBlank()) {
	        throw new IllegalArgumentException(
	                "ID del treno obbligatorio"
	        );
	    }

	    this.id = id;
        this.idPercorso = idPercorso;
        this.indiceTappa = indiceTappa == null ? 0 : indiceTappa;
	    this.prossimaStazione = prossimaStazione;
	    this.stazionePrecedente = stazionePrecedente;
	    this.istanteArrivoStazione = istanteArrivoStazione;
        this.arrivoProgrammato = arrivoProgrammato;
        this.arrivoStimato = arrivoStimato;
        this.ultimoArrivoEffettivo = ultimoArrivoEffettivo;
        this.ritardoSecondi = ritardoSecondi == null ? 0 : Math.max(0, ritardoSecondi);

	    this.stato = stato != null
	            ? stato
	            : StatoTreno.FERMO;
	}
	
    /** Costruttore legacy per compatibilita' con i test del progetto. */
    public Treno(String id, String prossimaStazione, StatoTreno stato,
            String stazionePrecedente, java.time.Instant istanteArrivoStazione) {
        this(id, prossimaStazione, stato, stazionePrecedente, istanteArrivoStazione,
                null, null, null, 0L, null, 0);
    }

	public Treno(String id, String stazionePrecedente,
	        StatoTreno stato, String prossimaStazione) {

	    this(id, prossimaStazione, stato, stazionePrecedente, null);
	}

    /** Prepara una nuova simulazione dalla stazione iniziale senza inventare un ingresso. */
    public void preparaPartenzaDa(String idStazione, String idProssimaStazione) {
        if (idStazione == null || idStazione.isBlank()
                || idProssimaStazione == null || idProssimaStazione.isBlank()) {
            throw new IllegalArgumentException("Stazioni di partenza e destinazione obbligatorie");
        }
        if (stato == StatoTreno.GUASTO) {
            throw new IllegalStateException("Un treno guasto non puo' ripartire");
        }
        // I treni della demo ripartono da S01 a ogni esecuzione.
        stazionePrecedente = idStazione;
        prossimaStazione = idProssimaStazione;
        istanteArrivoStazione = null;
        arrivoProgrammato = null;
        arrivoStimato = null;
        ultimoArrivoEffettivo = null;
        ritardoSecondi = 0;
        stato = StatoTreno.IN_STAZIONE;
        indiceTappa = 0;
    }

    /** Copia lo stato corrente per annullare un transito non persistito. */
    public synchronized Treno copiaPerRipristino() {
        return new Treno(id, prossimaStazione, stato, stazionePrecedente,
                istanteArrivoStazione, arrivoProgrammato, arrivoStimato,
                ultimoArrivoEffettivo, ritardoSecondi, idPercorso, indiceTappa);
    }

    /** Ripristina lo stesso oggetto Treno, mantenendo i riferimenti dei thread di simulazione. */
    public synchronized void ripristinaDa(Treno precedente) {
        if (precedente == null || !id.equals(precedente.id)) {
            throw new IllegalArgumentException("Stato del treno non valido per il ripristino");
        }
        idPercorso = precedente.idPercorso;
        indiceTappa = precedente.indiceTappa;
        stato = precedente.stato;
        stazionePrecedente = precedente.stazionePrecedente;
        prossimaStazione = precedente.prossimaStazione;
        istanteArrivoStazione = precedente.istanteArrivoStazione;
        arrivoProgrammato = precedente.arrivoProgrammato;
        arrivoStimato = precedente.arrivoStimato;
        ultimoArrivoEffettivo = precedente.ultimoArrivoEffettivo;
        ritardoSecondi = precedente.ritardoSecondi;
    }

	public void avviaViaggio() {
	    if (stato == StatoTreno.GUASTO || stato == StatoTreno.SOPPRESSO) {
	        throw new IllegalStateException(
	            "Un treno guasto non può avviare il viaggio"
	        );
	    }
	
	    // Il treno riparte: la sosta precedente è terminata.
	    istanteArrivoStazione = null;
	    stato = StatoTreno.IN_VIAGGIO;
	}

	public void fermaViaggio() {
	    if (stato != StatoTreno.GUASTO && stato!= StatoTreno.SOPPRESSO) {
	        stato = StatoTreno.FERMO;
	        istanteArrivoStazione = null;
            arrivoProgrammato = null;
            arrivoStimato = null;
	    }
	}
	
	public void raggiungiStazione(String idStazione) {
	    if (idStazione == null || idStazione.isBlank()) {
	        throw new IllegalArgumentException("Stazione non valida");
	    }

	    if (stato == StatoTreno.GUASTO) {
	        throw new IllegalStateException(
	            "Un treno guasto non può completare il transito"
	        );
	    }

	    stazionePrecedente = idStazione;
	    stato = StatoTreno.IN_STAZIONE;
	    istanteArrivoStazione = java.time.Instant.now();
	}
	
	public void impostaProssimaStazione(String idStazione) {
	    if (idStazione != null && idStazione.isBlank()) {
	        throw new IllegalArgumentException("Stazione non valida");
	    }

	    prossimaStazione = idStazione;
	}
	
	public void impostaRitardo() {
		if (stato != StatoTreno.GUASTO) {
			stato = StatoTreno.IN_RITARDO;
		}
	}
	
    /** Pianifica il prossimo arrivo sulla base del tempo nominale della tratta.
     *  L'ETA utilizza il ritardo osservato sull'ultimo tratto come stima iniziale. */
    public synchronized void pianificaArrivo(
            java.time.Instant oraPartenza, java.time.Duration tempoNominale) {
        if (oraPartenza == null || tempoNominale == null
                || tempoNominale.isNegative() || tempoNominale.isZero()) {
            throw new IllegalArgumentException("Partenza o durata nominale non valide");
        }
        if (stato != StatoTreno.IN_VIAGGIO) {
            throw new IllegalStateException("Il treno non risulta in viaggio");
        }
        arrivoProgrammato = oraPartenza.plus(tempoNominale);
        arrivoStimato = arrivoProgrammato.plusSeconds(ritardoSecondi);
    }

    /** Registra il confronto tra arrivo effettivo e tempo nominale. */
    public synchronized void registraArrivoEffettivo(java.time.Instant oraArrivo) {
        if (oraArrivo == null) throw new IllegalArgumentException("Arrivo non valido");
        ultimoArrivoEffettivo = oraArrivo;
        if (arrivoProgrammato != null) {
            long scartoMillis = Math.max(0,
                    java.time.Duration.between(arrivoProgrammato, oraArrivo).toMillis());
            // Arrotondamento al secondo piu' vicino: ignora la normale latenza MQTT.
            ritardoSecondi = (scartoMillis + 500) / 1000;
        }
        arrivoProgrammato = null;
        arrivoStimato = null;
    }

    public synchronized java.time.Instant getArrivoProgrammato() { return arrivoProgrammato; }
    public synchronized java.time.Instant getArrivoStimato() { return arrivoStimato; }
    public synchronized java.time.Instant getUltimoArrivoEffettivo() { return ultimoArrivoEffettivo; }
    public synchronized long getRitardoSecondi() { return ritardoSecondi; }

	/** Soppressione amministrativa: ammessa solo per un treno fermo in stazione. */
	public synchronized void sopprimi() {
		if (stato != StatoTreno.IN_STAZIONE) {
			throw new IllegalStateException(
					"Si puo' sopprimere solo un treno fermo in stazione (stato attuale: " + stato + ")");
		}
		stato = StatoTreno.SOPPRESSO;
		istanteArrivoStazione = null;
		arrivoProgrammato = null;
		arrivoStimato = null;
	}

	public void segnalaGuasto() {
		stato = StatoTreno.GUASTO;
	}
	
    public synchronized int getIndiceTappa() { return indiceTappa; }
    public synchronized void impostaIndiceTappa(int indice) {
        if (indice < 0) throw new IllegalArgumentException("Indice tappa non valido");
        indiceTappa = indice;
    }
    public synchronized String getIdPercorso() { return idPercorso; }

    public synchronized void impostaPercorso(String idPercorso) {
        if (idPercorso == null || idPercorso.isBlank()) {
            throw new IllegalArgumentException("Percorso obbligatorio");
        }
        this.idPercorso = idPercorso;
    }

	public String getId() {
		return id;
	}
	
	public StatoTreno getStato() {
		return stato;
	}
	
	
	public String getStazionePrecedente() {
		return stazionePrecedente;
	}
	
	public String getProssimaStazione() {
		return prossimaStazione;
	}
	
	@Override
	public String toString() {
		return "Treno{" +
				"id='" + id + '\'' +
				", stato=" + stato +
				", stazionePrecedente='" + stazionePrecedente + '\'' +
				", prossimaStazione='" + prossimaStazione + '\'' +
				'}';
	}
	


	public void completaTransito(
	        String idStazioneRaggiunta,
	        String idProssimaStazione) {
	
	    if (stato == StatoTreno.GUASTO) {
	        throw new IllegalStateException(
	                "Un treno guasto non può completare il transito."
	        );
	    }
	
	    if (idStazioneRaggiunta == null
	            || idStazioneRaggiunta.isBlank()) {
	        throw new IllegalArgumentException(
	                "La stazione raggiunta è obbligatoria."
	        );
	    }
	
	    // Verifica se la centrale ha già registrato questo arrivo.
	    boolean arrivoGiaRegistrato =
	            stato == StatoTreno.IN_STAZIONE
	            && idStazioneRaggiunta.equals(stazionePrecedente)
	            && istanteArrivoStazione != null;
	
	    stazionePrecedente = idStazioneRaggiunta;
	    prossimaStazione = idProssimaStazione;
	
	    // Avvia il timer soltanto se l'arrivo non era già stato registrato.
	    if (!arrivoGiaRegistrato) {
	        istanteArrivoStazione = java.time.Instant.now();
	    }
	
	    stato = StatoTreno.IN_STAZIONE;
	}


	
	public boolean sostaSuperioreA30Secondi() {
	    if (stato != StatoTreno.IN_STAZIONE
	            || istanteArrivoStazione == null) {
	        return false;
	    }

	    java.time.Duration sosta =
	            java.time.Duration.between(
	                    istanteArrivoStazione,
	                    java.time.Instant.now()
	            );

	    return sosta.compareTo(java.time.Duration.ofSeconds(30)) > 0;
	}
	
	
	public java.time.Instant getIstanteArrivoStazione() {
	    return istanteArrivoStazione;
	}

}
