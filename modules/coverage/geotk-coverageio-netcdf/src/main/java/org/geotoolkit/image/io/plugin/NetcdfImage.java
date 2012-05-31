/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2012, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2012, Geomatys
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
package org.geotoolkit.image.io.plugin;

import java.util.List;
import java.util.Arrays;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.IIOParam;
import javax.imageio.IIOException;
import javax.imageio.ImageWriter;
import java.awt.image.DataBuffer;
import javax.media.jai.iterator.RectIter;
import javax.measure.unit.Unit;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Variable;
import ucar.nc2.Dimension;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.constants.CDM;
import ucar.nc2.iosp.netcdf3.N3iosp;

import org.opengis.metadata.content.ImageDescription;
import org.opengis.metadata.content.RangeDimension;

import org.geotoolkit.util.collection.XCollections;
import org.geotoolkit.image.io.DimensionSlice;
import org.geotoolkit.image.io.ImageMetadataException;
import org.geotoolkit.image.io.metadata.SampleDimension;
import org.geotoolkit.internal.image.io.IIOImageHelper;
import org.geotoolkit.resources.Errors;

import static org.geotoolkit.internal.image.io.NetcdfVariable.*;
import static org.geotoolkit.image.io.MultidimensionalImageStore.*;


/**
 * Holds the information about an image to be written in a NetCDF file.
 *
 * @author Johann Sorel (Geomatys)
 * @author Martin Desruisseaux (Geomatys)
 * @version 3.20
 *
 * @since 3.20
 * @module
 */
final class NetcdfImage extends IIOImageHelper {
    /**
     * Approximative size (in number of primitive elements) of the buffer to use when writing
     * NetCDF variable values. The size of the buffer actually used may be different, either
     * smaller or larger than this size.
     */
    private static final int BUFFER_SIZE = 4096;

    /**
     * The dimensions associated with the NetCDF image.
     * The array length is the number of coordinate system axis.
     */
    private final NetcdfDimension[] dimensions;

    /**
     * The dimension associated to bands, or -1 if none. If no dimension is associated
     * to bands, then each bands will be written as a separated variable.
     */
    private final int bandDimension;

    /**
     * The variables where to write the pixel values. The length of this array must be equals
     * to either 1, or to the number of source bands in the image to write. More specifically,
     * the {@linkplain #dimensions} and {@linkplain #variables} must be related by exactly one,
     * and only one, of the following rules:
     * <p>
     * <ul>
     *   <li>If {@link #bandDimension} is positive, then {@code variables} contains only one
     *   element. The bands are typically the third dimension in the variable (the actual
     *   dimension is identified by {@code bandDimension}).</p></li>
     *
     *   <li>If {@link #bandDimension} is negative, then {@code variables} contains one or more
     *   elements. The length of this array is the number of bands.</li>
     * </ul>
     * <p>
     * Every elements in the array shall have the same shape. For each element, the
     * {@linkplain Variable#getRank() rank} is equals to the {@link #dimensions} array length.
     * For each <var>i</var> in the range [0…rank-1], the {@code variable.getShape()[i]} value
     * shall be equals to {@code dimensions[i].getLength()}.
     */
    private final Variable[] variables;

    /**
     * The iterator to use for writing the image sample values.
     */
    private transient RectIter iterator;

    /**
     * Creates a new object for the given image to write.
     *
     * @param writer The writer which is preparing the image to write, or {@code null} if unknown.
     * @param image  The image or raster to be read or written.
     * @param param  The parameters that control the writing process, or {@code null} if none.
     * @param allDimensions All dimensions created by previous invocations of this constructor.
     *        The constructor will add new dimensions in this array if needed.
     */
    NetcdfImage(final ImageWriter writer, final IIOImage image, final IIOParam parameters,
            final List<NetcdfDimension> allDimensions) throws IIOException
    {
        super(writer, image, parameters);
        int bandDimension = -1;
        dimensions = new NetcdfDimension[getCoordinateSystem().getDimension()];
        for (int i=0; i<dimensions.length; i++) {
            NetcdfDimension dimension = new NetcdfDimension(this, i);
            final int existing = allDimensions.indexOf(dimension);
            if (existing >= 0) {
                dimension = allDimensions.get(existing);
            } else {
                allDimensions.add(dimension);
            }
            dimensions[i] = dimension;
            if (dimension.api == DimensionSlice.API.BANDS) {
                if (bandDimension >= 0) {
                    throw new IIOException(Errors.format(Errors.Keys.DUPLICATED_VALUE_$1, DimensionSlice.API.BANDS));
                }
                bandDimension = i;
            }
        }
        this.bandDimension = bandDimension;
        variables = new Variable[bandDimension >= 0 ? 1 : getNumSourceBands()];
    }

