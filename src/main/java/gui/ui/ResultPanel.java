package gui.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import gui.run.SimulationResult;
import gui.trace.Trace;

/**
 * Seconda schermata dell'applicazione: grafico di Gantt della trace
 * selezionata, log degli eventi e statistiche del dataset.
 */
public final class ResultPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Runnable onBack;
    private final StatsPanel statsPanel = new StatsPanel();
    private final GanttPanel ganttPanel = new GanttPanel();
    private final LogPanel logPanel;
    private final JScrollPane ganttScroll;

    private final JButton previousButton = new JButton("<");
    private final JButton nextButton = new JButton(">");
    private final JLabel positionLabel = new JLabel("- / -");
    private final JLabel outcomeLabel = new JLabel();
    private final JSlider zoomSlider = new JSlider(1, 600, 40);

    private SimulationResult result;
    private int currentIndex = 0;

    // CONSTRUCTOR
    /**
     * @param onBack callback per tornare alla schermata di configurazione
     */
    public ResultPanel(Runnable onBack) {
        super(new BorderLayout(0, 8));
        this.onBack = onBack;
        this.setBackground(UiTheme.BACKGROUND);
        this.setBorder(UiTheme.padding(10, 10, 10, 10));

        this.logPanel = new LogPanel(event -> {
            if (event.hasTime())
                this.ganttPanel.setHighlightTime(event.getTime());
            this.scrollToTime(event.getTime());
        });

        this.ganttScroll = new JScrollPane(this.ganttPanel);
        this.ganttScroll.setRowHeaderView(this.ganttPanel.getLabelPanel());
        this.ganttScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        this.ganttScroll.getHorizontalScrollBar().setUnitIncrement(24);
        this.ganttScroll.getViewport().setBackground(UiTheme.SURFACE);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(this.buildToolbar(), BorderLayout.NORTH);
        header.add(this.statsPanel, BorderLayout.CENTER);
        this.add(header, BorderLayout.NORTH);

        JPanel chart = new JPanel(new BorderLayout(0, 6));
        chart.setOpaque(false);
        chart.add(this.buildTraceBar(), BorderLayout.NORTH);
        chart.add(this.ganttScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chart, this.logPanel);
        split.setResizeWeight(0.58);
        split.setBorder(null);
        split.setOpaque(false);
        this.add(split, BorderLayout.CENTER);
    }

    // METHOD
    /** Mostra il risultato di una nuova esecuzione. */
    public void setResult(SimulationResult result) {
        this.result = result;
        this.currentIndex = 0;
        this.statsPanel.setStats(result == null ? null : result.getStats());
        this.showCurrentTrace();
        SwingUtilities.invokeLater(this::fitZoom);

        if (result != null && result.isTruncated())
            JOptionPane.showMessageDialog(this,
                "Il log ha superato il limite di righe conservate in memoria:\n"
                    + "le trace mostrate potrebbero essere incomplete.\n"
                    + "Riduci la durata della simulazione o il numero di trace.",
                "Log troncato", JOptionPane.WARNING_MESSAGE);
    }

    // HELPER
    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JButton back = new JButton("Torna alla configurazione");
        back.addActionListener(e -> this.onBack.run());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(back);
        left.add(UiTheme.title("Risultati della simulazione"));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JButton exportImage = new JButton("Esporta grafico (PNG)");
        exportImage.addActionListener(e -> this.exportImage());
        JButton exportLog = new JButton("Esporta log");
        exportLog.addActionListener(e -> this.exportLog());
        right.add(exportImage);
        right.add(exportLog);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildTraceBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(UiTheme.hint("Trace"));

        this.previousButton.addActionListener(e -> this.move(-1));
        this.nextButton.addActionListener(e -> this.move(1));
        this.positionLabel.setFont(UiTheme.FONT_BOLD);
        this.positionLabel.setBorder(UiTheme.padding(0, 6, 0, 6));
        this.outcomeLabel.setFont(UiTheme.FONT_BASE);
        this.outcomeLabel.setOpaque(true);
        this.outcomeLabel.setBorder(UiTheme.padding(2, 8, 2, 8));

        left.add(this.previousButton);
        left.add(this.positionLabel);
        left.add(this.nextButton);
        left.add(this.outcomeLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(UiTheme.hint("zoom"));
        this.zoomSlider.setPreferredSize(new Dimension(150, 22));
        this.zoomSlider.setOpaque(false);
        this.zoomSlider.addChangeListener(e ->
            this.ganttPanel.setPixelsPerMs(this.zoomSlider.getValue() / 10.0));
        right.add(this.zoomSlider);
        JButton fit = new JButton("Adatta");
        fit.addActionListener(e -> this.fitZoom());
        right.add(fit);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void move(int delta) {
        if (this.result == null || this.result.isEmpty())
            return;
        int target = this.currentIndex + delta;
        if (target < 0 || target >= this.result.getTraces().size())
            return;
        this.currentIndex = target;
        this.showCurrentTrace();
    }

    private void showCurrentTrace() {
        if (this.result == null || this.result.isEmpty()) {
            this.ganttPanel.setTrace(null, 0);
            this.logPanel.setTrace(null);
            this.positionLabel.setText("- / -");
            this.outcomeLabel.setText("");
            this.outcomeLabel.setOpaque(false);
            this.previousButton.setEnabled(false);
            this.nextButton.setEnabled(false);
            return;
        }

        Trace trace = this.result.getTraces().get(this.currentIndex);
        int taskCount = this.result.getSpec().getTasks().size();
        this.ganttPanel.setTrace(trace, taskCount);
        this.logPanel.setTrace(trace);

        this.positionLabel.setText((this.currentIndex + 1) + " / " + this.result.getTraces().size());
        this.outcomeLabel.setText(trace.describeOutcome());
        this.outcomeLabel.setOpaque(true);
        if (trace.isDeadlineMissed()) {
            this.outcomeLabel.setBackground(UiTheme.DANGER_SOFT);
            this.outcomeLabel.setForeground(UiTheme.DANGER);
        } else {
            this.outcomeLabel.setBackground(UiTheme.SUCCESS_SOFT);
            this.outcomeLabel.setForeground(UiTheme.SUCCESS);
        }

        this.previousButton.setEnabled(this.currentIndex > 0);
        this.nextButton.setEnabled(this.currentIndex < this.result.getTraces().size() - 1);
    }

    private void fitZoom() {
        int width = this.ganttScroll.getViewport().getWidth();
        if (width <= 0)
            return;
        this.ganttPanel.fitTo(width);
        int value = (int) Math.round(this.ganttPanel.getPixelsPerMs() * 10);
        this.zoomSlider.setValue(Math.max(this.zoomSlider.getMinimum(),
            Math.min(this.zoomSlider.getMaximum(), value)));
    }

    private void scrollToTime(double time) {
        int x = this.ganttPanel.xOf(time);
        java.awt.Rectangle view = this.ganttScroll.getViewport().getViewRect();
        if (x < view.x || x > view.x + view.width) {
            int target = Math.max(0, x - view.width / 2);
            this.ganttScroll.getHorizontalScrollBar().setValue(target);
        }
    }

    private void exportImage() {
        BufferedImage image = this.ganttPanel.renderToImage();
        if (image == null) {
            JOptionPane.showMessageDialog(this, "Non c'e nessun grafico da esportare.",
                "Esportazione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Immagine PNG (*.png)", "png"));
        chooser.setSelectedFile(new File("gantt-trace" + (this.currentIndex + 1) + ".png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File file = ensureExtension(chooser.getSelectedFile(), "png");
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Impossibile salvare l'immagine:\n" + e.getMessage(),
                "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportLog() {
        String text = this.logPanel.getFullText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Non c'e nessun log da esportare.",
                "Esportazione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("File di testo (*.log)", "log"));
        chooser.setSelectedFile(new File("trace" + (this.currentIndex + 1) + ".log"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File file = ensureExtension(chooser.getSelectedFile(), "log");
        try {
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Impossibile salvare il log:\n" + e.getMessage(),
                "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static File ensureExtension(File file, String extension) {
        if (file.getName().toLowerCase().endsWith("." + extension))
            return file;
        return new File(file.getParentFile(), file.getName() + "." + extension);
    }

}
