package gui.run;

import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import exeptions.DeadlineMissedException;
import gui.model.SimulationSpec;
import gui.model.SpecBuilder;
import gui.trace.Trace;
import gui.trace.TraceCollector;
import gui.trace.TraceParser;
import scheduler.Scheduler;

/**
 * Esegue la simulazione fuori dall'Event Dispatch Thread e restituisce le trace
 * gia ricostruite.
 * <p>
 * Il ciclo sulle trace riproduce esattamente il comportamento di
 * {@code Scheduler.scheduleDataset}, cioe ignora le eccezioni di deadline miss
 * e passa alla trace successiva, ma lo fa dall'esterno per poter riportare
 * l'avanzamento all'interfaccia.
 */
public final class SimulationRunner extends SwingWorker<SimulationResult, Integer> {

    private final SimulationSpec spec;
    private final Consumer<Integer> progressListener;

    // CONSTRUCTOR
    /**
     * @param spec             la configurazione da simulare, gia validata
     * @param progressListener notificato con il numero di trace completate
     */
    public SimulationRunner(SimulationSpec spec, Consumer<Integer> progressListener) {
        this.spec = spec;
        this.progressListener = progressListener;
    }

    // METHOD
    @Override
    protected SimulationResult doInBackground() throws Exception {
        long start = System.currentTimeMillis();
        TraceCollector collector = new TraceCollector();
        Scheduler scheduler;

        collector.attach();
        try {
            scheduler = SpecBuilder.buildScheduler(this.spec);
            for (int i = 0; i < this.spec.getTraceCount(); i++) {
                if (this.isCancelled())
                    break;
                try {
                    scheduler.schedule();
                } catch (DeadlineMissedException e) {
                    // la trace si interrompe alla prima deadline persa: e il
                    // comportamento previsto dal simulatore
                }
                this.publish(i + 1);
            }
        } finally {
            collector.detach();
        }

        List<Trace> traces = new TraceParser(this.spec).parse(collector.getEntries());
        long elapsed = System.currentTimeMillis() - start;
        return new SimulationResult(this.spec, traces, collector.isTruncated(), elapsed);
    }

    @Override
    protected void process(List<Integer> chunks) {
        if (this.progressListener != null && !chunks.isEmpty())
            this.progressListener.accept(chunks.get(chunks.size() - 1));
    }

}
