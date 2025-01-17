/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2017, Geomatys
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
package org.geotoolkit.storage.coverage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.coverage.grid.IncompleteGridGeometryException;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.GeneralDirectPosition;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.operation.matrix.Matrices;
import org.apache.sis.referencing.operation.matrix.MatrixSIS;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.storage.DataStoreException;
import org.geotoolkit.coverage.grid.GridCoverageStack;
import org.geotoolkit.coverage.io.AbstractGridCoverageReader;
import org.geotoolkit.coverage.io.CoverageStoreException;
import org.geotoolkit.coverage.io.DisjointCoverageDomainException;
import org.geotoolkit.coverage.io.GridCoverageReadParam;
import org.geotoolkit.math.XMath;
import org.geotoolkit.referencing.ReferencingUtilities;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.Matrix;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.opengis.util.GenericName;

/**
 * Simplified GridCoverageReader which ensures the given GridCoverageReadParam
 * is not null and in the coverage CoordinateReferenceSystem.
 *
 * @author Johann Sorel (Geomatys)
 */
public abstract class GeoReferencedGridCoverageReader extends AbstractGridCoverageReader {

    protected final org.apache.sis.storage.GridCoverageResource ref;

    protected GeoReferencedGridCoverageReader(org.apache.sis.storage.GridCoverageResource ref) {
        this.ref = ref;
    }

    @Override
    public GenericName getCoverageName() throws DataStoreException, CancellationException {
        return ref.getIdentifier().orElse(null);
    }

    /**
     * {@inheritDoc }
     *
     * Checks parameters envelope, CRS and resolution and create or fix them to match
     * this coverage CRS.
     */
    @Override
    public final GridCoverage read(GridCoverageReadParam param) throws DataStoreException, CancellationException {

        final GridGeometry gridGeometry = getGridGeometry();
        final CoordinateReferenceSystem coverageCrs = gridGeometry.getCoordinateReferenceSystem();

        try {
            //find requested envelope
            Envelope queryEnv = param == null ? null : param.getEnvelope();
            if(queryEnv == null && param != null && param.getCoordinateReferenceSystem()!= null){
                queryEnv = Envelopes.transform(gridGeometry.getEnvelope(), param.getCoordinateReferenceSystem());
            }

            //convert resolution to coverage crs
            final double[] queryRes = param == null ? null : param.getResolution();
            double[] coverageRes = queryRes;
            if (queryRes != null && queryEnv != null) {
                try {
                    //this operation works only for 2D CRS
                    coverageRes = ReferencingUtilities.convertResolution(queryEnv, queryRes, coverageCrs);
                } catch (TransformException | IllegalArgumentException ex) {
                    //more general case, less accurate
                    coverageRes = convertCentralResolution(queryRes, queryEnv, coverageCrs);
                }
            }

            //if no envelope is defined, use the full extent
            final Envelope coverageEnv;
            if (queryEnv==null) {
                coverageEnv = gridGeometry.getEnvelope();
            } else {
                final GeneralEnvelope genv = new GeneralEnvelope(Envelopes.transform(queryEnv, coverageCrs));
                //clip to coverage envelope
                genv.intersect(gridGeometry.getEnvelope());
                coverageEnv = genv;

                //check for disjoint envelopes
                int dimension = 0;
                for (int i=genv.getDimension(); --i>=0;) {
                    if (genv.getSpan(i) > 0) {
                        dimension++;
                    }
                }
                if (dimension < 2) {
                    throw new DisjointCoverageDomainException("No coverage matched parameters");
                }
            }


            final GridCoverageReadParam cparam = new GridCoverageReadParam();
            cparam.setCoordinateReferenceSystem(coverageEnv.getCoordinateReferenceSystem());
            cparam.setEnvelope(coverageEnv);
            cparam.setResolution(coverageRes);
            cparam.setDestinationBands((param == null) ? null : param.getDestinationBands());
            cparam.setSourceBands((param == null) ? null : param.getSourceBands());
            cparam.setDeferred((param == null) ? false : param.isDeferred());

            return readInNativeCRS(cparam);
        } catch (TransformException | FactoryException ex) {
            throw new CoverageStoreException(ex.getMessage(), ex);
        }
    }

