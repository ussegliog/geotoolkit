/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2006-2011, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2009-2011, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotoolkit.image;

import java.awt.Image;
import java.awt.image.*;
import java.awt.Transparency;
import java.awt.RenderingHints;
import java.awt.HeadlessException;
import java.awt.color.ColorSpace;
import java.lang.reflect.InvocationTargetException;

import javax.media.jai.*;
import javax.media.jai.operator.*;
import com.sun.media.jai.util.ImageUtil;

import org.opengis.coverage.PaletteInterpretation;

import org.geotoolkit.factory.Hints;
import org.geotoolkit.internal.image.ColorModels;
import org.geotoolkit.internal.image.ImageUtilities;
import org.geotoolkit.internal.image.ColorUtilities;
import org.geotoolkit.util.collection.XCollections;

import static java.awt.image.DataBuffer.TYPE_BYTE;
import static org.geotoolkit.util.ArgumentChecks.ensureNonNull;


/**
 * Provides convenience methods for describing an image. This base class is "read only" in that
 * it doesn't provide any operation that may change the pixel values. However subclasses like
 * {@link ImageWorker} may applied JAI operations on the image.
 *
 * @author Martin Desruisseaux (Geomatys)
 * @author Simone Giannecchini (Geosolutions)
 * @author Bryce Nordgren
 * @version 3.00
 *
 * @since 2.3
 * @module
 */
public class ImageInspector {
    /**
     * An additional {@code PaletteInterpretation} code representing the IHS (Intensity, Hue,
     * Saturation) color space. This color space is also known as HSI or HIS and is implemented
     * in <cite>Java Advanced Imaging</cite> by the {@link IHSColorSpace} class.
     *
     * @since 3.00
     */
    public static final PaletteInterpretation IHS = PaletteInterpretation.valueOf("IHS");

    /**
     * If {@link Boolean#TRUE TRUE}, the RGB values of fully transparent pixels will not be taken
     * in account when deciding if an Index Color Model is gray scale. Fully transparent pixels
     * are the ones having an {@linkplain IndexColorModel#getAlpha(int) alpha} value of 0.
     * <p>
     * The default value is {@link Boolean#TRUE}.
     *
     * @see #isGrayScale
     * @see #setRenderingHint
     *
     * @since 3.00
     */
    public static final Hints.Key IGNORE_FULLY_TRANSPARENT_PIXELS = new Hints.Key(Boolean.class);

    /**
     * The image property name generated by {@link ExtremaDescriptor}.
     */
    private static final String EXTREMA = "extrema";

    /**
     * The image specified by the user at construction time, or last time
     * {@link #invalidateStatistics} were invoked. The {@link #getComputedProperty}
     * method will not search a property pass this point.
     */
    private RenderedImage inheritanceStopPoint;

    /**
     * The image on which operation are appiled. Subclasses may replace the value of this
     * field by the operation results every time a new operation is applied.
     */
    protected RenderedImage image;

    /**
     * The region of interest, or {@code null} if none.
     */
    private ROI roi;

    /**
     * The rendering hints to provide to all image operators. Additional hints may
     * be set (in a separated {@link RenderingHints} object) for particular images.
     */
    private RenderingHints commonHints;

    /**
     * Non-zeros if {@link ImageWorker} is performing an intermediate computation step.
     * This counter is incremented everytime {@code enableTileCache(false)} is invoked,
     * and decremented every time {@code enableTileCache(true)} is invoked.
     */
    private int tileCacheDisabled;

    /**
     * An tile cache that performs do caching. Created only when needed.
     */
    private transient TileCache nullCache;

    /**
     * Creates a new {@code ImageInspector} for the specified image.
     *
     * @param image The source image.
     */
    public ImageInspector(final RenderedImage image) {
        ensureNonNull("image", image);
        inheritanceStopPoint = this.image = image;
    }

    /**
     * Creates a new image descriptor initialized to the same image and hints than the given
     * descriptor.
     */
    ImageInspector(final ImageInspector base) {
        this(base.image);
        if (!XCollections.isNullOrEmpty(base.commonHints)) {
            commonHints = new RenderingHints(null);
            commonHints.add(base.commonHints);
        }
    }

    /**
     * Sets the image on which future operations will be applied.
     *
     * @param image The new source image.
     */
    public void setImage(final RenderedImage image) {
        ensureNonNull("image", image);
        inheritanceStopPoint = this.image = image;
    }

