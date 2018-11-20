package utils;

import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.MultiStepRescaleOp;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImageOptimizer {
    protected BufferedImage img;

    /**
     * Load image from InputStream
     * @param input
     * @throws IOException
     */
    public ImageOptimizer(InputStream input) throws IOException {
        img = ImageIO.read(input);
        input.close();
    }

    public ImageOptimizer(File file) throws IOException {
        img = ImageIO.read(file);
    }

    /**
     * Constructor for taking a BufferedImage
     * @param img
     */
    private ImageOptimizer(BufferedImage img) {
        this.img = img;
    }

    public <T extends ImageOptimizer> T update(Function<BufferedImage, BufferedImage> function) {
        this.img = function.apply(this.img);
        return (T) this;
    }

    /**
     * @return Width of the image in pixels
     */
    public int getWidth() {
        return img.getWidth();
    }

    /**
     * @return Height of the image in pixels
     */
    public int getHeight() {
        return img.getHeight();
    }

    /**
     * @return Aspect ratio of the image (width / height)
     */
    public double getAspectRatio() {
        return (double)getWidth() / (double)getHeight();
    }

    public <T extends ImageOptimizer> T getResised(int width, int height) {
        MultiStepRescaleOp rescale = new MultiStepRescaleOp(width, height);
        rescale.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Soft);
        BufferedImage resizedImage = rescale.filter(img, null);

        this.dispose();
        this.img = resizedImage;

        return (T) this;
    }

    public <T extends ImageOptimizer> T getResizedToHeight(int height) {
        int nWidth = height * img.getWidth() / img.getHeight();

        return getResised(nWidth, height);
    }

    /**
     * Generate a new Image object resized to a specific width, maintaining
     * the same aspect ratio of the original
     * @param width
     * @return Image scaled to new width
     */
    public <T extends ImageOptimizer> T getResizedToWidth(int width) {
        int nHeight = width * img.getHeight() / img.getWidth();

        return getResised(width, nHeight);
    }

    /**
     * Generate a new Image object cropped to a new size
     * @param x1 Starting x-axis position for crop area
     * @param y1 Starting y-axis position for crop area
     * @param x2 Ending x-axis position for crop area
     * @param y2 Ending y-axis position for crop area
     * @return Image cropped to new dimensions
     */
    public <T extends ImageOptimizer> T crop(int x1, int y1, int x2, int y2) {
        if (x1 < 0 || x2 <= x1 || y1 < 0 || y2 <= y1 || x2 > getWidth() || y2 > getHeight())
            throw new IllegalArgumentException("invalid crop coordinates");

        int type = img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType();
        int nNewWidth = x2 - x1;
        int nNewHeight = y2 - y1;
        BufferedImage cropped = new BufferedImage(nNewWidth, nNewHeight, type);
        Graphics2D g = cropped.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setComposite(AlphaComposite.Src);

        g.drawImage(img, 0, 0, nNewWidth, nNewHeight, x1, y1, x2, y2, null);
        g.dispose();

        this.dispose();
        img = cropped;

        return (T) this;
    }

    /**
     * Useful function to crop and resize an image to a square.
     * This is handy for thumbnail generation.
     * @param width Width of the resulting square
     * @param cropEdgesPct Specifies how much of an edge all around the square to crop,
     * which creates a zoom-in effect on the center of the resulting square. This may
     * be useful, given that when images are reduced to thumbnails, the detail of the
     * focus of the image is reduced.  Specifying a value such as 0.1 may help preserve
     * this detail. You should experiment with it. The value must be between 0 and 0.5
     * (representing 0% to 50%)
     * @return Image cropped and resized to a square
     */
    public <T extends ImageOptimizer> T getResizedToSquare(int width, double cropEdgesPct) {
        if (cropEdgesPct < 0 || cropEdgesPct > 0.5)
            throw new IllegalArgumentException("Crop edges pct must be between 0 and 0.5. "+ cropEdgesPct +" was supplied.");

        //crop to square first. determine the coordinates.
        int cropMargin = (int)Math.abs(Math.round(((img.getWidth() - img.getHeight()) / 2.0)));
        int x1 = 0;
        int y1 = 0;
        int x2 = getWidth();
        int y2 = getHeight();
        if (getWidth() > getHeight()) {
            x1 = cropMargin;
            x2 = x1 + y2;
        }
        else {
            y1 = cropMargin;
            y2 = y1 + x2;
        }

        //should there be any edge cropping?
        if (cropEdgesPct != 0) {
            int cropEdgeAmt = (int)((x2 - x1) * cropEdgesPct);
            x1 += cropEdgeAmt;
            x2 -= cropEdgeAmt;
            y1 += cropEdgeAmt;
            y2 -= cropEdgeAmt;
        }

        // generate the image cropped to a square
        T cropped = crop(x1, y1, x2, y2);

        // now resize. we do crop first then resize to preserve detail
        T resized = cropped.getResizedToWidth(width);
        cropped.dispose();

        return (T) resized;
    }

    /**
     * Soften the image to reduce pixelation. Helps Images look better after resizing.
     * @param softenFactor Strength of softening. 0.08 is a good value
     * @return New Image object post-softening, unless softenFactor == 0, in which
     * case the same object is returned
     */
    public <T extends ImageOptimizer> T soften(float softenFactor)
    {
        if (softenFactor > 0f)
        {
            float[] softenArray = {0, softenFactor, 0, softenFactor, 1-(softenFactor*4), softenFactor, 0, softenFactor, 0};
            Kernel kernel = new Kernel(3, 3, softenArray);
            ConvolveOp cOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            BufferedImage newImg = cOp.filter(this.img, null);
            this.dispose();
            this.img = newImg;
        }

        return (T) this;
    }

    /**
     * Converts the source to 1-bit colour depth (monochrome). No transparency.
     *
     * @return a copy of the source image with a 1-bit colour depth.
     */
    public <T extends ImageOptimizer> T convert1() {
        IndexColorModel icm = new IndexColorModel(1, 2, new byte[] { (byte) 0,
                (byte) 0xFF }, new byte[] { (byte) 0, (byte) 0xFF },
                new byte[] { (byte) 0, (byte) 0xFF });

        BufferedImage dest = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_BYTE_BINARY, icm);

        ColorConvertOp cco = new ColorConvertOp(img.getColorModel()
                .getColorSpace(), dest.getColorModel().getColorSpace(), null);

        cco.filter(img, dest);
        this.dispose();
        this.img = dest;

        return (T) this;
    }

    /**
     * Converts the source image to 4-bit colour using the default 16-colour
     * palette:
     * <ul>
     * <li>black</li>
     * <li>dark red</li>
     * <li>dark green</li>
     * <li>dark yellow</li>
     * <li>dark blue</li>
     * <li>dark magenta</li>
     * <li>dark cyan</li>
     * <li>dark grey</li>
     * <li>light grey</li>
     * <li>red</li>
     * <li>green</li>
     * <li>yellow</li>
     * <li>blue</li>
     * <li>magenta</li>
     * <li>cyan</li>
     * <li>white</li>
     * </ul>
     * No transparency.
     *
     * @return a copy of the source image with a 4-bit colour depth, with the
     *         default colour pallette
     */
    public  <T extends ImageOptimizer> T convert4() {
        int[] cmap = new int[] { 0x000000, 0x800000, 0x008000, 0x808000,
                0x000080, 0x800080, 0x008080, 0x808080, 0xC0C0C0, 0xFF0000,
                0x00FF00, 0xFFFF00, 0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF };
        return (T)convert4(cmap);
    }

    /**
     * Converts the source image to 4-bit colour using the given colour map. No
     * transparency.
     *
     * @param cmap
     *            the colour map, which should contain no more than 16 entries
     *            The entries are in the form RRGGBB (hex).
     * @return a copy of the source image with a 4-bit colour depth, with the
     *         custom colour pallette
     */
    public <T extends ImageOptimizer> T convert4(int[] cmap) {
        IndexColorModel icm = new IndexColorModel(4, cmap.length, cmap, 0,
                false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        BufferedImage dest = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_BYTE_BINARY, icm);
        ColorConvertOp cco = new ColorConvertOp(img.getColorModel()
                .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(img, dest);

        this.dispose();
        this.img = dest;

        return (T) this;
    }

    /**
     * Converts the source image to 8-bit colour using the default 256-colour
     * palette. No transparency.
     *
     * @return a copy of the source image with an 8-bit colour depth
     */
    public <T extends ImageOptimizer> T  convert8() {
        BufferedImage dest = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_BYTE_INDEXED);
        ColorConvertOp cco = new ColorConvertOp(img.getColorModel()
                .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(img, dest);

        this.dispose();
        this.img = dest;

        return (T) this;
    }

    /**
     * Converts the source image to 24-bit colour (RGB). No transparency.
     *
     * @return a copy of the source image with a 24-bit colour depth
     */
    public <T extends ImageOptimizer> T convert24() {
        BufferedImage dest = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_INT_RGB);
        ColorConvertOp cco = new ColorConvertOp(img.getColorModel()
                .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(img, dest);

        this.dispose();
        this.img = dest;

        return (T) this;
    }

    /**
     * Converts the source image to 32-bit colour with transparency (ARGB).
     *
     * @return a copy of the source image with a 32-bit colour depth.
     */
    public <T extends ImageOptimizer> T convert32() {
        BufferedImage dest = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        ColorConvertOp cco = new ColorConvertOp(img.getColorModel()
                .getColorSpace(), dest.getColorModel().getColorSpace(), null);
        cco.filter(img, dest);

        this.dispose();
        this.img = dest;

        return (T) this;
    }

    /**
     * Write image to a file, specify image type
     * This method will overwrite a file that exists with the same name
     * @see #getWriterFormatNames()
     * @param file File to saveForWeb image to
     * @param type jpg, gif, etc.
     * @throws IOException
     */
    public void write(File file, String type) throws IOException {
        if (file == null)
            throw new IllegalArgumentException("File argument was null");
        ImageIO.write(img, type, file);
    }

    /**
     * @return Array of supported image types for writing to file
     */
    public String[] getWriterFormatNames() {
        return ImageIO.getWriterFormatNames();
    }

    /**
     * Free up resources associated with this image
     */
    public void dispose() {
        img.flush();
    }

}
