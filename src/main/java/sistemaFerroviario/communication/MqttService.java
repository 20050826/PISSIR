package sistemaFerroviario.communication;

import com.fasterxml.jackson.databind.ObjectMapper; 
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.JsonProcessingException;

import sistemaFerroviario.communication.dto.MessaggioAllarme;
import sistemaFerroviario.communication.dto.MessaggioEsitoTransito;
import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.TipoMovimento;
import java.util.function.BiConsumer;
import java.util.Objects;
import sistemaFerroviario.service.SistemaFerroviario;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory; 
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import sistemaFerroviario.communication.dto.MessaggioHeartbeat;
import java.util.function.Consumer;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raccoglie le operazioni MQTT di pubblicazione e sottoscrizione. Gestisce il flusso treno-stazione-centrale e le risposte ai transiti.
 */
public class MqttService {
	private final String brokerUrl;
	private final String clientId;
	private MqttClient client;
    // Outbox per i messaggi applicativi QoS 1, persistente anche se il broker e offline.
    private final Queue<MessaggioInAttesa> bufferLocale = new ConcurrentLinkedQueue<>();
    private final Path outboxFile;
    private final ObjectMapper outboxMapper = new ObjectMapper();
    private final Map<String, IMqttMessageListener> sottoscrizioni = new ConcurrentHashMap<>();

    private record MessaggioInAttesa(String topic, String contenuto, int qos) {}
	
	public MqttService(String brokerUrl, String clientId) {
		this.brokerUrl = brokerUrl;
		this.clientId = clientId;
        if (!clientId.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Client MQTT non valido");
        this.outboxFile = Path.of("data", "mqtt-outbox", clientId + ".json");
        try {
            Files.createDirectories(outboxFile.getParent());
            if (Files.exists(outboxFile)) {
                MessaggioInAttesa[] precedenti = outboxMapper.readValue(
                        Files.readString(outboxFile), MessaggioInAttesa[].class);
                for (MessaggioInAttesa messaggio : precedenti) bufferLocale.add(messaggio);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Recupero outbox MQTT fallito: " + outboxFile, e);
        }
	}
	
	/** Apre la connessione TLS al broker; al ripristino ripete le sottoscrizioni e tenta di inviare i messaggi accodati. */
	public void connetti() throws MqttException{
		if (client != null) {
			return;
		}
		
		try {
            Path percorso = Path.of("data", "mqtt-paho");
            Files.createDirectories(percorso);
            client = new MqttClient(brokerUrl, clientId,
                    new MqttDefaultFilePersistence(percorso.toString()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Impossibile creare persistenza MQTT", e);
        }
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                	System.out.println("[MQTT] Riconnessione avvenuta: " + clientId);
                    Thread worker = new Thread(() -> {
                        try {
                            for (Map.Entry<String, IMqttMessageListener> entry : sottoscrizioni.entrySet()) {
                                client.subscribe(entry.getKey(), 1, entry.getValue());
                            }
                            svuotaBuffer();
                        } catch (MqttException e) {
                            System.err.println("Ripristino MQTT incompleto: " + e.getMessage());
                        }
                    }, "mqtt-recupero-" + clientId);
                    worker.setDaemon(true);
                    worker.start();
                }
            }
            @Override public void connectionLost(Throwable cause) {
                System.err.println("Connessione MQTT persa: " + cause);
            }
            @Override public void messageArrived(String topic, MqttMessage message) { }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
		MqttConnectOptions options = new MqttConnectOptions();
		options.setCleanSession(false);
		options.setConnectionTimeout(10);
		options.setKeepAliveInterval(30);
		options.setAutomaticReconnect(true);
        String username = System.getenv().getOrDefault("MQTT_USERNAME", "ferrovia");
        String password = System.getenv("MQTT_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Imposta la variabile MQTT_PASSWORD prima di avviare il programma.");
        }
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        if (brokerUrl.startsWith("ssl://")) {
            try {
                Path caPath = Path.of(System.getenv().getOrDefault("MQTT_CA_CERT", "infra/mosquitto/certs/ca.crt"));
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                X509Certificate ca;
                try (InputStream stream = Files.newInputStream(caPath)) {
                    ca = (X509Certificate) factory.generateCertificate(stream);
                }
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);
                trustStore.setCertificateEntry("ferrovia-ca", ca);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, tmf.getTrustManagers(), null);
                options.setSocketFactory(context.getSocketFactory());
                options.setHttpsHostnameVerificationEnabled(true);
            } catch (Exception e) {
                throw new IllegalStateException("Impossibile configurare MQTT TLS: " + e.getMessage(), e);
            }
        }
		
		client.connect(options);
        svuotaBuffer();
		
