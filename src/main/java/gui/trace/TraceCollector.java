package gui.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import utils.logger.MyLogger;

/**
 * Raccoglie in memoria le righe prodotte dal logger del simulatore.
 * <p>
 * Il collector si aggancia allo stesso {@link Logger} usato da
 * {@link MyLogger}, recuperandolo per nome: non e quindi necessario modificare
 * la classe di logging esistente, che continua a scrivere regolarmente anche su
 * {@code trace.log}. Durante la raccolta viene temporaneamente disattivata la
 * propagazione ai gestori padre per evitare che l'intera trace venga riversata
 * anche sulla console.
 */
public final class TraceCollector extends Handler {

    /** Una riga di log memorizzata, con l'indicazione del livello. */
    public static final class Entry {

        private final String message;
        private final boolean warning;

        Entry(String message, boolean warning) {
            this.message = message;
            this.warning = warning;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isWarning() {
            return this.warning;
        }
    }

    /** Numero massimo di righe conservate, per non esaurire la memoria. */
    public static final int MAX_ENTRIES = 400_000;

    private final List<Entry> entries = new ArrayList<>();
    private Logger target;
    private boolean previousUseParentHandlers;
    private boolean truncated = false;

    // METHOD
    /** Aggancia il collector al logger del simulatore. */
    public void attach() {
        this.target = Logger.getLogger(MyLogger.class.getName());
        this.previousUseParentHandlers = this.target.getUseParentHandlers();
        this.target.setUseParentHandlers(false);
        this.setLevel(Level.ALL);
        this.target.addHandler(this);
    }

    /** Sgancia il collector e ripristina la configurazione precedente del logger. */
    public void detach() {
        if (this.target == null)
            return;
        this.target.removeHandler(this);
        this.target.setUseParentHandlers(this.previousUseParentHandlers);
        this.target = null;
    }

    /** @return le righe raccolte, nell'ordine in cui sono state prodotte */
    public List<Entry> getEntries() {
        return this.entries;
    }

    /** @return true se il limite di righe e stato raggiunto e la raccolta e stata troncata */
    public boolean isTruncated() {
        return this.truncated;
    }

    public void clear() {
        this.entries.clear();
        this.truncated = false;
    }

    // HANDLER
    @Override
    public void publish(LogRecord record) {
        if (record == null)
            return;
        if (this.entries.size() >= MAX_ENTRIES) {
            this.truncated = true;
            return;
        }
        String message = record.getMessage();
        if (message == null)
            return;
        boolean warning = record.getLevel().intValue() >= Level.WARNING.intValue();
        for (String line : message.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty())
                this.entries.add(new Entry(trimmed, warning));
        }
    }

    @Override
    public void flush() {
        // niente da fare: la raccolta e interamente in memoria
    }

    @Override
    public void close() {
        this.detach();
    }

}
