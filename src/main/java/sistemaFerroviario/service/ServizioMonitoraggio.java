
package sistemaFerroviario.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Avvia e interrompe il controllo periodico di heartbeat e disponibilita delle stazioni.
 */
public class ServizioMonitoraggio implements AutoCloseable {

    private final MonitorHeartbeat monitor;
    private final Duration timeout;
    private final java.util.function.Consumer<java.util.Set<String>> aggiornamento;
    private final long intervalloSecondi;

    private ScheduledExecutorService esecutore;

    public ServizioMonitoraggio(
            MonitorHeartbeat monitor,
            Duration timeout,
            long intervalloSecondi) {
        this(monitor, timeout, intervalloSecondi, silenziose -> {});
    }

    public ServizioMonitoraggio(MonitorHeartbeat monitor, Duration timeout,
            long intervalloSecondi, java.util.function.Consumer<java.util.Set<String>> aggiornamento) {

        if (monitor == null) {
            throw new IllegalArgumentException(
                "Il monitor non può essere null."
            );
        }

        if (timeout == null || timeout.isZero()
                || timeout.isNegative()) {
            throw new IllegalArgumentException(
                "Il timeout deve essere positivo."
            );
        }

        if (intervalloSecondi <= 0) {
            throw new IllegalArgumentException(
                "L'intervallo deve essere positivo."
            );
        }

        this.monitor = monitor;
        this.aggiornamento = java.util.Objects.requireNonNull(aggiornamento);
        this.timeout = timeout;
        this.intervalloSecondi = intervalloSecondi;
    }

    /** Attiva il controllo periodico dei timeout del sistema. */
    public synchronized void avvia() {
        if (esecutore != null && !esecutore.isShutdown()) {
            return;
        }

        esecutore = Executors.newSingleThreadScheduledExecutor();

        esecutore.scheduleAtFixedRate(
            () -> {
                try {
                    aggiornamento.accept(monitor.controllaStazioni(timeout));
                } catch (Exception e) {
                    System.err.println(
                        "Errore nel controllo heartbeat: "
                        + e.getMessage()
                    );
                }
            },
            0,
            intervalloSecondi,
            TimeUnit.SECONDS
        );

        System.out.println(
            "Monitoraggio automatico avviato. Timeout: "
            + timeout.toSeconds() + " secondi."
        );
    }

    @Override
    /** Ferma il servizio di monitoraggio e rilascia le risorse. */
    public synchronized void close() {
        if (esecutore != null) {
            esecutore.shutdownNow();
            esecutore = null;
            System.out.println("Monitoraggio automatico arrestato.");
        }
    }
}