    /**
     * Adds the variables to the given NetCDF file. The NetCDF file must be in "define mode".
     * This method does not write the sample values. In order to compute the sample values,
     * invoke {@link #setSampleValues(RectIter)} after this method call.
     * <p>
     * This method stores a reference to the given iterator, but will not use it immediately.
     * The actual iteration will begin when the {@link #write(NetcdfFileWriteable)} method
     * will be invoked. The iteration is deferred because the {@link NetcdfFileWriteable}
     * needs to be switched from "define mode" to write mode before we can actually write
     * the pixel values. We can not switch the mode here because the caller may want to add
     * more variables before writing sample values.
     * <p>
     * The iterator must take the source bands, source region and source sub-sampling
     * in account. This is done automatically if the iterator has been created by
     * {@link SpatialImageWriter#createRectIter(IIOImage, ImageWriteParam)}.
     *
     * @param  file The NetCDF file where to add the variables.
     * @param  iter The iterator to use for extracting the image sample values.
     * @throws ImageMetadataException If an error occurred while creating the variables.
     */
    @SuppressWarnings("fallthrough")
    final void createVariables(final NetcdfFileWriteable file, final RectIter iter)
            throws ImageMetadataException
    {
        iterator = iter;
        /*
         * Get the UCAR variable data type, and whatever the data are signed or unsigned.
         * The fact that we compute those two information together explain why this code
         * is not declared in a separated method, like what we did for
         * org.geotoolkit.internal.image.io.NetcdfVariable#getRawDataType(VariableIF).
         */
        final DataType type;
        boolean unsigned = false;
        switch (dataType) {
            case DataBuffer.TYPE_BYTE:   type = DataType.BYTE; unsigned=true; break;
            case DataBuffer.TYPE_USHORT: unsigned = true; // Fallthrough
            case DataBuffer.TYPE_SHORT:  type = DataType.SHORT;  break;
            case DataBuffer.TYPE_INT:    type = DataType.INT;    break;
            case DataBuffer.TYPE_FLOAT:  type = DataType.FLOAT;  break;
            case DataBuffer.TYPE_DOUBLE: type = DataType.DOUBLE; break;
            default: throw new ImageMetadataException(Errors.format(Errors.Keys.UNSUPPORTED_DATA_TYPE_$1, dataType));
        }
        /*
         * Get the NetCDF dimensions to be given to Variable constructor.
         * NetCDF dimensions need to be declared in reverse order.
         */
        final Dimension[] ncDimensions = new Dimension[dimensions.length];
        for (int i=0; i<ncDimensions.length; i++) {
            ncDimensions[ncDimensions.length - (i+1)] = dimensions[i].getDimension();
        }
        /*
         * Get the metadata which will be shared for all variables.
         */
        final ImageDescription description = metadata.getInstanceForType(ImageDescription.class);
        final List<? extends RangeDimension> ranges;
        if (description != null) {
            ranges = XCollections.asList(description.getDimensions());
        } else {
            ranges = null;
        }
        final int numRanges = (ranges != null) ? ranges.size() : 0;
        for (int i=0; i<variables.length; i++) {
            final int band = getSourceBand(i);
            final RangeDimension range = (band < numRanges) ? ranges.get(band) : null;
            /*
             * Get the variable name from the metadata if possible, otherwise generate name
             * from a default pattern. The "band_1", "band_2", etc. pattern is used by GDAL.
             */
            String longName = null;
            String name = (range != null) ? toString(range.getDescriptor()) : null;
            if (name == null) {
                name = (bandDimension >= 0) ? "band_" + (i+1) : "data";
            } else if (!N3iosp.isValidNetcdf3ObjectName(name)) {
                longName = name;
                name = N3iosp.createValidNetcdf3ObjectName(name);
            }
            /*
             * Creation of the NetCDF variable happen here.
             * No data are written at this stage.
             */
            final Variable var = file.addVariable(name, type, ncDimensions);
            if (unsigned) {
                var.addAttribute(new Attribute(CDM.UNSIGNED, "true"));
            }
            if (longName != null) {
                var.addAttribute(new Attribute(CDM.LONG_NAME, longName));
            }
            if (range instanceof SampleDimension) {
                final SampleDimension sd = (SampleDimension) range;
                final Double max    = sd.getMaxValue();
                final Double min    = sd.getMinValue();
                final Double offset = sd.getOffset();
                final Double scale  = sd.getScaleFactor();
                final Unit<?> unit  = sd.getUnits();
                if (min    != null) var.addAttribute(new Attribute(VALID_MIN, min));
                if (max    != null) var.addAttribute(new Attribute(VALID_MAX, max));
                if (offset != null) var.addAttribute(new Attribute(CDM.ADD_OFFSET, offset));
                if (scale  != null) var.addAttribute(new Attribute(CDM.SCALE_FACTOR, scale));
                if (unit   != null) var.addAttribute(new Attribute(CDM.UNITS, String.valueOf(unit)));
            }
            variables[i] = var;
        }
    }

