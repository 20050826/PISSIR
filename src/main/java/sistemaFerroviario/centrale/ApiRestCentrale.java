package sistemaFerroviario.centrale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.json.JavalinJackson;
import io.javalin.http.Context;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.model.StatoTreno;
import sistemaFerroviario.model.Treno;
import sistemaFerroviario.service.SistemaFerroviario;
import java.util.List;
import java.util.Map;

/** API HTTP Javalin; i ruoli sono ricavati esclusivamente da token Keycloak verificati. */
public final class ApiRestCentrale {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final GestoreAutenticazione AUTH = new GestoreAutenticazione();
    private ApiRestCentrale() {}

    /** Configura le rotte REST e la dashboard, associando ai comandi i controlli di autorizzazione. */
    public static void avvia(SistemaFerroviario sistema, CentraleOperativa centrale) {
        SslPlugin ssl = new SslPlugin(conf -> {
            conf.pemFromPath("infra/mosquitto/certs/server.crt", "infra/mosquitto/certs/server.key");
            conf.insecure = false;   // niente HTTP in chiaro
            conf.securePort = 4567;  // stessa porta di prima, ora HTTPS
        });
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/static");
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.registerPlugin(ssl);
        });
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(Map.of("errore", "Errore interno del server"));
        });
        app.post("/api/login", ctx -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String,String> credentials = MAPPER.readValue(ctx.body(), Map.class);
                String token = AUTH.autentica(credentials.get("username"), credentials.get("password"));
                if (token == null) { ctx.status(401).json(Map.of("errore", "Credenziali non valide")); return; }
                String role = AUTH.getRuolo(token);
                ctx.json(Map.of("messaggio", "Login effettuato", "token", token,
                        "ruolo", role == null ? "UTENTE" : role));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                ctx.status(400).json(Map.of("errore", "JSON non valido"));
            }
        });
        // Un JWT rimane valido fino alla scadenza: il logout locale non revoca il token Keycloak.
        app.post("/api/logout", ctx -> ctx.json(Map.of("messaggio", "Elimina il token dal client; logout locale")));
        app.get("/api/stato", ctx -> ctx.json(Map.of("stato", "attivo", "servizio", "Sistema ferroviario")));
        app.get("/api/treni", ctx -> { if (!autorizzato(ctx, "TECNICO")) return; ctx.json(sistema.getTreni()); });
        app.get("/api/stazioni", ctx -> { if (!autorizzato(ctx, "TECNICO")) return; ctx.json(sistema.getStazioni()); });
        app.get("/api/percorsi", ctx -> { if (!autorizzato(ctx, "TECNICO")) return; ctx.json(sistema.getPercorsi()); });
        app.get("/api/guasti", ctx -> { if (!autorizzato(ctx, "TECNICO")) return; ctx.json(sistema.getGuasti()); });
        app.get("/api/transiti", ctx -> { if (!autorizzato(ctx, "TECNICO")) return; ctx.json(sistema.getStoricoTransiti()); });
        // Per ogni stazione: treni attesi in ingresso (in viaggio verso di essa) e in uscita (fermi in stazione con una tappa successiva).
        app.get("/api/treni-attesi", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            List<Map<String, Object>> risposta = sistema.getStazioni().stream().map(st -> {
                List<Map<String, Object>> inArrivo = sistema.getTreni().stream()
                        .filter(t -> (t.getStato() == StatoTreno.IN_VIAGGIO || t.getStato() == StatoTreno.IN_RITARDO)
                                && st.getId().equals(t.getProssimaStazione()))
                        .map(t -> voceTrenoAtteso(t, t.getStazionePrecedente())).toList();
                List<Map<String, Object>> inUscita = sistema.getTreni().stream()
                        .filter(t -> t.getStato() == StatoTreno.IN_STAZIONE
                                && st.getId().equals(t.getStazionePrecedente())
                                && t.getProssimaStazione() != null)
                        .map(t -> voceTrenoAtteso(t, t.getProssimaStazione())).toList();
                return Map.<String, Object>of("idStazione", st.getId(), "nome", st.getNome(),
                        "inArrivo", inArrivo, "inUscita", inUscita);
            }).toList();
            ctx.json(risposta);
        });
        app.get("/api/treni/{id}", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            var entity = sistema.cercaTreno(ctx.pathParam("id"));
            if (entity == null) ctx.status(404).json(Map.of("errore", "Treno non trovato"));
            else ctx.json(entity);
        });
        app.get("/api/stazioni/{id}", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            var entity = sistema.cercaStazione(ctx.pathParam("id"));
            if (entity == null) ctx.status(404).json(Map.of("errore", "Stazione non trovata"));
            else ctx.json(entity);
        });
        app.get("/api/guasti/{id}", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            var entity = sistema.cercaGuasto(ctx.pathParam("id"));
            if (entity == null) ctx.status(404).json(Map.of("errore", "Guasto non trovato"));
            else ctx.json(entity);
        });
        app.post("/api/treni/{id}/percorso", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                String idPercorso = MAPPER.readTree(ctx.body()).path("idPercorso").asText(null);
                sistema.assegnaPercorsoATreno(ctx.pathParam("id"), idPercorso);
                centrale.salvaStato();
                ctx.json(Map.of("messaggio", "Percorso assegnato al treno"));
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                ctx.status(400).json(Map.of("errore", "Treno o percorso non valido"));
            } catch (IllegalStateException ex) {
                ctx.status(409).json(Map.of("errore", ex.getMessage()));
            }
        });
        app.post("/api/treni/{id}/sopprimi", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                sistema.sopprimiTreno(ctx.pathParam("id"));
                centrale.salvaStato();
                ctx.json(Map.of("messaggio", "Treno soppresso"));
            } catch (IllegalArgumentException ex) {
                ctx.status(404).json(Map.of("errore", "Treno non trovato"));
            } catch (IllegalStateException ex) {
                ctx.status(409).json(Map.of("errore", ex.getMessage()));
            }
        });
        app.post("/api/percorsi", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                Percorso percorso = MAPPER.readValue(ctx.body(), Percorso.class);
                sistema.aggiungiPercorso(percorso);
                centrale.salvaStato();
                ctx.status(201).json(Map.of("messaggio", "Percorso creato correttamente", "id", percorso.getId()));
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                ctx.status(400).json(Map.of("errore", ex.getMessage() == null ? "Percorso non valido" : ex.getMessage()));
            }
        });
        app.post("/api/percorsi/{id}/stazioni", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                String id = MAPPER.readTree(ctx.body()).path("idStazione").asText(null);
                sistema.aggiungiStazioneAlPercorso(ctx.pathParam("id"), id);
                centrale.salvaStato();
                ctx.json(Map.of("messaggio", "Stazione aggiunta al percorso"));
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                ctx.status(400).json(Map.of("errore", "Stazione o richiesta non valida"));
            }
        });
        app.post("/api/percorsi/{id}/stazioni/rimuovi", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                String id = MAPPER.readTree(ctx.body()).path("idStazione").asText(null);
                sistema.rimuoviStazioneDalPercorso(ctx.pathParam("id"), id);
                centrale.salvaStato();
                ctx.json(Map.of("messaggio", "Stazione rimossa dal percorso"));
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                ctx.status(400).json(Map.of("errore", ex.getMessage() == null
                        ? "Stazione o richiesta non valida" : ex.getMessage()));
            }
        });
        app.delete("/api/percorsi/{id}", ctx -> {
            if (!autorizzato(ctx, "AMMINISTRATORE")) return;
            try {
                sistema.rimuoviPercorso(ctx.pathParam("id"));
                centrale.salvaStato();
                ctx.json(Map.of("messaggio", "Percorso eliminato correttamente"));
            } catch (IllegalArgumentException ex) {
                ctx.status(404).json(Map.of("errore", ex.getMessage()));
            } catch (IllegalStateException ex) {
                ctx.status(409).json(Map.of("errore", ex.getMessage()));
            }
        });
        app.post("/api/guasti/{id}/prendi-in-carico", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            try {
                centrale.prendiInCaricoGuasto(ctx.pathParam("id"));
                ctx.json(Map.of("messaggio", "Guasto preso in carico"));
            } catch (IllegalArgumentException ex) {
                ctx.status(404).json(Map.of("errore", "Guasto non trovato"));
            } catch (IllegalStateException ex) {
                ctx.status(409).json(Map.of("errore", "Guasto non nello stato previsto"));
            }
        });
        app.post("/api/guasti/{id}/risolvi", ctx -> {
            if (!autorizzato(ctx, "TECNICO")) return;
            try {
                centrale.risolviGuasto(ctx.pathParam("id"));
                ctx.json(Map.of("messaggio", "Guasto risolto; ripristino comunicato alla stazione"));
            } catch (IllegalArgumentException ex) {
                ctx.status(404).json(Map.of("errore", "Guasto non trovato"));
            } catch (IllegalStateException ex) {
                ctx.status(409).json(Map.of("errore", "Guasto non risolvibile nello stato attuale"));
            }
        });
        app.start();
        System.out.println("API Javalin avviata su https://localhost:4567");
    }

    /** Riga sintetica di un treno atteso; "altraStazione" e' l'origine (arrivi) o la destinazione (uscite). */
    private static Map<String, Object> voceTrenoAtteso(Treno t, String altraStazione) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("idTreno", t.getId());
        m.put("altraStazione", altraStazione);
        m.put("stato", t.getStato());
        m.put("arrivoStimato", t.getArrivoStimato());
        return m;
    }

    /** Verifica che il token dell utente abbia il ruolo richiesto dall operazione. */
    private static boolean autorizzato(Context ctx, String ruolo) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            ctx.status(401).json(Map.of("errore", "Autenticazione richiesta"));
            return false;
        }
        String role = AUTH.getRuolo(header.substring(7));
        if (role == null) {
            ctx.status(401).json(Map.of("errore", "Token non valido o scaduto"));
            return false;
        }
        if (!role.equals(ruolo) &&
                !("TECNICO".equals(ruolo) &&
                        "AMMINISTRATORE".equals(role) &&
                        !ctx.path().endsWith("/prendi-in-carico") &&
                        !ctx.path().endsWith("/risolvi"))) {
            ctx.status(403).json(Map.of("errore", "Permessi insufficienti"));
            return false;
        }
        return true;
    }
}
