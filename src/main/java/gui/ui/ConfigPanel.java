package gui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

import gui.model.ChunkSpec;
import gui.model.SimulationSpec;
import gui.model.SpecIO;
import gui.model.TaskSpec;

/**
 * Prima schermata dell'applicazione: definizione del taskset, delle risorse
 * condivise e dei parametri della simulazione.
 */
public final class ConfigPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SimulationSpec spec;
    private final Consumer<SimulationSpec> onRun;

    private final TaskTableModel taskModel = new TaskTableModel();
    private final ChunkTableModel chunkModel = new ChunkTableModel();
    private final JTable taskTable = new JTable(this.taskModel);
    private final JTable chunkTable = new JTable(this.chunkModel);
    private final DefaultListModel<String> resourceListModel = new DefaultListModel<>();
    private final JList<String> resourceList = new JList<>(this.resourceListModel);
    private final JLabel chunkTitle = UiTheme.title("Chunk");

    private final JComboBox<SimulationSpec.Algorithm> algorithmBox =
        new JComboBox<>(SimulationSpec.Algorithm.values());
    private final JComboBox<SimulationSpec.Protocol> protocolBox =
        new JComboBox<>(SimulationSpec.Protocol.values());
    private final JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(0.2, 0.0, 1.0, 0.05));
    private final JSpinner deltaMinSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1000.0, 1.0));
    private final JSpinner deltaMaxSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.0, 1000.0, 1.0));
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(480.0, 1.0, 1_000_000.0, 10.0));
    private final JSpinner traceSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 5000, 1));
    private final JLabel thresholdLabel = new JLabel("Soglia acquisizione");
    private final JLabel deltaLabel = new JLabel("Delta priorita (min / max)");

    private final JTextArea feasibilityArea = new JTextArea(7, 24);
    private final JButton runButton = new JButton("Esegui simulazione");

    private boolean updating = false;
    private boolean refreshing = false;

    // CONSTRUCTOR
    /**
     * @param spec  la configurazione condivisa con il resto dell'applicazione
     * @param onRun callback invocata quando l'utente avvia la simulazione
     */
    public ConfigPanel(SimulationSpec spec, Consumer<SimulationSpec> onRun) {
        super(new BorderLayout(0, 8));
        this.spec = spec;
        this.onRun = onRun;
        this.setBackground(UiTheme.BACKGROUND);
        this.setBorder(UiTheme.padding(10, 10, 10, 10));

        this.add(this.buildToolbar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            this.buildLeftSide(),
            this.buildRightSide());
        split.setResizeWeight(0.62);
        split.setBorder(null);
        split.setOpaque(false);
        this.add(split, BorderLayout.CENTER);

        this.wireListeners();
        this.reloadFromSpec();
    }

    // METHOD
    /** Ricarica tutti i controlli a partire dalla configurazione corrente. */
    public void reloadFromSpec() {
        this.updating = true;
        try {
            this.algorithmBox.setSelectedItem(this.spec.getAlgorithm());
            this.protocolBox.setSelectedItem(this.spec.getProtocol());
            this.thresholdSpinner.setValue(this.spec.getAcquireThreshold());
            this.deltaMinSpinner.setValue(this.spec.getDeltaMin());
            this.deltaMaxSpinner.setValue(this.spec.getDeltaMax());
            this.durationSpinner.setValue(this.spec.getDuration());
            this.traceSpinner.setValue(this.spec.getTraceCount());
            this.refreshResourceList();
            this.taskModel.fireTableDataChanged();
            if (!this.spec.getTasks().isEmpty())
                this.taskTable.setRowSelectionInterval(0, 0);
            this.refreshChunkTable();
        } finally {
            this.updating = false;
        }
        this.refreshProtocolControls();
        this.refreshFeasibility();
    }

    // HELPER - COSTRUZIONE
    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);

        JLabel heading = UiTheme.title("Configurazione della simulazione");
        heading.setBorder(UiTheme.padding(0, 0, 0, 12));
        bar.add(heading);

        JButton reset = new JButton("Configurazione di esempio");
        reset.addActionListener(e -> {
            this.spec.copyFrom(SimulationSpec.sample());
            this.reloadFromSpec();
        });

        JButton clear = new JButton("Svuota");
        clear.addActionListener(e -> {
            this.spec.copyFrom(new SimulationSpec());
            this.reloadFromSpec();
        });

        JButton load = new JButton("Carica...");
        load.addActionListener(e -> this.loadConfiguration());

        JButton save = new JButton("Salva...");
        save.addActionListener(e -> this.saveConfiguration());

        bar.add(reset);
        bar.add(clear);
        bar.add(load);
        bar.add(save);
        return bar;
    }

    private JComponent buildLeftSide() {
        JPanel tasks = new JPanel(new BorderLayout(0, 6));
        tasks.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiTheme.title("Taskset"), BorderLayout.WEST);

        JPanel taskButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        taskButtons.setOpaque(false);
        JButton addTask = new JButton("Aggiungi task");
        addTask.addActionListener(e -> this.addTask());
        JButton duplicateTask = new JButton("Duplica");
        duplicateTask.addActionListener(e -> this.duplicateTask());
        JButton removeTask = new JButton("Elimina");
        removeTask.addActionListener(e -> this.removeTask());
        taskButtons.add(addTask);
        taskButtons.add(duplicateTask);
        taskButtons.add(removeTask);
        header.add(taskButtons, BorderLayout.EAST);

        this.taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.taskTable.setRowHeight(22);
        this.taskTable.setFont(UiTheme.FONT_BASE);
        this.taskTable.getTableHeader().setFont(UiTheme.FONT_BOLD);
        this.taskTable.setGridColor(UiTheme.GRID);

        JScrollPane taskScroll = new JScrollPane(this.taskTable);
        taskScroll.setPreferredSize(new Dimension(420, 170));
        taskScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));

        tasks.add(header, BorderLayout.NORTH);
        tasks.add(taskScroll, BorderLayout.CENTER);
        tasks.add(UiTheme.hint("Periodo e deadline sono modificabili direttamente nella tabella."),
            BorderLayout.SOUTH);

        JPanel chunks = new JPanel(new BorderLayout(0, 6));
        chunks.setOpaque(false);

        JPanel chunkHeader = new JPanel(new BorderLayout());
        chunkHeader.setOpaque(false);
        chunkHeader.add(this.chunkTitle, BorderLayout.WEST);

        JPanel chunkButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        chunkButtons.setOpaque(false);
        JButton addChunk = new JButton("Aggiungi chunk");
        addChunk.addActionListener(e -> this.addChunk());
        JButton editChunk = new JButton("Modifica");
        editChunk.addActionListener(e -> this.editChunk());
        JButton removeChunk = new JButton("Elimina");
        removeChunk.addActionListener(e -> this.removeChunk());
        JButton upChunk = new JButton("Su");
        upChunk.addActionListener(e -> this.moveChunk(-1));
        JButton downChunk = new JButton("Giu");
        downChunk.addActionListener(e -> this.moveChunk(1));
        chunkButtons.add(addChunk);
        chunkButtons.add(editChunk);
        chunkButtons.add(removeChunk);
        chunkButtons.add(upChunk);
        chunkButtons.add(downChunk);
        chunkHeader.add(chunkButtons, BorderLayout.EAST);

        this.chunkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.chunkTable.setRowHeight(22);
        this.chunkTable.setFont(UiTheme.FONT_BASE);
        this.chunkTable.getTableHeader().setFont(UiTheme.FONT_BOLD);
        this.chunkTable.setGridColor(UiTheme.GRID);

        JScrollPane chunkScroll = new JScrollPane(this.chunkTable);
        chunkScroll.setPreferredSize(new Dimension(420, 150));
        chunkScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));

        chunks.add(chunkHeader, BorderLayout.NORTH);
        chunks.add(chunkScroll, BorderLayout.CENTER);
        chunks.add(UiTheme.hint("Doppio clic su una riga per modificare il chunk."), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tasks, chunks);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setOpaque(false);
        return split;
    }

    private JComponent buildRightSide() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(UiTheme.padding(0, 10, 0, 0));

        panel.add(this.buildResourcePanel());
        panel.add(javax.swing.Box.createVerticalStrut(8));
        panel.add(this.buildSettingsPanel());
        panel.add(javax.swing.Box.createVerticalStrut(8));
        panel.add(this.buildFeasibilityPanel());
        panel.add(javax.swing.Box.createVerticalStrut(8));

        this.runButton.setFont(UiTheme.FONT_TITLE);
        this.runButton.setBackground(UiTheme.ACCENT);
        this.runButton.setForeground(Color.WHITE);
        this.runButton.setOpaque(true);
        this.runButton.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        this.runButton.setAlignmentX(LEFT_ALIGNMENT);
        this.runButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        this.runButton.addActionListener(e -> this.run());
        panel.add(this.runButton);
        panel.add(javax.swing.Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private JComponent buildResourcePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(UiTheme.cardBorder("Risorse condivise"));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        this.resourceList.setFont(UiTheme.FONT_BASE);
        this.resourceList.setVisibleRowCount(3);
        JScrollPane scroll = new JScrollPane(this.resourceList);
        scroll.setPreferredSize(new Dimension(200, 66));
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        JButton add = new JButton("Aggiungi risorsa");
        add.addActionListener(e -> {
            this.spec.addResource();
            this.refreshResourceList();
            this.refreshChunkTable();
            this.refreshFeasibility();
        });
        JButton remove = new JButton("Elimina risorsa");
        remove.addActionListener(e -> {
            int index = this.resourceList.getSelectedIndex();
            if (index < 0) {
                JOptionPane.showMessageDialog(this, "Seleziona la risorsa da eliminare.",
                    "Nessuna selezione", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            this.spec.removeResource(index);
            this.refreshResourceList();
            this.refreshChunkTable();
            this.refreshFeasibility();
        });
        buttons.add(add);
        buttons.add(remove);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(UiTheme.cardBorder("Simulazione"));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 2, 3, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        int row = 0;
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(label("Algoritmo"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.algorithmBox.setFont(UiTheme.FONT_BASE);
        panel.add(this.algorithmBox, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(label("Protocollo risorse"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.protocolBox.setFont(UiTheme.FONT_BASE);
        panel.add(this.protocolBox, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        this.thresholdLabel.setFont(UiTheme.FONT_BASE);
        panel.add(this.thresholdLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(this.thresholdSpinner, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        this.deltaLabel.setFont(UiTheme.FONT_BASE);
        panel.add(this.deltaLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        JPanel deltaPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        deltaPanel.setOpaque(false);
        deltaPanel.add(this.deltaMinSpinner);
        deltaPanel.add(this.deltaMaxSpinner);
        panel.add(deltaPanel, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(label("Durata simulazione (ms)"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(this.durationSpinner, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(label("Numero di trace"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(this.traceSpinner, gbc);

        return panel;
    }

    private JComponent buildFeasibilityPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(UiTheme.cardBorder("Analisi di fattibilita"));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        this.feasibilityArea.setEditable(false);
        this.feasibilityArea.setLineWrap(true);
        this.feasibilityArea.setWrapStyleWord(true);
        this.feasibilityArea.setFont(UiTheme.FONT_BASE);
        this.feasibilityArea.setBackground(UiTheme.SURFACE);
        this.feasibilityArea.setBorder(UiTheme.padding(6, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(this.feasibilityArea);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        scroll.setPreferredSize(new Dimension(220, 140));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // HELPER - EVENTI
    private void wireListeners() {
        this.taskTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || this.refreshing)
                return;
            this.refreshChunkTable();
        });

        this.chunkTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2)
                    ConfigPanel.this.editChunk();
            }
        });

        this.algorithmBox.addActionListener(e -> {
            if (this.updating)
                return;
            this.spec.setAlgorithm((SimulationSpec.Algorithm) this.algorithmBox.getSelectedItem());
            if (!this.spec.getAlgorithm().supportsResources())
                this.spec.setProtocol(SimulationSpec.Protocol.NONE);
            this.updating = true;
            this.protocolBox.setSelectedItem(this.spec.getProtocol());
            this.updating = false;
            this.refreshProtocolControls();
            this.refreshFeasibility();
        });

        this.protocolBox.addActionListener(e -> {
            if (this.updating)
                return;
            this.spec.setProtocol((SimulationSpec.Protocol) this.protocolBox.getSelectedItem());
            this.refreshProtocolControls();
            this.refreshFeasibility();
        });

        this.thresholdSpinner.addChangeListener(e -> {
            this.spec.setAcquireThreshold(doubleValue(this.thresholdSpinner));
            this.refreshFeasibility();
        });
        this.deltaMinSpinner.addChangeListener(e -> {
            this.spec.setDeltaMin(doubleValue(this.deltaMinSpinner));
            this.refreshFeasibility();
        });
        this.deltaMaxSpinner.addChangeListener(e -> {
            this.spec.setDeltaMax(doubleValue(this.deltaMaxSpinner));
            this.refreshFeasibility();
        });
        this.durationSpinner.addChangeListener(e -> {
            this.spec.setDuration(doubleValue(this.durationSpinner));
            this.refreshFeasibility();
        });
        this.traceSpinner.addChangeListener(e -> {
            this.spec.setTraceCount(((Number) this.traceSpinner.getValue()).intValue());
            this.refreshFeasibility();
        });
    }

    private void refreshProtocolControls() {
        SimulationSpec.Protocol protocol = this.spec.getProtocol();
        boolean resourcesAllowed = this.spec.getAlgorithm().supportsResources();
        this.protocolBox.setEnabled(resourcesAllowed);
        this.thresholdLabel.setEnabled(protocol.usesThreshold());
        this.thresholdSpinner.setEnabled(protocol.usesThreshold());
        this.deltaLabel.setEnabled(protocol.usesDelta());
        this.deltaMinSpinner.setEnabled(protocol.usesDelta());
        this.deltaMaxSpinner.setEnabled(protocol.usesDelta());
    }

    private void refreshResourceList() {
        this.resourceListModel.clear();
        for (int i = 0; i < this.spec.getResourceCount(); i++)
            this.resourceListModel.addElement("Res" + (i + 1));
    }

    /**
     * Allinea la tabella dei chunk al task selezionato.
     * <p>
     * Il flag {@code refreshing} evita che gli eventi generati qui rientrino
     * nel listener di selezione della tabella dei task. Per aggiornare le
     * colonne calcolate si usa {@code fireTableRowsUpdated}, che a differenza
     * di {@code fireTableDataChanged} non azzera la selezione corrente.
     */
    private void refreshChunkTable() {
        if (this.refreshing)
            return;
        this.refreshing = true;
        try {
            TaskSpec task = this.selectedTask();
            int index = this.taskTable.getSelectedRow();
            this.chunkModel.setTask(task);
            this.chunkTitle.setText(task == null ? "Chunk" : "Chunk di Task" + (index + 1));
            if (index >= 0 && index < this.taskModel.getRowCount())
                this.taskModel.fireTableRowsUpdated(index, index);
        } finally {
            this.refreshing = false;
        }
    }

    private void refreshFeasibility() {
        StringBuilder text = new StringBuilder();
        double utilization = this.spec.expectedUtilization();
        text.append(String.format("Fattore di utilizzazione atteso: %.4f%n", utilization));

        if (this.spec.getAlgorithm() == SimulationSpec.Algorithm.RM) {
            double product = this.spec.expectedHyperbolicProduct();
            text.append(String.format("Test iperbolico: prodotto %.4f%n", product));
            text.append(product <= 2
                ? "Il taskset supera il bound iperbolico.\n"
                : "Il taskset non supera il bound iperbolico.\n");
        } else {
            text.append(utilization <= 1
                ? "Condizione necessaria e sufficiente EDF soddisfatta.\n"
                : "Condizione EDF non soddisfatta: U maggiore di 1.\n");
        }
        text.append("I valori sono stimati sui tempi attesi dei sampler.\n");

        List<String> problems = this.spec.validate();
        if (problems.isEmpty()) {
            text.append("\nLa configurazione e pronta per l'esecuzione.");
            this.runButton.setEnabled(true);
        } else {
            text.append("\nDa sistemare prima di eseguire:\n");
            for (String problem : problems)
                text.append("  - ").append(problem).append('\n');
            this.runButton.setEnabled(false);
        }

        this.feasibilityArea.setText(text.toString());
        this.feasibilityArea.setCaretPosition(0);
    }

    // HELPER - AZIONI
    private TaskSpec selectedTask() {
        int index = this.taskTable.getSelectedRow();
        if (index < 0 || index >= this.spec.getTasks().size())
            return null;
        return this.spec.getTasks().get(index);
    }

    private void addTask() {
        TaskSpec task = new TaskSpec(100, 100);
        this.spec.getTasks().add(task);
        this.taskModel.fireTableDataChanged();
        int index = this.spec.getTasks().size() - 1;
        this.taskTable.setRowSelectionInterval(index, index);
        this.refreshChunkTable();
        this.refreshFeasibility();
    }

    private void duplicateTask() {
        TaskSpec task = this.selectedTask();
        if (task == null) {
            JOptionPane.showMessageDialog(this, "Seleziona un task da duplicare.",
                "Nessuna selezione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        this.spec.getTasks().add(task.copy());
        this.taskModel.fireTableDataChanged();
        int index = this.spec.getTasks().size() - 1;
        this.taskTable.setRowSelectionInterval(index, index);
        this.refreshChunkTable();
        this.refreshFeasibility();
    }

    private void removeTask() {
        int index = this.taskTable.getSelectedRow();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Seleziona un task da eliminare.",
                "Nessuna selezione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        this.spec.getTasks().remove(index);
        this.taskModel.fireTableDataChanged();
        if (!this.spec.getTasks().isEmpty()) {
            int next = Math.min(index, this.spec.getTasks().size() - 1);
            this.taskTable.setRowSelectionInterval(next, next);
        }
        this.refreshChunkTable();
        this.refreshFeasibility();
    }

    private void addChunk() {
        TaskSpec task = this.selectedTask();
        if (task == null) {
            JOptionPane.showMessageDialog(this, "Seleziona prima un task.",
                "Nessuna selezione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ChunkSpec chunk = ChunkDialog.show(
            javax.swing.SwingUtilities.getWindowAncestor(this),
            null,
            task.nextChunkId(),
            this.spec.getResourceCount(),
            this.spec.getAlgorithm().supportsResources());
        if (chunk == null)
            return;
        task.getChunks().add(chunk);
        this.refreshChunkTable();
        int added = this.chunkTable.getRowCount() - 1;
        if (added >= 0)
            this.chunkTable.setRowSelectionInterval(added, added);
        this.refreshFeasibility();
    }

    private void editChunk() {
        TaskSpec task = this.selectedTask();
        int index = this.chunkTable.getSelectedRow();
        if (task == null || index < 0)
            return;
        ChunkSpec chunk = ChunkDialog.show(
            javax.swing.SwingUtilities.getWindowAncestor(this),
            task.getChunks().get(index),
            0,
            this.spec.getResourceCount(),
            this.spec.getAlgorithm().supportsResources());
        if (chunk == null)
            return;
        task.getChunks().set(index, chunk);
        this.refreshChunkTable();
        if (index < this.chunkTable.getRowCount())
            this.chunkTable.setRowSelectionInterval(index, index);
        this.refreshFeasibility();
    }

    private void removeChunk() {
        TaskSpec task = this.selectedTask();
        int index = this.chunkTable.getSelectedRow();
        if (task == null || index < 0) {
            JOptionPane.showMessageDialog(this, "Seleziona un chunk da eliminare.",
                "Nessuna selezione", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        task.getChunks().remove(index);
        this.refreshChunkTable();
        if (this.chunkTable.getRowCount() > 0) {
            int next = Math.min(index, this.chunkTable.getRowCount() - 1);
            this.chunkTable.setRowSelectionInterval(next, next);
        }
        this.refreshFeasibility();
    }

    private void moveChunk(int delta) {
        TaskSpec task = this.selectedTask();
        int index = this.chunkTable.getSelectedRow();
        if (task == null || index < 0)
            return;
        int target = index + delta;
        if (target < 0 || target >= task.getChunks().size())
            return;
        ChunkSpec chunk = task.getChunks().remove(index);
        task.getChunks().add(target, chunk);
        this.refreshChunkTable();
        this.chunkTable.setRowSelectionInterval(target, target);
    }

    private void run() {
        List<String> problems = this.spec.validate();
        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                String.join("\n", problems),
                "Configurazione non valida",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        this.onRun.accept(this.spec);
    }

    private void loadConfiguration() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Configurazione simulatore (*." + SpecIO.EXTENSION + ")", SpecIO.EXTENSION));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        try {
            SimulationSpec loaded = SpecIO.load(chooser.getSelectedFile());
            this.spec.copyFrom(loaded);
            this.reloadFromSpec();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Impossibile leggere il file:\n" + e.getMessage(),
                "Errore di caricamento", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfiguration() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Configurazione simulatore (*." + SpecIO.EXTENSION + ")", SpecIO.EXTENSION));
        chooser.setSelectedFile(new File("taskset." + SpecIO.EXTENSION));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + SpecIO.EXTENSION))
            file = new File(file.getParentFile(), file.getName() + "." + SpecIO.EXTENSION);
        try {
            SpecIO.save(this.spec, file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Impossibile salvare il file:\n" + e.getMessage(),
                "Errore di salvataggio", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.FONT_BASE);
        return label;
    }

    private static double doubleValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    // MODELLI DELLE TABELLE
    private final class TaskTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final String[] columns = { "Task", "Periodo", "Deadline", "Chunk", "U attesa" };

        @Override
        public int getRowCount() {
            return ConfigPanel.this.spec.getTasks().size();
        }

        @Override
        public int getColumnCount() {
            return this.columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return this.columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 || column == 2 ? Double.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1 || column == 2;
        }

        @Override
        public Object getValueAt(int row, int column) {
            TaskSpec task = ConfigPanel.this.spec.getTasks().get(row);
            switch (column) {
                case 0:
                    return "Task" + (row + 1);
                case 1:
                    return task.getPeriod();
                case 2:
                    return task.getDeadline();
                case 3:
                    return String.valueOf(task.getChunks().size());
                case 4:
                    return String.format("%.4f", task.expectedUtilization());
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!(value instanceof Number))
                return;
            double number = ((Number) value).doubleValue();
            TaskSpec task = ConfigPanel.this.spec.getTasks().get(row);
            if (column == 1)
                task.setPeriod(number);
            else if (column == 2)
                task.setDeadline(number);
            this.fireTableRowsUpdated(row, row);
            ConfigPanel.this.refreshFeasibility();
        }
    }

    private final class ChunkTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final String[] columns = { "Id", "Tempo di esecuzione", "Overhead", "Risorse" };
        private TaskSpec task;

        void setTask(TaskSpec task) {
            this.task = task;
            this.fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return this.task == null ? 0 : this.task.getChunks().size();
        }

        @Override
        public int getColumnCount() {
            return this.columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return this.columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ChunkSpec chunk = this.task.getChunks().get(row);
            switch (column) {
                case 0:
                    return String.valueOf(chunk.getId());
                case 1:
                    return chunk.getExecutionTime().describe();
                case 2:
                    return chunk.getOverhead().describe();
                case 3:
                    return chunk.describeResources();
                default:
                    return "";
            }
        }
    }

}
