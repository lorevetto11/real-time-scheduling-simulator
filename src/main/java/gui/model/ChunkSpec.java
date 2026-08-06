package gui.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Descrizione di un chunk cosi come viene configurato dall'interfaccia grafica.
 * <p>
 * Le risorse sono memorizzate come indici (0-based) riferiti alla lista di
 * risorse della {@link SimulationSpec}: gli oggetti {@code Resource} veri
 * vengono creati una sola volta, subito prima di ogni esecuzione.
 */
public final class ChunkSpec {

    private int id;
    private SamplerSpec executionTime;
    private SamplerSpec overhead;
    private final Set<Integer> resourceIndexes = new LinkedHashSet<>();

    // CONSTRUCTOR
    public ChunkSpec(int id, SamplerSpec executionTime, SamplerSpec overhead) {
        this.id = id;
        this.executionTime = executionTime;
        this.overhead = overhead;
    }

    public ChunkSpec copy() {
        ChunkSpec clone = new ChunkSpec(this.id, this.executionTime.copy(), this.overhead.copy());
        clone.resourceIndexes.addAll(this.resourceIndexes);
        return clone;
    }

    // GETTER AND SETTER
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public SamplerSpec getExecutionTime() {
        return this.executionTime;
    }

    public void setExecutionTime(SamplerSpec executionTime) {
        this.executionTime = executionTime;
    }

    public SamplerSpec getOverhead() {
        return this.overhead;
    }

    public void setOverhead(SamplerSpec overhead) {
        this.overhead = overhead;
    }

    public Set<Integer> getResourceIndexes() {
        return this.resourceIndexes;
    }

    public void setResourceIndexes(Iterable<Integer> indexes) {
        this.resourceIndexes.clear();
        for (Integer index : indexes)
            this.resourceIndexes.add(index);
    }

    public boolean hasResources() {
        return !this.resourceIndexes.isEmpty();
    }

    // METHOD
    /**
     * @return l'etichetta delle risorse nello stesso formato usato dal log,
     *         ad esempio {@code [Res1, Res2]}
     */
    public String describeResources() {
        if (this.resourceIndexes.isEmpty())
            return "—";
        return this.resourceIndexes.stream()
            .sorted()
            .map(index -> "Res" + (index + 1))
            .collect(Collectors.joining(", ", "[", "]"));
    }

    /** Rimuove il riferimento a una risorsa eliminata e ricompatta gli indici. */
    void onResourceRemoved(int removedIndex) {
        List<Integer> updated = new ArrayList<>();
        for (Integer index : this.resourceIndexes) {
            if (index == removedIndex)
                continue;
            updated.add(index > removedIndex ? index - 1 : index);
        }
        this.resourceIndexes.clear();
        this.resourceIndexes.addAll(updated);
    }

    // SERIALIZATION
    String encode() {
        String resources = this.resourceIndexes.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        return this.id + ";" + this.executionTime.encode() + ";" + this.overhead.encode() + ";" + resources;
    }

    static ChunkSpec decode(String text) {
        String[] parts = text.split(";", -1);
        if (parts.length < 3)
            throw new IllegalArgumentException("Chunk non valido: " + text);
        ChunkSpec chunk = new ChunkSpec(
            Integer.parseInt(parts[0].trim()),
            SamplerSpec.decode(parts[1]),
            SamplerSpec.decode(parts[2]));
        if (parts.length > 3 && !parts[3].isBlank())
            for (String index : parts[3].split(","))
                chunk.resourceIndexes.add(Integer.parseInt(index.trim()));
        return chunk;
    }

    @Override
    public String toString() {
        return "Chunk" + this.id;
    }

}
