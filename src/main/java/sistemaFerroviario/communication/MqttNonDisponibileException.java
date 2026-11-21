package sistemaFerroviario.communication;

/**
 * Disservizio temporaneo del canale MQTT (nessuna risposta, client disconnesso,
 * errore di invio). Il treno ritenta lo stesso evento senza crearne uno nuovo.
 */
public class MqttNonDisponibileException extends IllegalStateException {

    public MqttNonDisponibileException(String messaggio) {
        super(messaggio);
    }

    public MqttNonDisponibileException(String messaggio, Throwable causa) {
        super(messaggio, causa);
    }
}
