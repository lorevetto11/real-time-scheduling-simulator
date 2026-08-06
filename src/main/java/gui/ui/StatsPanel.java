package gui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import gui.trace.DatasetStats;

/**
 * Riepilogo del dataset: schede con i numeri principali e tabella dei tempi di
 * risposta per task.
 */
public final class StatsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel traceValue = metricValue();
    private final JLabel missValue = metricValue();
    private final JLabel responseValue = metricValue();
    private final JLabel preemptValue = metricValue();
    private final TaskStatsModel model = new TaskStatsModel();

    // CONSTRUCTOR
    public StatsPanel() {
        super(new BorderLayout(0, 8));
        this.setOpaque(false);

        JPanel cards = new JPanel(new GridLayout(1, 4, 8, 0));
        cards.setOpaque(false);
        cards.add(card("Trace generate", this.traceValue));
        cards.add(card("Trace con deadline miss", this.missValue));
        cards.add(card("Tempo di risposta medio", this.responseValue));
        cards.add(card("Preemption totali", this.preemptValue));
        this.add(cards, BorderLayout.NORTH);

        JTable table = new JTable(this.model);
        table.setFont(UiTheme.FONT_BASE);
        table.getTableHeader().setFont(UiTheme.FONT_BOLD);
        table.setRowHeight(21);
        table.setGridColor(UiTheme.GRID);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        scroll.setPreferredSize(new Dimension(420, 118));
        this.add(scroll, BorderLayout.CENTER);
    }

    // METHOD
    /** Aggiorna i valori mostrati. */
    public void setStats(DatasetStats stats) {
        if (stats == null) {
            this.traceValue.setText("-");
            this.missValue.setText("-");
            this.responseValue.setText("-");
            this.preemptValue.setText("-");
            this.model.setStats(null);
            return;
        }
        this.traceValue.setText(String.valueOf(stats.getTraceCount()));
        this.missValue.setText(String.format("%d (%.0f%%)", stats.getMissedTraces(), stats.getMissRate()));
        this.missValue.setForeground(stats.getMissedTraces() > 0 ? UiTheme.DANGER : UiTheme.TEXT);
        double average = stats.getAverageResponse();
        this.responseValue.setText(Double.isNaN(average) ? "-" : String.format("%.2f ms", average));
        this.preemptValue.setText(String.valueOf(stats.getPreemptions()));
        this.model.setStats(stats);
    }

    // HELPER
    private static JLabel metricValue() {
        JLabel label = new JLabel("-");
        label.setFont(UiTheme.FONT_METRIC);
        label.setForeground(UiTheme.TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel card(String title, JLabel value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0xF1F1EE));
        panel.setOpaque(true);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER),
            UiTheme.padding(8, 10, 8, 10)));

        JLabel caption = new JLabel(title);
        caption.setFont(UiTheme.FONT_SMALL);
        caption.setForeground(UiTheme.TEXT_MUTED);
        caption.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(caption);
        panel.add(javax.swing.Box.createVerticalStrut(2));
        panel.add(value);
        return panel;
    }

    /** Tabella dei tempi di risposta per task. */
    private static final class TaskStatsModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final String[] columns = {
            "Task", "Job", "Completati", "Deadline perse", "Risposta media", "Risposta massima" };
        private DatasetStats stats;

        void setStats(DatasetStats stats) {
            this.stats = stats;
            this.fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return this.stats == null ? 0 : this.stats.getPerTask().size();
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
            DatasetStats.TaskStats task = this.stats.getPerTask().get(row);
            switch (column) {
                case 0:
                    return "Task" + task.getTaskId();
                case 1:
                    return task.getJobs();
                case 2:
                    return task.getCompletedJobs();
                case 3:
                    return task.getMissedJobs();
                case 4:
                    return format(task.getAverageResponse());
                case 5:
                    return format(task.getMaxResponse());
                default:
                    return "";
            }
        }

        private static String format(double value) {
            return Double.isNaN(value) ? "-" : String.format("%.3f ms", value);
        }
    }

}
