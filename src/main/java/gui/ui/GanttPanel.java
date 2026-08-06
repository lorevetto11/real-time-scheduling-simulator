package gui.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import gui.trace.ExecutionSegment;
import gui.trace.Job;
import gui.trace.Trace;
import gui.trace.TraceEvent;

/**
 * Disegna il diagramma di Gantt di una trace: una corsia per task, barre per
 * l'esecuzione dei chunk, retinatura per le sezioni critiche e marcatori per
 * rilasci, deadline, semafori, preemption e guasti.
 */
public final class GanttPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int LANE_HEIGHT = 52;
    private static final int BAR_HEIGHT = 22;
    private static final int BAR_OFFSET = 18;
    private static final int TOP_PAD = 26;
    private static final int AXIS_HEIGHT = 56;
    private static final int LEFT_MARGIN = 14;
    private static final int RIGHT_MARGIN = 30;
    private static final int LABEL_WIDTH = 78;

    private static final double[] NICE_STEPS = {
        0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500,
        1000, 2000, 2500, 5000, 10000, 20000, 50000, 100000 };

    private Trace trace;
    private int taskCount = 0;
    private double pixelsPerMs = 4.0;
    private double highlightTime = Double.NaN;
    private final LabelPanel labelPanel = new LabelPanel();

    // CONSTRUCTOR
    public GanttPanel() {
        this.setBackground(UiTheme.SURFACE);
        this.setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    // GETTER AND SETTER
    /** @return il componente con le etichette dei task, da usare come intestazione di riga */
    public JPanel getLabelPanel() {
        return this.labelPanel;
    }

    /**
     * Imposta la trace da visualizzare.
     *
     * @param trace     la trace, oppure null per svuotare il grafico
     * @param taskCount numero di task del taskset, per disegnare anche le corsie vuote
     */
    public void setTrace(Trace trace, int taskCount) {
        this.trace = trace;
        this.taskCount = taskCount;
        this.highlightTime = Double.NaN;
        this.refreshSize();
    }

    /** @return la scala corrente in pixel per millisecondo */
    public double getPixelsPerMs() {
        return this.pixelsPerMs;
    }

    public void setPixelsPerMs(double pixelsPerMs) {
        this.pixelsPerMs = Math.max(0.05, pixelsPerMs);
        this.refreshSize();
    }

    /** Adatta lo zoom in modo che l'intera trace entri nella larghezza indicata. */
    public void fitTo(int availableWidth) {
        double horizon = this.horizon();
        if (horizon <= 0)
            return;
        int usable = Math.max(120, availableWidth - LEFT_MARGIN - RIGHT_MARGIN);
        this.setPixelsPerMs(usable / horizon);
    }

    /** Evidenzia un istante con una linea verticale, usato dal pannello di log. */
    public void setHighlightTime(double time) {
        this.highlightTime = time;
        this.repaint();
    }

    /** @return la coordinata orizzontale corrispondente a un istante */
    public int xOf(double time) {
        return (int) Math.round(LEFT_MARGIN + time * this.pixelsPerMs);
    }

    // METHOD
    /**
     * Produce un'immagine del grafico completo di etichette, pronta per essere
     * salvata su file.
     *
     * @return l'immagine renderizzata, oppure null se non c'e nulla da disegnare
     */
    public BufferedImage renderToImage() {
        if (this.trace == null)
            return null;
        Dimension content = this.getPreferredSize();
        int width = LABEL_WIDTH + content.width;
        int height = Math.max(content.height, this.labelPanel.getPreferredSize().height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(UiTheme.SURFACE);
        g.fillRect(0, 0, width, height);

        this.labelPanel.paintContent(g, height);
        g.translate(LABEL_WIDTH, 0);
        this.paintContent(g, content.width, height);
        g.dispose();
        return image;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        ExecutionSegment segment = this.segmentAt(event.getX(), event.getY());
        if (segment == null)
            return null;
        return String.format(
            "<html>%s<br>da %.3f a %.3f ms (%.3f ms)<br>%s%s</html>",
            segment.getLabel(),
            segment.getStart(),
            segment.getEnd(),
            segment.getDuration(),
            segment.isCritical() ? "sezione critica" : "esecuzione normale",
            segment.isCompleted() ? "" : "<br>interrotto da preemption o blocco");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        this.paintContent((Graphics2D) graphics.create(), this.getWidth(), this.getHeight());
    }

    // HELPER - DISEGNO
    private void paintContent(Graphics2D g, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(UiTheme.SURFACE);
        g.fillRect(0, 0, width, height);

        if (this.trace == null) {
            g.setColor(UiTheme.TEXT_MUTED);
            g.setFont(UiTheme.FONT_BASE);
            g.drawString("Nessuna trace da visualizzare.", LEFT_MARGIN, TOP_PAD);
            return;
        }

        int lanes = Math.max(this.taskCount, this.trace.getTaskIds().size());
        int lanesBottom = TOP_PAD + lanes * LANE_HEIGHT;

        this.paintLaneBackground(g, width, lanes);
        this.paintGrid(g, lanesBottom);
        this.paintSegments(g);
        this.paintJobMarkers(g, lanes);
        this.paintEventMarkers(g);
        this.paintHighlight(g, lanesBottom);
        this.paintAxis(g, width, lanesBottom);
        this.paintLegend(g, lanesBottom + AXIS_HEIGHT - 10);
    }

    private void paintLaneBackground(Graphics2D g, int width, int lanes) {
        for (int i = 0; i < lanes; i++) {
            int y = TOP_PAD + i * LANE_HEIGHT;
            g.setColor(i % 2 == 0 ? UiTheme.SURFACE : new Color(0xFBFBF9));
            g.fillRect(0, y, width, LANE_HEIGHT);
            g.setColor(UiTheme.GRID);
            g.drawLine(0, y + LANE_HEIGHT, width, y + LANE_HEIGHT);
        }
    }

    private void paintGrid(Graphics2D g, int bottom) {
        double step = this.tickStep();
        g.setColor(UiTheme.GRID);
        for (double t = 0; t <= this.horizon() + step / 2; t += step) {
            int x = this.xOf(t);
            g.drawLine(x, TOP_PAD - 8, x, bottom);
        }
    }

    private void paintSegments(Graphics2D g) {
        Stroke original = g.getStroke();
        for (ExecutionSegment segment : this.trace.getSegments()) {
            int lane = segment.getTaskId() - 1;
            if (lane < 0)
                continue;
            int x1 = this.xOf(segment.getStart());
            int x2 = this.xOf(segment.getEnd());
            int width = Math.max(1, x2 - x1);
            int y = this.barY(lane);
            Color base = UiTheme.taskColor(segment.getTaskId());

            if (segment.isCritical()) {
                g.setColor(UiTheme.lighten(base, 0.55));
                g.fillRect(x1, y, width, BAR_HEIGHT);
                this.paintHatch(g, x1, y, width, BAR_HEIGHT, UiTheme.darken(base, 0.15));
            } else {
                g.setColor(base);
                g.fillRect(x1, y, width, BAR_HEIGHT);
            }

            g.setColor(UiTheme.darken(base, 0.3));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(x1, y, width, BAR_HEIGHT);

            if (!segment.isCompleted()) {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] { 3f, 3f }, 0f));
                g.drawLine(x1 + width, y, x1 + width, y + BAR_HEIGHT);
                g.setStroke(original);
            }

            if (width > 34) {
                g.setFont(UiTheme.FONT_SMALL);
                g.setColor(segment.isCritical() ? UiTheme.darken(base, 0.45) : Color.WHITE);
                String label = String.valueOf(segment.getChunkId());
                int textWidth = g.getFontMetrics().stringWidth(label);
                g.drawString(label, x1 + (width - textWidth) / 2, y + BAR_HEIGHT - 7);
            }
        }
        g.setStroke(original);
    }

    private void paintHatch(Graphics2D g, int x, int y, int width, int height, Color color) {
        java.awt.Shape clip = g.getClip();
        g.setClip(x, y, width, height);
        g.setColor(color);
        g.setStroke(new BasicStroke(1f));
        for (int i = -height; i < width; i += 5)
            g.draw(new Line2D.Float(x + i, y + height, x + i + height, y));
        g.setClip(clip);
    }

    private void paintJobMarkers(Graphics2D g, int lanes) {
        for (Job job : this.trace.getJobs()) {
            int lane = job.getTaskId() - 1;
            if (lane < 0 || lane >= lanes)
                continue;
            Color base = UiTheme.darken(UiTheme.taskColor(job.getTaskId()), 0.25);
            int baseline = this.barY(lane) - 2;
            this.paintArrowUp(g, this.xOf(job.getRelease()), baseline, base);
            if (job.getAbsoluteDeadline() > 0 && job.getAbsoluteDeadline() <= this.horizon() + 1e-9)
                this.paintArrowDown(g, this.xOf(job.getAbsoluteDeadline()), baseline,
                    job.isDeadlineMissed() ? UiTheme.DANGER : base);
        }
    }

    private void paintEventMarkers(Graphics2D g) {
        g.setFont(UiTheme.FONT_SMALL);
        for (TraceEvent event : this.trace.getEvents()) {
            int lane = event.getTaskId() - 1;
            if (lane < 0)
                continue;
            int laneTop = TOP_PAD + lane * LANE_HEIGHT;
            int x = this.xOf(event.getTime());
            int y = this.barY(lane);

            switch (event.getType()) {
                case LOCK:
                    this.paintLockMark(g, x, y + BAR_HEIGHT + 12, true);
                    break;
                case UNLOCK:
                    this.paintLockMark(g, x, y + BAR_HEIGHT + 12, false);
                    break;
                case BLOCKED:
                    this.paintBlockedMark(g, x, y + BAR_HEIGHT / 2);
                    break;
                case PREEMPT:
                    g.setColor(UiTheme.TEXT_MUTED);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[] { 2f, 2f }, 0f));
                    g.drawLine(x, laneTop + 4, x, laneTop + LANE_HEIGHT - 4);
                    g.setStroke(new BasicStroke(1f));
                    break;
                case DEADLINE_MISS:
                    this.paintFaultMark(g, x, laneTop + LANE_HEIGHT / 2);
                    break;
                default:
                    break;
            }
        }
    }

    private void paintHighlight(Graphics2D g, int bottom) {
        if (Double.isNaN(this.highlightTime))
            return;
        int x = this.xOf(this.highlightTime);
        g.setColor(UiTheme.ACCENT);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x, TOP_PAD - 12, x, bottom);
        g.fillPolygon(
            new int[] { x - 4, x + 4, x },
            new int[] { TOP_PAD - 18, TOP_PAD - 18, TOP_PAD - 12 },
            3);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintAxis(Graphics2D g, int width, int top) {
        g.setColor(UiTheme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, top, width, top);

        g.setFont(UiTheme.FONT_SMALL);
        double step = this.tickStep();
        for (double t = 0; t <= this.horizon() + step / 2; t += step) {
            int x = this.xOf(t);
            g.setColor(UiTheme.BORDER);
            g.drawLine(x, top, x, top + 5);
            g.setColor(UiTheme.TEXT_MUTED);
            String label = formatTime(t, step);
            g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, top + 17);
        }
        g.setColor(UiTheme.TEXT_MUTED);
        g.drawString("ms", width - 24, top + 17);
    }

    private void paintLegend(Graphics2D g, int y) {
        g.setFont(UiTheme.FONT_SMALL);
        int x = LEFT_MARGIN;

        g.setColor(new Color(0x8A8A82));
        g.fillRect(x, y - 8, 14, 10);
        x += 18;
        x += this.legendText(g, "esecuzione", x, y);

        g.setColor(UiTheme.lighten(new Color(0x8A8A82), 0.55));
        g.fillRect(x, y - 8, 14, 10);
        this.paintHatch(g, x, y - 8, 14, 10, new Color(0x6A6A62));
        g.setColor(new Color(0x6A6A62));
        g.drawRect(x, y - 8, 14, 10);
        x += 18;
        x += this.legendText(g, "sezione critica", x, y);

        this.paintArrowUp(g, x + 4, y - 8, UiTheme.TEXT_MUTED);
        x += 14;
        x += this.legendText(g, "release", x, y);

        this.paintArrowDown(g, x + 4, y + 2, UiTheme.TEXT_MUTED);
        x += 14;
        x += this.legendText(g, "deadline", x, y);

        this.paintLockMark(g, x + 5, y - 8, true);
        x += 16;
        x += this.legendText(g, "lock", x, y);

        this.paintLockMark(g, x + 5, y - 8, false);
        x += 16;
        x += this.legendText(g, "unlock", x, y);

        this.paintBlockedMark(g, x + 5, y - 3);
        x += 16;
        x += this.legendText(g, "bloccato", x, y);

        this.paintFaultMark(g, x + 6, y - 3);
        x += 18;
        this.legendText(g, "deadline miss", x, y);
    }

    private int legendText(Graphics2D g, String text, int x, int y) {
        g.setFont(UiTheme.FONT_SMALL);
        g.setColor(UiTheme.TEXT_MUTED);
        g.drawString(text, x, y);
        return g.getFontMetrics().stringWidth(text) + 16;
    }

    private void paintArrowUp(Graphics2D g, int x, int baseY, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(x, baseY, x, baseY - 10);
        g.fillPolygon(
            new int[] { x - 4, x + 4, x },
            new int[] { baseY - 7, baseY - 7, baseY - 13 },
            3);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintArrowDown(Graphics2D g, int x, int baseY, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(x, baseY - 10, x, baseY);
        g.fillPolygon(
            new int[] { x - 4, x + 4, x },
            new int[] { baseY - 3, baseY - 3, baseY + 3 },
            3);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintLockMark(Graphics2D g, int x, int y, boolean locked) {
        g.setColor(locked ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED);
        if (locked)
            g.fillRect(x - 3, y - 5, 7, 6);
        else
            g.drawRect(x - 3, y - 5, 6, 5);
        g.drawArc(x - 3, y - 10, 7, 8, 0, 180);
    }

    private void paintBlockedMark(Graphics2D g, int x, int y) {
        g.setColor(UiTheme.WARNING_SOFT);
        g.fillOval(x - 5, y - 5, 11, 11);
        g.setColor(UiTheme.WARNING);
        g.setStroke(new BasicStroke(1.4f));
        g.drawOval(x - 5, y - 5, 11, 11);
        g.drawLine(x - 3, y + 3, x + 3, y - 3);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintFaultMark(Graphics2D g, int x, int y) {
        g.setColor(UiTheme.DANGER_SOFT);
        g.fillOval(x - 7, y - 7, 15, 15);
        g.setColor(UiTheme.DANGER);
        g.setStroke(new BasicStroke(1.6f));
        g.drawOval(x - 7, y - 7, 15, 15);
        g.setFont(UiTheme.FONT_BOLD);
        g.drawString("!", x - 2, y + 5);
        g.setStroke(new BasicStroke(1f));
    }

    // HELPER - GEOMETRIA
    private int barY(int lane) {
        return TOP_PAD + lane * LANE_HEIGHT + BAR_OFFSET;
    }

    private double horizon() {
        if (this.trace == null)
            return 0;
        return Math.max(this.trace.getHorizon(), 1e-6);
    }

    private double tickStep() {
        double target = 70 / Math.max(this.pixelsPerMs, 1e-6);
        for (double step : NICE_STEPS)
            if (step >= target)
                return step;
        return NICE_STEPS[NICE_STEPS.length - 1];
    }

    private ExecutionSegment segmentAt(int x, int y) {
        if (this.trace == null)
            return null;
        for (ExecutionSegment segment : this.trace.getSegments()) {
            int lane = segment.getTaskId() - 1;
            int barTop = this.barY(lane);
            if (y < barTop || y > barTop + BAR_HEIGHT)
                continue;
            int x1 = this.xOf(segment.getStart());
            int x2 = this.xOf(segment.getEnd());
            if (x >= x1 && x <= Math.max(x1 + 1, x2))
                return segment;
        }
        return null;
    }

    private void refreshSize() {
        int lanes = this.trace == null ? Math.max(1, this.taskCount)
            : Math.max(this.taskCount, this.trace.getTaskIds().size());
        int width = LEFT_MARGIN + (int) Math.ceil(this.horizon() * this.pixelsPerMs) + RIGHT_MARGIN;
        int height = TOP_PAD + lanes * LANE_HEIGHT + AXIS_HEIGHT;
        this.setPreferredSize(new Dimension(Math.max(width, 780), height));
        this.labelPanel.setPreferredSize(new Dimension(LABEL_WIDTH, height));
        this.revalidate();
        this.repaint();
        this.labelPanel.revalidate();
        this.labelPanel.repaint();
    }

    private static String formatTime(double value, double step) {
        if (step >= 1 && value == Math.rint(value))
            return String.valueOf((long) Math.rint(value));
        return String.format("%.1f", value);
    }

    /** @return elenco degli identificativi di task da mostrare nelle corsie */
    private List<Integer> laneIds() {
        List<Integer> ids = new ArrayList<>();
        int lanes = this.trace == null ? this.taskCount
            : Math.max(this.taskCount, this.trace.getTaskIds().size());
        for (int i = 1; i <= lanes; i++)
            ids.add(i);
        return ids;
    }

    /** Colonna fissa con le etichette dei task, allineata alle corsie del grafico. */
    private final class LabelPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        LabelPanel() {
            this.setBackground(UiTheme.SURFACE);
            this.setOpaque(true);
            this.setPreferredSize(new Dimension(LABEL_WIDTH, 200));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            this.paintContent((Graphics2D) graphics.create(), this.getHeight());
        }

        void paintContent(Graphics2D g, int height) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(UiTheme.SURFACE);
            g.fillRect(0, 0, LABEL_WIDTH, height);
            g.setFont(UiTheme.FONT_BASE);

            List<Integer> ids = GanttPanel.this.laneIds();
            for (int i = 0; i < ids.size(); i++) {
                int laneTop = TOP_PAD + i * LANE_HEIGHT;
                g.setColor(UiTheme.GRID);
                g.drawLine(0, laneTop + LANE_HEIGHT, LABEL_WIDTH, laneTop + LANE_HEIGHT);
                g.setColor(UiTheme.taskColor(ids.get(i)));
                g.fillRect(6, laneTop + LANE_HEIGHT / 2 - 5, 4, 11);
                g.setColor(UiTheme.TEXT);
                g.drawString("Task" + ids.get(i), 16, laneTop + LANE_HEIGHT / 2 + 5);
            }
            g.setColor(UiTheme.BORDER);
            g.drawLine(LABEL_WIDTH - 1, 0, LABEL_WIDTH - 1, height);
        }
    }

}
