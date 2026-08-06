package gui.trace;

/**
 * Intervallo continuo di esecuzione di un chunk su un task, ricostruito dagli
 * eventi {@code execute} e da quello immediatamente successivo.
 */
public final class ExecutionSegment {

    private final int taskId;
    private final int chunkId;
    private final double start;
    private double end;
    private final boolean critical;
    private boolean completed;

    // CONSTRUCTOR
    ExecutionSegment(int taskId, int chunkId, double start, boolean critical) {
        this.taskId = taskId;
        this.chunkId = chunkId;
        this.start = start;
        this.end = start;
        this.critical = critical;
        this.completed = false;
    }

    // GETTER AND SETTER
    public int getTaskId() {
        return this.taskId;
    }

    public int getChunkId() {
        return this.chunkId;
    }

    public double getStart() {
        return this.start;
    }

    public double getEnd() {
        return this.end;
    }

    void setEnd(double end) {
        this.end = end;
    }

    /** @return true se il chunk stava eseguendo dentro una sezione critica */
    public boolean isCritical() {
        return this.critical;
    }

    /** @return true se il chunk e arrivato a fine esecuzione, false se interrotto */
    public boolean isCompleted() {
        return this.completed;
    }

    void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public double getDuration() {
        return this.end - this.start;
    }

    /** @return etichetta del chunk nel formato usato dal log */
    public String getLabel() {
        return "Chunk" + this.taskId + "." + this.chunkId;
    }

    @Override
    public String toString() {
        return this.getLabel() + " [" + this.start + ", " + this.end + "]";
    }

}
