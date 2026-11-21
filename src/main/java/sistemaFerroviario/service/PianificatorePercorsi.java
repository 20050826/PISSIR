package sistemaFerroviario.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import sistemaFerroviario.model.Percorso;

/**
 * Ricava l'itinerario completo di andata e ritorno dal percorso registrato
 * nella centrale. Non modifica le stazioni del percorso memorizzato.
 */
public final class PianificatorePercorsi {

    private PianificatorePercorsi() { }

    /** Costruisce le tappe di andata e ritorno, senza duplicare il capolinea. */
    public static List<String> itinerarioAndataRitorno(
            SistemaFerroviario sistema, String idPercorso) {
        Objects.requireNonNull(sistema, "Il sistema ferroviario e' obbligatorio");
        if (idPercorso == null || idPercorso.isBlank()) {
            throw new IllegalArgumentException("L'ID del percorso e' obbligatorio");
        }

        Percorso percorso = sistema.cercaPercorso(idPercorso);
        if (percorso == null) {
            throw new IllegalArgumentException("Percorso " + idPercorso
                    + " inesistente nel database. Assegnare un percorso valido tramite la dashboard");
        }

        List<String> andata = percorso.getStazioni();
        if (andata.size() < 2) {
            throw new IllegalArgumentException("Il percorso " + idPercorso
                    + " deve avere almeno due stazioni per simulare un viaggio");
        }

        Set<String> visitate = new HashSet<>();
        for (String idStazione : andata) {
            if (idStazione == null || idStazione.isBlank()
                    || sistema.cercaStazione(idStazione) == null) {
                throw new IllegalArgumentException("Stazione non valida nel percorso "
                        + idPercorso + ": " + idStazione);
            }
            if (!visitate.add(idStazione)) {
                throw new IllegalArgumentException("Stazione duplicata nel percorso "
                        + idPercorso + ": " + idStazione);
            }
        }

        List<String> completo = new ArrayList<>(andata);
        // Si riparte dal capolinea, senza aggiungerlo due volte di seguito.
        // Es.: S01 -> S02 -> S03 diventa S01 -> S02 -> S03 -> S02 -> S01.
        for (int i = andata.size() - 2; i >= 0; i--) {
            completo.add(andata.get(i));
        }
        return List.copyOf(completo);
    }
}
