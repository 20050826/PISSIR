package sistemaFerroviario.model;

/**
 * Elenca gli stati operativi che un treno puo assumere nel corso della simulazione.
 */
public enum StatoTreno {
	FERMO,
	IN_VIAGGIO,
	IN_STAZIONE,
	IN_RITARDO,
	GUASTO,
	SOPPRESSO
}
