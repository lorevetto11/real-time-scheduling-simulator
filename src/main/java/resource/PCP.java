package resource;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import exeptions.AccessResourceProtocolExeption;
import taskSet.Chunk;
import taskSet.Task;
import utils.Utils;
import utils.logger.MyLogger;

public abstract class PCP extends ResourcesProtocol{

    private Map<Resource, Integer> ceiling = new HashMap<>();
    private Map<Resource, Task> busyResources = new HashMap<>();

    //GETTER AND SETTER
    protected Integer getCeilingValue(Resource resource) {
        return this.ceiling.get(resource);
    }

    protected void mergeCeiling(Resource resource, int nominalPriority) {
        this.ceiling.merge(resource, nominalPriority, Math::min);
    }

    protected void resetStructures() {
        this.ceiling = new HashMap<>();
        this.busyResources = new HashMap<>();
    }

    protected Map<Resource, Task> getBusyResources() {
        return this.busyResources;
    }

    //METHODS
    @Override
    public void access(Chunk chunk) throws AccessResourceProtocolExeption {
        if (!chunk.hasResources())
            return;
        Task parentTask = chunk.getParent();
        Optional<Resource> blockingResource = this.getBusyResources().entrySet().stream()
            .filter(entry -> !entry.getValue().equals(parentTask))
            .map(Map.Entry::getKey)
            .min(Comparator.comparingInt(this::getCeilingValue));
        int maxCeiling = blockingResource
            .map(this::getCeilingValue)
            .orElse(Integer.MAX_VALUE);
        if (parentTask.getNominalPriority() >= maxCeiling) {
            Resource resource = blockingResource.get();
            resource.addBlockedTask(parentTask);
            getScheduler().blockTask(parentTask);
            parentTask.addChunkToExecute(chunk);
            MyLogger.log("<" + Utils.printCurrentTime() + ", " + chunk.toString() + " blockedOn [" + resource.toString() + "]>");
            throw new AccessResourceProtocolExeption();
        }
    }

    // HELPER
    /**
     * Marks the resources of the chunk as owned by its parent task.
     *
     * @param chunk the chunk that enters its critical section
     */
    protected void lockResources(Chunk chunk) {
        Task parentTask = chunk.getParent();
        for (Resource resource : chunk.getResources()) {
            if (!parentTask.hasAquiredThatResource(resource))
                parentTask.acquireResources(List.of(resource));
            this.busyResources.put(resource, parentTask);
        }
    }

    /**
     * Releases the resources of the chunk and wakes up all the tasks blocked on them.
     *
     * @param chunk the chunk that exits its critical section
     */
    protected void unlockResources(Chunk chunk) {
        Task parentTask = chunk.getParent();
        for (Resource resource : chunk.getResources()) {
            if (Objects.equals(this.busyResources.get(resource), parentTask))
                this.busyResources.remove(resource);
            parentTask.releaseResource(resource);
            for (Task blockedTask : List.copyOf(resource.getBlockedTasks())) {
                resource.removeBlockedTask(blockedTask);
                this.getScheduler().unblockTask(blockedTask);
                this.getScheduler().addReadyTask(blockedTask);
            }
        }
    }

}
