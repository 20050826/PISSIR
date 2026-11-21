
package sistemaFerroviario.process;

import sistemaFerroviario.communication.MqttService;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.model.Sensore;
import sistemaFerroviario.service.MonitorSensori;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Simula il servizio autonomo di una stazione: riceve i rilevamenti, controlla i sensori e comunica con MQTT.
 */
public class ProcessoStazione implements AutoCloseable {

    private final Stazione stazione;
    private final Consumer<String> inviaHeartbeat;
    private final Consumer<String> segnalaAnomalia;
    private final ScheduledExecutorService scheduler;
    // Ogni sensore simula un agente autonomo con il proprio task heartbeat.
    private final ScheduledExecutorService esecutoreSensori;
    private final MonitorSensori monitorSensori = new MonitorSensori();
    private volatile MqttService mqttSensori;
    private boolean connessioneBrokerPersa;
    // Ultimo avviso di guasto inviato a ciascun treno (evita un messaggio per ogni tentativo).
    private final java.util.concurrent.ConcurrentHashMap<String, Long> ultimoAvvisoGuasto =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long INTERVALLO_AVVISI_MS = 10_000;

    // Sensori per i quali abbiamo già inviato una segnalazione.
    private final Set<String> anomalieSegnalate = new HashSet<>();

    public ProcessoStazione(
            Stazione stazione,
            Consumer<String> inviaHeartbeat) {

        this(stazione, inviaHeartbeat, idSensore -> {
            System.out.println(
                    "Anomalia rilevata, ma nessuna centrale configurata: "
                    + idSensore);
        });
    }

    public ProcessoStazione(
            Stazione stazione,
            Consumer<String> inviaHeartbeat,
            Consumer<String> segnalaAnomalia) {

        this.stazione = Objects.requireNonNull(stazione);
        this.inviaHeartbeat = Objects.requireNonNull(inviaHeartbeat);
        this.segnalaAnomalia = Objects.requireNonNull(segnalaAnomalia);

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.esecutoreSensori = Executors.newScheduledThreadPool(
                Math.max(1, stazione.getSensori().size()));
        for (Sensore sensore : stazione.getSensori()) monitorSensori.registra(sensore.getId());
    }

    /** Il listener MQTT appartiene al processo della specifica stazione. */
    public void avviaGestioneRilevamenti(MqttService mqtt) throws MqttException {
        Objects.requireNonNull(mqtt, "Client MQTT della stazione obbligatorio");
        this.mqttSensori = mqtt;
        mqtt.riceviHeartbeatSensori(stazione.getId(), idSensore -> {
            if (stazione.contieneSensore(idSensore)) monitorSensori.ricevi(idSensore);
        });
        mqtt.riceviRichiesteRilevamentoConId(stazione.getId(), richiesta -> {
            String idTreno = richiesta.getIdTreno();
            sistemaFerroviario.model.TipoMovimento movimento = richiesta.getTipoMovimento();
            // Una qualsiasi anomalia impedisce di certificare il passaggio.
            boolean rilevato = !stazione.haSensoriInAnomalia()
                    && stazione.rilevaTreno();
            System.out.println("[STAZIONE " + stazione.getId() + "] Treno "
                    + idTreno + " | movimento=" + movimento
                    + " | sensore rilevato=" + rilevato);
            // La stazione segnala direttamente al treno che entra l'indisponibilita' (guasto
            // permanente o sensori in anomalia): il treno non riparte finche' non e' ripristinata.
            if (!stazione.isOperativa() || stazione.haSensoriInAnomalia()) {
                long ora = System.currentTimeMillis();
                Long precedente = ultimoAvvisoGuasto.get(idTreno);
                if (precedente == null || ora - precedente > INTERVALLO_AVVISI_MS) {
                    ultimoAvvisoGuasto.put(idTreno, ora);
                    try {
                        mqtt.pubblicaNotificaTreno(idTreno, "GUASTO | La stazione " + stazione.getId()
                                + " non e' disponibile: attendere il ripristino prima di ripartire.");
                    } catch (MqttException e) {
                        ultimoAvvisoGuasto.remove(idTreno);
                        System.err.println("[STAZIONE " + stazione.getId()
                                + "] Avviso di guasto al treno " + idTreno + " non inviato: " + e.getMessage());
                    }
                }
            }
            try {
                mqtt.pubblicaTransitoStazione(idTreno, stazione.getId(), rilevato,
                        movimento, richiesta.getIdRichiesta());
            } catch (MqttException e) {
                throw new IllegalStateException("Inoltro del transito fallito", e);
            }
        });
    }

