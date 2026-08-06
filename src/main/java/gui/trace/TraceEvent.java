package gui.trace;

/**
 * Un singolo evento della trace, ricostruito a partire da una riga di log
 * prodotta dal simulatore.
 */
public final class TraceEvent {

    /** Tipologie di evento riconosciute nel log. */
    public enum Type {
        RELEASE("release"),
        EXECUTE("execute"),
        FINISH("finish"),
        COMPLETE("complete"),
        PREEMPT("preempt"),
        LOCK("lock"),
        UNLOCK("unlock"),
        BLOCKED("blockedOn"),
        DEADLINE_MISS("deadlineMiss"),
        END("end"),
        WARNING("warning"),
        UNKNOWN("altro");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private final double time;
    private final Type type;
    private final int taskId;
    private final int chunkId;
    private final String resources;
    private final String raw;
    private final boolean warning;

    // CONSTRUCTOR
    TraceEvent(double time, Type type, int taskId, int chunkId, String resources, String raw, boolean warning) {
        this.time = time;
        this.type = type;
        this.taskId = taskId;
        this.chunkId = chunkId;
        this.resources = resources;
        this.raw = raw;
        this.warning = warning;
    }

    // GETTER
    /** @return istante dell'evento in millisecondi */
    public double getTime() {
        return this.time;
    }

    public Type getType() {
        return this.type;
    }

    /** @return identificativo del task, oppure 0 se l'evento non riguarda un task */
    public int getTaskId() {
        return this.taskId;
    }

    /** @return identificativo del chunk, oppure 0 se l'evento non riguarda un chunk */
    public int getChunkId() {
        return this.chunkId;
    }

    /** @return elenco delle risorse coinvolte nel formato {@code [Res1, Res2]} */
    public String getResources() {
        return this.resources;
    }

    /** @return la riga di log originale */
    public String getRaw() {
        return this.raw;
    }

    public boolean isWarning() {
        return this.warning;
    }

    /** @return true se l'evento porta con se un istante temporale valido */
    public boolean hasTime() {
        return !this.warning;
    }

    @Override
    public String toString() {
        return this.raw;
    }

}
