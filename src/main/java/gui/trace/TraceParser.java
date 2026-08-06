package gui.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gui.model.SimulationSpec;
import gui.model.TaskSpec;

/**
 * Ricostruisce le trace a partire dalle righe di log raccolte dal
 * {@link TraceCollector}.
 * <p>
 * Il formato riconosciuto e quello gia prodotto dal simulatore, cioe coppie
 * {@code <istante, evento>}. Gli intervalli di esecuzione sono ricavati dagli
 * eventi {@code execute}: un intervallo inizia in corrispondenza dell'evento e
 * termina all'istante del primo evento successivo, perche il clock avanza
 * soltanto durante l'esecuzione di un chunk.
 */
public final class TraceParser {

    private static final Pattern EVENT = Pattern.compile("^<\\s*([0-9]+(?:\\.[0-9]+)?)\\s*,\\s*(.*?)\\s*>$");
    private static final Pattern TASK_EVENT = Pattern.compile("^(release|complete|preempt|deadlineMiss)\\s+Task(\\d+)$");
    private static final Pattern CHUNK_EVENT = Pattern.compile("^(execute|finish)\\s+Chunk(\\d+)\\.(\\d+)$");
    private static final Pattern LOCK_EVENT = Pattern.compile("^Chunk(\\d+)\\.(\\d+)\\s*(lock|unlock)\\s*(\\[.*\\])$");
    private static final Pattern BLOCKED_EVENT = Pattern.compile("^Chunk(\\d+)\\.(\\d+)\\s*blockedOn\\s*(\\[.*\\])$");

    private final Map<Integer, Double> relativeDeadlines = new HashMap<>();

    // CONSTRUCTOR
    /**
     * @param spec la configurazione usata per la simulazione, da cui si
     *             ricavano le deadline relative dei task
     */
    public TraceParser(SimulationSpec spec) {
        List<TaskSpec> tasks = spec.getTasks();
        for (int i = 0; i < tasks.size(); i++)
            this.relativeDeadlines.put(i + 1, tasks.get(i).getDeadline());
    }

    // METHOD
    /**
     * @param entries le righe raccolte durante l'esecuzione
     * @return l'elenco delle trace, una per ogni esecuzione completata o interrotta
     */
    public List<Trace> parse(List<TraceCollector.Entry> entries) {
        List<Trace> traces = new ArrayList<>();
        Trace current = new Trace(1);
        double lastTime = 0.0;

        for (TraceCollector.Entry entry : entries) {
            TraceEvent event = this.toEvent(entry, lastTime);
            if (event == null)
                continue;
            if (event.hasTime())
                lastTime = event.getTime();
            current.addEvent(event);
            if (event.getType() == TraceEvent.Type.END
                    || event.getType() == TraceEvent.Type.DEADLINE_MISS) {
                traces.add(current);
                current = new Trace(traces.size() + 1);
                lastTime = 0.0;
            }
        }
        if (!current.isEmpty())
            traces.add(current);

        for (Trace trace : traces) {
            this.buildSegments(trace);
            this.buildJobs(trace);
        }
        return traces;
    }