    /**
     * Read coverage,
     *
     * If this method is not overloaded, the default implementation will fall back
     * on
     *
     *
     * @param param Parameters are guarantee to be in coverage CRS.
     */
    protected GridCoverage readInNativeCRS(GridCoverageReadParam param) throws DataStoreException, TransformException, CancellationException {

        final Envelope coverageEnv = param.getEnvelope();
        final double[] coverageRes = param.getResolution();

        final GridGeometry gridGeom = getGridGeometry();

        final GeneralEnvelope imgEnv;
        try {
            final MathTransform gridToCRS = gridGeom.getGridToCRS(PixelInCell.CELL_CORNER);
            // convert envelope CS to image CS
            imgEnv = Envelopes.transform(gridToCRS.inverse(), coverageEnv);
        } catch (NoninvertibleTransformException ex) {
            throw new CoverageStoreException(ex.getMessage(), ex);
        }

        final GridExtent extent = gridGeom.getExtent();
        final int dim = extent.getDimension();

        // prepare image readInGridCRS param
        final int[] areaLower = new int[dim];
        final int[] areaUpper = new int[dim];

        final int[] subsampling = new int[dim];
        Arrays.fill(subsampling, 1);
        if (coverageRes != null) {
            try {
                final double[] sourceResolution = gridGeom.getResolution(false);
                /* If we cannot determine a source resolution, guessing subsampling
                 * will be very complicated, so we simplify workflow to return full
                 * resolution image.
                 * TODO : find an alternative method
                 */
                for (int i = 0; i < sourceResolution.length; i++) {
                    if (Double.isFinite(sourceResolution[i]) && sourceResolution[i] != 0) {
                        final double ratio = coverageRes[i] / sourceResolution[i];
                        subsampling[i] = Math.max(1, (int) ratio);
                    }
                }
            } catch (IncompleteGridGeometryException ex) {
            }
        }

        // clamp region from data coverage raster boundary
        long min,max;
        for(int i=0;i<dim;i++){
            min = extent.getLow(i);
            max = extent.getHigh(i)+1;//+1 for upper exclusive
            areaLower[i] = Math.toIntExact(XMath.clamp((int)Math.floor(imgEnv.getMinimum(i)), min, max));
            areaUpper[i] = Math.toIntExact(XMath.clamp((int)Math.ceil(imgEnv.getMaximum(i)),  min, max));
            if (areaLower[i] == areaUpper[i]) areaUpper[i]++;
        }

        return readInGridCRS(areaLower,areaUpper,subsampling, param);
    }

