package sistemaFerroviario.service;

import sistemaFerroviario.persistence.StatoPersistito;
import sistemaFerroviario.model.Percorso;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import sistemaFerroviario.model.*;

/**
 * Modello centrale dello stato ferroviario: treni, stazioni, percorsi, guasti, storico e regole di aggiornamento.
 */
public class SistemaFerroviario {
	private final Map<String, Treno> treni = new LinkedHashMap<>();
	private final Map<String, Stazione> stazioni = new LinkedHashMap<>();
	private final Map<String, Percorso> percorsi = new LinkedHashMap<>();
	private final Map<String, Guasto> guasti = new LinkedHashMap<>();
	private final java.util.List<EventoTransito> storicoTransiti = new java.util.ArrayList<>();
    // Modello dimostrativo: 2 secondi per ciascuna tratta S01/S02.
    // Nella simulazione del ritardo la durata effettiva sara' volutamente maggiore.
    private static final java.time.Duration DURATA_NOMINALE_TRATTA = java.time.Duration.ofSeconds(2);
	private final MonitorHeartbeat monitorHeartbeat;
	private final ControlloreSicurezzaTransiti controllore;
	
	public SistemaFerroviario() {
	    this.monitorHeartbeat = new MonitorHeartbeat();

	    this.controllore = new ControlloreSicurezzaTransiti(
	        this,
	        monitorHeartbeat,
	        java.time.Duration.ofSeconds(5)
	    );
	}
	
	public synchronized void aggiungiTreno(Treno treno) {
	    if (treno == null) {
	        throw new IllegalArgumentException("Treno nullo");
	    }

	    if (treni.containsKey(treno.getId())) {
	        throw new IllegalArgumentException(
	                "Treno già presente: " + treno.getId());
	    }

	    treni.put(treno.getId(), treno);
	}
	

	public void aggiungiStazione(Stazione stazione) {
	    if (stazione == null) {
	        throw new IllegalArgumentException(
	            "Stazione nulla"
	        );
	    }
	
	    if (stazioni.containsKey(stazione.getId())) {
	        throw new IllegalArgumentException(
	            "Stazione già presente: " + stazione.getId()
	        );
	    }
	
	    stazioni.put(stazione.getId(), stazione);
	
	    monitorHeartbeat.registraStazione(stazione.getId());
	}

	
	
	/** Registra un percorso solo se contiene almeno due stazioni note e non presenta ID o tappe duplicati. */
	public synchronized void aggiungiPercorso(Percorso percorso) {
	    if (percorso == null) {
	        throw new IllegalArgumentException(
	            "Il percorso è obbligatorio"
	        );
	    }
	
	    String id = percorso.getId();
	
	    if (id == null || id.isBlank()) {
	        throw new IllegalArgumentException(
	            "L'ID del percorso è obbligatorio"
	        );
	    }
	
	    if (percorsi.containsKey(id)) {
	        throw new IllegalArgumentException(
	            "Esiste già un percorso con ID: " + id
	        );
	    }
	
	    java.util.List<String> tappe = percorso.getStazioni();
	    if (tappe.size() < 2) {
	        throw new IllegalArgumentException(
	            "Il percorso deve contenere almeno due stazioni"
	        );
	    }
	    if (new java.util.HashSet<>(tappe).size() != tappe.size()) {
	        throw new IllegalArgumentException("Il percorso contiene stazioni duplicate");
	    }
	
	    for (String idStazione : tappe) {
	        if (!stazioni.containsKey(idStazione)) {
	            throw new IllegalArgumentException(
	                "Stazione non trovata: " + idStazione
	            );
	        }
	    }
	
	    percorsi.put(id, percorso);
	}


