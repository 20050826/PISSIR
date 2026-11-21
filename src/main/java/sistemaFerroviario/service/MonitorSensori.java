package sistemaFerroviario.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keepalive per singolo sensore. Registrare un sensore non conta come heartbeat. */
public final class MonitorSensori {
    private final Map<String, Instant> attesi = new ConcurrentHashMap<>();
    private final Map<String, Instant> ricevuti = new ConcurrentHashMap<>();

    public void registra(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ID sensore obbligatorio");
        attesi.putIfAbsent(id, Instant.now());
    }

    /** Aggiorna la data dell ultimo contatto con un sensore. */
    public void ricevi(String id) {
        if (attesi.containsKey(id)) ricevuti.put(id, Instant.now());
    }

    /** Restituisce i sensori che non hanno trasmesso entro il timeout. */
    public Set<String> silenziosi(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Timeout non valido");
        Instant limite = Instant.now().minus(timeout);
        Set<String> risultati = new HashSet<>();
        for (var s : attesi.entrySet()) {
            Instant visto = ricevuti.get(s.getKey());
            if ((visto == null ? s.getValue() : visto).isBefore(limite))
                risultati.add(s.getKey());
        }
        return risultati;
    }
}
