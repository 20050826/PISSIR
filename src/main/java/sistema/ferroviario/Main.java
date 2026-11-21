package sistema.ferroviario;

import sistemaFerroviario.communication.MqttService;
import sistemaFerroviario.config.ConfigurazioneStazioni;
import sistemaFerroviario.config.ConfigurazioneTreni;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;

import sistemaFerroviario.model.Sensore;
import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.model.Treno;
import sistemaFerroviario.service.SistemaFerroviario;
import sistemaFerroviario.service.PianificatorePercorsi;
import sistemaFerroviario.persistence.ArchivioPostgres;
import sistemaFerroviario.persistence.StatoPersistito;
import sistemaFerroviario.centrale.CentraleOperativa;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.centrale.ApiRestCentrale;
import sistemaFerroviario.service.MonitorHeartbeat;
import sistemaFerroviario.service.ServizioMonitoraggio;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;



/**
 * Avvia la centrale, la dashboard e la simulazione ferroviaria.
 * Le stazioni sono nodi in JVM separate e vengono lette da stazioni.properties.
 * I treni configurati sono registrati senza sovrascrivere lo stato PostgreSQL.
 * I treni sono processi separati (TrenoMain) che comunicano solo via MQTT.
 */
public class Main {

    /** Inizializza lo stato, la Centrale e i servizi di monitoraggio.
     * I treni vengono avviati separatamente tramite TrenoMain.
     */
     public static void main(String[] args) throws Exception {

        System.out.println("=== AVVIO SISTEMA FERROVIARIO ===");
        // Le stazioni sono avviate separatamente tramite NodoStazioneMain.
        System.out.println("[ARCHITETTURA] Modalita' stazioni indipendenti attiva.");
        // Stato centrale persistito esclusivamente in PostgreSQL.
        SistemaFerroviario sistema = new SistemaFerroviario();

        Map<String, Instant> sosteGiaSegnalate =
                new ConcurrentHashMap<>();
        // Per ogni treno conserva l'ETA della tratta gia' segnalata,
        // cosi' si invia un solo allarme per tratta e non uno al secondo.
        Map<String, Instant> tratteGiaSegnalate = new ConcurrentHashMap<>();

        ScheduledExecutorService monitorSoste =
                Executors.newSingleThreadScheduledExecutor();

        ArchivioPostgres db = new ArchivioPostgres();
        db.inizializza();
        // Le stazioni e i treni sono definiti in file esterni alla compilazione.
        // Lavorare dalla cartella del progetto, dove si trova la directory config/.
        ConfigurazioneStazioni configurazioneStazioni = ConfigurazioneStazioni.carica();
        ConfigurazioneTreni configurazioneTreni = ConfigurazioneTreni.carica();

        StatoPersistito snapshot = db.carica();
        if (snapshot != null) {
            // Il database prevale sulla configurazione per gli oggetti gia' esistenti.
            sistema.ripristinaStato(snapshot);
            System.out.println("[PostgreSQL] Stato recuperato dal database.");
        } else {
            // Primo avvio: registra i nodi definiti in stazioni.properties.
            for (String idStazione : configurazioneStazioni.idStazioni()) {
                sistema.aggiungiStazione(
                        configurazioneStazioni.creaStazione(idStazione, false));
            }

            // Il percorso iniziale della demo resta invariato.
            Percorso percorso = new Percorso("P001", "Linea Centrale-Nord", null);
            percorso.aggiungiStazione("S01");
            percorso.aggiungiStazione("S02");
            sistema.aggiungiPercorso(percorso);
            System.out.println("[PostgreSQL] Stato iniziale creato.");
        }

        // Sincronizzazione non distruttiva: registra nuove stazioni e sensori,
        // senza sovrascrivere i guasti e gli stati gia' recuperati dal database.
        for (String idStazione : configurazioneStazioni.idStazioni()) {
            Stazione esistente = sistema.cercaStazione(idStazione);
            if (esistente == null) {
                sistema.aggiungiStazione(
                        configurazioneStazioni.creaStazione(idStazione, false));
                System.out.println("[CONFIG] Nuova stazione registrata: " + idStazione);
            } else {
                Stazione configurata =
                        configurazioneStazioni.creaStazione(idStazione, false);
                for (Sensore sensore : configurata.getSensori()) {
                    if (!esistente.contieneSensore(sensore.getId())) {
                        esistente.aggiungiSensori(sensore);
                    }
                }
            }
        }

        // I treni gia' salvati mantengono posizione, stato e percorso.
        // Vengono registrati solo i nuovi treni presenti nel file properties.
        for (String idTreno : configurazioneTreni.idTreni()) {
            if (sistema.cercaTreno(idTreno) != null) {
                continue;
            }

            String idStazione = configurazioneTreni.stazioneIniziale(idTreno);
            String idPercorso = configurazioneTreni.idPercorso(idTreno);
            if (sistema.cercaStazione(idStazione) == null) {
                throw new IllegalStateException("Stazione iniziale inesistente per "
                        + idTreno + ": " + idStazione);
            }
            if (sistema.cercaPercorso(idPercorso) == null) {
                throw new IllegalStateException("Percorso inesistente per "
                        + idTreno + ": " + idPercorso);
            }

            sistema.aggiungiTreno(configurazioneTreni.creaTreno(idTreno));
            System.out.println("[CONFIG] Nuovo treno registrato: " + idTreno
                    + " | percorso=" + idPercorso);
        }

        // I treni gia' persistiti mantengono stato e percorso.
        // Si preparano gli itinerari dei soli treni presenti nella configurazione,
        // senza assumere che esistano soltanto T001 e T002.
        Map<String, List<String>> tappePerTreno = new LinkedHashMap<>();
        Map<String, String> percorsiPerTreno = new LinkedHashMap<>();
        for (String idTreno : configurazioneTreni.idTreni()) {
            Treno treno = sistema.cercaTreno(idTreno);
            if (treno == null) {
                throw new IllegalStateException("Treno configurato ma non registrato: " + idTreno);
            }
            String idPercorso = treno.getIdPercorso();
            if (idPercorso == null || idPercorso.isBlank()) {
                throw new IllegalStateException("Percorso non assegnato a " + idTreno
                        + ": configurarlo tramite il gestionale e riavviare.");
            }
            List<String> tappe = PianificatorePercorsi.itinerarioAndataRitorno(sistema, idPercorso);
            if (tappe.size() < 2) {
                throw new IllegalStateException("Itinerario non valido per " + idTreno);
            }
            percorsiPerTreno.put(idTreno, idPercorso);
            tappePerTreno.put(idTreno, tappe);
        }
        System.out.println("[PostgreSQL] Percorsi assegnati: " + percorsiPerTreno);

        // Alimenta i sensori al riavvio senza cancellarne eventuali anomalie.
        // L'anomalia viene gestita separatamente dal flag di alimentazione.
        for (Stazione stazione : sistema.getStazioni()) {
            for (Sensore sensore : stazione.getSensori()) {
                sensore.attiva();
            }
        }

        // Prima scrittura solo dopo il caricamento completo, mai prima di leggere il DB.
        db.salva(sistema);

        ScheduledExecutorService sincronizzazioneDb = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "postgres-snapshot");
            t.setDaemon(true);
            return t;
        });
        sincronizzazioneDb.scheduleWithFixedDelay(() -> {
            try { db.salva(sistema); }
            catch (Exception e) { System.err.println("[PostgreSQL] Sincronizzazione fallita: " + e.getMessage()); }
        }, 5, 5, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { db.salva(sistema); }
            catch (Exception e) { System.err.println("[PostgreSQL] Salvataggio finale fallito: " + e.getMessage()); }
            sincronizzazioneDb.shutdownNow();
        }));
        System.out.println("[PostgreSQL] Persistenza primaria attiva; sincronizzazione ogni 5 secondi.");

        // Creazione della centrale operativa
        CentraleOperativa centraleOperativa =
                new CentraleOperativa(sistema, db);

        ApiRestCentrale.avvia(sistema, centraleOperativa);
        MqttService mqttCentrale = new MqttService(
                System.getenv().getOrDefault("MQTT_BROKER_URL", "ssl://localhost:8883"),
                "centrale"
        );

        ServizioMonitoraggio servizioMonitoraggio = null;

        try {
            mqttCentrale.connetti();
            // Il ripristino viene comunicato al nodo stazione indipendente via MQTT.
            centraleOperativa.configuraNotificaRipristino(guasto -> {
                try {
                    if (guasto.getIdSensore() == null) {
                        mqttCentrale.pubblicaComandoStazione(guasto.getIdStazione(), "RIPRISTINA");
                    } else {
                        mqttCentrale.pubblicaComandoSensore(guasto.getIdStazione(),
                                guasto.getIdSensore(), "RIPRISTINA");
                    }
                } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                    throw new IllegalStateException("Notifica MQTT di ripristino fallita", e);
                }
            });

            // La centrale riceve gli heartbeat e aggiorna il monitor.
            mqttCentrale.riceviHeartbeat(
                    idStazione -> sistema.getMonitorHeartbeat()
                            .riceviHeartbeat(idStazione)
            );

            MonitorHeartbeat monitorHeartbeat = sistema.getMonitorHeartbeat();

            servizioMonitoraggio =
                    new ServizioMonitoraggio(
                            monitorHeartbeat, Duration.ofSeconds(5), 1,
                            nonRaggiungibili -> {
                                for (Stazione stazione : sistema.getStazioni()) {
                                    boolean guastoPermanente = sistema.getGuasti().stream()
                                            .anyMatch(g -> g.getIdStazione().equals(stazione.getId())
                                                    && g.getIdSensore() == null
                                                    && g.getStato() != sistemaFerroviario.model.Guasto.StatoGuasto.RISOLTO);
                                    boolean operativa = !nonRaggiungibili.contains(stazione.getId())
                                            && monitorHeartbeat.stazioneRaggiungibile(stazione.getId(),
                                            Duration.ofSeconds(5)) && !guastoPermanente;
                                    if (stazione.isOperativa() != operativa) {
                                        stazione.impostaOperativa(operativa);
                                        centraleOperativa.salvaStato();
                                        System.out.println("[CENTRALE] " + stazione.getId() + ": "
                                                + (operativa ? "operativa" : "non operativa"));
                                    }
                                }
                            });

            servizioMonitoraggio.avvia();

            // La centrale riceve e controlla le richieste di transito.
            mqttCentrale.riceviTransiti(sistema, centraleOperativa::salvaStato);

            // La stazione segnala via MQTT i guasti permanenti.
            mqttCentrale.riceviGuasti(sistema, centraleOperativa::salvaStato);

            // La centrale riceve le anomalie dei sensori tramite MQTT,
            // senza essere chiamata direttamente dai processi delle stazioni.
            mqttCentrale.riceviAnomalieSensori(dati -> {
                Stazione stazioneSegnalante = sistema.cercaStazione(dati.getIdStazione());
                // La stazione esterna mantiene un sensore in un'altra JVM.
                // Il messaggio MQTT sincronizza lo stato presso la centrale.
                if (stazioneSegnalante != null
                        && stazioneSegnalante.contieneSensore(dati.getIdSensore())
                        && !stazioneSegnalante.sensoreInAnomalia(dati.getIdSensore())) {
                    stazioneSegnalante.segnalaAnomaliaSensore(dati.getIdSensore());
                }
                centraleOperativa.riceviSegnalazioneAnomalia(
                        stazioneSegnalante, dati.getIdSensore(), dati.getDescrizione());
                System.out.println("[CENTRALE MQTT] Anomalia gestita: stazione="
                        + dati.getIdStazione() + ", sensore=" + dati.getIdSensore());
            });

            // La centrale riceve e stampa gli allarmi MQTT.
            mqttCentrale.riceviAllarmi(allarme -> {
                // Inoltra a entrambi i treni gli avvisi di sicurezza della rete.
                // I guasti ai sensori e i possibili blocchi in tratta utilizzano
                // entrambi il topic centrale/allarmi/{stazione}.
                for (Treno trenoDaAvvisare : sistema.getTreni()) {
                    try {
                        mqttCentrale.pubblicaNotificaTreno(trenoDaAvvisare.getId(),
                                "ALLARME | Stazione " + allarme.getIdStazione()
                                        + " | " + allarme.getDescrizione());
                    } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                        System.err.println("[NOTIFICA] Allarme non inoltrato a "
                                + trenoDaAvvisare.getId() + ": " + e.getMessage());
                    }
                }
            });

            // All'avvio, registra le soste già in corso.
            for (Treno treno : sistema.getTreni()) {
                Instant arrivo = treno.getIstanteArrivoStazione();

                if (treno.getStato() == sistemaFerroviario.model.StatoTreno.IN_STAZIONE
                        && arrivo != null) {
                    sosteGiaSegnalate.put(treno.getId(), arrivo);
                }
            }

            monitorSoste.scheduleAtFixedRate(() -> {
                for (Treno treno : sistema.getTreni()) {
                    try {

                        // Un'assenza prolungata di rilevamenti sulla tratta
                        // e' un possibile blocco: l'ETA e' calcolata dalla
                        // centrale all'USCITA e azzerata all'INGRESSO.
                        // Soglia dimostrativa: ETA programmata + 3 secondi.
                        // Ldoe normali tratte durano 2 secondi.
                        if (treno.getStato() == sistemaFerroviario.model.StatoTreno.IN_VIAGGIO) {
                            Instant arrivoAtteso = treno.getArrivoProgrammato();
                            String partenza = treno.getStazionePrecedente();
                            String destinazione = treno.getProssimaStazione();
                            if (arrivoAtteso != null && partenza != null && !partenza.isBlank()
                                    && destinazione != null && !destinazione.isBlank()
                                    && Instant.now().isAfter(arrivoAtteso.plusSeconds(3))
                                    && !arrivoAtteso.equals(tratteGiaSegnalate.get(treno.getId()))) {
                                mqttCentrale.pubblicaAllarme(
                                        partenza,
                                        "TRATTA-" + treno.getId() + "-" + arrivoAtteso.toEpochMilli(),
                                        "POSSIBILE_BLOCCO_IN_TRATTA: il treno " + treno.getId()
                                                + " non ha raggiunto " + destinazione
                                                + " entro 3 secondi dall'arrivo programmato (partenza "
                                                + partenza + ").");
                                // Deduplica solo dopo pubblicazione MQTT riuscita.
                                tratteGiaSegnalate.put(treno.getId(), arrivoAtteso);
                                System.out.println("[ALLARME TRATTA] Possibile blocco del treno "
                                        + treno.getId() + " sulla tratta " + partenza
                                        + " -> " + destinazione);
                            }
                            continue; // Un treno in viaggio non e' in sosta alla stazione.
                        }

                        // Capolinea: una sosta dopo la fine del viaggio e' normale.
                        // Un allarme di sosta ha senso solo se si deve ripartire.
                        if (treno.getProssimaStazione() == null) {
                            continue;
                        }

                        // Nelle stazioni intermedie resta attiva la soglia di 30 secondi.
                        if (!treno.sostaSuperioreA30Secondi()) {
                            continue;
                        }

                        Instant arrivo = treno.getIstanteArrivoStazione();

                        if (arrivo == null) {
                            continue;
                        }

                        String idStazione = treno.getStazionePrecedente();

                        if (idStazione == null || idStazione.isBlank()) {
                            continue;
                        }

                        Instant precedente = sosteGiaSegnalate.get(treno.getId());

                        if (precedente != null) {
                            if (arrivo.equals(precedente)) {
                                continue;
                            }
                        }

                        // Pubblica l'allarme sulla stazione in cui si trova il treno.
                        mqttCentrale.pubblicaAllarme(
                                idStazione,
                                "SOSTA-" + treno.getId(),
                                "SOSTA_PROLUNGATA: il treno "
                                        + treno.getId()
                                        + " è fermo da oltre 30 secondi."
                        );

                        // Registra la sosta solo dopo la pubblicazione.
                        sosteGiaSegnalate.put(treno.getId(), arrivo);

                        System.out.println(
                                "[CONTROLLO SOSTA] Il treno " + treno.getId()
                                        + " supera i 30 secondi alla stazione " + idStazione
                        );

                        System.out.println(
                                "[ALLARME SOSTA] Allarme pubblicato per il treno "
                                        + treno.getId()
                                        + " alla stazione " + idStazione
                        );

                    } catch (Exception e) {
                        System.err.println(
                                "[ERRORE MONITOR SOSTE] "
                                        + e.getMessage()
                        );
                    }
                }
            }, 1, 1, TimeUnit.SECONDS);

            // Le stazioni potranno inviare gli heartbeat tramite MQTT.
            Thread.sleep(1000);

            Thread.sleep(1000);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Errore durante l'avvio della comunicazione MQTT.",
                    e
            );
        }



        // Le stazioni configurate sono avviate separatamente con NodoStazioneMain.
        System.out.println("[ARCHITETTURA] Stazioni indipendenti configurate: "
                + String.join(", ", configurazioneStazioni.idStazioni()));

        // Ogni convoglio segue il percorso configurato in PostgreSQL.
        // Le richieste attraversano MQTT e il relativo nodo stazione.
        for (Map.Entry<String, List<String>> voce : tappePerTreno.entrySet()) {
            Treno treno = sistema.cercaTreno(voce.getKey());
            preparaNuovoViaggioSeConcluso(treno, voce.getValue());
        }
        centraleOperativa.salvaStato();
        for (Map.Entry<String, List<String>> voce : tappePerTreno.entrySet()) {
            String idTreno = voce.getKey();
            System.out.println("[PERCORSO " + idTreno + "] "
                    + percorsiPerTreno.get(idTreno) + " | tappe=" + voce.getValue());
        }

        // I treni sono processi separati (TrenoMain): chiedono alla centrale il proprio
        // itinerario via MQTT e si presentano alle stazioni con le richieste di transito.
        mqttCentrale.riceviRichiesteViaggio(sistema);
        System.out.println("[ARCHITETTURA] Treni come processi indipendenti: avviare TrenoMain <ID> per "
                + String.join(", ", tappePerTreno.keySet()));

        centraleOperativa.salvaStato();
        System.out.println("Centrale in ascolto. Dashboard e monitoraggio attivi su https://localhost:4567");

        // Chiude i servizi di monitoraggio quando viene arrestata la JVM.
        final ServizioMonitoraggio monitorAttivo = servizioMonitoraggio;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitorSoste.shutdownNow();
            if (monitorAttivo != null) monitorAttivo.close();
        }, "arresto-monitor"));
    }

    /** Riprende una corsa incompleta; riavvia solo treni fermi o gia' al capolinea. */
    private static void preparaNuovoViaggioSeConcluso(Treno treno, List<String> tappe) {
        if (treno.getStato() == sistemaFerroviario.model.StatoTreno.FERMO
                || (treno.getStato() == sistemaFerroviario.model.StatoTreno.IN_STAZIONE
                && treno.getProssimaStazione() == null)) {
            treno.preparaPartenzaDa(tappe.get(0), tappe.get(1));
        }
    }
}
