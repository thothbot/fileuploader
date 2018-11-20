package utils;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;

import javax.imageio.ImageIO;
import java.io.*;

public final class ImagePngTastic extends ImagePngOptimizer
{

    private Integer iterations;
    private int compressionLevel;
    private boolean removeGamma;

    /**
     * Load image from File
     *
     * @param file
     * @throws IOException
     */
    public ImagePngTastic(File file) throws IOException {
        super(file);
    }

    /**
     * Remove gamma correction info if found
     * @param remove
     * @return
     */
    public ImagePngTastic setRemoveGamma(boolean remove) {
        this.removeGamma = remove;

        return this;
    }

    /**
     * The compression level; 0-9 allowed (default is to try them all by brute force)
     * @param level
     * @return
     */
    public ImagePngTastic setCompressionLevel(int level) {
        this.compressionLevel = level;

        return this;
    }
    /**
     * Number of compression iterations (useful for zopfli)
     * @param iterations
     * @return
     */
    public ImagePngTastic setIterations(int iterations) {
        this.iterations = iterations;

        return this;
    }

    /**
     * Image optimization.
     * @param file
     * @throws IOException
     */
    @Override
    public void saveForWeb(File file) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(img, "png", os);

        PngImage image = new PngImage(new ByteArrayInputStream(os.toByteArray()));

        PngOptimizer optimizer = new PngOptimizer();
        optimizer.setCompressor("zopfli", iterations);

        PngImage optimizedImage = optimizer.optimize(image, removeGamma, compressionLevel);

        optimizedImage.writeDataOutputStream(new FileOutputStream(file));
    }
}
