package gui.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import gui.model.ChunkSpec;
import gui.model.SamplerSpec;

/**
 * Finestra di dialogo per creare o modificare un chunk: identificativo, tempo
 * di esecuzione, overhead e risorse condivise utilizzate.
 */
public final class ChunkDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JSpinner idSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
    private final SamplerEditor executionEditor = new SamplerEditor(false);
    private final SamplerEditor overheadEditor = new SamplerEditor(true);
    private final List<JCheckBox> resourceBoxes = new ArrayList<>();
    private boolean confirmed = false;

    // CONSTRUCTOR
    private ChunkDialog(Window owner, ChunkSpec chunk, int resourceCount, boolean resourcesEnabled) {
        super(owner, chunk == null ? "Nuovo chunk" : "Modifica chunk", ModalityType.APPLICATION_MODAL);
        this.setLayout(new BorderLayout());
        this.getRootPane().setBorder(UiTheme.padding(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(label("Identificativo"), gbc);
        gbc.gridx = 1;
        this.idSpinner.setPreferredSize(new Dimension(74, 24));
        form.add(this.idSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(label("Tempo di esecuzione"), gbc);
        gbc.gridx = 1;
        form.add(this.executionEditor, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        form.add(label("Overhead aggiuntivo"), gbc);
        gbc.gridx = 1;
        form.add(this.overheadEditor, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(label("Risorse condivise"), gbc);
        gbc.gridx = 1;
        form.add(this.buildResourcePanel(resourceCount, resourcesEnabled), gbc);

        this.add(form, BorderLayout.CENTER);
        this.add(this.buildButtons(), BorderLayout.SOUTH);

        if (chunk != null)
            this.load(chunk);

        this.pack();
        this.setMinimumSize(new Dimension(Math.max(460, this.getWidth()), this.getHeight()));
        this.setLocationRelativeTo(owner);
    }

    // METHOD
    /**
     * Apre la finestra di dialogo.
     *
     * @param owner             finestra padre
     * @param chunk             il chunk da modificare, oppure null per crearne uno nuovo
     * @param suggestedId       identificativo proposto per un nuovo chunk
     * @param resourceCount     numero di risorse condivise dichiarate
     * @param resourcesEnabled  false se l'algoritmo scelto non supporta le risorse
     * @return il chunk configurato, oppure null se l'utente ha annullato
     */
    public static ChunkSpec show(Window owner, ChunkSpec chunk, int suggestedId,
            int resourceCount, boolean resourcesEnabled) {
        ChunkDialog dialog = new ChunkDialog(owner, chunk, resourceCount, resourcesEnabled);
        if (chunk == null)
            dialog.idSpinner.setValue(suggestedId);
        dialog.setVisible(true);
        if (!dialog.confirmed)
            return null;
        return dialog.build();
    }

    // HELPER
    private JPanel buildResourcePanel(int resourceCount, boolean resourcesEnabled) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        if (resourceCount == 0) {
            panel.add(UiTheme.hint("Nessuna risorsa dichiarata nella configurazione."));
        } else if (!resourcesEnabled) {
            panel.add(UiTheme.hint("L'algoritmo selezionato non supporta le risorse condivise."));
        } else {
            for (int i = 0; i < resourceCount; i++) {
                JCheckBox box = new JCheckBox("Res" + (i + 1));
                box.setFont(UiTheme.FONT_BASE);
                box.setOpaque(false);
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                this.resourceBoxes.add(box);
                panel.add(box);
            }
        }

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(UiTheme.BORDER));
        scroll.setPreferredSize(new Dimension(240, 84));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.setBorder(UiTheme.padding(10, 0, 0, 0));

        JButton cancel = new JButton("Annulla");
        cancel.addActionListener(e -> this.dispose());

        JButton confirm = new JButton("Conferma");
        confirm.addActionListener(e -> {
            if (this.validateInput()) {
                this.confirmed = true;
                this.dispose();
            }
        });

        buttons.add(Box.createHorizontalGlue());
        buttons.add(cancel);
        buttons.add(confirm);
        this.getRootPane().setDefaultButton(confirm);
        return buttons;
    }

    private boolean validateInput() {
        SamplerSpec execution = this.executionEditor.getSpec();
        if (execution.expectedValue() <= 0) {
            JOptionPane.showMessageDialog(this,
                "Il tempo di esecuzione atteso deve essere maggiore di zero.",
                "Valore non valido", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void load(ChunkSpec chunk) {
        this.idSpinner.setValue(chunk.getId());
        this.executionEditor.setSpec(chunk.getExecutionTime());
        this.overheadEditor.setSpec(chunk.getOverhead());
        for (int i = 0; i < this.resourceBoxes.size(); i++)
            this.resourceBoxes.get(i).setSelected(chunk.getResourceIndexes().contains(i));
    }

    private ChunkSpec build() {
        ChunkSpec chunk = new ChunkSpec(
            ((Number) this.idSpinner.getValue()).intValue(),
            this.executionEditor.getSpec(),
            this.overheadEditor.getSpec());
        for (int i = 0; i < this.resourceBoxes.size(); i++)
            if (this.resourceBoxes.get(i).isSelected())
                chunk.getResourceIndexes().add(i);
        return chunk;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.FONT_BASE);
        return label;
    }

}