    /**
     * Returns the current {@linkplain #image}.
     *
     * @return The rendered image.
     *
     * @see #getBufferedImage
     * @see #getPlanarImage
     * @see #getRenderedOperation
     * @see #getImageAsROI
     */
    public RenderedImage getRenderedImage() {
        return image;
    }

    /**
     * Returns the current {@linkplain #image} as a buffered image.
     *
     * @return The buffered image.
     *
     * @see #getRenderedImage
     * @see #getPlanarImage
     * @see #getRenderedOperation
     * @see #getImageAsROI
     *
     * @since 2.5
     */
    public BufferedImage getBufferedImage() {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        } else {
            return getPlanarImage().getAsBufferedImage();
        }
    }

    /**
     * Returns the {@linkplain #getRenderedImage rendered image} as a planar image.
     *
     * @return The planar image.
     *
     * @see #getRenderedImage
     * @see #getRenderedOperation
     * @see #getImageAsROI
     */
    public PlanarImage getPlanarImage() {
        return PlanarImage.wrapRenderedImage(getRenderedImage());
    }

    /**
     * Returns the {@linkplain #getRenderedImage rendered image} as a rendered operation.
     *
     * @return The rendered operation.
     *
     * @see #getRenderedImage
     * @see #getPlanarImage
     * @see #getImageAsROI
     */
    public RenderedOp getRenderedOperation() {
        final RenderedImage image = getRenderedImage();
        if (image instanceof RenderedOp) {
            return (RenderedOp) image;
        }
        return NullDescriptor.create(image, getRenderingHints());
    }

    /**
     * Returns a {@linkplain ROI Region Of Interest} built from a
     * {@linkplain ImageWorker#binarize(boolean) binarized} version of the current
     * {@linkplain #getRenderedImage rendered image}.
     *
     * @return The image as a Region Of Interest.
     *
     * @see #getRenderedImage
     * @see #getPlanarImage
     * @see #getRenderedOperation
     */
    public ROI getImageAsROI() {
        final ImageWorker worker = new ImageWorker(this);
        worker.binarize(true);
        return new ROI(worker.getRenderedImage());
    }

    /**
     * Returns the <cite>Region Of Interest</cite> currently set, or {@code null} if none.
     * The ROI applies to statistical methods like {@link #getMinimums} and {@link #getMaximums}.
     * The default value is {@code null}, which means that statistics are computed on the whole
     * image.
     *
     * @return The current Region Of Interest.
     *
     * @see #getMinimums
     * @see #getMaximums
     */
    public ROI getROI() {
        return roi;
    }

    /**
     * Sets the <cite>Region Of Interest</cite> (ROI). A {@code null} value sets the ROI to the
     * whole {@linkplain #image}. The ROI applies to statistical methods like {@link #getMinimums}
     * and {@link #getMaximums}.
     *
     * @param roi The new Region Of Interest.
     *
     * @see #getMinimums
     * @see #getMaximums
     */
    public void setROI(final ROI roi) {
        this.roi = roi;
        invalidateStatistics();
    }

    /**
     * Returns the rendering hints for an image to be computed by subclasses.
     * The default implementation returns the following hints:
     * <p>
     * <ul>
     *   <li>An {@linkplain ImageLayout image layout} with tiles size computed automatically
     *       from the current {@linkplain #image} size, unless an image layout was explicitly
     *       set as explained in the item below.</li>
     *   <li>Any additional hints specified through the {@link #setRenderingHint} method. If the
     *       user provided explicitly a {@link JAI#KEY_IMAGE_LAYOUT}, then the user layout has
     *       precedence over the automatic layout computed in the previous item.</li>
     * </ul>
     *
     * @return The rendering hints to use for image computation (never {@code null}).
     */
    public RenderingHints getRenderingHints() {
        RenderingHints hints = ImageUtilities.getRenderingHints(image);
        if (hints == null) {
            hints = new RenderingHints(null);
            if (commonHints != null) {
                hints.add(commonHints);
            }
        } else if (commonHints != null) {
            hints.add(commonHints);
        }
        if (Boolean.FALSE.equals(hints.get(ImageWorker.TILING_ALLOWED))) {
            final ImageLayout layout = getImageLayout(hints);
            if (commonHints == null || layout != commonHints.get(JAI.KEY_IMAGE_LAYOUT)) {
                /*
                 * Setq the layout only if it is not a user-supplied object. We don't invoke
                 * modifiable(...) here because we don't want to write anything if the layout
                 * was specified by the user.
                 */
                layout.setTileWidth      (image.getWidth());
                layout.setTileHeight     (image.getHeight());
                layout.setTileGridXOffset(image.getMinX());
                layout.setTileGridYOffset(image.getMinY());
                hints.put(JAI.KEY_IMAGE_LAYOUT, layout);
            }
        }
        if (tileCacheDisabled != 0) {
            if (nullCache == null) {
                nullCache = JAI.createTileCache(0);
            }
            hints.put(JAI.KEY_TILE_CACHE, nullCache);
        }
        return hints;
    }

    /**
     * Returns the {@linkplain #getRenderingHints rendering hints}, but with a
     * {@linkplain ComponentColorModel component color model} of the specified
     * data type. The data type is changed only if no color model was explicitly
     * specified by the user through {@link #getRenderingHints()}.
     *
     * @param type The data type (typically {@link DataBuffer#TYPE_BYTE}).
     */
    final RenderingHints getRenderingHints(final int type) {
        /*
         * Gets the default hints, which usually contains only informations about tiling.
         * If the user overridden the rendering hints with an explict color model, keep
         * the user's choice.
         */
        final RenderingHints hints = getRenderingHints();
        ImageLayout layout = getImageLayout(hints);
        if (layout.isValid(ImageLayout.COLOR_MODEL_MASK)) {
            return hints;
        }
        final ColorModel oldCM = image.getColorModel();
        setColorModel(hints, new ComponentColorModel(
                oldCM.getColorSpace(),
                oldCM.hasAlpha(),               // If true, supports transparency.
                oldCM.isAlphaPremultiplied(),   // If true, alpha is premultiplied.
                oldCM.getTransparency(),        // What alpha values can be represented.
                type));                         // Type of primitive array used to represent pixel.
        return hints;
    }

    /**
     * Sets the {@link ColorModel} and a compatible {@link SampleModel} as an {@link ImageLayout}
     * in the given hints. Note: this method canonicalize the given color model using
     * {@code ColorModels.unique(cm)}, so there is no need to perform this step manually
     * before to invoke this method.
     *
     * @param hints The hints where to store the modified {@link ImageLayout}.
     * @param cm The color model to set in the given {@code hints}, or {@code null} to unset.
     * @return The unique instance of then given color model.
     */
    final ColorModel setColorModel(final RenderingHints hints, ColorModel cm) {
        final ImageLayout layout = modifiable(getImageLayout(hints), hints);
        if (cm != null) {
            cm = ColorModels.unique(cm);
            final SampleModel sm = cm.createCompatibleSampleModel(image.getWidth(), image.getHeight());
            layout.setColorModel(cm);
            layout.setSampleModel(sm);
        } else {
            layout.unsetValid(ImageLayout.COLOR_MODEL_MASK | ImageLayout.SAMPLE_MODEL_MASK);
        }
        return cm;
    }

    /**
     * Gets the image layout from the specified rendering hints, creating a new one if needed.
     * This method does not modify the specified hints. If the caller want to modify the image
     * layout, then it must invoke {@link #modifiable} before doing so.
     */
    static ImageLayout getImageLayout(final RenderingHints hints) {
        final Object candidate = hints.get(JAI.KEY_IMAGE_LAYOUT);
        if (candidate instanceof ImageLayout) {
            return (ImageLayout) candidate;
        }
        return new ImageLayout();
    }

    /**
     * Must be invoked after {@link #getImageLayout} if the caller intend to modify the layout.
     *
     * @param  layout The layout returned by {@code getImageLayout}.
     * @param  hints The rendering hints that were specified to {@code getImageLayout}.
     * @return The new image layout to use. May be a clone of the layout given in argument.
     */
    final ImageLayout modifiable(ImageLayout layout, final RenderingHints hints) {
        if (commonHints != null && layout == commonHints.get(JAI.KEY_IMAGE_LAYOUT)) {
            layout = (ImageLayout) layout.clone();
        }
        // Following put is inconditional because the layout may have
        // been created by new ImageLayout() in the above method.
        hints.put(JAI.KEY_IMAGE_LAYOUT, layout);
        return layout;
    }

    /**
     * Returns the rendering hint for the specified key, or {@code null} if none.
     * Newly created image worker have no rendering hints.
     *
     * @param key The key for which to get the rendering hint.
     * @return The rendering hint for the given key.
     */
    public Object getRenderingHint(final RenderingHints.Key key) {
        ensureNonNull("key", key);
        return (commonHints != null) ? commonHints.get(key) : null;
    }

    /**
     * Sets a rendering hint to use for all images to be computed by subclasses.
     * This method applies only to the next images to be computed. Images already
     * computed before this method call (if any) will not be affected.
     * <p>
     * Some common examples:
     * <p>
     * <ul>
     *   <li><code>setRenderingHint({@linkplain JAI#KEY_TILE_CACHE}, null)</code>
     *       disables completely the tile cache.</li>
     *   <li><code>setRenderingHint({@linkplain ImageWorker#TILING_ALLOWED}, Boolean.FALSE)</code>
     *       forces all operators to produce untiled images.</li>
     * </ul>
     *
     * @param key The key for which to set a rendering hint.
     * @param value The value to assign to the given key.
     */
    public void setRenderingHint(final RenderingHints.Key key, final Object value) {
        ensureNonNull("key", key);
        if (commonHints == null) {
            commonHints = new RenderingHints(null);
        }
        commonHints.add(new RenderingHints(key, value));
    }

    /**
     * Removes a rendering hint. Note that invoking this method is <strong>not</strong> the same
     * than invoking {@link #setRenderingHint setRenderingHint(key, null)}. This is especially
     * true for the {@linkplain javax.media.jai.TileCache tile cache} hint:
     * <p>
     * <ul>
     *   <li>{@code setRenderingHint(JAI.KEY_TILE_CACHE, null)} disables the use of any tile cache.
     *       In other words, this method call do request a tile cache, which happen to be the "null"
     *       cache.</li>
     *
     *   <li>{@code removeRenderingHint(JAI.KEY_TILE_CACHE)} unsets any tile cache specified by a
     *       previous rendering hint. All images to be computed after this method call will save
     *       their tiles in the {@linkplain JAI#getTileCache JAI default tile cache}.</li>
     * </ul>
     *
     * @param key The key for which to remove the rendering hint.
     */
    public void removeRenderingHint(final RenderingHints.Key key) {
        ensureNonNull("key", key);
        if (commonHints != null) {
            commonHints.remove(key);
        }
    }

    /**
     * Returns the number of bands in the {@linkplain #image}.
     *
     * @return The number of bands in the image.
     *
     * @see ImageWorker#retainBands
     * @see SampleModel#getNumBands
     */
    public int getNumBands() {
        return image.getSampleModel().getNumBands();
    }

    /**
     * Gets a property from the properties of the {@linkplain #image}. If the property name
     * is not recognized, then {@link Image#UndefinedProperty} will be returned. This method
     * does <strong>not</strong> inherits properties from the image specified at
     * {@linkplain #ImageWorker(RenderedImage) construction time} - only properties generated
     * by this class are returned.
     */
    private Object getComputedProperty(final String name) {
        final Object value = image.getProperty(name);
        return (value == inheritanceStopPoint.getProperty(name)) ? Image.UndefinedProperty : value;
    }

    /**
     * Returns the minimums and maximums values found in the image. Those extremas are
     * returned as an array of the form {@code double[2][#bands]}.
     */
    final double[][] getExtremas() {
        Object extrema = getComputedProperty(EXTREMA);
        if (!(extrema instanceof double[][])) {
            final Integer ONE = 1;
            image = ExtremaDescriptor.create(
                    image,  // The source image.
                    roi,    // The region of the image to scan. Default to all.
                    ONE,    // The horizontal sampling rate. Default to 1.
                    ONE,    // The vertical sampling rate. Default to 1.
                    null,   // Whether to store extrema locations. Default to false.
                    ONE,    // Maximum number of run length codes to store. Default to 1.
                    getRenderingHints());
            extrema = getComputedProperty(EXTREMA);
        }
        return (double[][]) extrema;
    }

    /**
     * Tells this builder that all statistics on pixel values (e.g. the "extrema" property
     * in the {@linkplain #image}) should not be inherited from the source images (if any).
     * This method should be invoked every time an operation changed the pixel values.
     */
    final void invalidateStatistics() {
        inheritanceStopPoint = image;
    }

    /**
     * Returns the minimal values found in every {@linkplain #image} bands. If a
     * {@linkplain #getROI Region Of Interest} is defined, then the statistics
     * will be computed only over that region.
     *
     * @return The minimal values found in all bands. This is a direct reference to the
     *         array stored in {@linkplain RenderedImage#getProperty image properties},
     *         not a clone.
     *
     * @see #getMaximums
     * @see #setROI
     */
    public double[] getMinimums() {
        return getExtremas()[0];
    }

    /**
     * Returns the maximal values found in every {@linkplain #image} bands. If a
     * {@linkplain #getROI Region Of Interest} is defined, then the statistics
     * will be computed only over that region.
     *
     * @return The maximal values found in all bands. This is a direct reference to the
     *         array stored in {@linkplain RenderedImage#getProperty image properties},
     *         not a clone.
     *
     * @see #getMinimums
     * @see #setROI
     */
    public double[] getMaximums() {
        return getExtremas()[1];
    }

    /**
     * Returns the transparent pixel value, or -1 if none.
     *
     * @return The transparent pixel value, or -1 if none.
     */
    public int getTransparentPixel() {
        final ColorModel cm = image.getColorModel();
        return (cm instanceof IndexColorModel) ? ((IndexColorModel) cm).getTransparentPixel() : -1;
    }

    /**
     * Returns {@code true} if the {@linkplain #image} is
     * {@linkplain Transparency#TRANSLUCENT translucent}.
     *
     * @return {@code true} if the image is translucent.
     *
     * @see ImageWorker#forceBitmaskIndexColorModel
     */
    public boolean isTranslucent() {
        return image.getColorModel().getTransparency() == Transparency.TRANSLUCENT;
    }

    /**
     * Returns {@code true} if the {@linkplain #image} is tiled.
     *
     * @return {@code true} if the {@linkplain #image} is tiled.
     *
     * @see ImageWorker#tile
     *
     * @since 3.00
     */
    public boolean isTiled() {
        return image.getNumXTiles() != 1 || image.getNumYTiles() != 1;
    }

    /**
     * Returns {@code true} if the {@linkplain #image} stores its pixel values in bytes.
     * If {@code true}, then each sample values use at most 8 bits. However they may use
     * less than 8 bits. For example a binary image stores 8 pixels by byte.
     *
     * @return {@code true} if the image stores pixel values as bytes.
     *
     * @see ImageWorker#format
     */
    public boolean isBytes() {
        return image.getSampleModel().getDataType() == TYPE_BYTE;
    }

    /**
     * Returns {@code true} if the {@linkplain #image} is binary. An image is binary if it has
     * only one band and uses only one bit per pixel. Such image can contains only two values:
     * 0 and 1.
     *
     * @return {@code true} if the image is binary.
     *
     * @see ImageWorker#binarize(boolean)
     */
    public boolean isBinary() {
        return ImageUtil.isBinary(image.getSampleModel());
    }

    /**
     * Returns {@code true} if the {@linkplain #image} uses an
     * {@linkplain IndexColorModel Index Color Model}.
     *
     * @return {@code true} if the image is indexed.
     *
     * @see ImageWorker#setColorModelType
     */
    public boolean isIndexed() {
        return image.getColorModel() instanceof IndexColorModel;
    }

    /**
     * Returns {@code true} if the {@linkplain #image} uses gray scale. This method returns
     * {@code true} if one of the following conditions is true:
     * <p>
     * <ul>
     *   <li>The {@linkplain ColorSpace Color Space} is of
     *       {@linkplain ColorSpace#TYPE_GRAY type gray}</li>
     *   <li>The Color Model is an instance of {@link IndexColorModel} and the color map contains
     *       identical values for the Red, Green and Blue components. The color of fully transparent
     *       pixels is ignored by default, but this can be changed by assigning the value {@code FALSE}
     *       to the {@link #IGNORE_FULLY_TRANSPARENT_PIXELS} rendering hint.</li>
     * </ul>
     *
     *
     * {@section Relationship with Color Space type}
     *
     * If this method returns {@code false}, then it is guaranteed that {@link #getColorSpaceType}
     * will not return {@link PaletteInterpretation#GRAY}. However the converse is not necessarily
     * true. See the <cite>Index Color Model</cite> section in {@code getColorSpaceType()}.
     *
     * @return {@code true} if the {@linkplain #image} uses gray scale.
     *
     * @see #IGNORE_FULLY_TRANSPARENT_PIXELS
     *
     * @since 3.00
     */
    public boolean isGrayScale() {
        final ColorModel cm = image.getColorModel();
        if (cm != null) {
            if (cm instanceof IndexColorModel) {
                Boolean ignoreTransparents = null;
                if (commonHints != null) {
                    ignoreTransparents = (Boolean) commonHints.get(IGNORE_FULLY_TRANSPARENT_PIXELS);
                }
                if (ignoreTransparents == null) {
                    ignoreTransparents = Boolean.TRUE;
                }
                if (ColorUtilities.isGrayPalette((IndexColorModel) cm, ignoreTransparents)) {
                    return true;
                }
            }
            final ColorSpace cs = cm.getColorSpace();
            if (cs != null) {
                return cs.getType() == ColorSpace.TYPE_GRAY;
            }
        }
        return false;
    }

    /**
     * Returns the type of the {@linkplain ColorSpace Color Space} used by the {@linkplain #image}.
     * If the Color Space is known to this method, then it returns one of the constants defined
     * in the {@link PaletteInterpretation} code list, or the {@link #IHS} constant. Otherwise
     * this methor returns {@code null}.
     *
     *
     * {@section Index Color Model}
     *
     * RGB Color Space doesn't mean that pixel values are directly stored as RGB components.
     * The main canvat is {@link IndexColorModel}, which has RGB color space despite the fact
     * that images using such Color Model have only one band. In addition the color map in an
     * {@code IndexColorModel} may contains only gray colors - this method will <strong>not</strong>
     * returns the {@link PaletteInterpretation#GRAY} in this case since the Color Space still
     * of {@linkplain ColorSpace#TYPE_RGB type RGB} in the Java2D sense. For detecting such
     * {@code IndexColorModel} having only gray colors, use {@link #isGrayScale} instead.
     *
     * @return The palette interpretation inferred from the Color Space of the current image,
     *         or {@code null} if unnkown.
     *
     * @see ImageWorker#setColorSpaceType
     *
     * @since 3.00
     */
    public PaletteInterpretation getColorSpaceType() {
        final ColorModel cm = image.getColorModel();
        if (cm != null) {
            final ColorSpace cs = cm.getColorSpace();
            if (cs != null) {
                switch (cs.getType()) {
                    case ColorSpace.TYPE_GRAY: return PaletteInterpretation.GRAY;
                    case ColorSpace.TYPE_RGB:  return PaletteInterpretation.RGB;
                    case ColorSpace.TYPE_CMYK: return PaletteInterpretation.CMYK;
                    case ColorSpace.TYPE_HLS:  return PaletteInterpretation.HLS;
                    case ColorSpace.TYPE_HSV: {
                        if (cs instanceof IHSColorSpace) {
                            return IHS;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Shows the current {@linkplain #image} in a window together with the operation chain as a
     * {@linkplain javax.swing.JTree tree}. This method is provided mostly for debugging purpose.
     * This method requires the {@code geotk-widgets.jar} file in the classpath.
     *
     * @throws HeadlessException if {@code geotk-widgets.jar} is not on the classpath, or
     *         if AWT can't create the window components.
     *
     * @see org.geotoolkit.gui.swing.image.OperationTreeBrowser#show(RenderedImage)
     */
    public void show() throws HeadlessException {
        /*
         * Uses reflection because the "gt2-widgets-swing.jar" dependency is optional and may not
         * be available in the classpath. All the complicated stuff below is simply doing this call:
         *
         *     OperationTreeBrowser.show(image);
         *
         * Tip: The @see tag in the above javadoc can be used as a check for the existence
         *      of class and method referenced below. Check for the javadoc warnings.
         */
        final Class<?> c;
        try {
            c = Class.forName("org.geotoolkit.gui.swing.image.OperationTreeBrowser");
        } catch (ClassNotFoundException cause) {
            final HeadlessException e;
            e = new HeadlessException("The \"geotk-widgets-swing.jar\" file is required.");
            e.initCause(cause);
            throw e;
        }
        try {
            c.getMethod("show", new Class<?>[] {RenderedImage.class}).invoke(null, new Object[] {image});
        } catch (InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new AssertionError(e);
        } catch (Exception e) {
            /*
             * ClassNotFoundException may be expected, but all other kinds of
             * checked exceptions (and they are numerous...) are errors.
             */
            throw new AssertionError(e);
        }
    }

    /**
     * If {@code false}, disables the tile cache. Invoking this method with value {@code true}
     * cancel the last invocation with value {@code false}. If this method has been invoked many
     * time with value {@code false}, then this method must be invoked the same amount of time
     * with the value {@code true} for reenabling the cache.
     *
     * {@note This method name doesn't contain the usual <code>set</code> prefix because it
     *        doesn't really set a flag. Instead it increments or decrements a counter.}
     *
     * @param status {@code true} for enabling the tile cache, or {@code false} for disabling it.
     */
    final void enableTileCache(final boolean status) {
        if (status) {
            if (tileCacheDisabled != 0) {
                tileCacheDisabled--;
            } else {
                throw new IllegalStateException();
            }
        } else {
            tileCacheDisabled++;
        }
    }
}
