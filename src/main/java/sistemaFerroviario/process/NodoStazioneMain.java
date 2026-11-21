
package sistemaFerroviario.process;

import sistemaFerroviario.communication.MqttService;
import sistemaFerroviario.config.ConfigurazioneStazioni;
import sistemaFerroviario.model.Stazione;

import java.util.concurrent.CountDownLatch;

/**
 * Eseguibile autonomo di un nodo stazione.
 *
 * Ogni stazione viene avviata in una JVM indipendente.
 * La configurazione viene letta da config/stazioni.properties.
 *
 * Il nodo comunica con la centrale esclusivamente tramite MQTT.
 */
public final class NodoStazioneMain {

    private NodoStazioneMain() {
    }

    /**
     * Avvia un nodo stazione autonomo.
     *
     * Argomenti:
     *   <ID_STAZIONE> [--guasto]
     *
     * Esempi:
     *   S01
     *   S02
     *   S03
     *   S03 --guasto
     */
    public static void main(String[] args) throws Exception {

        // Verifica degli argomenti di avvio.
        if (args.length < 1 || args.length > 2
                || (args.length == 2
                && !"--guasto".equals(args[1]))) {

            System.err.println(
                    "Uso: NodoStazioneMain <ID_STAZIONE> [--guasto]");

            throw new IllegalArgumentException(
                    "Argomenti non validi");
        }

        // Caricamento della configurazione esterna.
        ConfigurazioneStazioni configurazione =
                ConfigurazioneStazioni.carica();

        String id = args[0];

        // Verifica che la stazione sia configurata.
        if (!configurazione.idStazioni().contains(id)) {

            System.err.println(
                    "Stazione non configurata: " + id);

            throw new IllegalArgumentException(
                    "ID stazione non valido");
        }

        // Creazione della stazione e dei relativi sensori.
        Stazione stazione =
                configurazione.creaStazione(id, true);

        System.out.println(
                "[NODO " + id + "] Configurazione caricata.");

        System.out.println(
                "[NODO " + id + "] Sensori configurati: "
                        + stazione.getSensori().size());

        // Connessione MQTT dedicata alla stazione.
        String broker = System.getenv().getOrDefault(
                "MQTT_BROKER_URL",
                "ssl://localhost:8883"
        );

        MqttService mqtt =
                new MqttService(broker, "nodo-" + id);

        mqtt.connetti();

        try (ProcessoStazione nodo = new ProcessoStazione(

                stazione,

                // Invio heartbeat della stazione.
                idStazione -> {
                    try {
                        mqtt.pubblicaHeartbeat(idStazione);

                    } catch (
                            org.eclipse.paho.client.mqttv3.MqttException e) {

                        throw new IllegalStateException(
                                "Heartbeat fallito: " + idStazione, e);
                    }
                },

                // Segnalazione delle anomalie dei sensori.
                idSensore -> {
                    try {
                        mqtt.pubblicaAnomaliaSensore(
                                stazione.getId(),
                                idSensore,
                                "Anomalia rilevata automaticamente "
                                        + "dal monitoraggio"
                        );

                    } catch (
                            org.eclipse.paho.client.mqttv3.MqttException e) {

                        throw new IllegalStateException(
                                "Segnalazione guasto fallita: "
                                        + idSensore, e);
                    }
                }

        )) {

            // Ricezione dei comandi relativi ai sensori.
            mqtt.riceviComandiSensore(
                    id,
                    (idSensore, azione) -> {

                        if (!stazione.contieneSensore(idSensore)) {

                            System.err.println(
                                    "[" + id + "] Sensore sconosciuto: "
                                            + idSensore);
                            return;
                        }

                        if ("ANOMALIA".equals(azione)) {

                            stazione.segnalaAnomaliaSensore(idSensore);

                        } else if ("RIPRISTINA".equals(azione)) {

                            nodo.ripristinaSensore(idSensore);

                        } else if ("FAIL_STOP".equals(azione)) {

                            // Simulazione dell'interruzione
                            // degli heartbeat di un sensore.
                            stazione.getSensori()
                                    .stream()
                                    .filter(s ->
                                            idSensore.equals(s.getId()))
                                    .findFirst()
                                    .orElseThrow()
                                    .disattiva();

                            System.out.println(
                                    "[FAIL-STOP SENSORE] Simulato arresto di "
                                            + id + "/" + idSensore);
                        }

                        System.out.println(
                                "[" + id + "] Comando "
                                        + azione + " per " + idSensore);
                    }
            );

            // Ricezione dei comandi di ripristino stazione.
            mqtt.riceviComandiStazione(
                    id,
                    comando -> {

                        stazione.impostaOperativa(true);

                        System.out.println(
                                "[NODO " + id
                                        + "] Stazione ripristinata dal tecnico.");
                    }
            );

            // Avvio del rilevamento dei transiti.
            nodo.avviaGestioneRilevamenti(mqtt);

            // Avvio heartbeat e monitoraggio sensori.
            nodo.avvia();

            // Simulazione opzionale di guasto permanente.
            if (args.length == 2) {

                stazione.impostaOperativa(false);

                mqtt.pubblicaGuasto(
                        "GS-" + java.util.UUID.randomUUID(),
                        id,
                        "Guasto non temporaneo alla stazione " + id
                );

                System.err.println(
                        "[NODO " + id
                                + "] Guasto permanente segnalato alla centrale.");
            }

            System.out.println(
                    "[NODO " + id
                            + "] Attivo in JVM autonoma. "
                            + "Ctrl+C per arrestare.");

            // Chiusura ordinata alla terminazione del processo.
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {

                        nodo.close();

                        try {
                            mqtt.disconnetti();

                        } catch (Exception e) {

                            System.err.println(
                                    "Errore chiusura MQTT "
                                            + id + ": "
                                            + e.getMessage());
                        }

                    }, "shutdown-nodo-" + id)
            );

            // Console del nodo: scrivendo "guasto" si simula un guasto permanente a runtime
            // (equivale all'opzione --guasto all'avvio).
            Thread console = new Thread(() -> {
                try (java.util.Scanner in = new java.util.Scanner(System.in)) {
                    while (in.hasNextLine()) {
                        if (!"guasto".equalsIgnoreCase(in.nextLine().trim())) continue;
                        stazione.impostaOperativa(false);
                        try {
                            mqtt.pubblicaGuasto("GS-" + java.util.UUID.randomUUID(), id,
                                    "Guasto non temporaneo alla stazione " + id);
                            System.err.println("[NODO " + id + "] Guasto permanente segnalato alla centrale.");
                        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                            System.err.println("[NODO " + id + "] Segnalazione guasto fallita: " + e.getMessage());
                        }
                    }
                }
            }, "console-nodo-" + id);
            console.setDaemon(true);
            console.start();
            System.out.println("[NODO " + id + "] Digita 'guasto' + Invio per simulare un guasto permanente.");

            // Mantiene attivo il processo stazione.
            new CountDownLatch(1).await();
        }
    }
}
