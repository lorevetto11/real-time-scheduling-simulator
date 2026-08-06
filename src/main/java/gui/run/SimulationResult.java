package gui.run;

import java.util.Collections;
import java.util.List;

import gui.model.SimulationSpec;
import gui.trace.DatasetStats;
import gui.trace.Trace;

/**
 * Esito completo di una esecuzione: le trace generate, le statistiche aggregate
 * e la configurazione con cui sono state prodotte.
 */
public final class SimulationResult {

    private final SimulationSpec spec;
    private final List<Trace> traces;
    private final DatasetStats stats;
    private final boolean truncated;
    private final long elapsedMillis;

    // CONSTRUCTOR
    public SimulationResult(SimulationSpec spec, List<Trace> traces, boolean truncated, long elapsedMillis) {
        this.spec = spec;
        this.traces = traces;
        this.stats = new DatasetStats(traces);
        this.truncated = truncated;
        this.elapsedMillis = elapsedMillis;
    }

    // GETTER
    public SimulationSpec getSpec() {
        return this.spec;
    }

    public List<Trace> getTraces() {
        return Collections.unmodifiableList(this.traces);
    }

    public DatasetStats getStats() {
        return this.stats;
    }

    /** @return true se il log e stato troncato per superamento del limite di righe */
    public boolean isTruncated() {
        return this.truncated;
    }

    public long getElapsedMillis() {
        return this.elapsedMillis;
    }

    public boolean isEmpty() {
        return this.traces.isEmpty();
    }

}
