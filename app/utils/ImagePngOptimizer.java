package utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class ImagePngOptimizer extends ImageOptimizer {


    /**
     * Load image from InputStream
     *
     * @param input
     * @throws IOException
     */
    public ImagePngOptimizer(InputStream input) throws IOException {
        super(input);
    }

    public ImagePngOptimizer(File file) throws IOException {
        super(file);
    }

    /**
     * Image optimization.
     * @param file
     * @throws Exception
     */
    public abstract void saveForWeb(File file) throws Exception;
}