	/** Applica le regole di ingresso e uscita e aggiorna la posizione del convoglio e lo storico. */
	public synchronized void registraTransito(EventoTransito evento) {

	    if (evento == null) {
	        throw new IllegalArgumentException(
	                "Evento obbligatorio"
	        );
	    }

	    Treno treno = treni.get(evento.getIdTreno());
	    Stazione stazione = stazioni.get(evento.getIdStazione());

	    if (treno == null || stazione == null) {
	        throw new IllegalArgumentException(
	                "Treno o stazione non presenti nel sistema"
	        );
	    }

		if (treno.getStato() == StatoTreno.SOPPRESSO) {
			throw new IllegalStateException("Treno " + treno.getId() + " soppresso: transito non ammesso");
		}

	    if (!controllore.transitoConsentito(stazione.getId())) {
	        throw new IllegalStateException(
	                controllore.motivoBlocco(stazione.getId())
	        );
	    }

        TipoMovimento movimento = evento.getTipoMovimento() == null
                ? TipoMovimento.INGRESSO : evento.getTipoMovimento();

        if (movimento == TipoMovimento.USCITA) {
            // Si puo' uscire soltanto dalla stazione in cui si e' presenti.
            if (treno.getStato() != StatoTreno.IN_STAZIONE
                    || !stazione.getId().equals(treno.getStazionePrecedente())) {
                throw new IllegalStateException("Uscita non valida: treno " + treno.getId()
                        + " non presente nella stazione " + stazione.getId());
            }
            // Non autorizzare una partenza se manca una destinazione valida.
            String destinazione = treno.getProssimaStazione();
            if (destinazione == null || destinazione.isBlank()
                    || destinazione.equals(stazione.getId())
                    || !stazioni.containsKey(destinazione)) {
                throw new IllegalStateException("Uscita non valida: destinazione non definita o inesistente per il treno "
                        + treno.getId());
            }
            treno.avviaViaggio();
            treno.pianificaArrivo(evento.getTimeStamp(), DURATA_NOMINALE_TRATTA);
            System.out.println("[ETA CENTRALE] Treno=" + treno.getId()
                    + " | stazione successiva=" + treno.getProssimaStazione()
                    + " | arrivo programmato=" + treno.getArrivoProgrammato()
                    + " | arrivo stimato=" + treno.getArrivoStimato());
        } else {
            // Un ingresso e' valido solo durante il viaggio e nella destinazione
            // comunicata al treno prima della partenza. Evita arrivi fuori rotta
            // e doppie registrazioni dello stesso ingresso.
            if (treno.getStato() != StatoTreno.IN_VIAGGIO
                    && treno.getStato() != StatoTreno.IN_RITARDO) {
                throw new IllegalStateException("Ingresso non valido: treno " + treno.getId()
                        + " non in viaggio verso " + stazione.getId());
            }
            if (!stazione.getId().equals(treno.getProssimaStazione())) {
                throw new IllegalStateException("Ingresso non valido: il treno " + treno.getId()
                        + " e' atteso a " + treno.getProssimaStazione()
                        + ", non a " + stazione.getId());
            }
            // Avanza l'itinerario nella stessa transazione che registra l'ingresso.
            java.util.List<String> tappe = PianificatorePercorsi.itinerarioAndataRitorno(this, treno.getIdPercorso());
            int successiva = treno.getIndiceTappa() + 1;
            if (successiva >= tappe.size() || !stazione.getId().equals(tappe.get(successiva))) {
                throw new IllegalStateException("Ingresso non coerente con la tappa del treno " + treno.getId());
            }
            treno.raggiungiStazione(stazione.getId());
            treno.impostaIndiceTappa(successiva);
            treno.impostaProssimaStazione(successiva + 1 < tappe.size() ? tappe.get(successiva + 1) : null);
            treno.registraArrivoEffettivo(evento.getTimeStamp());
            System.out.println("[RITARDO CENTRALE] Treno=" + treno.getId()
                    + " | arrivo effettivo=" + treno.getUltimoArrivoEffettivo()
                    + " | ritardo=" + treno.getRitardoSecondi() + " s");
        }

        // Aggiunge l'evento soltanto dopo averlo autorizzato e applicato.
        storicoTransiti.add(evento);

	    System.out.println(
	            "Transito registrato: " + evento
	    );
	}

	
    /**
     * Autorizza un transito solo se lo stato risultante e' stato salvato.
     * In caso di errore di persistenza annulla il movimento e l'evento,
     * evitando che un nuovo tentativo del treno incontri uno stato incoerente.
     * Il lock protegge anche il salvataggio periodico dello snapshot.
     */
    public synchronized void registraTransitoConSalvataggio(
            EventoTransito evento, Runnable salvaDopoTransito) {
        java.util.Objects.requireNonNull(evento, "Evento obbligatorio");
        java.util.Objects.requireNonNull(salvaDopoTransito, "Salvataggio obbligatorio");
        Treno treno = treni.get(evento.getIdTreno());
        if (treno == null) {
            throw new IllegalArgumentException("Treno non presente nel sistema");
        }
        // QoS 1 puo' ritrasmettere il medesimo evento; il suo ID persiste nello storico.
        // Non applicare due volte un movimento gia' confermato dal database.
        if (evento.getIdRichiesta() != null && storicoTransiti.stream()
                .anyMatch(e -> evento.getIdRichiesta().equals(e.getIdRichiesta()))) return;
        Treno precedente = treno.copiaPerRipristino();
        int numeroEventiPrecedente = storicoTransiti.size();
        try {
            registraTransito(evento);
            salvaDopoTransito.run();
        } catch (RuntimeException errore) {
            treno.ripristinaDa(precedente);
            while (storicoTransiti.size() > numeroEventiPrecedente) {
                storicoTransiti.remove(storicoTransiti.size() - 1);
            }
            throw errore;
        }
    }

