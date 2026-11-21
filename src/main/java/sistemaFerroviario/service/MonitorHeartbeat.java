
package sistemaFerroviario.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memorizza gli ultimi heartbeat delle stazioni e identifica quelle che non rispondono entro la soglia.
 */
public class MonitorHeartbeat {

    private final Map<String, Instant> stazioniAttese = new ConcurrentHashMap<>();

    private final Map<String, Instant> ultimiHeartbeat =
            new ConcurrentHashMap<>();

    private final Set<String> stazioniInAllarme =
            ConcurrentHashMap.newKeySet();


	public void registraStazione(String idStazione) {
	    if (idStazione == null || idStazione.isBlank()) {
	        throw new IllegalArgumentException(
	            "L'ID della stazione non può essere vuoto."
	        );
	    }
	
	    // Inizia la finestra di attesa anche per chi non ha mai inviato heartbeat.
        stazioniAttese.putIfAbsent(idStazione, Instant.now());
	}


    /** Aggiorna l istante dell ultimo messaggio della stazione. */
    public void riceviHeartbeat(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            return;
        }

        stazioniAttese.putIfAbsent(idStazione, Instant.now());
        ultimiHeartbeat.put(idStazione, Instant.now());

        // Se era in allarme, la stazione è tornata raggiungibile.
        if (stazioniInAllarme.remove(idStazione)) {
            System.out.println(
                "[RIPRISTINO] La stazione " + idStazione
                + " è nuovamente raggiungibile."
            );
        } else {
            System.out.println(
                "[HEARTBEAT] Contatto ricevuto da " + idStazione
            );
        }
    }

    public Set<String> stazioniNonRaggiungibili(Duration timeout) {
        Instant limite = Instant.now().minus(timeout);
        Set<String> risultato = new HashSet<>();

        for (Map.Entry<String, Instant> entry : stazioniAttese.entrySet()) {
            Instant ultima = ultimiHeartbeat.get(entry.getKey());
            Instant riferimento = ultima == null ? entry.getValue() : ultima;
            if (riferimento.isBefore(limite)) risultato.add(entry.getKey());
        }

        return risultato;
    }

    // Controlla tutte le stazioni e segnala i nuovi allarmi.
    /** Confronta ultimo contatto e timeout per identificare le stazioni non raggiungibili. */
    public Set<String> controllaStazioni(Duration timeout) {
        Set<String> nonRaggiungibili =
                stazioniNonRaggiungibili(timeout);

        for (String idStazione : nonRaggiungibili) {
            if (stazioniInAllarme.add(idStazione)) {
                System.err.println(
                    "[ALLARME] La stazione " + idStazione
                    + " non invia heartbeat da troppo tempo!"
                );
            }
        }

        return nonRaggiungibili;
    }

    public Instant ultimoContatto(String idStazione) {
        return ultimiHeartbeat.get(idStazione);
    }

    public boolean isInAllarme(String idStazione) {
        return stazioniInAllarme.contains(idStazione);
    }

	/**
	 * Restituisce true se la stazione ha inviato un heartbeat
	 * entro il timeout previsto.
	 */
	public boolean stazioneRaggiungibile(
	        String idStazione,
	        Duration timeout) {
	
	    if (idStazione == null || idStazione.isBlank()) {
	        return false;
	    }
	
	    if (timeout == null || timeout.isNegative()
	            || timeout.isZero()) {
	        throw new IllegalArgumentException(
	            "Il timeout deve essere positivo"
	        );
	    }
	
	    Instant ultimo = ultimiHeartbeat.get(idStazione);
	
	    return ultimo != null
	        && !ultimo.isBefore(Instant.now().minus(timeout));
	}
}

