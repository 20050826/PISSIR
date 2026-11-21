package sistemaFerroviario.model;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Evento rilevato da una stazione e conservato nello storico della centrale. */
public class EventoTransito {
    private final String idTreno;
    private final String idRichiesta;
    private final String idStazione;
    private final Instant timestamp;
    // null solo per i vecchi eventi, registrati prima della distinzione ingresso/uscita.
    private final TipoMovimento tipoMovimento;

    @JsonCreator
    public EventoTransito(
            @JsonProperty("idTreno") String idTreno,
            @JsonProperty("idStazione") String idStazione,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("tipoMovimento") TipoMovimento tipoMovimento,
            @JsonProperty("idRichiesta") String idRichiesta) {
        if (idTreno == null || idTreno.isBlank())
            throw new IllegalArgumentException("ID del treno obbligatorio");
        if (idStazione == null || idStazione.isBlank())
            throw new IllegalArgumentException("ID della stazione obbligatorio");
        this.idTreno = idTreno;
        this.idRichiesta = idRichiesta;
        this.idStazione = idStazione;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.tipoMovimento = tipoMovimento;
    }

    public EventoTransito(String idTreno, String idStazione, Instant timestamp,
            TipoMovimento tipoMovimento) {
        this(idTreno, idStazione, timestamp, tipoMovimento, java.util.UUID.randomUUID().toString());
    }

    /** Compatibilita' con i vecchi chiamanti: il transito e' un ingresso. */
    public EventoTransito(String idTreno, String idStazione, Instant timestamp) {
        this(idTreno, idStazione, timestamp, TipoMovimento.INGRESSO);
    }

    public EventoTransito(String idTreno, String idStazione) {
        this(idTreno, idStazione, Instant.now(), TipoMovimento.INGRESSO);
    }

    public EventoTransito(String idTreno, String idStazione, TipoMovimento tipoMovimento) {
        this(idTreno, idStazione, Instant.now(),
                java.util.Objects.requireNonNull(tipoMovimento, "Tipo movimento obbligatorio"));
    }

    public String getIdRichiesta() { return idRichiesta; }
    public String getIdTreno() { return idTreno; }
    public String getIdStazione() { return idStazione; }
    @JsonProperty("timestamp")
    public Instant getTimeStamp() { return timestamp; }
    public TipoMovimento getTipoMovimento() { return tipoMovimento; }

    @Override
    public String toString() {
        return "EventoTransito{" +
                "idTreno='" + idTreno + '\'' +
                ", idStazione='" + idStazione + '\'' +
                ", tipoMovimento=" + tipoMovimento +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