	/** Aggiunge un nuovo guasto allo stato del sistema. */
	public synchronized void registraGuasto(Guasto guasto) {
	    if (guasto == null
	            || !stazioni.containsKey(guasto.getIdStazione())) {
	        throw new IllegalArgumentException(
	                "Guasto non valido o stazione inesistente");
	    }

	    if (guasti.containsKey(guasto.getId())) {
	        throw new IllegalArgumentException(
	                "Guasto già registrato: " + guasto.getId());
	    }

	    guasti.put(guasto.getId(), guasto);
	}
	
	public void segnalaGuasto(String idStazione, String descrizione) {
	    segnalaGuasto(idStazione, descrizione, null);
	}
	
	public void segnalaGuasto(
	        String idStazione,
	        String descrizione,
	        String idSensore) {

	    if (idStazione == null || idStazione.isBlank()) {
	        throw new IllegalArgumentException(
	            "L'identificativo della stazione è obbligatorio"
	        );
	    }

	    if (descrizione == null || descrizione.isBlank()) {
	        throw new IllegalArgumentException(
	            "La descrizione del guasto è obbligatoria"
	        );
	    }

	    if (!stazioni.containsKey(idStazione)) {
	        throw new IllegalArgumentException(
	            "Stazione non trovata: " + idStazione
	        );
	    }

	    if (idSensore != null
	            && !stazioni.get(idStazione).contieneSensore(idSensore)) {
	        throw new IllegalArgumentException(
	            "Sensore non trovato nella stazione " + idStazione
	        );
	    }

	    String idGuasto = String.format("G%03d", guasti.size() + 1);

	    Guasto guasto = new Guasto(
	        idGuasto,
	        idStazione,
	        descrizione,
	        java.time.Instant.now(),
	        Guasto.StatoGuasto.APERTO,
	        idSensore
	    );

	    registraGuasto(guasto);

	    System.out.println("Segnalazione ricevuta: " + guasto);
	}
	/** Segna l intervento di un tecnico sul guasto indicato. */
	public void prendiInCaricoGuasto(String idGuasto) {

	    Guasto guasto = guasti.get(idGuasto);

	    if (guasto == null) {
	        throw new IllegalArgumentException(
	                "Guasto non trovato: " + idGuasto
	        );
	    }

	    guasto.prendiInCarico();

	    System.out.println(
	            "Guasto preso in carico: " + idGuasto
	    );
	}
	
