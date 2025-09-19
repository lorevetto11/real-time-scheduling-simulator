package utils.sampler;

import java.math.BigDecimal;
import java.util.Random;
import org.oristool.simulator.samplers.Sampler;

/**
 * Represents a sampler that, when invoked, returns one of two provided values
 * based on a specified probability.
 */
public class ChoiceSampler implements Sampler {

    private BigDecimal main;
    private BigDecimal other;
    private double probability;
    private Random random;

    /**
     * @param main        The primary value, which will be returned with a probability
     *                    defined by the {@code probability} parameter.
     * @param other       The alternative value, which will be returned if the primary
     *                    value is not chosen.
     * @param probability The probability (from 0 to 100) of the {@code main} value
     *                    being returned.
     */
    public ChoiceSampler(BigDecimal main, BigDecimal other, double probability) {
        if (probability < 0 || probability > 100) {
            throw new IllegalArgumentException("Probability must be between 0 and 100.");
        }
        this.main = main;
        this.other = other;
        this.probability = probability;
        this.random = new Random();
    }

    @Override
    public BigDecimal getSample() {
        if (random.nextDouble() < probability / 100) {
            return main;
        } else {
            return other;
        }
    }

}