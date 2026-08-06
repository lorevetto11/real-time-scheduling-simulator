package gui.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * Costanti grafiche condivise da tutta l'interfaccia: colori, font e piccoli
 * aiuti per costruire bordi ed etichette in modo uniforme.
 */
public final class UiTheme {

    // COLORI DI BASE
    public static final Color BACKGROUND = new Color(0xF7F7F5);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xDCDCD6);
    public static final Color TEXT = new Color(0x24241F);
    public static final Color TEXT_MUTED = new Color(0x76766E);
    public static final Color ACCENT = new Color(0x2F6FB5);
    public static final Color ACCENT_SOFT = new Color(0xE6F1FB);
    public static final Color DANGER = new Color(0xC0392B);
    public static final Color DANGER_SOFT = new Color(0xFBEAEA);
    public static final Color SUCCESS = new Color(0x3B7A2A);
    public static final Color SUCCESS_SOFT = new Color(0xEDF5E4);
    public static final Color WARNING = new Color(0x9A6410);
    public static final Color WARNING_SOFT = new Color(0xFCF2DE);
    public static final Color GRID = new Color(0xE8E8E2);
    public static final Color IDLE = new Color(0xF0F0EC);

    // FONT
    public static final Font FONT_BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font FONT_METRIC = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    public static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Palette usata per distinguere i task nel grafico. */
    private static final Color[] TASK_COLORS = {
        new Color(0x7F77DD),
        new Color(0x1D9E75),
        new Color(0xD85A30),
        new Color(0xD4537E),
        new Color(0x378ADD),
        new Color(0xBA7517),
        new Color(0x639922),
        new Color(0x8A8A82)
    };

    private UiTheme() {}

    // METHOD
    /**
     * @param taskId identificativo del task, a partire da 1
     * @return il colore assegnato al task nel grafico
     */
    public static Color taskColor(int taskId) {
        int index = Math.max(0, taskId - 1) % TASK_COLORS.length;
        return TASK_COLORS[index];
    }

    /** @return una variante piu chiara del colore, usata per i riempimenti tenui */
    public static Color lighten(Color color, double amount) {
        int r = (int) Math.round(color.getRed() + (255 - color.getRed()) * amount);
        int g = (int) Math.round(color.getGreen() + (255 - color.getGreen()) * amount);
        int b = (int) Math.round(color.getBlue() + (255 - color.getBlue()) * amount);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    /** @return una variante piu scura del colore, usata per bordi e contorni */
    public static Color darken(Color color, double amount) {
        int r = (int) Math.round(color.getRed() * (1 - amount));
        int g = (int) Math.round(color.getGreen() * (1 - amount));
        int b = (int) Math.round(color.getBlue() * (1 - amount));
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    /** Bordo di una scheda con titolo. */
    public static Border cardBorder(String title) {
        Border line = BorderFactory.createLineBorder(BORDER);
        Border titled = BorderFactory.createTitledBorder(line, title, 0, 0, FONT_BOLD, TEXT);
        return BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(4, 8, 8, 8));
    }

    /** Bordo semplice con spaziatura interna. */
    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** @return una etichetta di intestazione */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_TITLE);
        label.setForeground(TEXT);
        return label;
    }

    /** @return una etichetta di testo secondario */
    public static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    /** Applica il font di base a un componente e ai suoi figli diretti. */
    public static void applyBaseFont(JComponent component) {
        component.setFont(FONT_BASE);
        for (Component child : component.getComponents())
            if (child instanceof JComponent)
                child.setFont(FONT_BASE);
    }

    // HELPER
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

}
