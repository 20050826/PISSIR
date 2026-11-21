
package sistemaFerroviario.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sequenza ordinata di stazioni attraversate da un treno; offre operazioni di modifica validate dal sistema.
 */
public class Percorso {

    private final String id;
    private String nome;
    private final List<String> stazioni;

    @JsonCreator
    public Percorso(
            @JsonProperty("id") String id,
            @JsonProperty("nome") String nome,
            @JsonProperty("stazioni") List<String> stazioni) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "ID del percorso obbligatorio");
        }

        this.id = id;
        this.nome = nome;

        this.stazioni = (stazioni == null)
                ? new ArrayList<>()
                : new ArrayList<>(stazioni);
    }

    public void aggiungiStazione(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            throw new IllegalArgumentException("Stazione non valida");
        }

        if (stazioni.contains(idStazione)) {
            throw new IllegalArgumentException(
                "La stazione è già presente nel percorso: " + idStazione
            );
        }

        stazioni.add(idStazione);
    }
    public void rimuoviStazione(String idStazione) {
        if (idStazione == null || idStazione.isBlank()) {
            throw new IllegalArgumentException(
                "Stazione non valida"
            );
        }

        if (!stazioni.remove(idStazione)) {
            throw new IllegalArgumentException(
                "La stazione non è presente nel percorso: " + idStazione
            );
        }
    }

    public void modificaNome(String nuovoNome) {
        if (nuovoNome == null || nuovoNome.isBlank()) {
            throw new IllegalArgumentException(
                    "Nome non valido");
        }

        nome = nuovoNome;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public List<String> getStazioni() {
        return new ArrayList<>(stazioni);
    }

    @Override
    public String toString() {
        return "Percorso{" +
                "id='" + id + '\'' +
                ", nome='" + nome + '\'' +
                ", stazioni=" + stazioni +
                '}';
    }
}
