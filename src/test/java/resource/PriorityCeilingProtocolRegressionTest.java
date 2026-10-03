package resource;

import static org.assertj.core.api.Assertions.assertThatCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import scheduler.RMScheduler;
import taskSet.Chunk;
import taskSet.Task;
import taskSet.TaskSet;
import utils.sampler.ConstantSampler;

public class PriorityCeilingProtocolRegressionTest {

    @Test
    public void accessHigherPriorityThanCeilingOK() {
        Resource res1 = new Resource();
        Resource res2 = new Resource();
        Task task1 = new Task(
            10,
            10,
            List.of(
                new Chunk(1, new ConstantSampler(new BigDecimal(1))),
                new Chunk(2, new ConstantSampler(new BigDecimal(1)), List.of(res2))));
        Task task2 = new Task(
            100,
            100,
            List.of(
                new Chunk(1, new ConstantSampler(new BigDecimal(15)), List.of(res1))));
        TaskSet taskSet = new TaskSet(Set.of(task1, task2));
        ResourcesProtocol protocol = new PriorityCeilingProtocol();
        RMScheduler scheduler = new RMScheduler(taskSet, protocol, 40);
        assertThatCode(() -> scheduler.schedule())
            .doesNotThrowAnyException();
    }

    @Test
    public void releasedResourceNotInCeilingOK() {
        Resource res1 = new Resource();
        Resource res2 = new Resource();
        Task task1 = new Task(
            10,
            10,
            List.of(
                new Chunk(1, new ConstantSampler(new BigDecimal(1))),
                new Chunk(2, new ConstantSampler(new BigDecimal(1)), List.of(res1))));
        Task task2 = new Task(
            20,
            20,
            List.of(
                new Chunk(1, new ConstantSampler(new BigDecimal(2))),
                new Chunk(2, new ConstantSampler(new BigDecimal(1)), List.of(res2))));
        Task task3 = new Task(
            40,
            40,
            List.of(
                new Chunk(1, new ConstantSampler(new BigDecimal(8)), List.of(res1))));
        TaskSet taskSet = new TaskSet(Set.of(task1, task2, task3));
        ResourcesProtocol protocol = new PriorityCeilingProtocol();
        RMScheduler scheduler = new RMScheduler(taskSet, protocol, 40);
        assertThatCode(() -> scheduler.schedule())
            .doesNotThrowAnyException();
    }

}