    // HELPER
    private TraceEvent toEvent(TraceCollector.Entry entry, double lastTime) {
        String message = entry.getMessage();
        if (message.isEmpty())
            return null;

        Matcher matcher = EVENT.matcher(message);
        if (!matcher.matches())
            return new TraceEvent(lastTime, TraceEvent.Type.WARNING, 0, 0, "", message, true);

        double time = Double.parseDouble(matcher.group(1));
        String body = matcher.group(2);

        if ("end".equals(body))
            return new TraceEvent(time, TraceEvent.Type.END, 0, 0, "", message, false);

        Matcher taskMatcher = TASK_EVENT.matcher(body);
        if (taskMatcher.matches()) {
            TraceEvent.Type type = switch (taskMatcher.group(1)) {
                case "release" -> TraceEvent.Type.RELEASE;
                case "complete" -> TraceEvent.Type.COMPLETE;
                case "preempt" -> TraceEvent.Type.PREEMPT;
                default -> TraceEvent.Type.DEADLINE_MISS;
            };
            int taskId = Integer.parseInt(taskMatcher.group(2));
            return new TraceEvent(time, type, taskId, 0, "", message, false);
        }

        Matcher chunkMatcher = CHUNK_EVENT.matcher(body);
        if (chunkMatcher.matches()) {
            TraceEvent.Type type = "execute".equals(chunkMatcher.group(1))
                ? TraceEvent.Type.EXECUTE
                : TraceEvent.Type.FINISH;
            return new TraceEvent(
                time,
                type,
                Integer.parseInt(chunkMatcher.group(2)),
                Integer.parseInt(chunkMatcher.group(3)),
                "",
                message,
                false);
        }

        Matcher lockMatcher = LOCK_EVENT.matcher(body);
        if (lockMatcher.matches()) {
            TraceEvent.Type type = "lock".equals(lockMatcher.group(3))
                ? TraceEvent.Type.LOCK
                : TraceEvent.Type.UNLOCK;
            return new TraceEvent(
                time,
                type,
                Integer.parseInt(lockMatcher.group(1)),
                Integer.parseInt(lockMatcher.group(2)),
                lockMatcher.group(4),
                message,
                false);
        }

        Matcher blockedMatcher = BLOCKED_EVENT.matcher(body);
        if (blockedMatcher.matches())
            return new TraceEvent(
                time,
                TraceEvent.Type.BLOCKED,
                Integer.parseInt(blockedMatcher.group(1)),
                Integer.parseInt(blockedMatcher.group(2)),
                blockedMatcher.group(3),
                message,
                false);

        return new TraceEvent(time, TraceEvent.Type.UNKNOWN, 0, 0, "", message, false);
    }

    private void buildSegments(Trace trace) {
        Set<String> locked = new HashSet<>();
        ExecutionSegment open = null;

        for (TraceEvent event : trace.getEvents()) {
            if (!event.hasTime())
                continue;

            if (open != null) {
                boolean sameChunk = event.getTaskId() == open.getTaskId()
                    && event.getChunkId() == open.getChunkId();
                boolean terminates = (event.getType() == TraceEvent.Type.FINISH && sameChunk)
                    || event.getType() == TraceEvent.Type.EXECUTE
                    || event.getTime() > open.getStart();
                if (terminates) {
                    open.setEnd(Math.max(open.getStart(), event.getTime()));
                    open.setCompleted(event.getType() == TraceEvent.Type.FINISH && sameChunk);
                    if (open.getDuration() > 0)
                        trace.addSegment(open);
                    open = null;
                }
            }

            String key = event.getTaskId() + "." + event.getChunkId();
            if (event.getType() == TraceEvent.Type.LOCK)
                locked.add(key);
            else if (event.getType() == TraceEvent.Type.UNLOCK)
                locked.remove(key);
            else if (event.getType() == TraceEvent.Type.EXECUTE)
                open = new ExecutionSegment(
                    event.getTaskId(),
                    event.getChunkId(),
                    event.getTime(),
                    locked.contains(key));
        }

        if (open != null) {
            open.setEnd(Math.max(open.getStart(), trace.getHorizon()));
            if (open.getDuration() > 0)
                trace.addSegment(open);
        }
    }

    private void buildJobs(Trace trace) {
        Map<Integer, Job> pending = new HashMap<>();

        for (TraceEvent event : trace.getEvents()) {
            if (!event.hasTime() || event.getTaskId() <= 0)
                continue;
            int taskId = event.getTaskId();

            switch (event.getType()) {
                case RELEASE: {
                    Job previous = pending.remove(taskId);
                    if (previous != null)
                        trace.addJob(previous);
                    double deadline = this.relativeDeadlines.getOrDefault(taskId, 0.0);
                    pending.put(taskId, new Job(taskId, event.getTime(), event.getTime() + deadline));
                    break;
                }
                case COMPLETE: {
                    Job job = pending.get(taskId);
                    if (job != null)
                        job.setCompletion(event.getTime());
                    break;
                }
                case DEADLINE_MISS: {
                    Job job = pending.get(taskId);
                    if (job != null)
                        job.setDeadlineMissed(true);
                    trace.markDeadlineMiss(taskId, event.getTime());
                    break;
                }
                default:
                    break;
            }
        }

        pending.values().forEach(trace::addJob);
    }

}
