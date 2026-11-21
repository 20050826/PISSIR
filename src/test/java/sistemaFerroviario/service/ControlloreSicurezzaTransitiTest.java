package sistemaFerroviario.service;

import sistemaFerroviario.model.Guasto;
import sistemaFerroviario.model.Stazione;

/**
 * Prova manuale delle condizioni che consentono o bloccano un transito.
 */
public class ControlloreSicurezzaTransitiTest {

    public static void main(String[] args) {

        SistemaFerroviario sistema = new SistemaFerroviario();

        // 1. Registriamo una stazione.
        Stazione stazione =
        	    new Stazione("S01", "Stazione Centrale");

        	// La stazione deve essere operativa per il test.
        	stazione.impostaOperativa(true);

        	sistema.aggiungiStazione(stazione);

        // 2. Creiamo il controllore.
        ControlloreSicurezzaTransiti controllore =
            new ControlloreSicurezzaTransiti(sistema);

        // 3. Senza guasti, il transito deve essere consentito.
        if (!controllore.transitoConsentito("S01")) {
            throw new AssertionError(
                "Il transito dovrebbe essere consentito"
            );
        }

        System.out.println("[OK] Transito consentito senza guasti");

        // 4. Registriamo un nuovo guasto.
        Guasto guasto = new Guasto(
            "G001",
            "S01",
            "Problema al sistema di segnalamento"
        );

        sistema.registraGuasto(guasto);

        // 5. Il guasto aperto deve bloccare il transito.
        if (controllore.transitoConsentito("S01")) {
            throw new AssertionError(
                "Il transito dovrebbe essere bloccato"
            );
        }

        System.out.println("[OK] Transito bloccato con guasto aperto");

        // 6. Prendiamo in carico il guasto.
        guasto.prendiInCarico();

        // Il guasto in gestione deve ancora bloccare.
        if (controllore.transitoConsentito("S01")) {
            throw new AssertionError(
                "Il transito deve restare bloccato durante la gestione"
            );
        }

        System.out.println("[OK] Blocco mantenuto durante la gestione");

        // 7. Risolviamo il guasto.
        guasto.risolvi();

        // 8. Il transito può tornare consentito.
        if (!controllore.transitoConsentito("S01")) {
            throw new AssertionError(
                "Il transito dovrebbe essere consentito dopo la risoluzione"
            );
        }

        System.out.println("[OK] Transito consentito dopo la risoluzione");
        System.out.println("TEST SUPERATO");
    }
}