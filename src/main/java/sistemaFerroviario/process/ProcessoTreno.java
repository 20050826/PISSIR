package sistemaFerroviario.process;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import sistemaFerroviario.communication.MqttNonDisponibileException;
import sistemaFerroviario.communication.TransitoBloccatoException;
import sistemaFerroviario.communication.dto.MessaggioViaggio;
import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.TipoMovimento;

/**
 * Viaggio di un convoglio: invia ingressi e uscite, aspetta i consensi e gestisce i blocchi.
 * Non conosce il modello della centrale: lavora solo sul viaggio che la centrale gli ha
 * comunicato via MQTT (itinerario, tappa corrente, stato) e sull'esito di ogni transito.
 */
public class ProcessoTreno implements Runnable {

    private final String idTreno;
    private final List<String> percorso;
    private final int indiceIniziale;
    private final boolean inViaggioIniziale;
    private final String stazioneCorrente;
    private final String prossimaStazione;
    private final Consumer<EventoTransito> inviaTransito;
    private final Duration attesaTraStazioni;
    private final double probabilitaRallentamento;
    private volatile boolean completato = false;
    private volatile boolean attesaRiparazione;
    private volatile boolean haAttesoRiparazione;

    public ProcessoTreno(
            MessaggioViaggio viaggio,
            Consumer<EventoTransito> inviaTransito,
            Duration attesaTraStazioni) {
        this(viaggio, inviaTransito, attesaTraStazioni, PROBABILITA_RALLENTAMENTO);
    }

    public ProcessoTreno(
            MessaggioViaggio viaggio,
            Consumer<EventoTransito> inviaTransito,
            Duration attesaTraStazioni,
            double probabilitaRallentamento) {

        Objects.requireNonNull(viaggio, "Il viaggio è obbligatorio");
        if (viaggio.getErrore() != null) {
            throw new IllegalArgumentException("Viaggio rifiutato dalla centrale: " + viaggio.getErrore());
        }
        if (viaggio.getIdTreno() == null || viaggio.getIdTreno().isBlank()) {
            throw new IllegalArgumentException("Il treno è obbligatorio");
        }
        this.idTreno = viaggio.getIdTreno();

        List<String> tappe = Objects.requireNonNull(
                viaggio.getTappe(), "Il percorso è obbligatorio");

        if (tappe.size() < 2) {
            throw new IllegalArgumentException(
                    "Il percorso deve contenere partenza e capolinea");
        }

        for (String idStazione : tappe) {
            if (idStazione == null || idStazione.isBlank()) {
                throw new IllegalArgumentException(
                        "Il percorso contiene una stazione non valida");
            }
        }

        this.percorso = List.copyOf(tappe);
        this.indiceIniziale = viaggio.getIndiceTappa();
        this.inViaggioIniziale = "IN_VIAGGIO".equals(viaggio.getStato())
                || "IN_RITARDO".equals(viaggio.getStato());
        this.stazioneCorrente = viaggio.getStazioneCorrente();
        this.prossimaStazione = viaggio.getProssimaStazione();
        if (!inViaggioIniziale && !"IN_STAZIONE".equals(viaggio.getStato())) {
            throw new IllegalStateException("Treno " + idTreno + " non pronto al viaggio (stato "
                    + viaggio.getStato() + ")");
        }

        this.inviaTransito = Objects.requireNonNull(
                inviaTransito, "Il gestore dei transiti è obbligatorio");

        this.attesaTraStazioni = Objects.requireNonNull(
                attesaTraStazioni, "L'attesa è obbligatoria");

        if (attesaTraStazioni.isNegative()) {
            throw new IllegalArgumentException(
                    "L'attesa non può essere negativa");
        }
        this.probabilitaRallentamento = Math.min(1.0, Math.max(0.0, probabilitaRallentamento));
    }