	public Guasto cercaGuasto(String idGuasto) {
	    return guasti.get(idGuasto);
	}
	
	public Treno cercaTreno(String id) {
		return treni.get(id);
	}
	
	public Stazione cercaStazione(String id) {
		return stazioni.get(id);
	}
	
	public Collection<Treno> getTreni(){
		return java.util.List.copyOf(treni.values());
	}
	
	public Collection<Stazione> getStazioni(){
		return java.util.List.copyOf(stazioni.values());
	}
	
	public Collection<Percorso> getPercorsi(){
		return java.util.List.copyOf(percorsi.values());
	}
	

	public synchronized Percorso cercaPercorso(String id) {
	    if (id == null || id.isBlank()) {
	        throw new IllegalArgumentException(
	            "ID del percorso obbligatorio"
	        );
	    }
	
	    return percorsi.get(id);
	}

	
	public synchronized Collection<Guasto> getGuasti(){
		return java.util.List.copyOf(guasti.values());
	}

	public synchronized java.util.List<EventoTransito> getStoricoTransiti() {
		return java.util.List.copyOf(storicoTransiti);
	}
	
	public MonitorHeartbeat getMonitorHeartbeat() {
	    return monitorHeartbeat;
	}
	
	/** Conclude il guasto e aggiorna il suo stato. */
	public void risolviGuasto(String idGuasto) {
	
	    Guasto guasto = guasti.get(idGuasto);
	
	    if (guasto == null) {
	        throw new IllegalArgumentException(
	                "Guasto non trovato: " + idGuasto
	        );
	    }
	
	    guasto.risolvi();
	
	    System.out.println(
	            "Guasto risolto: " + idGuasto
	    );
	}
	

    /** Ripristino atomico dal database (solo su un sistema nuovo). */
    public synchronized void ripristinaStato(StatoPersistito stato) {
        if (stato == null || stato.getTreni() == null || stato.getStazioni() == null
                || stato.getPercorsi() == null || stato.getGuasti() == null
                || stato.getTreni().isEmpty() || stato.getStazioni().isEmpty()) {
            throw new IllegalArgumentException("Snapshot mancante o incompleto");
        }
        if (!treni.isEmpty() || !stazioni.isEmpty() || !percorsi.isEmpty() || !guasti.isEmpty()) {
            throw new IllegalStateException("Ripristino consentito solo su un sistema vuoto");
        }
        // Verifica in un'istanza temporanea: nessun caricamento parziale in caso di errore.
        SistemaFerroviario verifica = new SistemaFerroviario();
        for (Stazione stazione : stato.getStazioni()) verifica.aggiungiStazione(stazione);
        for (Treno treno : stato.getTreni()) verifica.aggiungiTreno(treno);
        for (Percorso percorso : stato.getPercorsi()) verifica.aggiungiPercorso(percorso);
        for (Guasto guasto : stato.getGuasti()) verifica.registraGuasto(guasto);
        for (Stazione stazione : stato.getStazioni()) aggiungiStazione(stazione);
        for (Treno treno : stato.getTreni()) aggiungiTreno(treno);
        for (Percorso percorso : stato.getPercorsi()) aggiungiPercorso(percorso);
        for (Guasto guasto : stato.getGuasti()) registraGuasto(guasto);
        if (stato.getTransiti() != null) {
            for (EventoTransito evento : stato.getTransiti()) {
                if (evento == null) throw new IllegalArgumentException("Transito nullo nello storico");
                storicoTransiti.add(evento);
            }
        }
        System.out.println("Stato ripristinato: " + treni.size() + " treni, "
                + stazioni.size() + " stazioni, " + percorsi.size() + " percorsi, "
                + guasti.size() + " guasti.");
    }


