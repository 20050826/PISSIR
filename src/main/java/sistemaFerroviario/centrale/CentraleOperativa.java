
package sistemaFerroviario.centrale;


import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.Guasto;
import sistemaFerroviario.model.Guasto.StatoGuasto;
import sistemaFerroviario.service.SistemaFerroviario;
import sistemaFerroviario.persistence.ArchivioPostgres;
import sistemaFerroviario.model.Stazione;

/**
 * Coordina la ricezione degli eventi, la gestione guasti e la persistenza PostgreSQL.
 */
public class CentraleOperativa {

    private final SistemaFerroviario sistema; 
    private final ArchivioPostgres database;
    private volatile java.util.function.Consumer<Guasto> notificaRipristino = guasto -> {};

    /** Collega il ripristino a un nodo stazione remoto, se configurato. */
    public void configuraNotificaRipristino(java.util.function.Consumer<Guasto> callback) {
        this.notificaRipristino = java.util.Objects.requireNonNull(callback);
    }

    public CentraleOperativa(SistemaFerroviario sistema, ArchivioPostgres database) {
        this.sistema = sistema;
        this.database = database;
    }

    /** Applica il transito al modello e richiede il salvataggio dei dati. */
    public void registraTransito(EventoTransito evento) {
        sistema.registraTransito(evento);

        System.out.println(
                "Centrale: transito registrato.");

        salvaStato();
    }

    public void riceviHeartbeat(String idStazione) {
        sistema.getMonitorHeartbeat()
               .riceviHeartbeat(idStazione);

        System.out.println(
                "Centrale: heartbeat ricevuto da "
                + idStazione);
    }

    /** Unico archivio: lo stato della centrale viene salvato su PostgreSQL. */
    public void salvaStato() {
        try {
            database.salva(sistema);
        } catch (Exception e) {
            throw new IllegalStateException("Salvataggio PostgreSQL fallito: " + e.getMessage(), e);
        }
    }

    public void prendiInCaricoGuasto(String idGuasto) {
        sistema.prendiInCaricoGuasto(idGuasto);
        salvaStato();
    }
    
    /** Risolve il guasto in centrale, ripristina il sensore quando possibile e comunica l esito al nodo remoto. */
    public void risolviGuasto(String idGuasto) {
        Guasto guasto = sistema.cercaGuasto(idGuasto);

        if (guasto == null) {
            throw new IllegalArgumentException(
                "Guasto non trovato: " + idGuasto
            );
        }

        // Risolve prima il guasto nel sistema centrale.
        sistema.risolviGuasto(idGuasto);

     // Ripristina il sensore soltanto quando non esistono
     // altri guasti irrisolti associati allo stesso sensore.

     String idSensore = guasto.getIdSensore();
     boolean sensoreRipristinato = false;

     if (idSensore != null) {

         boolean altriGuastiIrrisolti = sistema.getGuasti().stream()
             .anyMatch(g ->
                 guasto.getIdStazione().equals(g.getIdStazione())
                 && idSensore.equals(g.getIdSensore())
                 && g.getStato() != StatoGuasto.RISOLTO
             );

         if (!altriGuastiIrrisolti) {

             Stazione stazione =
                 sistema.cercaStazione(guasto.getIdStazione());

             if (stazione != null) {

                 sensoreRipristinato = stazione.ripristinaSensore(idSensore);

                 System.out.println(
                     "Sensore " + idSensore +
                     " ripristinato nella stazione " +
                     stazione.getId()
                 );
             }
         }
     }

        salvaStato();
        // In modalita' distribuita il ripristino va notificato anche al nodo remoto.
        boolean altriGuastiStazione = idSensore == null && sistema.getGuasti().stream()
                .anyMatch(g -> guasto.getIdStazione().equals(g.getIdStazione())
                        && g.getIdSensore() == null && g.getStato() != StatoGuasto.RISOLTO);
        if (sensoreRipristinato || (idSensore == null && !altriGuastiStazione)) {
            try {
                notificaRipristino.accept(guasto);
            } catch (RuntimeException e) {
                System.err.println("[CENTRALE] Ripristino salvato, ma notifica remota fallita: " + e.getMessage());
            }
        }
    }
    public void segnalaGuasto(String idStazione, String descrizione) {
        sistema.segnalaGuasto(idStazione, descrizione);

        // Persiste subito lo stato su PostgreSQL.
        salvaStato();

        System.out.println(
            "Centrale Operativa: guasto registrato e stato salvato."
        );
    }
    public synchronized void riceviSegnalazioneAnomalia(
            Stazione stazione,
            String idSensore,
            String descrizione) {

    	if (stazione == null) {
    	    throw new IllegalArgumentException(
    	            "La stazione è obbligatoria");
    	}

    	if (idSensore == null || idSensore.isBlank()) {
    	    throw new IllegalArgumentException(
    	            "L'ID del sensore è obbligatorio");
    	}

    	if (!stazione.contieneSensore(idSensore)) {
    	    throw new IllegalArgumentException(
    	            "Sensore " + idSensore
    	            + " non presente nella stazione "
    	            + stazione.getId());
    	}

    	if (!stazione.sensoreInAnomalia(idSensore)) {
    	    throw new IllegalStateException(
    	            "Il sensore " + idSensore
    	            + " non risulta in anomalia");
    	}

        // Una stazione potrebbe reinviare lo stesso guasto dopo una riconnessione MQTT.
        // Non creare un altro Gxxx se per quel sensore esiste gia' un guasto irrisolto.
        boolean guastoGiaAperto = sistema.getGuasti().stream()
                .anyMatch(g -> stazione.getId().equals(g.getIdStazione())
                        && idSensore.equals(g.getIdSensore())
                        && g.getStato() != StatoGuasto.RISOLTO);
        if (guastoGiaAperto) {
            System.out.println("[CENTRALE MQTT] Anomalia gia' registrata per "
                    + stazione.getId() + "/" + idSensore);
            return;
        }

    	System.out.println(
    	        "Centrale Operativa: anomalia ricevuta dalla stazione "
    	        + stazione.getId());

    	sistema.segnalaGuasto(
    	        stazione.getId(),
    	        descrizione,
    	        idSensore);

    	salvaStato();
    	
    }
}
