package gui.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una singola trace di esecuzione: la sequenza completa degli eventi, gli
 * intervalli di esecuzione ricostruiti e i job di ciascun task.
 */
public final class Trace {

    private final int index;
    private final List<TraceEvent> events = new ArrayList<>();
    private final List<ExecutionSegment> segments = new ArrayList<>();
    private final List<Job> jobs = new ArrayList<>();
    private double horizon = 0.0;
    private boolean deadlineMissed = false;
    private double deadlineMissTime = Double.NaN;
    private int deadlineMissTaskId = 0;

    // CONSTRUCTOR
    Trace(int index) {
        this.index = index;
    }

    // GETTER AND SETTER
    /** @return numero progressivo della trace all'interno del dataset, a partire da 1 */
    public int getIndex() {
        return this.index;
    }

    public List<TraceEvent> getEvents() {
        return Collections.unmodifiableList(this.events);
    }

    public List<ExecutionSegment> getSegments() {
        return Collections.unmodifiableList(this.segments);
    }

    public List<Job> getJobs() {
        return Collections.unmodifiableList(this.jobs);
    }

    /** @return istante finale della trace in millisecondi */
    public double getHorizon() {
        return this.horizon;
    }

    public boolean isDeadlineMissed() {
        return this.deadlineMissed;
    }

    public double getDeadlineMissTime() {
        return this.deadlineMissTime;
    }

    public int getDeadlineMissTaskId() {
        return this.deadlineMissTaskId;
    }

    // METHOD
    /** @return numero di preemption osservate nella trace */
    public int countPreemptions() {
        return (int) this.events.stream()
            .filter(event -> event.getType() == TraceEvent.Type.PREEMPT)
            .count();
    }

    /** @return numero di messaggi di warning presenti nella trace */
    public int countWarnings() {
        return (int) this.events.stream()
            .filter(TraceEvent::isWarning)
            .count();
    }

    /** @return numero di blocchi su semaforo */
    public int countBlocks() {
        return (int) this.events.stream()
            .filter(event -> event.getType() == TraceEvent.Type.BLOCKED)
            .count();
    }

    /** @return elenco ordinato degli identificativi di task presenti nella trace */
    public List<Integer> getTaskIds() {
        List<Integer> ids = new ArrayList<>();
        for (TraceEvent event : this.events)
            if (event.getTaskId() > 0 && !ids.contains(event.getTaskId()))
                ids.add(event.getTaskId());
        Collections.sort(ids);
        return ids;
    }

    /** @return descrizione sintetica dell'esito, da mostrare accanto al selettore */
    public String describeOutcome() {
        if (!this.deadlineMissed)
            return "completata fino a " + String.format("%.3f", this.horizon) + " ms";
        return "deadline miss di Task" + this.deadlineMissTaskId
            + " a " + String.format("%.3f", this.deadlineMissTime) + " ms";
    }

    // PACKAGE API
    void addEvent(TraceEvent event) {
        this.events.add(event);
        if (event.hasTime())
            this.horizon = Math.max(this.horizon, event.getTime());
    }

    void addSegment(ExecutionSegment segment) {
        this.segments.add(segment);
    }

    void addJob(Job job) {
        this.jobs.add(job);
    }

    void markDeadlineMiss(int taskId, double time) {
        this.deadlineMissed = true;
        this.deadlineMissTaskId = taskId;
        this.deadlineMissTime = time;
    }

    boolean isEmpty() {
        return this.events.isEmpty();
    }

    @Override
    public String toString() {
        return "Trace " + this.index;
    }

}
