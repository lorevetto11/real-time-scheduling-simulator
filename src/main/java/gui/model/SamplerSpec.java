package gui.model;

import java.math.BigDecimal;

import org.oristool.simulator.samplers.Sampler;
import org.oristool.simulator.samplers.UniformSampler;

import utils.sampler.ChoiceSampler;
import utils.sampler.ConstantSampler;

/**
 * Descrizione dichiarativa di un {@link Sampler}, modificabile dall'interfaccia
 * grafica e trasformabile in un sampler reale solo al momento dell'esecuzione.
 * <p>
 * Questa classe non fa parte della logica di simulazione: si limita a
 * memorizzare i parametri scelti dall'utente e a istanziare i sampler gia
 * presenti nel progetto.
 */
public final class SamplerSpec {

    /** Tipo di sampler selezionabile dall'interfaccia. */
    public enum Kind {
        NONE("Nessuno"),
        CONSTANT("Costante"),
        UNIFORM("Uniforme"),
        CHOICE("Scelta");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private Kind kind;
    private double first;
    private double second;
    private double probability;

    // CONSTRUCTOR
    public SamplerSpec(Kind kind, double first, double second, double probability) {
        this.kind = kind;
        this.first = first;
        this.second = second;
        this.probability = probability;
    }

    public static SamplerSpec none() {
        return new SamplerSpec(Kind.NONE, 0, 0, 0);
    }

    public static SamplerSpec constant(double value) {
        return new SamplerSpec(Kind.CONSTANT, value, value, 0);
    }

    public static SamplerSpec uniform(double min, double max) {
        return new SamplerSpec(Kind.UNIFORM, min, max, 0);
    }

    public static SamplerSpec choice(double main, double other, double probability) {
        return new SamplerSpec(Kind.CHOICE, main, other, probability);
    }

    public SamplerSpec copy() {
        return new SamplerSpec(this.kind, this.first, this.second, this.probability);
    }

    // GETTER AND SETTER
    public Kind getKind() {
        return this.kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public double getFirst() {
        return this.first;
    }

    public void setFirst(double first) {
        this.first = first;
    }

    public double getSecond() {
        return this.second;
    }

    public void setSecond(double second) {
        this.second = second;
    }

    public double getProbability() {
        return this.probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    // METHOD
    /**
     * Costruisce il sampler reale corrispondente a questa specifica.
     *
     * @return un sampler tra quelli gia disponibili nel progetto
     */
    public Sampler build() {
        switch (this.kind) {
            case NONE:
                return new ConstantSampler(BigDecimal.ZERO);
            case CONSTANT:
                return new ConstantSampler(BigDecimal.valueOf(this.first));
            case UNIFORM:
                return new UniformSampler(
                    BigDecimal.valueOf(Math.min(this.first, this.second)),
                    BigDecimal.valueOf(Math.max(this.first, this.second)));
            case CHOICE:
                return new ChoiceSampler(
                    BigDecimal.valueOf(this.first),
                    BigDecimal.valueOf(this.second),
                    this.probability);
            default:
                throw new IllegalStateException("Tipo di sampler non gestito: " + this.kind);
        }
    }

    /**
     * Valore atteso approssimato, usato solo per stimare a video il fattore di
     * utilizzazione prima di lanciare la simulazione.
     *
     * @return il valore medio del campionamento
     */
    public double expectedValue() {
        switch (this.kind) {
            case NONE:
                return 0.0;
            case CONSTANT:
                return this.first;
            case UNIFORM:
                return (this.first + this.second) / 2.0;
            case CHOICE:
                double p = this.probability / 100.0;
                return this.first * p + this.second * (1 - p);
            default:
                return 0.0;
        }
    }

    /**
     * @return una descrizione compatta da mostrare nelle tabelle
     */
    public String describe() {
        switch (this.kind) {
            case NONE:
                return "nessuno";
            case CONSTANT:
                return "costante " + trim(this.first);
            case UNIFORM:
                return "uniforme " + trim(this.first) + " - " + trim(this.second);
            case CHOICE:
                return "scelta " + trim(this.first) + " / " + trim(this.second)
                    + " p=" + trim(this.probability) + "%";
            default:
                return "";
        }
    }

    /** @return true se il sampler produce sempre zero */
    public boolean isNegligible() {
        return this.kind == Kind.NONE
            || (this.kind == Kind.CONSTANT && this.first == 0.0);
    }

    // HELPER
    private static String trim(double value) {
        if (value == Math.rint(value))
            return String.valueOf((long) value);
        return String.valueOf(value);
    }

    // SERIALIZATION
    String encode() {
        return this.kind.name() + "," + this.first + "," + this.second + "," + this.probability;
    }

    static SamplerSpec decode(String text) {
        String[] parts = text.split(",");
        if (parts.length < 4)
            throw new IllegalArgumentException("Sampler non valido: " + text);
        return new SamplerSpec(
            Kind.valueOf(parts[0]),
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3]));
    }

    @Override
    public String toString() {
        return this.describe();
    }

}
