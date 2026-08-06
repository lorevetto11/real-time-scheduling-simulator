package gui.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistiche aggregate calcolate sull'insieme delle trace generate.
 */
public final class DatasetStats {

    /** Riepilogo dei tempi di risposta di un singolo task. */
    public static final class TaskStats {

        private final int taskId;
        private int jobs = 0;
        private int completedJobs = 0;
        private int missedJobs = 0;
        private double totalResponse = 0.0;
        private double maxResponse = 0.0;

        TaskStats(int taskId) {
            this.taskId = taskId;
        }

        public int getTaskId() {
            return this.taskId;
        }

        public int getJobs() {
            return this.jobs;
        }

        public int getCompletedJobs() {
            return this.completedJobs;
        }

        public int getMissedJobs() {
            return this.missedJobs;
        }

        public double getAverageResponse() {
            return this.completedJobs == 0 ? Double.NaN : this.totalResponse / this.completedJobs;
        }

        public double getMaxResponse() {
            return this.completedJobs == 0 ? Double.NaN : this.maxResponse;
        }

        void accumulate(Job job) {
            this.jobs++;
            if (job.isDeadlineMissed())
                this.missedJobs++;
            if (!job.isCompleted())
                return;
            this.completedJobs++;
            double response = job.getResponseTime();
            this.totalResponse += response;
            this.maxResponse = Math.max(this.maxResponse, response);
        }
    }

    private final int traceCount;
    private int missedTraces = 0;
    private int preemptions = 0;
    private int warnings = 0;
    private int blocks = 0;
    private final Map<Integer, TaskStats> perTask = new LinkedHashMap<>();

    // CONSTRUCTOR
    public DatasetStats(List<Trace> traces) {
        this.traceCount = traces.size();
        for (Trace trace : traces) {
            if (trace.isDeadlineMissed())
                this.missedTraces++;
            this.preemptions += trace.countPreemptions();
            this.warnings += trace.countWarnings();
            this.blocks += trace.countBlocks();
            for (Job job : trace.getJobs())
                this.perTask
                    .computeIfAbsent(job.getTaskId(), TaskStats::new)
                    .accumulate(job);
        }
    }

    // GETTER
    public int getTraceCount() {
        return this.traceCount;
    }

    public int getMissedTraces() {
        return this.missedTraces;
    }

    public int getPreemptions() {
        return this.preemptions;
    }

    public int getWarnings() {
        return this.warnings;
    }

    public int getBlocks() {
        return this.blocks;
    }

    /** @return percentuale di trace terminate con un deadline miss */
    public double getMissRate() {
        return this.traceCount == 0 ? 0.0 : 100.0 * this.missedTraces / this.traceCount;
    }

    /** @return tempo di risposta medio su tutti i job completati */
    public double getAverageResponse() {
        double total = 0.0;
        int count = 0;
        for (TaskStats stats : this.perTask.values()) {
            if (stats.getCompletedJobs() == 0)
                continue;
            total += stats.getAverageResponse() * stats.getCompletedJobs();
            count += stats.getCompletedJobs();
        }
        return count == 0 ? Double.NaN : total / count;
    }

    /** @return statistiche per task, ordinate per identificativo */
    public List<TaskStats> getPerTask() {
        List<TaskStats> stats = new ArrayList<>(this.perTask.values());
        stats.sort((a, b) -> Integer.compare(a.getTaskId(), b.getTaskId()));
        return stats;
    }

}
