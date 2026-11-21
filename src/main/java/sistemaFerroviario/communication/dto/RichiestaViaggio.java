package sistemaFerroviario.communication.dto;

/** Richiesta di un treno alla centrale: "dammi il mio viaggio". */
public class RichiestaViaggio {

    private String idTreno;
    private String idRichiesta;

    public RichiestaViaggio() {
    }

    public RichiestaViaggio(String idTreno, String idRichiesta) {
        this.idTreno = idTreno;
        this.idRichiesta = idRichiesta;
    }

    public String getIdTreno() { return idTreno; }
    public void setIdTreno(String idTreno) { this.idTreno = idTreno; }

    public String getIdRichiesta() { return idRichiesta; }
    public void setIdRichiesta(String idRichiesta) { this.idRichiesta = idRichiesta; }
}
