package sistemaFerroviario.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import sistemaFerroviario.model.Guasto;
import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.service.ControlloreSicurezzaTransiti;
import sistemaFerroviario.service.MonitorHeartbeat;
import sistemaFerroviario.service.SistemaFerroviario;

/** Verifica quali rifiuti della centrale fanno attendere il treno e quali sono errori logici. */
class TransitoBloccatoExceptionJUnitTest {

    @Test
    void ilMessaggioConservaIlPrefissoEIlMotivo() {
        TransitoBloccatoException e = new TransitoBloccatoException("motivo di prova");
        assertEquals("Transito bloccato dalla centrale: motivo di prova", e.getMessage());
        assertEquals("motivo di prova", e.getMotivo());
        assertTrue(e instanceof IllegalStateException);
    }

    @Test
    void motivoNulloNonEsplode() {
        TransitoBloccatoException e = new TransitoBloccatoException(null);
        assertEquals("", e.getMotivo());
        assertFalse(e.isRiparabile());
    }

    @Test
    void erroriLogiciNonSonoRiparabili() {
        assertFalse(new TransitoBloccatoException(
                "Ingresso non valido: treno T001 non in viaggio verso S02").isRiparabile());
        assertFalse(new TransitoBloccatoException(
                "Uscita non valida: treno T001 non presente nella stazione S01").isRiparabile());
        assertFalse(new TransitoBloccatoException(
                "Transito bloccato: stazione S09 inesistente.").isRiparabile());
    }

    @Test
    void sensoreNonDisponibileESiRiparabile() {
        assertTrue(new TransitoBloccatoException(
                "Rilevamento non valido: sensore o stazione non disponibili presso S02.").isRiparabile());
    }

    /** I motivi reali prodotti dal controllore di sicurezza devono essere riconosciuti come riparabili. */
    @Test
    void imotiviRealiDelControlloreSonoRiparabili() {
        SistemaFerroviario sistema = new SistemaFerroviario();
        Stazione stazione = new Stazione("S01", "Stazione uno");
        stazione.impostaOperativa(true);
        sistema.aggiungiStazione(stazione);
        MonitorHeartbeat monitor = new MonitorHeartbeat();
        monitor.registraStazione("S01");
        ControlloreSicurezzaTransiti controllore =
                new ControlloreSicurezzaTransiti(sistema, monitor, Duration.ofSeconds(5));

        // Nessun heartbeat ricevuto: stazione non raggiungibile.
        assertFalse(controllore.transitoConsentito("S01"));
        assertTrue(new TransitoBloccatoException(controllore.motivoBlocco("S01")).isRiparabile());

        // Guasto aperto.
        monitor.riceviHeartbeat("S01");
        assertTrue(controllore.transitoConsentito("S01"));
        sistema.registraGuasto(new Guasto("G001", "S01", "Binario interrotto"));
        assertFalse(controllore.transitoConsentito("S01"));
        assertTrue(new TransitoBloccatoException(controllore.motivoBlocco("S01")).isRiparabile());

        // Stazione non operativa.
        sistema.cercaGuasto("G001").prendiInCarico();
        sistema.cercaGuasto("G001").risolvi();
        stazione.impostaOperativa(false);
        assertFalse(controllore.transitoConsentito("S01"));
        assertTrue(new TransitoBloccatoException(controllore.motivoBlocco("S01")).isRiparabile());
    }

    @Test
    void elencoDiControlloPerEvitareRegressioni() {
        List<String> daAttendere = List.of(
                "Transito bloccato: la stazione S01 presenta un guasto non risolto.",
                "Transito bloccato: la stazione S01 non \u00e8 operativa.",
                "Transito bloccato: la stazione S01 non \u00e8 raggiungibile o non invia heartbeat recenti.");
        for (String motivo : daAttendere) {
            assertTrue(new TransitoBloccatoException(motivo).isRiparabile(), motivo);
        }
    }
}
