package sistemaFerroviario.communication.dto;

import java.util.List;

/**
 * Risposta della centrale a un treno che chiede il proprio viaggio:
 * itinerario completo di andata e ritorno e posizione attuale del convoglio.
 * Se {@code errore} non e' nullo la richiesta e' stata rifiutata.
 */
public class MessaggioViaggio {

    private String idTreno;
    private String idRichiesta;
    private String errore;
    private String idPercorso;
    private List<String> tappe;
    private int indiceTappa;
    private String stato;
    private String stazioneCorrente;
    private String prossimaStazione;

    public MessaggioViaggio() {
    }

    public String getIdTreno() { return idTreno; }
    public void setIdTreno(String idTreno) { this.idTreno = idTreno; }

    public String getIdRichiesta() { return idRichiesta; }
    public void setIdRichiesta(String idRichiesta) { this.idRichiesta = idRichiesta; }

    public String getErrore() { return errore; }
    public void setErrore(String errore) { this.errore = errore; }

    public String getIdPercorso() { return idPercorso; }
    public void setIdPercorso(String idPercorso) { this.idPercorso = idPercorso; }

    public List<String> getTappe() { return tappe; }
    public void setTappe(List<String> tappe) { this.tappe = tappe; }

    public int getIndiceTappa() { return indiceTappa; }
    public void setIndiceTappa(int indiceTappa) { this.indiceTappa = indiceTappa; }

    /** Stato del treno secondo la centrale (nome di StatoTreno). */
    public String getStato() { return stato; }
    public void setStato(String stato) { this.stato = stato; }

    public String getStazioneCorrente() { return stazioneCorrente; }
    public void setStazioneCorrente(String stazioneCorrente) { this.stazioneCorrente = stazioneCorrente; }

    public String getProssimaStazione() { return prossimaStazione; }
    public void setProssimaStazione(String prossimaStazione) { this.prossimaStazione = prossimaStazione; }
}