    /**
     * Writes the sample values in the given NetCDF file. This method can be invoked only when
     * the NetCDF file is no longer in "define mode".
     * <p>
     * This method uses the iterator given to the {@link #createVariables(NetcdfFileWriteable,
     * RectIter)} method. This iterator must take the source bands, source region and source
     * sub-sampling in account. This is done automatically if the iterator has been created by
     * {@link SpatialImageWriter#createRectIter(IIOImage, ImageWriteParam)}.
     *
     * @param  file The UCAR NetCDF object where to write the new variables.
     * @throws IOException if an error occurred while writing the NetCDF variables.
     */
    final void write(final NetcdfFileWriteable file) throws IOException {
        final RectIter iter = iterator;
        iterator = null;
        /*
         * Creates a buffer large enough for at least one row, and possibly a few more
         * rows if they are not too large.
         */
        Variable var = variables[0];
        final int[] shape    = var.getShape();
        final int xDimension = shape.length - (X_DIMENSION + 1);
        final int yDimension = shape.length - (Y_DIMENSION + 1);
        final int width      = shape[xDimension];
        final int height     = Math.min(Math.max(1, BUFFER_SIZE / width), shape[yDimension]);
        final int capacity   = width * height;
        shape[yDimension] = height;
        Arrays.fill(shape, 0, yDimension, 1); // Set all extra dimensions (if any) to a length of 1.
        final Array buffer = Array.factory(var.getDataType(), shape);
        /*
         * Everytime we start an iteration over a new bands, the target variable may change.
         * After every iteration on a row, we will flush the buffer if it is full.
         */
        int band = 0;
        try {
            iter.startBands();
            if (!iter.finishedBands()) do {
                if (bandDimension < 0) {
                    var = variables[band];
                }
                final String name = var.getFullNameEscaped();
                int index = 0; // Flat index in the matrix.
                iter.startLines();
                if (!iter.finishedLines()) do {
                    iter.startPixels();
                    if (!iter.finishedPixels()) do {
                        switch (dataType) {
                            case DataBuffer.TYPE_DOUBLE: buffer.setDouble(index, iter.getSampleDouble()); break;
                            case DataBuffer.TYPE_FLOAT:  buffer.setFloat (index, iter.getSampleFloat());  break;
                            default:                     buffer.setInt   (index, iter.getSample());       break;
                        }
                    } while (!iter.nextPixelDone());
                    if (index == capacity) {
                        file.write(name, buffer);
                        index = 0;
                    }
                } while (!iter.nextLineDone());
                if (index != 0) {
                    shape[yDimension] = index;
                    file.write(name, shape, buffer);
                }
                band++;
            } while (!iter.nextBandDone());
        } catch (InvalidRangeException e) {
            throw new IIOException(e.getLocalizedMessage(), e);
        }
    }
}