    /**
     * Read a coverage with defined image area.
     *
     * @param areaLower readInGridCRS lower corner, inclusive
     * @param areaUpper readInGridCRS upper corner, exclusive
     * @param subsampling image subsampling in pixels
     * @param param grid coverage features parameters in native CRS
     * @throws CoverageStoreException if Coverage readInGridCRS failed
     * @throws CancellationException if reading operation has been canceled
     */
    protected GridCoverage readInGridCRS(int[] areaLower, int[] areaUpper, int[] subsampling, GridCoverageReadParam param)
            throws DataStoreException, TransformException, CancellationException {

        //ensure we readInGridCRS at least 3x3 pixels otherwise the gridgeometry won't be
        //able to identify the 2D composant of the grid to crs transform.
        for (int i=0; i<2; i++) {
            int width = (areaUpper[i] - areaLower[i] + subsampling[i] - 1) / subsampling[i];
            if (width < 2) {
                subsampling[i] = 1;
                if (areaLower[i] == 0) {
                    areaUpper[i] = 3;
                } else {
                    areaLower[i]--;
                    areaUpper[i]++;
                }
            }
        }

        // find if we need to readInGridCRS more then one slice
        int cubeDim = -1;
        for (int i=0; i<subsampling.length; i++) {
            final int width = (areaUpper[i] - areaLower[i] + subsampling[i] - 1) / subsampling[i];
            if (i>1 && width>1) {
                cubeDim = i;
                break;
            }
        }

        if (cubeDim == -1) {
            //read a single slice
            return readGridSlice(areaLower, areaUpper, subsampling, param);
        } else {
            //read an Nd cube
            final List<GridCoverage> coverages = new ArrayList<>();
            final int lower = areaLower[cubeDim];
            final int upper = areaUpper[cubeDim];
            for(int i=lower;i<upper;i++){
                areaLower[cubeDim] = i;
                areaUpper[cubeDim] = i+1;
                coverages.add(readInGridCRS(areaLower, areaUpper, subsampling, param));
            }
            areaLower[cubeDim] = lower;
            areaUpper[cubeDim] = upper;

            try {
                return new GridCoverageStack(ref.getIdentifier().toString(), coverages, cubeDim);
            } catch (IOException | TransformException | FactoryException ex) {
                throw new CoverageStoreException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Read a coverage slice with defined image area.
     *
     * @param param grid coverage features parameters in native CRS
     */
    protected GridCoverage readGridSlice(int[] areaLower, int[] areaUpper, int[] subsampling, GridCoverageReadParam param) throws DataStoreException, TransformException, CancellationException {
        throw new UnsupportedOperationException("Subclass must implement either : read, readCoverage, readImage or readSlice methods");
    }

    /**
     * Convert resolution from one CRS to another at the center of given envelope.
     */
    private static double[] convertCentralResolution(final double[] resolution, final Envelope area,
            final CoordinateReferenceSystem targetCRS) throws FactoryException, TransformException {
        final CoordinateReferenceSystem areaCrs = area.getCoordinateReferenceSystem();
        if (areaCrs.equals(targetCRS)) {
            //nothing to do.
            return resolution;
        }

        final GeneralDirectPosition center = new GeneralDirectPosition(area.getDimension());
        for (int i=center.getDimension(); --i >= 0;) {
            center.setOrdinate(i, area.getMedian(i));
        }
        final Matrix derivative = CRS.findOperation(areaCrs, targetCRS, null).getMathTransform().derivative(center);
        final Matrix vector = Matrices.createZero(resolution.length, 1);
        for (int i=0; i<resolution.length; i++) {
            vector.setElement(i, 0, resolution[i]);
        }
        final Matrix result = Matrices.multiply(derivative, vector);
        double[] res = MatrixSIS.castOrCopy(result).getElements();
        for (int i=0; i<res.length; i++) {
            res[i] = Math.abs(res[i]);
        }
        return res;
    }

    /**
     * Calculate the final size of each dimension.
     *
     * @param areaLower image features lower corner
     * @param areaUpper image features upper corner
     * @param subsampling image subsampling
     */
    public static long[] getResultExtent(int[] areaLower, int[] areaUpper, int[] subsampling) {

        //calculate output size
        final long[] outExtent = new long[areaLower.length];
        for(int i=0;i<outExtent.length;i++){
            outExtent[i] = (areaUpper[i]-areaLower[i]+subsampling[i]-1) / subsampling[i];
        }

        return outExtent;
    }

    /**
     * Derivate a grid geometry from the original grid geometry and the features
     * image parameters.
     *
     * @param gridGeom original grid geometry
     * @param param user param, in grid CRS
     * @return derivated grid geometry.
     */
    public static GridGeometry getGridGeometry(GridGeometry gridGeom,
            GridCoverageReadParam param) throws CoverageStoreException, TransformException {

        final Envelope coverageEnv = param.getEnvelope();
        final double[] coverageRes = param.getResolution();

        final GeneralEnvelope imgEnv;
        try {
            final MathTransform gridToCRS = gridGeom.getGridToCRS(PixelInCell.CELL_CORNER);
            // convert envelope CS to image CS
            imgEnv = Envelopes.transform(gridToCRS.inverse(), coverageEnv);
        } catch (NoninvertibleTransformException ex) {
            throw new CoverageStoreException(ex.getMessage(), ex);
        }

        final GridExtent extent = gridGeom.getExtent();
        final int dim = extent.getDimension();

        // prepare image readInGridCRS param
        final int[] areaLower = new int[dim];
        final int[] areaUpper = new int[dim];

        final int[] subsampling = new int[dim];
        Arrays.fill(subsampling, 1);
        if (coverageRes != null) {
            try {
                final double[] sourceResolution = gridGeom.getResolution(false);
                /* If we cannot determine a source resolution, guessing subsampling
                 * will be very complicated, so we simplify workflow to return full
                 * resolution image.
                 * TODO : find an alternative method
                 */
                for (int i = 0; i < sourceResolution.length; i++) {
                    if (Double.isFinite(sourceResolution[i]) && sourceResolution[i] != 0) {
                        final double ratio = coverageRes[i] / sourceResolution[i];
                        subsampling[i] = Math.max(1, (int) ratio);
                    }
                }
            } catch (IncompleteGridGeometryException ex) {
            }
        }

        // clamp region from data coverage raster boundary
        long min,max;
        for(int i=0;i<dim;i++){
            min = extent.getLow(i);
            max = extent.getHigh(i)+1;//+1 for upper exclusive
            areaLower[i] = Math.toIntExact(XMath.clamp((int)Math.floor(imgEnv.getMinimum(i)), min, max));
            areaUpper[i] = Math.toIntExact(XMath.clamp((int)Math.ceil(imgEnv.getMaximum(i)),  min, max));
        }

        return getGridGeometry(gridGeom, areaLower, areaUpper, subsampling);
    }

    /**
     * Derivate a grid geometry from the original grid geometry and the features
     * image parameters.
     *
     * @param gridGeom original grid geometry
     * @param areaLower image features lower corner
     * @param areaUpper image features upper corner
     * @param subsampling image subsampling
     * @return derivated grid geometry.
     */
    public static GridGeometry getGridGeometry(GridGeometry gridGeom,
            int[] areaLower, int[] areaUpper, int[] subsampling) {

        //calculate output size
        final long[] outExtent = getResultExtent(areaLower, areaUpper, subsampling);

        //build grid geometry
        int dim = areaLower.length;
        final Matrix matrix = Matrices.createDiagonal(dim+1, dim+1);
        for(int i=0;i<dim;i++){
            matrix.setElement(i, i, subsampling[i]);
            matrix.setElement(i, dim, areaLower[i]);
        }
        final MathTransform ssToGrid = MathTransforms.linear(matrix);
        final MathTransform ssToCrs = MathTransforms.concatenate(ssToGrid, gridGeom.getGridToCRS(PixelInCell.CELL_CENTER));
        final GridExtent extent = new GridExtent(null, null, outExtent, false);
        return new GridGeometry(extent, PixelInCell.CELL_CENTER, ssToCrs, gridGeom.getCoordinateReferenceSystem());
    }
}
