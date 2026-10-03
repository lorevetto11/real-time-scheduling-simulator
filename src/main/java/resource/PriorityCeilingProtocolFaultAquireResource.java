package resource;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import taskSet.Chunk;
import taskSet.Task;
import taskSet.TaskSet;
import utils.Utils;
import utils.logger.MyLogger;

/**
 * The {@code PriorityCeilingProtocolFaultAquireResource} class implements the PCP protocol that simulates a fault in acquiring resources.
 * With a given probability (specified by the {@code threshold} parameter), a task use a resource without acquiring it first.
 */
public final class PriorityCeilingProtocolFaultAquireResource extends PCP {

    private final double threshold;
    private List<Chunk> faultChunks = new LinkedList<>();

    // CONSTRUCTOR
    /**
     * @param threshold the threshold value, which must be in the range [0.0, 1.0].
     */
    public PriorityCeilingProtocolFaultAquireResource (double threshold) {
        if (threshold<0.0 || threshold>1.0)
            throw new IllegalArgumentException("La soglia con cui acquisire un semaforo deve essere compresa tra 0.0 e 1.0.");
        this.threshold = threshold;
    }

    // METHOD
    @Override
    public void progress(Chunk chunk) {
        Task parentTask = chunk.getParent();
        List<Resource> resources = chunk.getResources();
        int MaxDinamicPriorityBlockedtask = resources.stream()
            .flatMap(res -> res.getBlockedTasks().stream())
            .mapToInt(Task::getNominalPriority)
            .min()
            .orElse(Integer.MAX_VALUE);
        parentTask.setDinamicPriority(Math.min(
            parentTask.getNominalPriority(),
            MaxDinamicPriorityBlockedtask));
        if (!resources.isEmpty()) {
            if (Math.random() > this.threshold) {
                String resourcesId = resources.stream()
                    .map(Resource::toString)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]"));
                MyLogger.wrn("Il Chunk" + chunk.toString()
                    +" non ha acquisito le risorse " + resourcesId
                    + " prima di usarle");
            } else {
                boolean newLock = this.lockResources(chunk);
                String resourcesId = resources.stream()
                    .map(Resource::toString)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]"));
                if (newLock)
                    MyLogger.log("<" + Utils.printCurrentTime() + ", " + chunk.toString() + " lock " + resourcesId + ">");
                this.faultChunks.add(chunk);
            }
        }
    }

    @Override
    public void release(Chunk chunk) {
        List<Resource> resources = chunk.getResources();
        if (resources.isEmpty())
            return;
        Task parentTask = chunk.getParent();
        if (!this.faultChunks.contains(chunk)) {
            this.unlockResources(chunk);
        }
        parentTask.getResourcesAcquiredStream()
            .flatMap(res -> res.getBlockedTasks().stream())
            .mapToInt(Task::getNominalPriority)
            .min()
            .ifPresentOrElse(
                parentTask::setDinamicPriority,
                () -> parentTask.setDinamicPriority(parentTask.getNominalPriority()));
        if (!this.faultChunks.contains(chunk)) {
            String resourcesId = resources.stream()
                .map(Resource::toString)
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
            MyLogger.log("<" + Utils.printCurrentTime() + ", " + chunk.toString() + " unlock " + resourcesId + ">");
            this.faultChunks.remove(chunk);
        }
    }

    @Override
    public void initStructures(TaskSet taskSet) {
        this.resetStructures();
        this.faultChunks = new LinkedList<>();
        for (Task task : taskSet.getTasks())
            for (Chunk chunk : task.getChunks())
                for (Resource resource : chunk.getResources())
                    this.mergeCeiling(resource, task.getNominalPriority());
    }

}