		System.out.println("Connesso al broker MQTT: "+brokerUrl);
		
	}
	
	public void sottoScrivi(String topic) throws MqttException{
		verificaConnessione();
		
		sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
			String contenuto = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
		
		
		System.out.println("\n--- Messaggio MQTT ricevuto ---");
		System.out.println("Topic: " + topicRicevuto);
		System.out.println("Contenuto: "+ contenuto);
		System.out.println("QoS: "+messaggio.getQos());
		
		});
		
		System.out.println("Sottoscritto al topic: "+topic);
		
	}
	
    /** QoS 1 con outbox su disco: la pubblicazione non e' mai dichiarata riuscita prima del broker. */
    public synchronized void pubblica(String topic, String contenuto, int qos) throws MqttException {
        if (qos != 1) throw new IllegalArgumentException("L'applicazione richiede QoS 1");
        // Gli heartbeat sono effimeri: non pubblicare messaggi di presenza vecchi al reconnect.
        if (topic.endsWith("/heartbeat") || topic.endsWith("/status")) {
            verificaConnessione();
            MqttMessage messaggio = new MqttMessage(contenuto.getBytes(StandardCharsets.UTF_8));
            messaggio.setQos(1);
            client.publish(topic, messaggio);
            return;
        }
        MessaggioInAttesa nuovo = new MessaggioInAttesa(topic, contenuto, qos);
        if (!bufferLocale.contains(nuovo)) {
            bufferLocale.add(nuovo);
            salvaOutbox();
        }
        svuotaBuffer();
        if (bufferLocale.contains(nuovo))
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
    }

    /** Scrittura atomica: anche un arresto del processo non tronca la coda su disco. */
    private void salvaOutbox() {
        try {
            Files.createDirectories(outboxFile.getParent());
            Path tmp = outboxFile.resolveSibling(outboxFile.getFileName() + ".tmp");
            Files.writeString(tmp, outboxMapper.writeValueAsString(new ArrayList<>(bufferLocale)));
            try {
                Files.move(tmp, outboxFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, outboxFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile persistere i messaggi MQTT in attesa", e);
        }
    }

    /** Reinvia in ordine i messaggi e li rimuove solo dopo conferma della publish. */
    private synchronized void svuotaBuffer() {
        while (isConnesso() && !bufferLocale.isEmpty()) {
            MessaggioInAttesa elemento = bufferLocale.peek();
            MqttMessage messaggio = new MqttMessage(elemento.contenuto().getBytes(StandardCharsets.UTF_8));
            messaggio.setQos(elemento.qos());
            try {
                client.publish(elemento.topic(), messaggio);
                bufferLocale.poll();
                salvaOutbox();
            } catch (MqttException e) {
                System.err.println("Messaggio MQTT conservato nell'outbox: " + e.getMessage());
                break;
            }
        }
    }

    private void sottoscriviConListener(String topic, int qos, IMqttMessageListener listener)
            throws MqttException {
        client.subscribe(topic, qos, listener);
        sottoscrizioni.put(topic, listener);
    }

	public boolean isConnesso() {
		return client != null && client.isConnected();
	}
	
	public void disconnetti() throws MqttException{
		if(client != null && client.isConnected()) {
			client.disconnect();
			client.close();
			
			System.out.println("Disconnesso dal broker MQTT");
		}
	}
	
	public void verificaConnessione() throws MqttException{
		if(!isConnesso()) {
			throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
			
		}
	}
	
	public static String generaClientId(String prefisso) {
		return prefisso + "-" + UUID.randomUUID().toString().substring(0, 8);
	}
	
	public void pubblicaTransito(String idTreno, String idStazione) throws MqttException {
		String topic = "stazione/" + idStazione + "/transito";
		String timestamp = java.time.Instant.now().toString();
		
		String contenuto = String.format("{\"idTreno\":\"%s\",\"idStazione\":\"%s\",\"timestamp\":\"%s\"}",
				idTreno,
				idStazione,
				timestamp
		);
		
		pubblica(topic,  contenuto, 1);
	}
	

    /** Il treno comunica la propria presenza alla stazione, non alla centrale. */
    public void pubblicaRichiestaRilevamento(String idTreno, String idStazione)
            throws MqttException {
        pubblicaRichiestaRilevamento(idTreno, idStazione, TipoMovimento.INGRESSO);
    }

    /** Il treno segnala la presenza alla stazione, che eseguira il rilevamento prima di coinvolgere la centrale. */
    public void pubblicaRichiestaRilevamento(String idTreno, String idStazione,
            TipoMovimento tipoMovimento) throws MqttException {
        pubblicaRichiestaRilevamento(idTreno, idStazione, tipoMovimento,
                UUID.randomUUID().toString());
    }

    public void pubblicaRichiestaRilevamento(String idTreno, String idStazione,
            TipoMovimento tipoMovimento, String idRichiesta) throws MqttException {
        if (idTreno == null || idTreno.isBlank()
                || idStazione == null || idStazione.isBlank()) {
            throw new IllegalArgumentException("Identificativi obbligatori.");
        }
        pubblica("stazione/" + idStazione + "/rilevamento",
                creaMessaggioTransito(idTreno, idStazione, null, tipoMovimento, idRichiesta), 1);
    }

    /** La stazione inoltra alla centrale il risultato del proprio sensore. */
    public void pubblicaTransitoStazione(String idTreno, String idStazione,
            boolean rilevato) throws MqttException {
        pubblicaTransitoStazione(idTreno, idStazione, rilevato, TipoMovimento.INGRESSO);
    }

    /** La stazione inoltra alla centrale un transito rilevato localmente dal proprio sensore. */
    public void pubblicaTransitoStazione(String idTreno, String idStazione,
            boolean rilevato, TipoMovimento tipoMovimento) throws MqttException {
        pubblicaTransitoStazione(idTreno, idStazione, rilevato, tipoMovimento, UUID.randomUUID().toString());
    }

    public void pubblicaTransitoStazione(String idTreno, String idStazione,
            boolean rilevato, TipoMovimento tipoMovimento, String idRichiesta) throws MqttException {
        pubblica("stazione/" + idStazione + "/transito",
                creaMessaggioTransito(idTreno, idStazione, rilevato, tipoMovimento, idRichiesta), 1);
    }

    private String creaMessaggioTransito(String idTreno, String idStazione,
            Boolean rilevato, TipoMovimento tipoMovimento, String idRichiesta) {
        MessaggioTransito dati = new MessaggioTransito();
        dati.setIdTreno(idTreno);
        dati.setIdRichiesta(idRichiesta);
        dati.setIdStazione(idStazione);
        dati.setTimestamp(Instant.now().toString());
        dati.setRilevato(rilevato);
        dati.setTipoMovimento(java.util.Objects.requireNonNull(tipoMovimento,
                "Tipo movimento obbligatorio"));
        try {
            return new ObjectMapper().writeValueAsString(dati);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serializzazione transito fallita", e);
        }
    }

    /** Heartbeat sensore -> nodo stazione via MQTT; QoS 1. */
    public void pubblicaHeartbeatSensore(String idStazione, String idSensore) throws MqttException {
        if (idStazione == null || idStazione.isBlank() || idSensore == null || idSensore.isBlank())
            throw new IllegalArgumentException("ID stazione/sensore obbligatori");
        pubblica("sensore/" + idStazione + "/" + idSensore + "/heartbeat", idSensore, 1);
    }

    /** La stazione ascolta i keepalive dei suoi sensori; filtra topic e payload. */
    public void riceviHeartbeatSensori(String idStazione, Consumer<String> gestore) throws MqttException {
        Objects.requireNonNull(gestore, "Gestore heartbeat sensori obbligatorio");
        verificaConnessione();
        if (idStazione == null || idStazione.isBlank())
            throw new IllegalArgumentException("ID stazione obbligatorio");
        String topic = "sensore/" + idStazione + "/+/heartbeat";
        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            String[] parti = topicRicevuto.split("/");
            if (parti.length != 4 || !"sensore".equals(parti[0])
                    || !idStazione.equals(parti[1]) || !"heartbeat".equals(parti[3])) return;
            String idSensore = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
            if (parti[2].equals(idSensore)) gestore.accept(idSensore);
        });
        System.out.println("[STAZIONE " + idStazione + "] Heartbeat sensori su " + topic);
    }

    /** Comando dal core a un nodo stazione remoto. */
    public void pubblicaComandoSensore(String idStazione, String idSensore, String azione)
            throws MqttException {
        if (idStazione == null || idStazione.isBlank() || idSensore == null
                || idSensore.isBlank() || !("ANOMALIA".equals(azione) || "RIPRISTINA".equals(azione) || "FAIL_STOP".equals(azione))) {
            throw new IllegalArgumentException("Dati del comando sensore non validi");
        }
        try {
            String payload = new ObjectMapper().writeValueAsString(
                    Map.of("idSensore", idSensore, "azione", azione));
            pubblica("stazione/" + idStazione + "/controllo-sensore", payload, 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serializzazione comando sensore fallita", e);
        }
    }

    /** Notifica al nodo la riparazione di un guasto non temporaneo di stazione. */
    public void pubblicaComandoStazione(String idStazione, String azione) throws MqttException {
        if (idStazione == null || idStazione.isBlank() || !"RIPRISTINA".equals(azione))
            throw new IllegalArgumentException("Comando stazione non valido");
        pubblica("stazione/" + idStazione + "/comando", azione, 1);
    }

    public void riceviComandiStazione(String idStazione, Consumer<String> gestore) throws MqttException {
        verificaConnessione();
        String topic = "stazione/" + idStazione + "/comando";
        sottoscriviConListener(topic, 1, (ricevuto, messaggio) -> {
            String comando = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
            if (topic.equals(ricevuto) && "RIPRISTINA".equals(comando)) gestore.accept(comando);
        });
    }

    /** Istruzioni MQTT per il sensore associato alla stazione. */
    public void riceviComandiSensore(String idStazione, BiConsumer<String, String> gestore)
            throws MqttException {
        verificaConnessione();
        Objects.requireNonNull(gestore);
        String topic = "stazione/" + idStazione + "/controllo-sensore";
        ObjectMapper mapper = new ObjectMapper();
        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            try {
                com.fasterxml.jackson.databind.JsonNode dati = mapper.readTree(messaggio.getPayload());
                String idSensore = dati.path("idSensore").asText();
                String azione = dati.path("azione").asText();
                if (!topic.equals(topicRicevuto) || idSensore.isBlank()
                        || !("ANOMALIA".equals(azione) || "RIPRISTINA".equals(azione) || "FAIL_STOP".equals(azione))) {
                    System.err.println("[STAZIONE] Comando sensore non valido");
                    return;
                }
                gestore.accept(idSensore, azione);
            } catch (Exception e) {
                System.err.println("[STAZIONE] Errore comando sensore: " + e.getMessage());
            }
        });
        System.out.println("[STAZIONE " + idStazione + "] Controllo sensori su " + topic);
    }

    /** Sottoscrizione specifica del nodo-stazione. */
    public void riceviRichiesteRilevamento(String idStazione,
            Consumer<String> gestore) throws MqttException {
        Objects.requireNonNull(gestore, "Gestore obbligatorio");
        riceviRichiesteRilevamento(idStazione, (idTreno, movimento) -> gestore.accept(idTreno));
    }

    public void riceviRichiesteRilevamento(String idStazione,
            BiConsumer<String, TipoMovimento> gestore) throws MqttException {
        Objects.requireNonNull(gestore);
        riceviRichiesteRilevamentoConId(idStazione,
                dati -> gestore.accept(dati.getIdTreno(), dati.getTipoMovimento()));
    }

    public void riceviRichiesteRilevamentoConId(String idStazione,
            Consumer<MessaggioTransito> gestore) throws MqttException {
        verificaConnessione();
        if (idStazione == null || idStazione.isBlank()) {
            throw new IllegalArgumentException("Stazione obbligatoria.");
        }
        Objects.requireNonNull(gestore, "Gestore rilevamenti obbligatorio");
        String topic = "stazione/" + idStazione + "/rilevamento";
        ObjectMapper mapper = new ObjectMapper();
        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            try {
                MessaggioTransito dati = mapper.readValue(
                        messaggio.getPayload(), MessaggioTransito.class);
                if (!topic.equals(topicRicevuto)
                        || !idStazione.equals(dati.getIdStazione())
                        || dati.getIdTreno() == null
                        || dati.getIdTreno().isBlank()) {
                    System.err.println("[STAZIONE] Richiesta di rilevamento non valida");
                    return;
                }
                TipoMovimento movimento = dati.getTipoMovimento() == null
                        ? TipoMovimento.INGRESSO : dati.getTipoMovimento();
                dati.setTipoMovimento(movimento);
                gestore.accept(dati);
            } catch (Exception e) {
                System.err.println("[STAZIONE] Errore rilevamento: " + e.getMessage());
            }
        });
        System.out.println("[STAZIONE " + idStazione + "] In ascolto su " + topic);
    }

	
	/** La centrale valida i transiti provenienti dalle stazioni, aggiorna lo stato e comunica il risultato al treno. */
	public void riceviTransiti(SistemaFerroviario sistema) throws MqttException {
	    riceviTransiti(sistema, () -> {});
	}

	/** Commit dello stato subito dopo ciascun transito approvato. */
	public void riceviTransiti(SistemaFerroviario sistema,
	        Runnable salvaDopoTransito) throws MqttException {
	    Objects.requireNonNull(salvaDopoTransito, "Salvataggio obbligatorio");
	    verificaConnessione();
	
	    ObjectMapper mapper = new ObjectMapper();
	    String topic = "stazione/+/transito";
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	
	        String contenuto = new String(
	            messaggio.getPayload(),
	            StandardCharsets.UTF_8
	        );
	
	        try {
	            MessaggioTransito dati = mapper.readValue(
	                contenuto,
	                MessaggioTransito.class
	            );
	
	            // Controlliamo che gli ID siano presenti.
	            if (dati.getIdTreno() == null
	                    || dati.getIdTreno().isBlank()
	                    || dati.getIdStazione() == null
	                    || dati.getIdStazione().isBlank()) {
	
	                System.err.println(
	                    "[ERRORE] Transito non valido: " + contenuto
	                );
	                return;
	            }
	
	            // Il topic deve essere stazione/{id}/transito.
	            String[] parti = topicRicevuto.split("/");
	
	            if (parti.length != 3
	                    || !"stazione".equals(parti[0])
	                    || !"transito".equals(parti[2])
	                    || !parti[1].equals(dati.getIdStazione())) {
	
	                System.err.println(
	                    "[ERRORE] Topic e stazione del transito "
	                    + "non corrispondono."
	                );
	                return;
	            }

                TipoMovimento movimento = dati.getTipoMovimento() == null
                        ? TipoMovimento.INGRESSO : dati.getTipoMovimento();

                // Usa l'istante del rilevamento comunicato dalla stazione.
                Instant istanteTransito =
                        dati.getTimestamp() == null || dati.getTimestamp().isBlank()
                                ? Instant.now()
                                : Instant.parse(dati.getTimestamp());

                EventoTransito evento = new EventoTransito(
                        dati.getIdTreno(),
                        dati.getIdStazione(),
                        istanteTransito,
                        movimento,
                        dati.getIdRichiesta());
	
	            try {
                // Un treno soppresso non transita piu': deve saperlo anche se la stazione
                // e' in guasto, altrimenti attenderebbe per sempre un ripristino.
                sistemaFerroviario.model.Treno trenoRichiedente = sistema.cercaTreno(dati.getIdTreno());
                if (trenoRichiedente != null
                        && trenoRichiedente.getStato() == sistemaFerroviario.model.StatoTreno.SOPPRESSO) {
                    throw new IllegalStateException("Treno " + dati.getIdTreno()
                            + " soppresso: transito non ammesso");
                }
                if (Boolean.FALSE.equals(dati.getRilevato())) {
                    throw new IllegalStateException(
                        "Rilevamento non valido: sensore o stazione non disponibili "
                        + "presso " + dati.getIdStazione() + "."
                    );
                }
                // Autorizza solo dopo che PostgreSQL ha confermato il salvataggio.
                // Se il DB non e' disponibile lo stato viene annullato e il
                // treno riceve BLOCCATO, senza un movimento fantasma in memoria.
                sistema.registraTransitoConSalvataggio(evento, salvaDopoTransito);

                // Ritardo accertato dalla centrale all'INGRESSO in stazione.
                if (movimento == TipoMovimento.INGRESSO) {
                    sistemaFerroviario.model.Treno treno = sistema.cercaTreno(dati.getIdTreno());
                    if (treno != null && treno.getRitardoSecondi() > 0) {
                        try {
                            pubblicaNotificaTreno(dati.getIdTreno(),
                                    "RITARDO | Arrivo a " + dati.getIdStazione()
                                            + " con " + treno.getRitardoSecondi() + " s di ritardo.");
                        } catch (MqttException erroreNotifica) {
                            System.err.println("[NOTIFICA] Ritardo rilevato, ma avviso MQTT non inviato: "
                                    + erroreNotifica.getMessage());
                        }
                    }
                }
	
	                // Se non viene sollevata un'eccezione, il transito
	                // è stato registrato.
	                pubblicaEsitoTransito(
	                    dati.getIdTreno(),
	                    dati.getIdStazione(),
	                    "CONSENTITO",
	                    "Transito registrato correttamente.",
                    movimento, dati.getIdRichiesta()
	                );
	
	            } catch (RuntimeException e) {
	
	                // Il sistema ha rifiutato la registrazione.
	                String motivo = e.getMessage();
	
	                if (motivo == null || motivo.isBlank()) {
	                    motivo = "Transito rifiutato dal sistema.";
	                }
	
	                System.err.println(
	                    "[TRANSITO BLOCCATO] Treno: "
	                    + dati.getIdTreno()
	                    + " | Stazione: " + dati.getIdStazione()
	                    + " | Motivo: " + motivo
	                );
	
	                try {
	                    pubblicaEsitoTransito(
	                        dati.getIdTreno(),
	                        dati.getIdStazione(),
	                        "BLOCCATO",
	                        motivo,
                        movimento, dati.getIdRichiesta()
	                    );
	
	                } catch (Exception erroreMqtt) {
	                    System.err.println(
	                        "[ERRORE] Impossibile inviare l'esito "
	                        + "del transito bloccato: "
	                        + erroreMqtt.getMessage()
	                    );
	                }
	            }
	
	        } catch (Exception e) {
	            System.err.println(
	                "[ERRORE] Elaborazione del transito: "
	                + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println(
	        "[CENTRALE] In ascolto sui transiti: " + topic
	    );
	}


	public void pubblicaHeartbeat(String idStazione)
	        throws MqttException {
	
	    String topic = "stazione/" + idStazione + "/heartbeat";
	    String timestamp = java.time.Instant.now().toString();
	
	    String contenuto = String.format(
	        "{\"idStazione\":\"%s\",\"timestamp\":\"%s\"}",
	        idStazione,
	        timestamp
	    );
	
	    pubblica(topic, contenuto, 1);
	}


	/** Ascolta gli heartbeat delle stazioni e li inoltra al monitor della centrale. */
	public void riceviHeartbeat(Consumer<String> gestore)
	        throws MqttException {
	
	    verificaConnessione();
	
	    com.fasterxml.jackson.databind.ObjectMapper mapper =
	            new com.fasterxml.jackson.databind.ObjectMapper();
	
	    String topic = "stazione/+/heartbeat";
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	        String contenuto = new String(
	            messaggio.getPayload(),
	            java.nio.charset.StandardCharsets.UTF_8
	        );
	
	        try {
	            MessaggioHeartbeat dati = mapper.readValue(
	                contenuto,
	                MessaggioHeartbeat.class
	            );
	
	            String idStazione = dati.getIdStazione();
	
	            if (idStazione == null || idStazione.isBlank()) {
	                System.err.println("Heartbeat non valido: " + contenuto);
	                return;
	            }
	
	            gestore.accept(idStazione);
	
	        } catch (Exception e) {
	            System.err.println(
	                "Errore durante la lettura dell'heartbeat: "
	                + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println("Centrale in ascolto su: " + topic);
	}
	


    /**
     * La stazione segnala alla centrale un sensore in anomalia tramite MQTT.
     * L'id e' un identificativo del MESSAGGIO, non il Gxxx assegnato dalla centrale.
     */
    public void pubblicaAnomaliaSensore(
            String idStazione, String idSensore, String descrizione) throws MqttException {
        if (idStazione == null || idStazione.isBlank()
                || idSensore == null || idSensore.isBlank()
                || descrizione == null || descrizione.isBlank()) {
            throw new IllegalArgumentException("Stazione, sensore e descrizione sono obbligatori.");
        }

        sistemaFerroviario.communication.dto.MessaggioGuasto dati =
                new sistemaFerroviario.communication.dto.MessaggioGuasto();
        dati.setId("ANOM-" + UUID.randomUUID());
        dati.setIdStazione(idStazione);
        dati.setIdSensore(idSensore);
        dati.setDescrizione(descrizione);
        dati.setTimestamp(Instant.now().toString());

        try {
            String contenuto = new ObjectMapper().writeValueAsString(dati);
            pubblica("stazione/" + idStazione + "/guasto", contenuto, 1);
            System.out.println("[STAZIONE " + idStazione
                    + "] Anomalia del sensore " + idSensore + " segnalata via MQTT");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile serializzare l'anomalia del sensore", e);
        }
    }

    /**
     * La centrale ascolta le anomalie dei sensori inviate dalle stazioni.
     * La registrazione e il salvataggio vengono eseguiti dal gestore fornito da Main.
     */
    public void riceviAnomalieSensori(
            Consumer<sistemaFerroviario.communication.dto.MessaggioGuasto> gestore)
            throws MqttException {
        Objects.requireNonNull(gestore, "Gestore delle anomalie obbligatorio");
        verificaConnessione();

        ObjectMapper mapper = new ObjectMapper();
        java.util.Set<String> messaggiGestiti = ConcurrentHashMap.newKeySet();
        String topic = "stazione/+/guasto";

        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            String contenuto = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
            try {
                sistemaFerroviario.communication.dto.MessaggioGuasto dati = mapper.readValue(
                        contenuto, sistemaFerroviario.communication.dto.MessaggioGuasto.class);

                if (dati.getId() == null || dati.getId().isBlank()
                        || dati.getIdStazione() == null || dati.getIdStazione().isBlank()
                        || dati.getIdSensore() == null || dati.getIdSensore().isBlank()
                        || dati.getDescrizione() == null || dati.getDescrizione().isBlank()) {
                    System.err.println("[MQTT GUASTO] Messaggio di anomalia incompleto: " + contenuto);
                    return;
                }
                if (!("stazione/" + dati.getIdStazione() + "/guasto").equals(topicRicevuto)) {
                    System.err.println("[MQTT GUASTO] Topic e ID stazione non corrispondono");
                    return;
                }
                if (!messaggiGestiti.add(dati.getId())) {
                    System.out.println("[MQTT GUASTO] Messaggio duplicato ignorato: " + dati.getId());
                    return;
                }
                try {
                    gestore.accept(dati);
                } catch (RuntimeException e) {
                    // Il guasto non e' stato gestito correttamente: consenti un eventuale reinvio.
                    messaggiGestiti.remove(dati.getId());
                    throw e;
                }
            } catch (Exception e) {
                System.err.println("[MQTT GUASTO] Errore nella gestione dell'anomalia: "
                        + e.getMessage());
            }
        });

        System.out.println("[CENTRALE] In ascolto sui guasti: " + topic);
    }

	public void pubblicaGuasto(
	        String idGuasto,
	        String idStazione,
	        String descrizione) throws MqttException {
	
	    if (idGuasto == null || idGuasto.isBlank()
	            || idStazione == null || idStazione.isBlank()
	            || descrizione == null || descrizione.isBlank()) {
	        throw new IllegalArgumentException(
	            "ID guasto, ID stazione e descrizione sono obbligatori."
	        );
	    }
	
	    String topic = "stazione/" + idStazione + "/guasto-permanente";
	
	    String timestamp = java.time.Instant.now().toString();
	
	    try {
	        com.fasterxml.jackson.databind.ObjectMapper mapper =
	                new com.fasterxml.jackson.databind.ObjectMapper();
	
	        sistemaFerroviario.communication.dto.MessaggioGuasto dati =
	                new sistemaFerroviario.communication.dto.MessaggioGuasto();
	
	        dati.setId(idGuasto);
	        dati.setIdStazione(idStazione);
	        dati.setDescrizione(descrizione);
	        dati.setTimestamp(timestamp);
	
	        String contenuto = mapper.writeValueAsString(dati);
	
	        pubblica(topic, contenuto, 1);
	
	    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
	        throw new IllegalStateException(
	            "Impossibile serializzare il messaggio del guasto.", e
	        );
	    }
	    
	}
	
	
	public void riceviGuasti(sistemaFerroviario.service.SistemaFerroviario sistema) throws MqttException {
        riceviGuasti(sistema, () -> {});
    }

    public void riceviGuasti(sistemaFerroviario.service.SistemaFerroviario sistema,
            Runnable salvaDopoGuasto) throws MqttException {
        Objects.requireNonNull(salvaDopoGuasto);
        verificaConnessione();
	
	    com.fasterxml.jackson.databind.ObjectMapper mapper =
	            new com.fasterxml.jackson.databind.ObjectMapper();
	
	    String topic = "stazione/+/guasto-permanente";
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	
	        String contenuto = new String(
	            messaggio.getPayload(),
	            java.nio.charset.StandardCharsets.UTF_8
	        );
	
	        try {
	            sistemaFerroviario.communication.dto.MessaggioGuasto dati =
	                    mapper.readValue(
	                        contenuto,
	                        sistemaFerroviario.communication.dto.MessaggioGuasto.class
	                    );
	
	            if (dati.getId() == null || dati.getId().isBlank()
	                    || dati.getIdStazione() == null
	                    || dati.getIdStazione().isBlank()
	                    || dati.getDescrizione() == null
	                    || dati.getDescrizione().isBlank()) {
	
	                System.err.println(
	                    "Messaggio di guasto non valido: " + contenuto
	                );
	                return;
	            }
	
	            // Controlliamo che il topic corrisponda alla stazione dichiarata.
	            String[] parti = topicRicevuto.split("/");
	
	            if (parti.length != 3
	                    || !parti[1].equals(dati.getIdStazione())) {
	                System.err.println(
	                    "Topic e stazione del messaggio non corrispondono."
	                );
	                return;
	            }
	

                if (sistema.cercaGuasto(dati.getId()) != null) return;
				sistema.registraGuasto(
				    new sistemaFerroviario.model.Guasto(
				        dati.getId(),
				        dati.getIdStazione(),
				        dati.getDescrizione()
				    )
				);
                sistema.cercaStazione(dati.getIdStazione()).impostaOperativa(false);
                salvaDopoGuasto.run();
				
				System.out.println(
				    "[NUOVO GUASTO] " + dati.getId()
				    + " presso " + dati.getIdStazione()
				    + ": " + dati.getDescrizione()
				);
				
				// Il guasto è stato registrato: pubblichiamo l'allarme.
				try {
				    pubblicaAllarme(
				        dati.getIdStazione(),
				        dati.getId(),
				        dati.getDescrizione()
				    );
				
				} catch (MqttException e) {
				    System.err.println(
				        "[ERRORE MQTT] Guasto registrato, "
				        + "ma allarme non pubblicato: " + e.getMessage()
				    );
				}

	        } catch (Exception e) {
	            System.err.println(
	                "Errore nella gestione del guasto: " + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println(
	        "Centrale in ascolto sui guasti: " + topic
	    );
	}
	

	public void pubblicaAllarme(
	        String idStazione,
	        String idGuasto,
	        String descrizione) throws MqttException {
	
	    pubblicaEventoGuasto(
	        idStazione,
	        idGuasto,
	        descrizione,
	        "ALLARME",
	        "centrale/allarmi/"
	    );
	}
	

	private void pubblicaEventoGuasto(
	        String idStazione,
	        String idGuasto,
	        String descrizione,
	        String evento,
	        String prefissoTopic) throws MqttException {
	
	    if (idStazione == null || idStazione.isBlank()
	            || idGuasto == null || idGuasto.isBlank()
	            || descrizione == null || descrizione.isBlank()) {
	
	        throw new IllegalArgumentException(
	            "Stazione, guasto e descrizione sono obbligatori."
	        );
	    }
	
	    MessaggioAllarme dati = new MessaggioAllarme(
	        idStazione,
	        idGuasto,
	        descrizione,
	        Instant.now().toString(),
	        evento
	    );
	
	    try {
	        ObjectMapper mapper = new ObjectMapper();
	        String json = mapper.writeValueAsString(dati);
	
	        String topic = prefissoTopic + idStazione;
	
	        pubblica(topic, json, 1);
	
	        System.out.println(
	            "[EVENTO MQTT] " + evento
	            + " | Stazione: " + idStazione
	            + " | Guasto: " + idGuasto
	        );
	
	    } catch (JsonProcessingException e) {
	        throw new IllegalStateException(
	            "Impossibile convertire l'evento in JSON.",
	            e
	        );
	    }
	}

	
	public void pubblicaPresaInCarico(
	        String idStazione,
	        String idGuasto,
	        String descrizione) throws MqttException {

	    pubblicaEventoGuasto(
	        idStazione,
	        idGuasto,
	        descrizione,
	        "IN_GESTIONE",
	        "centrale/allarmi/"
	    );
	}
	
	public void pubblicaRipristino(
	        String idStazione,
	        String idGuasto,
	        String descrizione) throws MqttException {

	    pubblicaEventoGuasto(
	        idStazione,
	        idGuasto,
	        descrizione,
	        "RIPRISTINO",
	        "centrale/ripristino/"
	    );
	}


    /** La centrale invia notifiche mirate a un treno sul suo topic MQTT. */
    public void pubblicaNotificaTreno(String idTreno, String testo) throws MqttException {
        if (idTreno == null || !idTreno.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("ID treno non valido");
        }
        if (testo == null || testo.isBlank()) {
            throw new IllegalArgumentException("Testo della notifica obbligatorio");
        }
        pubblica("centrale/treni/" + idTreno + "/notifiche", testo, 1);
    }

    /** Il client MQTT del treno riceve solo le proprie notifiche. */
    public void riceviNotificheTreno(String idTreno) throws MqttException {
        verificaConnessione();
        if (idTreno == null || !idTreno.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("ID treno non valido");
        }
        String topic = "centrale/treni/" + idTreno + "/notifiche";
        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            String testo = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
            if (!testo.isBlank()) {
                System.out.println("[NOTIFICA TRENO " + idTreno + "] " + testo);
            }
        });
        System.out.println("[TRENO " + idTreno + "] Notifiche MQTT attive su " + topic);
    }

    /** Riceve gli allarmi MQTT; puo anche inoltrarli a un gestore per notificare i treni. */
    public void riceviAllarmi() throws MqttException {
        riceviAllarmi(null);
    }

    /** La centrale gestisce gli allarmi e puo' inoltrarli ai treni interessati. */
    public void riceviAllarmi(Consumer<MessaggioAllarme> gestore) throws MqttException {
	
	    verificaConnessione();
	
	    ObjectMapper mapper = new ObjectMapper();
	    String topic = "centrale/allarmi/+";
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	
	        String json = new String(
	            messaggio.getPayload(),
	            StandardCharsets.UTF_8
	        );
	
	        try {
	            MessaggioAllarme dati =
	                mapper.readValue(json, MessaggioAllarme.class);
	
	            String prefisso = "centrale/allarmi/";
	            String idDalTopic =
	                topicRicevuto.substring(prefisso.length());
	
	            if (dati.getIdStazione() == null
	                    || dati.getIdStazione().isBlank()
	                    || dati.getIdGuasto() == null
	                    || dati.getIdGuasto().isBlank()
	                    || dati.getEvento() == null
	                    || !idDalTopic.equals(dati.getIdStazione())) {
	
	                System.err.println(
	                    "[ERRORE] Messaggio di allarme non valido."
	                );
	                return;
	            }
	
	            System.out.println(
	                "[ALLARME RICEVUTO] Evento: " + dati.getEvento()
	                + " | Stazione: " + dati.getIdStazione()
	                + " | Guasto: " + dati.getIdGuasto()
	                + " | Descrizione: " + dati.getDescrizione()
	            );
	
            if (gestore != null) {
                gestore.accept(dati);
            }
	        } catch (Exception e) {
	            System.err.println(
	                "[ERRORE] Lettura allarme: " + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println(
	        "[MQTT] In ascolto sugli allarmi della centrale."
	    );
	}
	

	public void riceviRipristini() throws MqttException {
	
	    verificaConnessione();
	
	    ObjectMapper mapper = new ObjectMapper();
	    String topic = "centrale/ripristino/+";
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	
	        String json = new String(
	            messaggio.getPayload(),
	            StandardCharsets.UTF_8
	        );
	
	        try {
	            MessaggioAllarme dati =
	                mapper.readValue(json, MessaggioAllarme.class);
	
	            String prefisso = "centrale/ripristino/";
	            String idDalTopic =
	                topicRicevuto.substring(prefisso.length());
	
	            if (dati.getIdStazione() == null
	                    || dati.getIdStazione().isBlank()
	                    || dati.getIdGuasto() == null
	                    || dati.getIdGuasto().isBlank()
	                    || !"RIPRISTINO".equals(dati.getEvento())
	                    || !idDalTopic.equals(dati.getIdStazione())) {
	
	                System.err.println(
	                    "[ERRORE] Messaggio di ripristino non valido."
	                );
	                return;
	            }
	
	            System.out.println(
	                "[RIPRISTINO RICEVUTO] Stazione: "
	                + dati.getIdStazione()
	                + " | Guasto: " + dati.getIdGuasto()
	                + " | Descrizione: " + dati.getDescrizione()
	            );
	
	        } catch (Exception e) {
	            System.err.println(
	                "[ERRORE] Lettura ripristino: " + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println(
	        "[MQTT] In ascolto sui ripristini della centrale."
	    );
	}

	
	/** Pubblica sul topic dedicato al treno la decisione della centrale. */
	public void pubblicaEsitoTransito(
	        String idTreno,
	        String idStazione,
	        String esito,
	        String motivo) throws MqttException {
        pubblicaEsitoTransito(idTreno, idStazione, esito, motivo, null);
    }

    /** Pubblica sul topic dedicato al treno la decisione della centrale. */
    public void pubblicaEsitoTransito(
            String idTreno, String idStazione, String esito, String motivo,
            TipoMovimento tipoMovimento) throws MqttException {
        pubblicaEsitoTransito(idTreno, idStazione, esito, motivo, tipoMovimento, null);
    }

    public void pubblicaEsitoTransito(String idTreno, String idStazione, String esito,
            String motivo, TipoMovimento tipoMovimento, String idRichiesta) throws MqttException {

	    if (idTreno == null || idTreno.isBlank()
	            || idStazione == null || idStazione.isBlank()
	            || esito == null || esito.isBlank()
	            || motivo == null || motivo.isBlank()) {
	
	        throw new IllegalArgumentException(
	            "I dati dell'esito del transito sono obbligatori."
	        );
	    }
	
	    if (!"CONSENTITO".equals(esito)
	            && !"BLOCCATO".equals(esito)) {
	
	        throw new IllegalArgumentException(
	            "Esito non valido: " + esito
	        );
	    }
	
	    MessaggioEsitoTransito dati =
	        new MessaggioEsitoTransito(
	            idTreno,
	            idStazione,
	            esito,
	            motivo,
	            Instant.now().toString()
	        );
	
        dati.setTipoMovimento(tipoMovimento);
        dati.setIdRichiesta(idRichiesta);

	    try {
	        ObjectMapper mapper = new ObjectMapper();
	
	        String json = mapper.writeValueAsString(dati);
	
	        String topic = "centrale/transiti/esito/" + idTreno;
	
	        pubblica(topic, json, 1);
	
	        System.out.println(
	            "[ESITO TRANSITO] " + esito
	            + " | Treno: " + idTreno
	            + " | Stazione: " + idStazione
	            + " | Motivo: " + motivo
	        );
	
	    } catch (JsonProcessingException e) {
	        throw new IllegalStateException(
	            "Impossibile convertire l'esito del transito in JSON.",
	            e
	        );
	    }
	}
	

	/** Prepara la ricezione delle risposte della centrale sui transiti. */
	public void riceviEsitiTransito(
	        String idTreno) throws MqttException {
	
	    if (idTreno == null || idTreno.isBlank()) {
	        throw new IllegalArgumentException(
	            "L'ID del treno è obbligatorio."
	        );
	    }
	
	    verificaConnessione();
	
	    ObjectMapper mapper = new ObjectMapper();
	
	    String topic = "centrale/transiti/esito/" + idTreno;
	
	    sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
	
	        String contenuto = new String(
	            messaggio.getPayload(),
	            StandardCharsets.UTF_8
	        );
	
	        try {
	            MessaggioEsitoTransito dati =
	                mapper.readValue(
	                    contenuto,
	                    MessaggioEsitoTransito.class
	                );
	
	            if (!idTreno.equals(dati.getIdTreno())
	                    || !topic.equals(topicRicevuto)) {
	
	                System.err.println(
	                    "[ERRORE] Esito ricevuto su topic non valido."
	                );
	                return;
	            }
	
	            System.out.println();
	            System.out.println("====== ESITO DEL TRANSITO ======");
	            System.out.println("Treno: " + dati.getIdTreno());
	            System.out.println("Stazione: " + dati.getIdStazione());
	            System.out.println("Esito: " + dati.getEsito());
	            System.out.println("Motivo: " + dati.getMotivo());
	            System.out.println("Timestamp: " + dati.getTimestamp());
	            System.out.println("================================");
	
	        } catch (Exception e) {
	            System.err.println(
	                "[ERRORE] Lettura esito transito: "
	                + e.getMessage()
	            );
	        }
	    });
	
	    System.out.println(
	        "[TRENO " + idTreno + "] In ascolto su " + topic
	    );
	}
	
    public void inviaTransitoEAttendiEsito(String idTreno, String idStazione) {
        inviaTransitoConEsito(idTreno, idStazione, false, TipoMovimento.INGRESSO, null);
    }

    /** Flusso completo del progetto: treno -> stazione -> centrale -> treno. */
    public void inviaRichiestaRilevamentoEAttendiEsito(String idTreno, String idStazione) {
        inviaRichiestaRilevamentoEAttendiEsito(idTreno, idStazione, TipoMovimento.INGRESSO);
    }

    public void inviaRichiestaRilevamentoEAttendiEsito(String idTreno, String idStazione,
            TipoMovimento tipoMovimento) {
        inviaRichiestaRilevamentoEAttendiEsito(idTreno, idStazione, tipoMovimento,
                UUID.randomUUID().toString());
    }

    public void inviaRichiestaRilevamentoEAttendiEsito(String idTreno, String idStazione,
            TipoMovimento tipoMovimento, String idRichiesta) {
        inviaTransitoConEsito(idTreno, idStazione, true,
                java.util.Objects.requireNonNull(tipoMovimento, "Tipo movimento obbligatorio"), idRichiesta);
    }

    /** Attiva prima l ascolto della risposta, poi inoltra il rilevamento e attende l esito per non procedere senza consenso. */
    private void inviaTransitoConEsito(String idTreno, String idStazione,
            boolean tramiteStazione, TipoMovimento tipoMovimento, String idRichiesta) {
	    verificaDatiTransito(idTreno, idStazione);
	
	    String topicEsito = "centrale/transiti/esito/" + idTreno;
	
	    CountDownLatch confermaRicevuta = new CountDownLatch(1);
	    AtomicReference<String> esito = new AtomicReference<>();
	    AtomicReference<String> motivo = new AtomicReference<>();
	
	    ObjectMapper mapper = new ObjectMapper();
	
	    try {
	        // Prima ci mettiamo in ascolto della risposta della centrale.
	        sottoscriviConListener(topicEsito, 1, (topic, messaggio) -> {
	            try {
	                String contenuto = new String(
	                    messaggio.getPayload(),
	                    StandardCharsets.UTF_8
	                );
	
	                MessaggioEsitoTransito dati = mapper.readValue(
	                    contenuto,
	                    MessaggioEsitoTransito.class
	                );
	
	                if (topicEsito.equals(topic)
	                        && idTreno.equals(dati.getIdTreno())
	                        && idStazione.equals(dati.getIdStazione())
                            && java.util.Objects.equals(idRichiesta, dati.getIdRichiesta())
	                        && ("CONSENTITO".equals(dati.getEsito())
	                            || "BLOCCATO".equals(dati.getEsito()))
                        && (dati.getTipoMovimento() == tipoMovimento
                            || (dati.getTipoMovimento() == null
                                && tipoMovimento == TipoMovimento.INGRESSO))) {
	
	                    esito.set(dati.getEsito());
	                    motivo.set(dati.getMotivo());
	                    confermaRicevuta.countDown();
	                }
	
	            } catch (Exception e) {
	                System.err.println(
	                    "[ERRORE] Lettura conferma MQTT: "
	                    + e.getMessage()
	                );
	            }
	        });
	
	        // Solo dopo la sottoscrizione inviamo la richiesta.
            if (tramiteStazione) {
                pubblicaRichiestaRilevamento(idTreno, idStazione, tipoMovimento, idRichiesta);
            } else {
                pubblicaTransito(idTreno, idStazione);
            }
	
	        // Attendiamo la decisione della centrale.
	        boolean ricevuta = confermaRicevuta.await(
	            5, TimeUnit.SECONDS
	        );
	
	        if (!ricevuta) {
	            throw new MqttNonDisponibileException(
	                "Nessuna risposta dalla centrale per il treno "
	                + idTreno + " alla stazione " + idStazione
	            );
	        }
	
	        if ("BLOCCATO".equals(esito.get())) {
	            throw new TransitoBloccatoException(motivo.get());
	        }
	
	        if (!"CONSENTITO".equals(esito.get())) {
	            throw new IllegalStateException(
	                "Esito del transito non valido."
	            );
	        }
	
	        System.out.println(
	            "[TRENO " + idTreno + "] Transito confermato dalla centrale."
	        );
	
	    } catch (MqttException e) {
	        throw new MqttNonDisponibileException(
	            "Errore MQTT durante l'invio del transito.", e
	        );
	
	    } catch (InterruptedException e) {
	        Thread.currentThread().interrupt();
	
	        throw new IllegalStateException(
	            "Attesa della conferma MQTT interrotta.", e
	        );
	    }
	}

	private void verificaDatiTransito(
	        String idTreno,
	        String idStazione) {
	
	    if (idTreno == null || idTreno.isBlank()
	            || idStazione == null || idStazione.isBlank()) {
	        throw new IllegalArgumentException(
	            "Treno e stazione sono obbligatori."
	        );
	    }
	
	    verificaConnessioneSenzaEccezione();
	}
	
    /**
     * I client dei treni possono non essere ancora riconnessi quando la centrale
     * e le stazioni hanno gia' ripristinato i loro heartbeat. Prima di avviare
     * un nuovo transito, aspettiamo il recupero automatico di Paho; se non
     * avviene, effettuiamo un singolo tentativo esplicito di riconnessione.
     * Un transito non viene pubblicato finche' non esiste una connessione.
     */
    private void verificaConnessioneSenzaEccezione() {
        if (isConnesso()) {
            return;
        }
        if (client == null) {
            throw new MqttNonDisponibileException(
                "Client MQTT " + clientId + " non inizializzato."
            );
        }

        System.out.println("[MQTT] Client " + clientId
                + " disconnesso: attendo la riconnessione automatica...");
        attendeConnessionePer(8);

        if (!isConnesso()) {
            try {
                // Se Paho ha ancora una riconnessione in corso, puo' rifiutare
                // questa chiamata: in tal caso continuiamo ad attendere.
                System.out.println("[MQTT] Tentativo di riconnessione esplicito: " + clientId);
                client.reconnect();
            } catch (MqttException e) {
                System.err.println("[MQTT] Riconnessione esplicita non completata per "
                        + clientId + ": " + e.getMessage());
            }
            attendeConnessionePer(12);
        }

        if (!isConnesso()) {
            throw new MqttNonDisponibileException(
                "Client MQTT " + clientId + " ancora disconnesso dopo il tentativo di recupero."
            );
        }
        System.out.println("[MQTT] Client pronto per il transito: " + clientId);
    }

    private void attendeConnessionePer(int secondi) {
        long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(secondi);
        while (!isConnesso() && System.nanoTime() < scadenza) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Attesa riconnessione MQTT interrotta.", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Richiesta del viaggio: il treno e' un processo separato e non condivide
    // memoria con la centrale. Chiede via MQTT itinerario e posizione e li
    // riceve dalla centrale, che li legge dal proprio database.
    //   treno    -> centrale/treni/{idTreno}/richiesta-viaggio
    //   centrale -> centrale/treni/{idTreno}/viaggio
    // ------------------------------------------------------------------

    /** Lato centrale: risponde alle richieste di viaggio dei treni con itinerario e stato. */
    public void riceviRichiesteViaggio(SistemaFerroviario sistema) throws MqttException {
        Objects.requireNonNull(sistema, "Sistema obbligatorio");
        verificaConnessione();
        ObjectMapper mapper = new ObjectMapper();
        String topic = "centrale/treni/+/richiesta-viaggio";
        sottoscriviConListener(topic, 1, (topicRicevuto, messaggio) -> {
            try {
                String contenuto = new String(messaggio.getPayload(), StandardCharsets.UTF_8);
                sistemaFerroviario.communication.dto.RichiestaViaggio richiesta = mapper.readValue(
                        contenuto, sistemaFerroviario.communication.dto.RichiestaViaggio.class);
                String[] parti = topicRicevuto.split("/");
                // Il topic deve essere centrale/treni/{id}/richiesta-viaggio e l'ID deve coincidere.
                if (richiesta.getIdTreno() == null || !richiesta.getIdTreno().matches("[A-Za-z0-9_-]+")
                        || parti.length != 4 || !"centrale".equals(parti[0]) || !"treni".equals(parti[1])
                        || !parti[2].equals(richiesta.getIdTreno()) || !"richiesta-viaggio".equals(parti[3])) {
                    System.err.println("[ERRORE] Richiesta di viaggio non valida: " + contenuto);
                    return;
                }
                sistemaFerroviario.communication.dto.MessaggioViaggio risposta =
                        costruisciRispostaViaggio(sistema, richiesta);
                pubblica("centrale/treni/" + richiesta.getIdTreno() + "/viaggio",
                        mapper.writeValueAsString(risposta), 1);
                if (risposta.getErrore() == null) {
                    System.out.println("[CENTRALE] Viaggio comunicato al treno " + richiesta.getIdTreno()
                            + " | tappa " + risposta.getIndiceTappa() + "/" + risposta.getTappe().size()
                            + " | stato=" + risposta.getStato());
                } else {
                    System.err.println("[CENTRALE] Viaggio rifiutato al treno " + richiesta.getIdTreno()
                            + ": " + risposta.getErrore());
                }
            } catch (Exception e) {
                System.err.println("[ERRORE] Richiesta di viaggio: " + e.getMessage());
            }
        });
        System.out.println("[CENTRALE] In ascolto sulle richieste di viaggio: " + topic);
    }

    /** Legge lo stato del treno e l'itinerario sotto lock; prepara una nuova corsa se il precedente e' concluso. */
    static sistemaFerroviario.communication.dto.MessaggioViaggio costruisciRispostaViaggio(
            SistemaFerroviario sistema, sistemaFerroviario.communication.dto.RichiestaViaggio richiesta) {
        sistemaFerroviario.communication.dto.MessaggioViaggio risposta =
                new sistemaFerroviario.communication.dto.MessaggioViaggio();
        risposta.setIdTreno(richiesta.getIdTreno());
        risposta.setIdRichiesta(richiesta.getIdRichiesta());
        synchronized (sistema) {
            sistemaFerroviario.model.Treno treno = sistema.cercaTreno(richiesta.getIdTreno());
            if (treno == null) {
                risposta.setErrore("Treno non registrato nella centrale: " + richiesta.getIdTreno());
                return risposta;
            }
            if (treno.getStato() == sistemaFerroviario.model.StatoTreno.SOPPRESSO) {
                risposta.setErrore("Treno " + treno.getId() + " soppresso");
                return risposta;
            }
            try {
                java.util.List<String> tappe = sistemaFerroviario.service.PianificatorePercorsi
                        .itinerarioAndataRitorno(sistema, treno.getIdPercorso());
                // Corsa conclusa (o treno fermo): la nuova partenza e' decisa dalla centrale.
                if (treno.getStato() == sistemaFerroviario.model.StatoTreno.FERMO
                        || (treno.getStato() == sistemaFerroviario.model.StatoTreno.IN_STAZIONE
                            && treno.getProssimaStazione() == null)) {
                    treno.preparaPartenzaDa(tappe.get(0), tappe.get(1));
                }
                risposta.setIdPercorso(treno.getIdPercorso());
                risposta.setTappe(tappe);
                risposta.setIndiceTappa(treno.getIndiceTappa());
                risposta.setStato(treno.getStato().name());
                risposta.setStazioneCorrente(treno.getStazionePrecedente());
                risposta.setProssimaStazione(treno.getProssimaStazione());
            } catch (IllegalArgumentException | IllegalStateException e) {
                risposta.setErrore(e.getMessage());
            }
        }
        return risposta;
    }

    /** Lato treno: chiede alla centrale itinerario e posizione e attende la risposta. */
    public sistemaFerroviario.communication.dto.MessaggioViaggio richiediViaggio(String idTreno) {
        if (idTreno == null || !idTreno.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("ID treno non valido");
        }
        verificaConnessioneSenzaEccezione();
        String idRichiesta = UUID.randomUUID().toString();
        String topicRisposta = "centrale/treni/" + idTreno + "/viaggio";
        CountDownLatch ricevuta = new CountDownLatch(1);
        AtomicReference<sistemaFerroviario.communication.dto.MessaggioViaggio> risposta = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Prima ci si mette in ascolto, poi si invia la richiesta.
            sottoscriviConListener(topicRisposta, 1, (topic, messaggio) -> {
                try {
                    sistemaFerroviario.communication.dto.MessaggioViaggio dati = mapper.readValue(
                            new String(messaggio.getPayload(), StandardCharsets.UTF_8),
                            sistemaFerroviario.communication.dto.MessaggioViaggio.class);
                    if (idTreno.equals(dati.getIdTreno()) && idRichiesta.equals(dati.getIdRichiesta())) {
                        risposta.set(dati);
                        ricevuta.countDown();
                    }
                } catch (Exception e) {
                    System.err.println("[ERRORE] Lettura del viaggio: " + e.getMessage());
                }
            });
            pubblica("centrale/treni/" + idTreno + "/richiesta-viaggio",
                    mapper.writeValueAsString(
                            new sistemaFerroviario.communication.dto.RichiestaViaggio(idTreno, idRichiesta)),
                    1);
            if (!ricevuta.await(5, TimeUnit.SECONDS)) {
                throw new MqttNonDisponibileException(
                        "Nessuna risposta dalla centrale alla richiesta di viaggio del treno " + idTreno);
            }
            return risposta.get();
        } catch (MqttException | JsonProcessingException e) {
            throw new MqttNonDisponibileException("Errore MQTT durante la richiesta di viaggio.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attesa del viaggio interrotta.", e);
        }
    }

}
