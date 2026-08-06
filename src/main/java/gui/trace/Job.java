package gui.trace;

/**
 * Una singola istanza (job) di un task all'interno di una trace, dal rilascio
 * al completamento.
 */
public final class Job {

    private final int taskId;
    private final double release;
    private final double absoluteDeadline;
    private double completion = Double.NaN;
    private boolean deadlineMissed = false;

    // CONSTRUCTOR
    Job(int taskId, double release, double absoluteDeadline) {
        this.taskId = taskId;
        this.release = release;
        this.absoluteDeadline = absoluteDeadline;
    }

    // GETTER AND SETTER
    public int getTaskId() {
        return this.taskId;
    }

    public double getRelease() {
        return this.release;
    }

    public double getAbsoluteDeadline() {
        return this.absoluteDeadline;
    }

    public double getCompletion() {
        return this.completion;
    }

    void setCompletion(double completion) {
        this.completion = completion;
    }

    public boolean isDeadlineMissed() {
        return this.deadlineMissed;
    }

    void setDeadlineMissed(boolean deadlineMissed) {
        this.deadlineMissed = deadlineMissed;
    }

    public boolean isCompleted() {
        return !Double.isNaN(this.completion);
    }

    /** @return tempo di risposta del job, oppure NaN se non e stato completato */
    public double getResponseTime() {
        return this.isCompleted() ? this.completion - this.release : Double.NaN;
    }

}
