package sistemaFerroviario.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sistemaFerroviario.communication.dto.MessaggioViaggio;
import sistemaFerroviario.communication.dto.RichiestaViaggio;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.model.StatoTreno;
import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.model.Treno;
import sistemaFerroviario.process.ProcessoTreno;
import sistemaFerroviario.service.SistemaFerroviario;

/** Il treno e' un processo separato: conosce solo il messaggio di viaggio e l'esito dei transiti. */
class ViaggioTrenoSeparatoJUnitTest {

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
        return sistema;
    }

    @Test
    void trenoFermoRiceveViaggioDallaCentraleEIlProcessoLoCompleta() {
        SistemaFerroviario sistema = creaSistema();
        MessaggioViaggio viaggio = MqttService.costruisciRispostaViaggio(
                sistema, new RichiestaViaggio("T001", "r1"));

        assertNull(viaggio.getErrore());
        assertEquals(List.of("S01", "S02", "S01"), viaggio.getTappe());
        assertEquals(0, viaggio.getIndiceTappa());
        assertEquals("S01", viaggio.getStazioneCorrente());

        // Il "canale MQTT" e' simulato: ogni evento del treno arriva alla centrale.
        List<String> eventi = new ArrayList<>();
        ProcessoTreno processo = new ProcessoTreno(viaggio, e -> {
            eventi.add(e.getTipoMovimento() + ":" + e.getIdStazione());
            sistema.registraTransito(e);
        }, Duration.ZERO, 0.0);
        processo.run();

        assertTrue(processo.isCompletato());
        assertEquals(List.of("USCITA:S01", "INGRESSO:S02", "USCITA:S02", "INGRESSO:S01"), eventi);
        Treno treno = sistema.cercaTreno("T001");
        assertEquals(StatoTreno.IN_STAZIONE, treno.getStato());
        assertNull(treno.getProssimaStazione());
    }

    @Test
    void secondaRichiestaDopoViaggioConclusoAvviaNuovaCorsa() {
        SistemaFerroviario sistema = creaSistema();
        MessaggioViaggio primo = MqttService.costruisciRispostaViaggio(
                sistema, new RichiestaViaggio("T001", "r1"));
        new ProcessoTreno(primo, sistema::registraTransito, Duration.ZERO, 0.0).run();

        MessaggioViaggio secondo = MqttService.costruisciRispostaViaggio(
                sistema, new RichiestaViaggio("T001", "r2"));
        assertNull(secondo.getErrore());
        assertEquals(0, secondo.getIndiceTappa());
        assertNotNull(secondo.getProssimaStazione());
    }

    @Test
    void trenoSoppressoNonRiceveViaggio() {
        SistemaFerroviario sistema = creaSistema();
        sistema.cercaTreno("T001").preparaPartenzaDa("S01", "S02");
        sistema.sopprimiTreno("T001");

        MessaggioViaggio risposta = MqttService.costruisciRispostaViaggio(
                sistema, new RichiestaViaggio("T001", "r1"));
        assertNotNull(risposta.getErrore());
        assertTrue(risposta.getErrore().contains("soppresso"));
    }

    @Test
    void trenoSconosciutoRiceveErrore() {
        MessaggioViaggio risposta = MqttService.costruisciRispostaViaggio(
                creaSistema(), new RichiestaViaggio("T999", "r1"));
        assertNotNull(risposta.getErrore());
    }

    @Test
    void soppressioneDuranteIlViaggioFermaIlProcesso() {
        SistemaFerroviario sistema = creaSistema();
        MessaggioViaggio viaggio = MqttService.costruisciRispostaViaggio(
                sistema, new RichiestaViaggio("T001", "r1"));
        // Il controllo SOPPRESSO fa parte dell'esito della centrale: simulato come nel handler MQTT.
        ProcessoTreno processo = new ProcessoTreno(viaggio, e -> {
            sistema.sopprimiTreno("T001");
            throw new TransitoBloccatoException("Treno T001 soppresso: transito non ammesso");
        }, Duration.ZERO, 0.0);
        processo.run();
        assertTrue(!processo.isCompletato());
    }
}
