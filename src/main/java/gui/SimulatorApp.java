package gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import gui.model.SimulationSpec;
import gui.run.SimulationResult;
import gui.run.SimulationRunner;
import gui.ui.ConfigPanel;
import gui.ui.ResultPanel;
import gui.ui.UiTheme;

/**
 * Punto di ingresso dell'interfaccia grafica del simulatore.
 * <p>
 * L'applicazione e composta da due schermate gestite da un {@link CardLayout}:
 * la configurazione del taskset e la visualizzazione dei risultati. La logica di
 * simulazione non viene modificata in alcun modo: l'interfaccia costruisce gli
 * oggetti del dominio con i costruttori pubblici esistenti, invoca
 * {@code schedule()} e ricostruisce il grafico leggendo il log che il
 * simulatore produce gia oggi.
 */
public final class SimulatorApp extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String CARD_CONFIG = "config";
    private static final String CARD_RESULT = "result";

    private final SimulationSpec spec = SimulationSpec.sample();
    private final CardLayout cards = new CardLayout();
    private final JPanel container = new JPanel(this.cards);
    private final ConfigPanel configPanel;
    private final ResultPanel resultPanel;

    // CONSTRUCTOR
    public SimulatorApp() {
        super("Real-Time Scheduling Simulator");
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        this.configPanel = new ConfigPanel(this.spec, this::runSimulation);
        this.resultPanel = new ResultPanel(() -> this.cards.show(this.container, CARD_CONFIG));

        this.container.setBackground(UiTheme.BACKGROUND);
        this.container.add(this.configPanel, CARD_CONFIG);
        this.container.add(this.resultPanel, CARD_RESULT);

        this.setLayout(new BorderLayout());
        this.add(this.container, BorderLayout.CENTER);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize(Math.min(1280, screen.width - 80), Math.min(820, screen.height - 80));
        this.setMinimumSize(new Dimension(1000, 640));
        this.setLocationRelativeTo(null);
    }

    // METHOD
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // in caso di problemi si usa l'aspetto predefinito
            }
            new SimulatorApp().setVisible(true);
        });
    }

    // HELPER
    private void runSimulation(SimulationSpec spec) {
        ProgressDialog progress = new ProgressDialog(this, spec.getTraceCount());
        SimulationRunner runner = new SimulationRunner(spec, progress::setCompleted);

        runner.addPropertyChangeListener(event -> {
            if (!"state".equals(event.getPropertyName())
                    || runner.getState() != SwingWorker.StateValue.DONE)
                return;
            progress.finish();
            this.consumeResult(runner);
        });

        runner.execute();
        progress.showIfStillRunning();
    }

    private void consumeResult(SimulationRunner runner) {
        try {
            SimulationResult result = runner.get();
            if (result.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "La simulazione non ha prodotto alcuna trace.",
                    "Nessun risultato", JOptionPane.WARNING_MESSAGE);
                return;
            }
            this.resultPanel.setResult(result);
            this.cards.show(this.container, CARD_RESULT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            JOptionPane.showMessageDialog(this,
                "La simulazione si e interrotta:\n" + cause,
                "Errore durante l'esecuzione", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Finestra modale con la barra di avanzamento della generazione delle trace. */
    private static final class ProgressDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private final JProgressBar bar;
        private boolean finished = false;

        ProgressDialog(JFrame owner, int traceCount) {
            super(owner, "Simulazione in corso", true);
            this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(UiTheme.padding(14, 16, 14, 16));

            JLabel label = new JLabel("Generazione delle trace in corso...");
            label.setFont(UiTheme.FONT_BASE);

            this.bar = new JProgressBar(0, traceCount);
            this.bar.setStringPainted(true);
            this.bar.setPreferredSize(new Dimension(300, 20));

            panel.add(label, BorderLayout.NORTH);
            panel.add(this.bar, BorderLayout.CENTER);

            this.getContentPane().add(panel);
            this.pack();
            this.setLocationRelativeTo(owner);
        }

        void setCompleted(int traces) {
            this.bar.setValue(traces);
        }

        void finish() {
            this.finished = true;
            this.dispose();
        }

        void showIfStillRunning() {
            if (!this.finished)
                this.setVisible(true);
        }
    }

}
