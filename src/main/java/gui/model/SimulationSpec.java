package gui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenitore di tutta la configurazione impostabile dall'interfaccia: taskset,
 * risorse condivise, algoritmo di scheduling, protocollo di accesso alle
 * risorse e parametri della simulazione.
 */
public final class SimulationSpec {

    /** Algoritmi di scheduling supportati dal simulatore. */
    public enum Algorithm {
        RM("Rate Monotonic", true),
        EDF("Earliest Deadline First", false);

        private final String label;
        private final boolean supportsResources;

        Algorithm(String label, boolean supportsResources) {
            this.label = label;
            this.supportsResources = supportsResources;
        }

        /**
         * @return true se l'algoritmo puo essere usato con un protocollo di
         *         accesso alle risorse. EDF non lo supporta perche gestisce le
         *         priorita in modo dinamico.
         */
        public boolean supportsResources() {
            return this.supportsResources;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /** Protocolli di accesso alle risorse disponibili nel progetto. */
    public enum Protocol {
        NONE("Nessuno (senza risorse)", false, false),
        PCP("Priority Ceiling Protocol", false, false),
        PCP_FAULT_ACQUIRE("PCP - fault acquisizione semaforo", true, false),
        PCP_FAULT_PRIORITY("PCP - fault innalzamento priorita", false, true);

        private final String label;
        private final boolean usesThreshold;
        private final boolean usesDelta;

        Protocol(String label, boolean usesThreshold, boolean usesDelta) {
            this.label = label;
            this.usesThreshold = usesThreshold;
            this.usesDelta = usesDelta;
        }

        public boolean usesThreshold() {
            return this.usesThreshold;
        }

        public boolean usesDelta() {
            return this.usesDelta;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private final List<TaskSpec> tasks = new ArrayList<>();
    private int resourceCount = 0;
    private Algorithm algorithm = Algorithm.RM;
    private Protocol protocol = Protocol.NONE;
    private double acquireThreshold = 0.2;
    private double deltaMin = 1.0;
    private double deltaMax = 3.0;
    private double duration = 500.0;
    private int traceCount = 1;

    // GETTER AND SETTER
    public List<TaskSpec> getTasks() {
        return this.tasks;
    }

    public int getResourceCount() {
        return this.resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = Math.max(0, resourceCount);
    }

    public Algorithm getAlgorithm() {
        return this.algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public Protocol getProtocol() {
        return this.protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public double getAcquireThreshold() {
        return this.acquireThreshold;
    }

    public void setAcquireThreshold(double acquireThreshold) {
        this.acquireThreshold = acquireThreshold;
    }

    public double getDeltaMin() {
        return this.deltaMin;
    }

    public void setDeltaMin(double deltaMin) {
        this.deltaMin = deltaMin;
    }

    public double getDeltaMax() {
        return this.deltaMax;
    }

    public void setDeltaMax(double deltaMax) {
        this.deltaMax = deltaMax;
    }

    public double getDuration() {
        return this.duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public int getTraceCount() {
        return this.traceCount;
    }

    public void setTraceCount(int traceCount) {
        this.traceCount = Math.max(1, traceCount);
    }

    // METHOD
    /** Aggiunge una risorsa condivisa e ne restituisce l'indice. */
    public int addResource() {
        return this.resourceCount++;
    }

    /** Elimina una risorsa e aggiorna i riferimenti di tutti i chunk. */
    public void removeResource(int index) {
        if (index < 0 || index >= this.resourceCount)
            return;
        this.resourceCount--;
        this.tasks.forEach(task -> task.onResourceRemoved(index));
    }

    /** @return true se almeno un chunk del taskset dichiara una risorsa */
    public boolean usesResources() {
        return this.tasks.stream().anyMatch(TaskSpec::usesResources);
    }

    /** @return fattore di utilizzazione atteso dell'intero taskset */
    public double expectedUtilization() {
        return this.tasks.stream()
            .mapToDouble(TaskSpec::expectedUtilization)
            .sum();
    }

    /** @return prodotto del test iperbolico calcolato sui valori attesi */
    public double expectedHyperbolicProduct() {
        return this.tasks.stream()
            .mapToDouble(task -> task.expectedUtilization() + 1)
            .reduce(1.0, (a, b) -> a * b);
    }

    /**
     * Elenca i problemi che impedirebbero l'avvio della simulazione.
     *
     * @return la lista dei messaggi di errore, vuota se la configurazione e valida
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (this.tasks.isEmpty())
            problems.add("Il taskset e vuoto: aggiungi almeno un task.");
        for (int i = 0; i < this.tasks.size(); i++) {
            TaskSpec task = this.tasks.get(i);
            String name = "Task" + (i + 1);
            if (task.getPeriod() <= 0)
                problems.add(name + ": il periodo deve essere maggiore di zero.");
            if (task.getDeadline() <= 0)
                problems.add(name + ": la deadline deve essere maggiore di zero.");
            if (task.getChunks().isEmpty())
                problems.add(name + ": serve almeno un chunk.");
            if (this.algorithm == Algorithm.RM && task.getPeriod() != task.getDeadline())
                problems.add(name + ": Rate Monotonic richiede periodo uguale alla deadline.");
            if (this.algorithm == Algorithm.EDF && task.getPeriod() < task.getDeadline())
                problems.add(name + ": il periodo non puo essere minore della deadline.");
        }
        if (this.duration <= 0)
            problems.add("La durata della simulazione deve essere maggiore di zero.");
        if (this.algorithm == Algorithm.EDF && this.usesResources())
            problems.add("EDF non supporta le risorse condivise: rimuovi le risorse dai chunk.");
        if (this.algorithm == Algorithm.EDF && this.protocol != Protocol.NONE)
            problems.add("EDF non accetta un protocollo di accesso alle risorse.");
        if (this.protocol != Protocol.NONE && !this.usesResources())
            problems.add("E stato scelto un protocollo ma nessun chunk usa risorse condivise.");
        if (this.protocol == Protocol.NONE && this.usesResources())
            problems.add("Alcuni chunk usano risorse condivise ma non e stato scelto un protocollo.");
        if (this.protocol == Protocol.PCP_FAULT_ACQUIRE
                && (this.acquireThreshold < 0.0 || this.acquireThreshold > 1.0))
            problems.add("La soglia di acquisizione deve essere compresa tra 0.0 e 1.0.");
        if (this.protocol == Protocol.PCP_FAULT_PRIORITY && this.deltaMin > this.deltaMax)
            problems.add("Il delta minimo non puo superare il delta massimo.");
        return problems;
    }

    /** Sostituisce integralmente il contenuto con quello di un'altra configurazione. */
    public void copyFrom(SimulationSpec other) {
        this.tasks.clear();
        for (TaskSpec task : other.tasks)
            this.tasks.add(task.copy());
        this.resourceCount = other.resourceCount;
        this.algorithm = other.algorithm;
        this.protocol = other.protocol;
        this.acquireThreshold = other.acquireThreshold;
        this.deltaMin = other.deltaMin;
        this.deltaMax = other.deltaMax;
        this.duration = other.duration;
        this.traceCount = other.traceCount;
    }

    /**
     * Configurazione di esempio con cui si apre l'applicazione: e lo stesso
     * taskset presente nel {@code Main} del progetto, con due risorse gia
     * dichiarate ma non ancora assegnate ad alcun chunk, cosi che la prima
     * esecuzione parta senza protocollo di accesso alle risorse.
     */
    public static SimulationSpec sample() {
        SimulationSpec spec = new SimulationSpec();
        spec.setResourceCount(2);

        TaskSpec task1 = new TaskSpec(35, 35);
        task1.getChunks().add(chunk(1, SamplerSpec.uniform(2, 2)));
        task1.getChunks().add(chunk(2, SamplerSpec.uniform(1, 1.5)));
        task1.getChunks().add(chunk(3, SamplerSpec.uniform(0.5, 1)));

        TaskSpec task2 = new TaskSpec(50, 50);
        task2.getChunks().add(chunk(1, SamplerSpec.uniform(3, 4)));
        task2.getChunks().add(chunk(2, SamplerSpec.uniform(3, 3.5)));
        task2.getChunks().add(chunk(3, SamplerSpec.uniform(3, 3.5)));

        TaskSpec task3 = new TaskSpec(80, 80);
        task3.getChunks().add(chunk(1, SamplerSpec.uniform(4, 5)));
        task3.getChunks().add(chunk(2, SamplerSpec.uniform(4, 4.5)));

        TaskSpec task4 = new TaskSpec(110, 110);
        task4.getChunks().add(chunk(1, SamplerSpec.uniform(5, 5.5)));
        task4.getChunks().add(chunk(2, SamplerSpec.uniform(2, 3)));

        TaskSpec task5 = new TaskSpec(160, 160);
        task5.getChunks().add(chunk(2, SamplerSpec.uniform(3.5, 4)));

        spec.tasks.add(task1);
        spec.tasks.add(task2);
        spec.tasks.add(task3);
        spec.tasks.add(task4);
        spec.tasks.add(task5);

        spec.setAlgorithm(Algorithm.RM);
        spec.setProtocol(Protocol.NONE);
        spec.setDuration(480);
        spec.setTraceCount(5);
        return spec;
    }

    // HELPER
    private static ChunkSpec chunk(int id, SamplerSpec execution) {
        return new ChunkSpec(id, execution, SamplerSpec.none());
    }

}
