
package sistemaFerroviario.service;

import java.util.Collection;
import java.util.Objects;
import java.time.Duration;

import sistemaFerroviario.model.Guasto;

/**
 * Decide se un transito e consentito considerando disponibilita della stazione, heartbeat e guasti aperti.
 */
public class ControlloreSicurezzaTransiti {

    private final SistemaFerroviario sistema;
    private final MonitorHeartbeat monitorHeartbeat;
    private final Duration timeoutHeartbeat;


	/**
	 * Compatibilità con i test precedenti:
	 * controlla soltanto i guasti.
	 */
	public ControlloreSicurezzaTransiti(SistemaFerroviario sistema) {
	    this(sistema, null, null);
	}
	
	/**
	 * Controllore completo: verifica guasti e heartbeat.
	 */
	public ControlloreSicurezzaTransiti(
	        SistemaFerroviario sistema,
	        MonitorHeartbeat monitorHeartbeat,
	        Duration timeoutHeartbeat) {
	
	    this.sistema = Objects.requireNonNull(
	        sistema,
	        "Il sistema ferroviario è obbligatorio"
	    );
	
	    this.monitorHeartbeat = monitorHeartbeat;
	    this.timeoutHeartbeat = timeoutHeartbeat;
	
	    if ((monitorHeartbeat == null) != (timeoutHeartbeat == null)) {
	        throw new IllegalArgumentException(
	            "Monitor e timeout devono essere entrambi specificati"
	        );
	    }
	
	    if (timeoutHeartbeat != null
	            && (timeoutHeartbeat.isZero()
	                || timeoutHeartbeat.isNegative())) {
	        throw new IllegalArgumentException(
	            "Il timeout deve essere positivo"
	        );
	    }
}


    /**
     * Restituisce true se la stazione ha almeno
     * un guasto non ancora risolto.
     */
    public boolean stazioneInAllarme(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            throw new IllegalArgumentException(
                "L'ID della stazione è obbligatorio"
            );
        }

        Collection<Guasto> guasti = sistema.getGuasti();

        return guasti.stream().anyMatch(guasto ->
            guasto.getIdStazione().equals(idStazione)
                && guasto.getStato() != Guasto.StatoGuasto.RISOLTO
        );
    }


    /** Restituisce se la stazione e sicura e disponibile al transito. */
    public boolean transitoConsentito(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            return false;
        }

        // La stazione deve esistere.
        sistemaFerroviario.model.Stazione stazione =
                sistema.cercaStazione(idStazione);

        if (stazione == null || !stazione.isOperativa()) {
            return false;
        }

        // La stazione non deve avere guasti attivi.
        if (stazioneInAllarme(idStazione)) {
            return false;
        }

        // Se configurato, l'heartbeat deve essere recente.
        if (monitorHeartbeat != null
                && !monitorHeartbeat.stazioneRaggiungibile(
                        idStazione, timeoutHeartbeat)) {
            return false;
        }

        return true;
    }
	
    /** Restituisce la spiegazione del rifiuto utile alla centrale e al convoglio. */
    public String motivoBlocco(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            return "Transito bloccato: ID della stazione non valido.";
        }

        sistemaFerroviario.model.Stazione stazione =
                sistema.cercaStazione(idStazione);

        if (stazione == null) {
            return "Transito bloccato: stazione "
                    + idStazione + " inesistente.";
        }

        if (!stazione.isOperativa()) {
            return "Transito bloccato: la stazione "
                    + idStazione + " non è operativa.";
        }

        if (stazioneInAllarme(idStazione)) {
            return "Transito bloccato: la stazione "
                    + idStazione + " presenta un guasto non risolto.";
        }

        if (monitorHeartbeat != null
                && !monitorHeartbeat.stazioneRaggiungibile(
                        idStazione, timeoutHeartbeat)) {
            return "Transito bloccato: la stazione "
                    + idStazione
                    + " non è raggiungibile o non invia heartbeat recenti.";
        }

        return "Transito consentito: stazione operativa, "
                + "nessun guasto attivo e heartbeat valido.";
    }
}
