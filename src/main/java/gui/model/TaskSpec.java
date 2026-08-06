package gui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Descrizione di un task cosi come viene configurato dall'interfaccia grafica.
 * <p>
 * L'identificativo mostrato ({@code Task1}, {@code Task2}, ...) coincide con
 * quello che il simulatore assegnera al momento della costruzione, perche i
 * contatori statici vengono azzerati prima di ogni esecuzione e i task sono
 * costruiti nell'ordine in cui compaiono nella tabella.
 */
public final class TaskSpec {

    private double period;
    private double deadline;
    private final List<ChunkSpec> chunks = new ArrayList<>();

    // CONSTRUCTOR
    public TaskSpec(double period, double deadline) {
        this.period = period;
        this.deadline = deadline;
    }

    public TaskSpec copy() {
        TaskSpec clone = new TaskSpec(this.period, this.deadline);
        for (ChunkSpec chunk : this.chunks)
            clone.chunks.add(chunk.copy());
        return clone;
    }

    // GETTER AND SETTER
    public double getPeriod() {
        return this.period;
    }

    public void setPeriod(double period) {
        this.period = period;
    }

    public double getDeadline() {
        return this.deadline;
    }

    public void setDeadline(double deadline) {
        this.deadline = deadline;
    }

    public List<ChunkSpec> getChunks() {
        return this.chunks;
    }

    // METHOD
    /** @return il primo identificativo di chunk libero */
    public int nextChunkId() {
        int max = 0;
        for (ChunkSpec chunk : this.chunks)
            max = Math.max(max, chunk.getId());
        return max + 1;
    }

    /** @return true se almeno un chunk dichiara una risorsa condivisa */
    public boolean usesResources() {
        return this.chunks.stream().anyMatch(ChunkSpec::hasResources);
    }

    /** @return tempo di esecuzione atteso complessivo, overhead incluso */
    public double expectedExecutionTime() {
        return this.chunks.stream()
            .mapToDouble(chunk -> chunk.getExecutionTime().expectedValue()
                + chunk.getOverhead().expectedValue())
            .sum();
    }

    /** @return fattore di utilizzazione atteso del task */
    public double expectedUtilization() {
        if (this.period <= 0)
            return 0.0;
        return this.expectedExecutionTime() / this.period;
    }

    void onResourceRemoved(int removedIndex) {
        this.chunks.forEach(chunk -> chunk.onResourceRemoved(removedIndex));
    }

    // SERIALIZATION
    String encode() {
        return this.period + ";" + this.deadline;
    }

    static TaskSpec decode(String text) {
        String[] parts = text.split(";");
        if (parts.length < 2)
            throw new IllegalArgumentException("Task non valido: " + text);
        return new TaskSpec(
            Double.parseDouble(parts[0].trim()),
            Double.parseDouble(parts[1].trim()));
    }

}
