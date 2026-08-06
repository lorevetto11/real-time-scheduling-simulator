package gui.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import resource.PriorityCeilingProtocol;
import resource.PriorityCeilingProtocolFaultAquireResource;
import resource.PriorityCeilingProtocolFaultSetPriority;
import resource.Resource;
import resource.ResourcesProtocol;
import scheduler.EDFScheduler;
import scheduler.RMScheduler;
import scheduler.Scheduler;
import taskSet.Chunk;
import taskSet.Task;
import taskSet.TaskSet;

/**
 * Traduce una {@link SimulationSpec} negli oggetti reali del simulatore.
 * <p>
 * Il builder non modifica in alcun modo la logica esistente: si limita a
 * invocare i costruttori pubblici gia disponibili. L'unica accortezza e
 * l'azzeramento dei contatori statici di {@link Task} e {@link Resource}, che
 * altrimenti continuerebbero a crescere fra un'esecuzione e la successiva
 * facendo comparire nel grafico identificativi come {@code Task11}. Il reset
 * avviene per riflessione, con lo stesso approccio gia usato dai test del
 * progetto, e non richiede quindi di toccare le classi del dominio.
 */
public final class SpecBuilder {

    private SpecBuilder() {}

    /**
     * Costruisce lo scheduler pronto per l'esecuzione a partire dalla
     * configurazione dell'interfaccia.
     *
     * @param spec la configurazione scelta dall'utente
     * @return lo scheduler configurato con taskset, protocollo e durata
     */
    public static Scheduler buildScheduler(SimulationSpec spec) {
        resetIdCounters();

        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < spec.getResourceCount(); i++)
            resources.add(new Resource());

        Set<Task> tasks = new LinkedHashSet<>();
        for (TaskSpec taskSpec : spec.getTasks())
            tasks.add(buildTask(taskSpec, resources));

        TaskSet taskSet = new TaskSet(tasks);
        double duration = spec.getDuration();

        if (spec.getAlgorithm() == SimulationSpec.Algorithm.EDF)
            return new EDFScheduler(taskSet, duration);

        ResourcesProtocol protocol = buildProtocol(spec);
        if (protocol == null)
            return new RMScheduler(taskSet, duration);
        return new RMScheduler(taskSet, protocol, duration);
    }

    /**
     * @param spec la configurazione scelta dall'utente
     * @return il protocollo di accesso alle risorse, oppure null se non serve
     */
    public static ResourcesProtocol buildProtocol(SimulationSpec spec) {
        switch (spec.getProtocol()) {
            case PCP:
                return new PriorityCeilingProtocol();
            case PCP_FAULT_ACQUIRE:
                return new PriorityCeilingProtocolFaultAquireResource(spec.getAcquireThreshold());
            case PCP_FAULT_PRIORITY:
                return new PriorityCeilingProtocolFaultSetPriority(spec.getDeltaMin(), spec.getDeltaMax());
            case NONE:
            default:
                return null;
        }
    }

    /**
     * Azzera i contatori statici degli identificativi in modo che ogni
     * esecuzione riparta da {@code Task1} e {@code Res1}.
     */
    public static void resetIdCounters() {
        setStaticInt(Task.class, "idCounter", 1);
        setStaticInt(Resource.class, "idCounter", 1);
    }

    // HELPER
    private static Task buildTask(TaskSpec taskSpec, List<Resource> resources) {
        List<Chunk> chunks = new ArrayList<>();
        for (ChunkSpec chunkSpec : taskSpec.getChunks()) {
            List<Resource> chunkResources = new ArrayList<>();
            chunkSpec.getResourceIndexes().stream()
                .sorted()
                .filter(index -> index >= 0 && index < resources.size())
                .forEach(index -> chunkResources.add(resources.get(index)));
            chunks.add(new Chunk(
                chunkSpec.getId(),
                chunkSpec.getExecutionTime().build(),
                chunkSpec.getOverhead().build(),
                chunkResources));
        }
        return new Task(taskSpec.getPeriod(), taskSpec.getDeadline(), chunks);
    }

    private static void setStaticInt(Class<?> clazz, String fieldName, int value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(null, value);
        } catch (ReflectiveOperationException | SecurityException e) {
            // Il reset e un'ottimizzazione estetica: se non e possibile la
            // simulazione resta comunque corretta, cambiano solo gli id mostrati.
            System.err.println("Impossibile azzerare " + clazz.getSimpleName() + "." + fieldName
                + ": " + e.getMessage());
        }
    }

}