    /** Attiva il monitoraggio periodico dei sensori della stazione. */
    public void avvia() {
        // I task sensore non si fermano se il task heartbeat della stazione continua.
        // FAIL_STOP disattiva soltanto il singolo task del sensore interessato.
        for (Sensore sensore : stazione.getSensori()) {
            esecutoreSensori.scheduleAtFixedRate(() -> {
                if (!sensore.isAttivo()) return;
                try {
                    MqttService mqtt = mqttSensori;
                    if (mqtt != null)
                        mqtt.pubblicaHeartbeatSensore(stazione.getId(), sensore.getId());
                    else monitorSensori.ricevi(sensore.getId());
                } catch (Exception e) {
                    System.err.println("[SENSORE " + sensore.getId() + "] Heartbeat fallito: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
        }
        System.out.println(
                "Processo stazione avviato: " + stazione.getId());

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Invio heartbeat
                inviaHeartbeat.accept(stazione.getId());

                System.out.println(
                        "Heartbeat inviato dalla stazione "
                        + stazione.getId());

                // 2. Controllo automatico dei sensori
                controllaSensori();

            } catch (RuntimeException e) {
                System.err.println(
                        "Errore nel processo della stazione "
                        + stazione.getId() + ": " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /** Identifica i sensori che hanno smesso di inviare heartbeat e notifica le anomalie. */
    private void controllaSensori() {
        // Non scambiare il down del broker MQTT per un guasto fisico dei sensori.
        MqttService mqtt = mqttSensori;
        if (mqtt != null && !mqtt.isConnesso()) {
            connessioneBrokerPersa = true;
            return;
        }
        if (connessioneBrokerPersa) {
            // Dopo il reconnect si concede ai sensori una nuova finestra di keepalive.
            for (Sensore sensore : stazione.getSensori()) monitorSensori.ricevi(sensore.getId());
            connessioneBrokerPersa = false;
            return;
        }
        Set<String> sensoriSilenziosi = monitorSensori.silenziosi(Duration.ofSeconds(5));
        for (Sensore sensore : stazione.getSensori()) {
            if (sensoriSilenziosi.contains(sensore.getId()) && !sensore.isInAnomalia()) {
                System.err.println("[FAIL-STOP SENSORE] Nessun heartbeat da "
                        + stazione.getId() + "/" + sensore.getId() + ": segnalazione guasto");
                sensore.segnalaAnomalia();
            }

            String idSensore = sensore.getId();

            if (sensore.isInAnomalia()) {

                // Segnala soltanto la prima volta.
                if (anomalieSegnalate.add(idSensore)) {
                    System.out.println(
                            "Monitoraggio: anomalia rilevata sul sensore "
                            + idSensore);

                    try {
                        segnalaAnomalia.accept(idSensore);
                    } catch (RuntimeException e) {
                        // Consente di ritentare al controllo successivo.
                        anomalieSegnalate.remove(idSensore);

                        System.err.println(
                                "Impossibile segnalare l'anomalia del sensore "
                                + idSensore + ": " + e.getMessage());
                    }
                }

            } else {
                // Se il sensore è stato ripristinato,
                // potrà essere segnalato in caso di un nuovo guasto.
                anomalieSegnalate.remove(idSensore);
            }
        }
    }

    /** Ripristina il sensore locale dopo il relativo comando di riparazione. */
    public void ripristinaSensore(String idSensore) {
        for (Sensore sensore : stazione.getSensori()) {
            if (sensore.getId().equals(idSensore)) {
                sensore.attiva();
                sensore.ripristina();
                monitorSensori.ricevi(idSensore);
                return;
            }
        }
        throw new IllegalArgumentException("Sensore non trovato: " + idSensore);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        esecutoreSensori.shutdownNow();

        System.out.println(
                "Processo stazione arrestato: " + stazione.getId());
    }
}
