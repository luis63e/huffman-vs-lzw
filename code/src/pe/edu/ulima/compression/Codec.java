package pe.edu.ulima.compression;

/**
 * A lossless compression algorithm operating on whole byte arrays.
 *
 * <p>Implementations must satisfy {@code decode(encode(x))} equals {@code x} for every input
 * {@code x}, and must reject malformed encoded data with an {@link IllegalArgumentException}.
 */
public interface Codec {

    /** Human-readable algorithm name used in reports and CSV files. */
    String name();

    /** Compresses {@code input} into a self-describing byte array. */
    byte[] encode(byte[] input);

    /**
     * Restores the original bytes.
     *
     * @throws IllegalArgumentException if {@code encoded} is not a valid stream for this codec
     */
    byte[] decode(byte[] encoded);
}
