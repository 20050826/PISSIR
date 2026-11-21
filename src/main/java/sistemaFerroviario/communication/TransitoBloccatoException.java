package sistemaFerroviario.communication;

/**
 * La centrale ha risposto BLOCCATO a una richiesta di transito.
 * Il tipo dell'eccezione e il suo metodo {@link #isRiparabile()} sostituiscono
 * il confronto sul testo del messaggio: la decisione "attendere il ripristino
 * oppure considerarlo un errore logico" e' concentrata in un solo punto.
 */
public class TransitoBloccatoException extends IllegalStateException {

    public static final String PREFISSO = "Transito bloccato dalla centrale: ";

    private final String motivo;

    public TransitoBloccatoException(String motivo) {
        super(PREFISSO + motivo);
        this.motivo = motivo == null ? "" : motivo;
    }

    public String getMotivo() {
        return motivo;
    }

    /**
     * true se il blocco puo' cessare con una riparazione (guasto, stazione non
     * operativa o senza heartbeat, sensore non disponibile): il treno attende e riprova.
     * Un ingresso fuori percorso o un transito duplicato sono invece errori logici.
     */
    public boolean isRiparabile() {
        String blocco = motivo.trim();
        return blocco.startsWith("Rilevamento non valido: sensore o stazione non disponibili")
                || (blocco.startsWith("Transito bloccato: la stazione ")
                    && (blocco.contains("presenta un guasto non risolto")
                        || blocco.contains("non \u00e8 raggiungibile o non invia heartbeat recenti")
                        || blocco.contains("non \u00e8 operativa")));
    }
}
