package gui.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Salvataggio e caricamento della configurazione in un formato testuale
 * leggibile, senza dipendenze aggiuntive nel {@code pom.xml}.
 */
public final class SpecIO {

    /** Estensione consigliata per i file di configurazione. */
    public static final String EXTENSION = "rtsim";

    private static final String VERSION = "1";

    private SpecIO() {}

    /**
     * Scrive la configurazione su file.
     *
     * @param spec la configurazione da salvare
     * @param file il file di destinazione
     * @throws IOException se la scrittura fallisce
     */
    public static void save(SimulationSpec spec, File file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("# Real-Time Scheduling Simulator - configurazione");
            writer.newLine();
            writeProperty(writer, "version", VERSION);
            writeProperty(writer, "resources", String.valueOf(spec.getResourceCount()));
            writeProperty(writer, "algorithm", spec.getAlgorithm().name());
            writeProperty(writer, "protocol", spec.getProtocol().name());
            writeProperty(writer, "acquireThreshold", String.valueOf(spec.getAcquireThreshold()));
            writeProperty(writer, "deltaMin", String.valueOf(spec.getDeltaMin()));
            writeProperty(writer, "deltaMax", String.valueOf(spec.getDeltaMax()));
            writeProperty(writer, "duration", String.valueOf(spec.getDuration()));
            writeProperty(writer, "traces", String.valueOf(spec.getTraceCount()));
            for (TaskSpec task : spec.getTasks()) {
                writeProperty(writer, "task", task.encode());
                for (ChunkSpec chunk : task.getChunks())
                    writeProperty(writer, "chunk", chunk.encode());
            }
        }
    }

    /**
     * Legge una configurazione da file.
     *
     * @param file il file da leggere
     * @return la configurazione ricostruita
     * @throws IOException se la lettura o il parsing falliscono
     */
    public static SimulationSpec load(File file) throws IOException {
        SimulationSpec spec = new SimulationSpec();
        TaskSpec current = null;
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                int separator = line.indexOf('=');
                if (separator < 0)
                    throw new IOException("Riga " + lineNumber + " non valida: " + line);
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                try {
                    switch (key) {
                        case "version":
                            break;
                        case "resources":
                            spec.setResourceCount(Integer.parseInt(value));
                            break;
                        case "algorithm":
                            spec.setAlgorithm(SimulationSpec.Algorithm.valueOf(value));
                            break;
                        case "protocol":
                            spec.setProtocol(SimulationSpec.Protocol.valueOf(value));
                            break;
                        case "acquireThreshold":
                            spec.setAcquireThreshold(Double.parseDouble(value));
                            break;
                        case "deltaMin":
                            spec.setDeltaMin(Double.parseDouble(value));
                            break;
                        case "deltaMax":
                            spec.setDeltaMax(Double.parseDouble(value));
                            break;
                        case "duration":
                            spec.setDuration(Double.parseDouble(value));
                            break;
                        case "traces":
                            spec.setTraceCount(Integer.parseInt(value));
                            break;
                        case "task":
                            current = TaskSpec.decode(value);
                            spec.getTasks().add(current);
                            break;
                        case "chunk":
                            if (current == null)
                                throw new IOException("Chunk senza task alla riga " + lineNumber);
                            current.getChunks().add(ChunkSpec.decode(value));
                            break;
                        default:
                            throw new IOException("Chiave sconosciuta alla riga " + lineNumber + ": " + key);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IOException("Valore non valido alla riga " + lineNumber + ": " + value, e);
                }
            }
        }
        List<String> problems = spec.validate();
        if (spec.getTasks().isEmpty() && !problems.isEmpty())
            throw new IOException("Il file non contiene alcun task.");
        return spec;
    }

    // HELPER
    private static void writeProperty(BufferedWriter writer, String key, String value) throws IOException {
        writer.write(key + "=" + value);
        writer.newLine();
    }

}
