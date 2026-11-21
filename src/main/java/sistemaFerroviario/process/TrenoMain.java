package sistemaFerroviario.process;

import java.time.Duration;

import sistemaFerroviario.communication.MqttNonDisponibileException;
import sistemaFerroviario.communication.MqttService;
import sistemaFerroviario.communication.dto.MessaggioViaggio;
import sistemaFerroviario.config.ConfigurazioneTreni;

/**
 * Eseguibile autonomo di un treno: un processo (JVM) per ogni convoglio.
 *
 * Il treno non condivide memoria con la centrale: chiede via MQTT il proprio
 * itinerario e la posizione attuale, poi percorre le tappe presentandosi alle
 * stazioni. Ogni ingresso e ogni uscita e' una richiesta MQTT che la centrale
 * autorizza (CONSENTITO) oppure rifiuta (BLOCCATO).
 *
 * Argomenti:
 *   <ID_TRENO>
 *
 * Esempi:
 *   T001
 *   T002
 */
public final class TrenoMain {

    private static final Duration ATTESA_TRA_STAZIONI = Duration.ofSeconds(2);
    private static final long PAUSA_RIPROVA_MS = 2000;

    private TrenoMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: TrenoMain <ID_TRENO>");
            throw new IllegalArgumentException("Argomenti non validi");
        }
        String id = args[0];

        // Il treno deve essere tra quelli configurati (config/treni.properties).
        ConfigurazioneTreni configurazione = ConfigurazioneTreni.carica();
        if (!configurazione.idTreni().contains(id)) {
            System.err.println("Treno non configurato: " + id);
            throw new IllegalArgumentException("ID treno non valido");
        }

        String broker = System.getenv().getOrDefault("MQTT_BROKER_URL", "ssl://localhost:8883");
        // Stesso ID client usato in passato dal treno: l'outbox su disco resta per treno.
        MqttService mqtt = new MqttService(broker, "treno-" + id);
        mqtt.connetti();
        mqtt.riceviNotificheTreno(id);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqtt.disconnetti();
            } catch (Exception ignorata) {
                // Chiusura best effort.
            }
        }, "arresto-treno-" + id));

        boolean completato = false;
        try {
            MessaggioViaggio viaggio = attendiViaggio(mqtt, id);
            if (viaggio == null) {
                return;
            }
            System.out.println("[TRENO " + id + "] Percorso " + viaggio.getIdPercorso()
                    + " | tappe=" + viaggio.getTappe()
                    + " | tappa corrente=" + viaggio.getIndiceTappa()
                    + " (" + viaggio.getStazioneCorrente() + ") | stato=" + viaggio.getStato());

            ProcessoTreno processo = new ProcessoTreno(
                    viaggio,
                    evento -> mqtt.inviaRichiestaRilevamentoEAttendiEsito(
                            evento.getIdTreno(), evento.getIdStazione(),
                            evento.getTipoMovimento(), evento.getIdRichiesta()),
                    ATTESA_TRA_STAZIONI);
            // Il viaggio si esegue nel thread principale: il processo termina con il viaggio.
            processo.run();
            completato = processo.isCompletato();
            System.out.println(completato
                    ? "Viaggio del treno " + id + " completato."
                    : "Viaggio del treno " + id + " non completato.");
        } finally {
            try {
                mqtt.disconnetti();
            } catch (Exception ignorata) {
                // Gia' disconnesso.
            }
        }
        if (!completato) {
            System.exit(1);
        }
    }

    /**
     * Chiede il viaggio finche' la centrale non risponde. Un disservizio temporaneo
     * (centrale non ancora avviata, rete) si riprova; un rifiuto esplicito
     * (treno soppresso o non registrato) termina il processo.
     */
    private static MessaggioViaggio attendiViaggio(MqttService mqtt, String id) throws InterruptedException {
        while (true) {
            try {
                MessaggioViaggio viaggio = mqtt.richiediViaggio(id);
                if (viaggio.getErrore() == null) {
                    return viaggio;
                }
                System.err.println("[TRENO " + id + "] La centrale rifiuta il viaggio: " + viaggio.getErrore());
                System.exit(1);
                return null;
            } catch (MqttNonDisponibileException e) {
                System.out.println("[TRENO " + id + "] Centrale non raggiungibile, riprovo: " + e.getMessage());
                Thread.sleep(PAUSA_RIPROVA_MS);
            }
        }
    }
}
