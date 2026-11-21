package sistemaFerroviario.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modello di un nodo ferroviario, dei sensori associati e delle condizioni che consentono i rilevamenti.
 */
public class Stazione {
	private final String id;
	private final String nome;
	private boolean operativa;
	private List<Sensore> sensori = new ArrayList<>();
	
	@JsonCreator
	public Stazione(
	        @JsonProperty("id") String id,
	        @JsonProperty("nome") String nome,
	        @JsonProperty("operativa") boolean operativa,
	        @JsonProperty("sensori") List<Sensore> sensori) {

	    if (id == null || id.isBlank()) {
	        throw new IllegalArgumentException(
	            "ID della stazione obbligatorio"
	        );
	    }

	    if (nome == null || nome.isBlank()) {
	        throw new IllegalArgumentException(
	            "Nome della stazione obbligatorio"
	        );
	    }

	    this.id = id;
	    this.nome = nome;
	    this.operativa = operativa;
	    this.sensori = sensori == null
	            ? new ArrayList<>()
	            : new ArrayList<>(sensori);
	}
	public Stazione(String id, String nome) {
	    this(id, nome, false, new ArrayList<>());
	}
	
	public void aggiungiSensori(Sensore sensore) {
		if (sensore == null) {
			throw new IllegalArgumentException("il sensore non può essere nullo");
		}
		
		sensori.add(sensore);
	}
	
	public void setSensori(List<Sensore> sensori) {
	    this.sensori = (sensori == null)
	            ? new ArrayList<>()
	            : new ArrayList<>(sensori);
	} 
	
	public void rimuoviSensore(String idSensore) {
		sensori.removeIf(Sensore -> Sensore.getId().equals(idSensore));
	}
	
	public boolean rilevaTreno() {
		if(!operativa) {
			System.out.println("Stazione "+ id + " non operativa");
			return false;
		}
		
		for (Sensore sensore: sensori) {
			if (sensore.rilevaPassaggio()) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean segnalaAnomaliaSensore(String idSensore) {
	    if (idSensore == null || idSensore.isBlank()) {
	        throw new IllegalArgumentException(
	            "ID del sensore obbligatorio"
	        );
	    }

	    for (Sensore sensore : sensori) {
	        if (sensore.getId().equals(idSensore)) {
	            sensore.segnalaAnomalia();
	            return true;
	        }
	    }

	    System.out.println(
	        "Sensore " + idSensore +
	        " non trovato nella stazione " + id
	    );

	    return false;
	}
	
	public boolean haSensoriInAnomalia() {
	    for (Sensore sensore : sensori) {
	        if (sensore.isInAnomalia()) {
	            return true;
	        }
	    }

	    return false;
	}
	
	public boolean ripristinaSensore(String idSensore) {
	    for (Sensore sensore : sensori) {
	        if (sensore.getId().equals(idSensore)) {
	            sensore.ripristina();
	            return true;
	        }
	    }

	    return false;
	}
	
	public void impostaOperativa(boolean operativa) {
		this.operativa = operativa;
	}
	
	public String getId() {
		return id;
	}
	
	public String getNome() {
		return nome;
	}
	
	public boolean isOperativa() {
		return operativa;
	}
	
	public List<Sensore> getSensori() {
	    if (sensori == null) {
	        sensori = new ArrayList<>();
	    }

	    return new ArrayList<>(sensori);
	}
	
	public boolean sensoreInAnomalia(String idSensore) {
	    for (Sensore sensore : sensori) {
	        if (sensore.getId().equals(idSensore)) {
	            return sensore.isInAnomalia();
	        }
	    }

	    return false;
	}
	
	public boolean contieneSensore(String idSensore) {
	    for (Sensore sensore : sensori) {
	        if (sensore.getId().equals(idSensore)) {
	            return true;
	        }
	    }

	    return false;
	}
	@Override
	public String toString() {
	    int numeroSensori = (sensori == null)
	            ? 0
	            : sensori.size();

	    return "Stazione{" +
	            "id='" + id + '\'' +
	            ", nome='" + nome + '\'' +
	            ", operativa=" + operativa +
	            ", numero Sensori=" + numeroSensori +
	            '}';
	}
}
