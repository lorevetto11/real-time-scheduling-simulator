package gui.ui;

import java.awt.CardLayout;
import java.awt.FlowLayout;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import gui.model.SamplerSpec;

/**
 * Pannello riutilizzabile per configurare un {@link SamplerSpec}: un menu a
 * tendina sceglie il tipo di campionamento e i campi visibili si adattano di
 * conseguenza.
 */
public final class SamplerEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JComboBox<SamplerSpec.Kind> kindBox;
    private final JPanel cards = new JPanel(new CardLayout());
    private final JSpinner constantValue = spinner(1.0, 0.0, 1_000_000.0, 0.5);
    private final JSpinner uniformMin = spinner(1.0, 0.0, 1_000_000.0, 0.5);
    private final JSpinner uniformMax = spinner(2.0, 0.0, 1_000_000.0, 0.5);
    private final JSpinner choiceMain = spinner(0.0, -1_000_000.0, 1_000_000.0, 0.5);
    private final JSpinner choiceOther = spinner(1.0, -1_000_000.0, 1_000_000.0, 0.5);
    private final JSpinner choiceProbability = spinner(50.0, 0.0, 100.0, 5.0);

    // CONSTRUCTOR
    /**
     * @param allowNone se true il sampler puo essere assente, come nel caso
     *                  dell'overhead di un chunk
     */
    public SamplerEditor(boolean allowNone) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        this.setOpaque(false);

        SamplerSpec.Kind[] kinds = allowNone
            ? SamplerSpec.Kind.values()
            : new SamplerSpec.Kind[] {
                SamplerSpec.Kind.CONSTANT,
                SamplerSpec.Kind.UNIFORM,
                SamplerSpec.Kind.CHOICE };
        this.kindBox = new JComboBox<>(kinds);
        this.kindBox.setFont(UiTheme.FONT_BASE);

        this.cards.setOpaque(false);
        this.cards.add(new JPanel(), SamplerSpec.Kind.NONE.name());
        this.cards.add(row("valore", this.constantValue), SamplerSpec.Kind.CONSTANT.name());
        this.cards.add(row("min", this.uniformMin, "max", this.uniformMax), SamplerSpec.Kind.UNIFORM.name());
        this.cards.add(
            row("principale", this.choiceMain, "alternativo", this.choiceOther, "prob. %", this.choiceProbability),
            SamplerSpec.Kind.CHOICE.name());

        this.add(this.kindBox);
        this.add(this.cards);

        this.kindBox.addActionListener(e -> this.showCard());
        this.showCard();
    }

    // METHOD
    /** Carica nei campi i valori della specifica indicata. */
    public void setSpec(SamplerSpec spec) {
        this.kindBox.setSelectedItem(spec.getKind());
        switch (spec.getKind()) {
            case CONSTANT:
                this.constantValue.setValue(spec.getFirst());
                break;
            case UNIFORM:
                this.uniformMin.setValue(spec.getFirst());
                this.uniformMax.setValue(spec.getSecond());
                break;
            case CHOICE:
                this.choiceMain.setValue(spec.getFirst());
                this.choiceOther.setValue(spec.getSecond());
                this.choiceProbability.setValue(spec.getProbability());
                break;
            default:
                break;
        }
        this.showCard();
    }

    /** @return una nuova specifica costruita con i valori attualmente inseriti */
    public SamplerSpec getSpec() {
        SamplerSpec.Kind kind = (SamplerSpec.Kind) this.kindBox.getSelectedItem();
        if (kind == null)
            kind = SamplerSpec.Kind.CONSTANT;
        switch (kind) {
            case NONE:
                return SamplerSpec.none();
            case CONSTANT:
                return SamplerSpec.constant(value(this.constantValue));
            case UNIFORM:
                return SamplerSpec.uniform(value(this.uniformMin), value(this.uniformMax));
            case CHOICE:
            default:
                return SamplerSpec.choice(
                    value(this.choiceMain),
                    value(this.choiceOther),
                    value(this.choiceProbability));
        }
    }

    // HELPER
    private void showCard() {
        SamplerSpec.Kind kind = (SamplerSpec.Kind) this.kindBox.getSelectedItem();
        if (kind == null)
            return;
        ((CardLayout) this.cards.getLayout()).show(this.cards, kind.name());
    }

    private static JPanel row(Object... parts) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        for (Object part : parts) {
            if (part instanceof String) {
                JLabel label = new JLabel((String) part);
                label.setFont(UiTheme.FONT_SMALL);
                label.setForeground(UiTheme.TEXT_MUTED);
                panel.add(label);
            } else {
                panel.add((JSpinner) part);
            }
        }
        return panel;
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setFont(UiTheme.FONT_BASE);
        spinner.setPreferredSize(new java.awt.Dimension(74, 24));
        return spinner;
    }

    private static double value(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

}
