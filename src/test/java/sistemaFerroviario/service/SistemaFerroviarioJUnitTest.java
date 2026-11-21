package sistemaFerroviario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.Guasto;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.model.StatoTreno;
import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.model.TipoMovimento;
import sistemaFerroviario.model.Treno;

/** Regole di movimento dei treni, ritardi, guasti e annullamento dei transiti nella centrale. */
class SistemaFerroviarioJUnitTest {

    /** Due stazioni operative con heartbeat valido, percorso S01-S02 e un treno pronto a partire da S01. */
    private static SistemaFerroviario creaSistema() {
        SistemaFerroviario sistema = new SistemaFerroviario();
        for (String id : List.of("S01", "S02")) {
            Stazione stazione = new Stazione(id, "Stazione " + id);
            stazione.impostaOperativa(true);
            sistema.aggiungiStazione(stazione);
            sistema.getMonitorHeartbeat().registraStazione(id);
            sistema.getMonitorHeartbeat().riceviHeartbeat(id);
        }
        sistema.aggiungiPercorso(new Percorso("P001", "Linea 1", List.of("S01", "S02")));
        Treno treno = new Treno("T001", "S01", null, null);
        treno.impostaPercorso("P001");
        sistema.aggiungiTreno(treno);
        treno.preparaPartenzaDa("S01", "S02");
        return sistema;
    }

    private static EventoTransito evento(String stazione, TipoMovimento movimento, Instant istante) {
        return new EventoTransito("T001", stazione, istante, movimento);
    }

    @Test
    void itinerarioComprendeAndataERitorno() {
        SistemaFerroviario sistema = creaSistema();
        assertEquals(List.of("S01", "S02", "S01"),
                PianificatorePercorsi.itinerarioAndataRitorno(sistema, "P001"));
    }

    @Test
    void viaggioCompletoAggiornaPosizioneEStorico() {
        SistemaFerroviario sistema = creaSistema();
        Treno treno = sistema.cercaTreno("T001");
        Instant t0 = Instant.now();

        sistema.registraTransito(evento("S01", TipoMovimento.USCITA, t0));
        assertEquals(StatoTreno.IN_VIAGGIO, treno.getStato());
        assertEquals("S02", treno.getProssimaStazione());

        sistema.registraTransito(evento("S02", TipoMovimento.INGRESSO, t0.plusSeconds(2)));
        assertEquals(StatoTreno.IN_STAZIONE, treno.getStato());
        assertEquals("S02", treno.getStazionePrecedente());
        assertEquals("S01", treno.getProssimaStazione());
        assertEquals(1, treno.getIndiceTappa());
        assertEquals(0, treno.getRitardoSecondi());

        sistema.registraTransito(evento("S02", TipoMovimento.USCITA, t0.plusSeconds(3)));
        sistema.registraTransito(evento("S01", TipoMovimento.INGRESSO, t0.plusSeconds(5)));
        assertEquals(StatoTreno.IN_STAZIONE, treno.getStato());
        assertEquals("S01", treno.getStazionePrecedente());
        assertNull(treno.getProssimaStazione());   // capolinea del ritorno
        assertEquals(4, sistema.getStoricoTransiti().size());
    }

    @Test
    void arrivoInRitardoVieneMisurato() {
        SistemaFerroviario sistema = creaSistema();
        Treno treno = sistema.cercaTreno("T001");
        Instant t0 = Instant.now();
        sistema.registraTransito(evento("S01", TipoMovimento.USCITA, t0));
        // Durata nominale della tratta: 2 s. Arrivo dopo 5 s: ritardo di 3 s.
        sistema.registraTransito(evento("S02", TipoMovimento.INGRESSO, t0.plusSeconds(5)));
        assertEquals(3, treno.getRitardoSecondi());
    }

    @Test
    void ingressoSenzaPartenzaOUscitaDaStazioneSbagliataSonoRifiutati() {
        SistemaFerroviario sistema = creaSistema();
        Instant t0 = Instant.now();
        assertThrows(IllegalStateException.class,
                () -> sistema.registraTransito(evento("S02", TipoMovimento.INGRESSO, t0)));
        assertThrows(IllegalStateException.class,
                () -> sistema.registraTransito(evento("S02", TipoMovimento.USCITA, t0)));
        assertTrue(sistema.getStoricoTransiti().isEmpty());
    }

    @Test
    void ilGuastoBloccaIlTransitoFinoAllaRisoluzione() {
        SistemaFerroviario sistema = creaSistema();
        Treno treno = sistema.cercaTreno("T001");
        Instant t0 = Instant.now();
        sistema.registraTransito(evento("S01", TipoMovimento.USCITA, t0));

        Guasto guasto = new Guasto("G001", "S02", "Binario interrotto");
        sistema.registraGuasto(guasto);

        IllegalStateException bloccato = assertThrows(IllegalStateException.class,
                () -> sistema.registraTransito(evento("S02", TipoMovimento.INGRESSO, t0.plusSeconds(2))));
        assertTrue(bloccato.getMessage().contains("guasto non risolto"), bloccato.getMessage());
        assertEquals(StatoTreno.IN_VIAGGIO, treno.getStato());

        guasto.prendiInCarico();
        guasto.risolvi();
        sistema.registraTransito(evento("S02", TipoMovimento.INGRESSO, t0.plusSeconds(4)));
        assertEquals(StatoTreno.IN_STAZIONE, treno.getStato());
    }

    @Test
    void erroreDiSalvataggioAnnullaIlTransito() {
        SistemaFerroviario sistema = creaSistema();
        Treno treno = sistema.cercaTreno("T001");
        EventoTransito uscita = evento("S01", TipoMovimento.USCITA, Instant.now());

        assertThrows(IllegalStateException.class, () -> sistema.registraTransitoConSalvataggio(uscita,
                () -> { throw new IllegalStateException("database non raggiungibile"); }));

        assertEquals(StatoTreno.IN_STAZIONE, treno.getStato());
        assertEquals("S01", treno.getStazionePrecedente());
        assertTrue(sistema.getStoricoTransiti().isEmpty());

        // Riprovando con il database funzionante il transito viene applicato una sola volta.
        sistema.registraTransitoConSalvataggio(uscita, () -> { });
        sistema.registraTransitoConSalvataggio(uscita, () -> { });
        assertEquals(StatoTreno.IN_VIAGGIO, treno.getStato());
        assertEquals(1, sistema.getStoricoTransiti().size());
    }

    @Test
    void trenoFermoInStazionePuoEssereSoppressoENonTransitaPiu() {
        SistemaFerroviario sistema = creaSistema();
        sistema.sopprimiTreno("T001");
        assertEquals(StatoTreno.SOPPRESSO, sistema.cercaTreno("T001").getStato());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> sistema.registraTransito(evento("S01", TipoMovimento.USCITA, Instant.now())));
        assertTrue(e.getMessage().contains("soppresso"), e.getMessage());
        assertTrue(sistema.getStoricoTransiti().isEmpty());
    }

    @Test
    void trenoInViaggioNonPuoEssereSoppresso() {
        SistemaFerroviario sistema = creaSistema();
        sistema.registraTransito(evento("S01", TipoMovimento.USCITA, Instant.now()));
        assertThrows(IllegalStateException.class, () -> sistema.sopprimiTreno("T001"));
        assertEquals(StatoTreno.IN_VIAGGIO, sistema.cercaTreno("T001").getStato());
    }

    @Test
    void sopprimereUnTrenoInesistenteDaErrore() {
        assertThrows(IllegalArgumentException.class, () -> creaSistema().sopprimiTreno("T999"));
    }
}