	public synchronized void aggiungiStazioneAlPercorso(
	        String idPercorso,
	        String idStazione) {

	    verificaPercorsoModificabile(idPercorso);
	    Percorso percorso = cercaPercorso(idPercorso);

	    if (percorso == null) {
	        throw new IllegalArgumentException(
	            "Percorso non trovato: " + idPercorso
	        );
	    }

	    Stazione stazione = stazioni.get(idStazione);

	    if (stazione == null) {
	        throw new IllegalArgumentException(
	            "Stazione non trovata: " + idStazione
	        );
	    }

	    percorso.aggiungiStazione(idStazione);
	}
	
	
	public synchronized void rimuoviStazioneDalPercorso(
	        String idPercorso,
	        String idStazione) {
	
	    verificaPercorsoModificabile(idPercorso);
	    Percorso percorso = cercaPercorso(idPercorso);
	
	    if (percorso == null) {
	        throw new IllegalArgumentException(
	            "Percorso non trovato: " + idPercorso
	        );
	    }
	
	    Stazione stazione = stazioni.get(idStazione);
	
	    if (stazione == null) {
	        throw new IllegalArgumentException(
	            "Stazione non trovata: " + idStazione
	        );
	    }
	
	    if (!percorso.getStazioni().contains(idStazione)) {
	        throw new IllegalArgumentException("Stazione non presente nel percorso: " + idStazione);
	    }
	    if (percorso.getStazioni().size() <= 2) {
	        throw new IllegalArgumentException(
	                "Impossibile rimuovere: il percorso deve conservare almeno due stazioni");
	    }
	    percorso.rimuoviStazione(idStazione);
	}
	
    private void verificaPercorsoModificabile(String idPercorso) {
        boolean inCorso = treni.values().stream().anyMatch(t -> idPercorso.equals(t.getIdPercorso())
                && (t.getStato() == StatoTreno.IN_VIAGGIO
                    || t.getStato() == StatoTreno.IN_RITARDO
                    || (t.getStato() == StatoTreno.IN_STAZIONE && t.getIndiceTappa() > 0
                        && t.getProssimaStazione() != null)));
        if (inCorso) throw new IllegalStateException("Percorso in uso da un treno in viaggio");
    }

    /** Imposta il percorso che sara' caricato alla successiva partenza. */
    public synchronized void assegnaPercorsoATreno(String idTreno, String idPercorso) {
        Treno treno = treni.get(idTreno);
        if (treno == null) throw new IllegalArgumentException("Treno non trovato: " + idTreno);
        if (treno.getStato() == StatoTreno.IN_VIAGGIO
                || treno.getStato() == StatoTreno.IN_RITARDO
                || (treno.getStato() == StatoTreno.IN_STAZIONE
                    && treno.getIndiceTappa() > 0 && treno.getProssimaStazione() != null))
            throw new IllegalStateException("Non modificare il percorso di un treno durante un viaggio");
        // Valida il percorso prima di salvarne l'ID.
        PianificatorePercorsi.itinerarioAndataRitorno(this, idPercorso);
        treno.impostaPercorso(idPercorso);
    }

	/** Sopprime un treno fermo in stazione. */
	public synchronized void sopprimiTreno(String idTreno) {
		Treno treno = treni.get(idTreno);
		if (treno == null) {
			throw new IllegalArgumentException("Treno non trovato: " + idTreno);
		}
		treno.sopprimi();
	}

	/** Rimuove un percorso se le regole applicative lo consentono. */
	public synchronized void rimuoviPercorso(String idPercorso) {
	    if (idPercorso == null || idPercorso.isBlank()) {
	        throw new IllegalArgumentException(
	            "ID del percorso obbligatorio"
	        );
	    }

        if (treni.values().stream().anyMatch(t -> idPercorso.equals(t.getIdPercorso()))) {
            throw new IllegalStateException("Percorso ancora assegnato a un treno: " + idPercorso);
        }
	    Percorso rimosso = percorsi.remove(idPercorso);

	    if (rimosso == null) {
	        throw new IllegalArgumentException(
	            "Percorso non trovato: " + idPercorso
	        );
	    }
	}
	

	
}
