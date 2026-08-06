package gui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import gui.trace.Trace;
import gui.trace.TraceEvent;

/**
 * Pannello con il log della trace selezionata: righe filtrabili per testo e per
 * livello, con evidenziazione dell'istante corrispondente nel grafico.
 */
public final class LogPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<TraceEvent> model = new DefaultListModel<>();
    private final JList<TraceEvent> list = new JList<>(this.model);
    private final JTextField filterField = new JTextField(16);
    private final JCheckBox showInfo = new JCheckBox("info", true);
    private final JCheckBox showWarnings = new JCheckBox("warning", true);
    private final JLabel counter = UiTheme.hint("");
    private final Consumer<TraceEvent> onSelect;

    private List<TraceEvent> events = new ArrayList<>();

    // CONSTRUCTOR
    /**
     * @param onSelect callback invocata quando l'utente seleziona una riga
     */
    public LogPanel(Consumer<TraceEvent> onSelect) {
        super(new BorderLayout(0, 6));
        this.onSelect = onSelect;
        this.setOpaque(false);

        this.add(this.buildToolbar(), BorderLayout.NORTH);

        this.list.setFont(UiTheme.FONT_MONO);
        this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.list.setCellRenderer(new EventRenderer());
        this.list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting())
                return;
            TraceEvent event = this.list.getSelectedValue();
            if (event != null && this.onSelect != null)
                this.onSelect.accept(event);
        });

        JScrollPane scroll = new JScrollPane(this.list);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        scroll.setPreferredSize(new Dimension(360, 200));
        this.add(scroll, BorderLayout.CENTER);
    }

    // METHOD
    /** Mostra il log della trace indicata. */
    public void setTrace(Trace trace) {
        this.events = trace == null ? new ArrayList<>() : new ArrayList<>(trace.getEvents());
        this.applyFilter();
    }

    /** @return il testo completo del log, per l'esportazione su file */
    public String getFullText() {
        StringBuilder text = new StringBuilder();
        for (TraceEvent event : this.events) {
            if (event.isWarning())
                text.append("[WARNING] ");
            text.append(event.getRaw()).append(System.lineSeparator());
        }
        return text.toString();
    }

    // HELPER
    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);

        bar.add(UiTheme.title("Log"));

        JLabel filterLabel = UiTheme.hint("filtra");
        bar.add(filterLabel);

        this.filterField.setFont(UiTheme.FONT_BASE);
        this.filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                LogPanel.this.applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                LogPanel.this.applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                LogPanel.this.applyFilter();
            }
        });
        bar.add(this.filterField);

        this.showInfo.setFont(UiTheme.FONT_BASE);
        this.showInfo.setOpaque(false);
        this.showInfo.addActionListener(e -> this.applyFilter());
        this.showWarnings.setFont(UiTheme.FONT_BASE);
        this.showWarnings.setOpaque(false);
        this.showWarnings.addActionListener(e -> this.applyFilter());
        bar.add(this.showInfo);
        bar.add(this.showWarnings);
        bar.add(this.counter);
        return bar;
    }

    private void applyFilter() {
        String needle = this.filterField.getText().trim().toLowerCase();
        this.model.clear();
        int shown = 0;
        for (TraceEvent event : this.events) {
            if (event.isWarning() && !this.showWarnings.isSelected())
                continue;
            if (!event.isWarning() && !this.showInfo.isSelected())
                continue;
            if (!needle.isEmpty() && !event.getRaw().toLowerCase().contains(needle))
                continue;
            this.model.addElement(event);
            shown++;
        }
        this.counter.setText(shown + " di " + this.events.size() + " righe");
    }

    /** Colora le righe in base al tipo di evento. */
    private static final class EventRenderer extends JLabel implements ListCellRenderer<TraceEvent> {

        private static final long serialVersionUID = 1L;

        EventRenderer() {
            this.setOpaque(true);
            this.setBorder(UiTheme.padding(1, 6, 1, 6));
            this.setFont(UiTheme.FONT_MONO);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends TraceEvent> list,
                TraceEvent value, int index, boolean selected, boolean focused) {
            this.setText(value.isWarning() ? "[WARNING] " + value.getRaw() : value.getRaw());

            Color foreground = UiTheme.TEXT;
            Color background = index % 2 == 0 ? Color.WHITE : new Color(0xFBFBF9);

            if (value.isWarning()) {
                foreground = UiTheme.WARNING;
                background = UiTheme.WARNING_SOFT;
            } else if (value.getType() == TraceEvent.Type.DEADLINE_MISS) {
                foreground = UiTheme.DANGER;
                background = UiTheme.DANGER_SOFT;
            } else if (value.getType() == TraceEvent.Type.LOCK
                    || value.getType() == TraceEvent.Type.UNLOCK
                    || value.getType() == TraceEvent.Type.BLOCKED) {
                foreground = UiTheme.ACCENT;
            } else if (value.getType() == TraceEvent.Type.PREEMPT) {
                foreground = UiTheme.TEXT_MUTED;
            }

            if (selected) {
                background = UiTheme.ACCENT_SOFT;
                foreground = UiTheme.darken(foreground, 0.1);
            }

            this.setForeground(foreground);
            this.setBackground(background);
            return this;
        }
    }

}
