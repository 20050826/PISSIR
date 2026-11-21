
package sistemaFerroviario.service;

import java.util.List;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.model.Stazione;

public class VerificaModificaPercorsi {

    public static void main(String[] args) {

        // Sistema temporaneo, indipendente da MQTT e PostgreSQL.
        SistemaFerroviario sistema = new SistemaFerroviario();

        sistema.aggiungiStazione(new Stazione("S01", "Stazione 1"));
        sistema.aggiungiStazione(new Stazione("S02", "Stazione 2"));
        sistema.aggiungiStazione(new Stazione("S03", "Stazione 3"));

        sistema.aggiungiPercorso(
                new Percorso(
                        "PTEST",
                        "Percorso di prova",
                        List.of("S01", "S02")
                )
        );

        verificaTappe(sistema, List.of("S01", "S02"));

        System.out.println("Percorso iniziale: "
                + sistema.cercaPercorso("PTEST").getStazioni());

        // TEST 1: aggiunta di una stazione.
        sistema.aggiungiStazioneAlPercorso("PTEST", "S03");

        verificaTappe(sistema, List.of("S01", "S02", "S03"));

        System.out.println("[OK] Aggiunta S03: "
                + sistema.cercaPercorso("PTEST").getStazioni());

        // TEST 2: rimozione di una stazione.
        sistema.rimuoviStazioneDalPercorso("PTEST", "S02");

        verificaTappe(sistema, List.of("S01", "S03"));

        System.out.println("[OK] Rimozione S02: "
                + sistema.cercaPercorso("PTEST").getStazioni());

        System.out.println("TUTTI I TEST SUI PERCORSI SUPERATI");
    }

    private static void verificaTappe(
            SistemaFerroviario sistema,
            List<String> attese) {

        List<String> effettive =
                sistema.cercaPercorso("PTEST").getStazioni();

        if (!effettive.equals(attese)) {
            throw new AssertionError(
                    "Tappe attese: " + attese
                            + "; ottenute: " + effettive
            );
        }
    }
}
