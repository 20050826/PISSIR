package sistemaFerroviario.persistence;

import java.util.ArrayList;
import java.util.List;

import sistemaFerroviario.model.EventoTransito;
import sistemaFerroviario.model.Guasto;
import sistemaFerroviario.model.Percorso;
import sistemaFerroviario.model.Stazione;
import sistemaFerroviario.model.Treno;

/**
 * Contenitore serializzabile delle collezioni necessarie per ricostruire lo stato della centrale.
 */
public class StatoPersistito {

    private List<Treno> treni = new ArrayList<>();
    private List<Stazione> stazioni = new ArrayList<>();
    private List<Percorso> percorsi = new ArrayList<>();
    private List<Guasto> guasti = new ArrayList<>();
    // Compatibile con gli snapshot precedenti, che non contenevano questo campo.
    private List<EventoTransito> transiti = new ArrayList<>();

    public StatoPersistito() {
        // Costruttore vuoto necessario per Jackson.
    }

    public List<Treno> getTreni() {
        return treni;
    }

    public void setTreni(List<Treno> treni) {
        this.treni = treni;
    }

    public List<Stazione> getStazioni() {
        return stazioni;
    }

    public void setStazioni(List<Stazione> stazioni) {
        this.stazioni = stazioni;
    }

    public List<Percorso> getPercorsi() {
        return percorsi;
    }

    public void setPercorsi(List<Percorso> percorsi) {
        this.percorsi = percorsi;
    }

    public List<Guasto> getGuasti() {
        return guasti;
    }

    public void setGuasti(List<Guasto> guasti) {
        this.guasti = guasti;
    }

    public List<EventoTransito> getTransiti() { return transiti; }

    public void setTransiti(List<EventoTransito> transiti) {
        this.transiti = transiti == null ? new ArrayList<>() : new ArrayList<>(transiti);
    }
}