package sistemaFerroviario.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.TipoMovimento;

/** La chiave dello storico relazionale deve essere stabile e distinguere eventi diversi. */
class ArchivioPostgresJUnitTest {

    @Test
    void usaLIdRichiestaQuandoPresente() {
        EventoTransito e = new EventoTransito("T001", "S01", Instant.parse("2026-11-18T10:00:00Z"),
                TipoMovimento.USCITA, "richiesta-123");
        assertEquals("richiesta-123", ArchivioPostgres.idTransito(e));
    }

    @Test
    void senzaIdRichiestaUsaUnaChiaveComposta() {
        Instant t = Instant.parse("2026-11-18T10:00:00Z");
        EventoTransito uscita = new EventoTransito("T001", "S01", t, TipoMovimento.USCITA, null);
        EventoTransito ingresso = new EventoTransito("T001", "S01", t, TipoMovimento.INGRESSO, null);
        assertEquals("T001|S01|2026-11-18T10:00:00Z|USCITA", ArchivioPostgres.idTransito(uscita));
        assertNotEquals(ArchivioPostgres.idTransito(uscita), ArchivioPostgres.idTransito(ingresso));
    }
}