    @Override
    /** Esegue le tappe in ordine; alla partenza emette solo USCITA e al capolinea solo INGRESSO. */
    public void run() {
        completato = false;
        attesaRiparazione = false;
        haAttesoRiparazione = false;
        System.out.println("\n=== INIZIO VIAGGIO " + idTreno + " ===");

        try {
            // Riprende dalla tappa persistita. Se il treno era gia' in viaggio,
            // attende solo l'ingresso nella destinazione: non duplica l'uscita.
            int indice = indiceIniziale;
            if (indice < 0 || indice >= percorso.size())
                throw new IllegalStateException("Indice tappa fuori percorso: " + indice);
            boolean inViaggio = inViaggioIniziale;
            String attuale = percorso.get(indice);
            if (!attuale.equals(stazioneCorrente))
                throw new IllegalStateException("Stazione corrente incoerente per " + idTreno);
            if (inViaggio && (indice + 1 >= percorso.size()
                    || !percorso.get(indice + 1).equals(prossimaStazione)))
                throw new IllegalStateException("Prossima destinazione incoerente per " + idTreno);

            for (int i = indice; i < percorso.size(); i++) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Viaggio interrotto");
                String stazione = percorso.get(i);
                String successiva = i + 1 < percorso.size() ? percorso.get(i + 1) : null;
                if (i > indice || inViaggio) {
                    if (inViaggio) attendi(attesaTraStazioni);
                    inviaTransitoConAttesa(new EventoTransito(idTreno,
                            inViaggio ? percorso.get(i + 1) : stazione,
                            TipoMovimento.INGRESSO));
                    System.out.println("[MOVIMENTO] " + idTreno + " INGRESSO "
                            + (inViaggio ? percorso.get(i + 1) : stazione));
                    if (inViaggio) {
                        i++; // L'ingresso ha completato la tratta gia' partita.
                        inViaggio = false;
                        if (i >= percorso.size()) break;
                        stazione = percorso.get(i);
                        successiva = i + 1 < percorso.size() ? percorso.get(i + 1) : null;
                    }
                }
                if (successiva != null) {
                    inviaTransitoConAttesa(new EventoTransito(idTreno, stazione,
                            TipoMovimento.USCITA));
                    System.out.println("[MOVIMENTO] " + idTreno
                            + " USCITA " + stazione + " -> " + successiva);
                    attendi(attesaTraStazioni.plus(rallentamentoSimulato()));
                }
            }

            completato = true;
            System.out.println("=== FINE VIAGGIO " + idTreno + " ===");
        } catch (InterruptedException e) {
            attesaRiparazione = false;
            Thread.currentThread().interrupt();
            System.out.println("Viaggio interrotto: " + idTreno);
        } catch (RuntimeException e) {
            attesaRiparazione = false;
            System.err.println("Viaggio non completato per il treno "
                    + idTreno + ": " + e.getMessage());
        }
    }

    /** Il transito si riprova SOLO se la centrale ha risposto esplicitamente BLOCCATO.
     *  Il movimento non viene applicato prima del consenso MQTT. I timeout, errori
     *  di rete ed altri errori rimangono errori: non sono scambiati per guasti riparabili.
     *  L'attesa termina al ripristino, oppure all'interruzione del thread. */
    private void inviaTransitoConAttesa(EventoTransito evento) throws InterruptedException {
        boolean bloccato = false;
        while (true) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Transito interrotto");
            try {
                inviaTransito.accept(evento);
                if (bloccato) {
                    System.out.println("[TRENO " + idTreno
                            + "] RIPARTENZA autorizzata dalla centrale a " + evento.getIdStazione());
                }
                attesaRiparazione = false;
                return;
            } catch (MqttNonDisponibileException e) {
                // Recupero da un disservizio temporaneo, senza creare un secondo transito:
                // l'ID dell'evento rimane invariato in tutti i tentativi.
                System.err.println("[TRENO " + idTreno + "] MQTT non disponibile, ritento: " + e.getMessage());
                Thread.sleep(1000);
                continue;
            } catch (TransitoBloccatoException e) {
                // Solo i blocchi di sicurezza recuperabili giustificano l'attesa.
                // Un ingresso fuori percorso o un transito duplicato e' un errore
                // logico: ritentarlo per sempre non puo' risolverlo.
                if (!e.isRiparabile()) throw e;
                if (!bloccato) {
                    System.out.println("[TRENO " + idTreno + "] IN_ATTESA_RIPRISTINO a "
                            + evento.getIdStazione() + " | " + e.getMessage());
                    bloccato = true;
                    attesaRiparazione = true;
                    haAttesoRiparazione = true;
                }
                Thread.sleep(1000);
            }
        }
    }

    /** Probabilita' (0..1) di rallentamento su una tratta: variabile d'ambiente RALLENTAMENTI_PROBABILITA, 0 la disattiva. */
    private static final double PROBABILITA_RALLENTAMENTO = leggiProbabilita();

    private static double leggiProbabilita() {
        try {
            double p = Double.parseDouble(System.getenv().getOrDefault("RALLENTAMENTI_PROBABILITA", "0.25"));
            return Math.min(1.0, Math.max(0.0, p));
        } catch (NumberFormatException e) {
            return 0.25;
        }
    }

    /** Simula traffico o rallentamenti: con probabilita' p la tratta dura da 1 a 2 secondi in piu'
     *  (resta sotto la soglia di allarme "possibile blocco", ETA programmata + 3 s). */
    private Duration rallentamentoSimulato() {
        if (ThreadLocalRandom.current().nextDouble() >= probabilitaRallentamento) return Duration.ZERO;
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(1000, 2001));
    }

    public boolean isInAttesaRiparazione() { return attesaRiparazione; }
    public boolean haAttesoRiparazione() { return haAttesoRiparazione; }

    private static void attendi(Duration durata)
            throws InterruptedException {

        long millisecondi = durata.toMillis();
        int nanosecondi = durata.minusMillis(millisecondi).getNano();

        if (millisecondi > 0 || nanosecondi > 0) {
            Thread.sleep(millisecondi, nanosecondi);
        }
    }
    public boolean isCompletato() {
        return completato;
    }
}
