/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2019, Geomatys
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
package org.geotoolkit.coverage.mosaic;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.measure.Unit;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.DisjointExtentException;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.image.PixelIterator;
import org.apache.sis.image.WritablePixelIterator;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.GridCoverageResource;
import org.apache.sis.storage.event.StoreEvent;
import org.apache.sis.storage.event.StoreListener;
import org.apache.sis.util.collection.FrequencySortedSet;
import org.geotoolkit.coverage.grid.GridCoverageBuilder;
import org.geotoolkit.coverage.io.DisjointCoverageDomainException;
import org.geotoolkit.geometry.jts.JTSEnvelope2D;
import org.geotoolkit.image.BufferedImages;
import org.geotoolkit.image.interpolation.InterpolationCase;
import org.geotoolkit.image.interpolation.Resample;
import org.geotoolkit.image.interpolation.ResampleBorderComportement;
import org.geotoolkit.internal.coverage.CoverageUtilities;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.geometry.Envelope;
import org.opengis.metadata.Metadata;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.opengis.util.GenericName;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public final class AggregatedCoverageResource implements GridCoverageResource {

    public static enum Mode {
        /**
         * Coverage will be generated by progressively aggregating resource is the user defined order.
         * This mode should be used to merge data by there quality order.
         */
        ORDER,
        /**
         * Resources will be sorted by scale and combined is a smart way by requested resolution at reading.
         * This mode should be used when all data represent the same information but in different
         * scales and areas.
         */
        SCALE
    }

    private final List<GridCoverageResource> resources;
    private final Quadtree tree = new Quadtree();
    private final GridGeometry gridGeometry;
    private final Mode mode;
    private InterpolationCase interpolation = InterpolationCase.BILINEAR;
    private List<SampleDimension> sampleDimensions;

    public static GridCoverageResource create(CoordinateReferenceSystem resultCrs, GridCoverageResource ... resources) throws DataStoreException, TransformException {
        return create(resultCrs, Mode.ORDER, resources);
    }

    public static GridCoverageResource create(CoordinateReferenceSystem resultCrs, Mode mode, GridCoverageResource ... resources) throws DataStoreException, TransformException {
        if (resources.length == 0) {
            throw new DataStoreException("No resources to aggregate");
        } else if (resources.length == 1) {
            return resources[0];
        } else {
            return new AggregatedCoverageResource(Arrays.asList(resources), mode, resultCrs);
        }
    }

    private AggregatedCoverageResource(List<GridCoverageResource> resources, Mode mode, CoordinateReferenceSystem resultCrs) throws DataStoreException, TransformException {
        this.resources = resources;
        this.mode = mode;

        if (resultCrs == null) {
            //use most common crs
            //TODO find a better approach to determinate a common crs
            final FrequencySortedSet<CoordinateReferenceSystem> map = new FrequencySortedSet<>();
            for (GridCoverageResource resource : resources) {
                map.add(resource.getGridGeometry().getCoordinateReferenceSystem());
            }
            resultCrs = map.last();
        }

        //compute envelope and check sample dimensions
        GeneralEnvelope env = new GeneralEnvelope(resultCrs);
        env.setToNaN();
        int index = 0;
        for (GridCoverageResource resource : resources) {
            Envelope envelope = resource.getGridGeometry().getEnvelope();
            envelope = Envelopes.transform(envelope, resultCrs);
            tree.insert(new JTSEnvelope2D(envelope), new AbstractMap.SimpleImmutableEntry<>(index++,resource));

            if (env.isAllNaN()) {
                env.setEnvelope(envelope);
            } else {
                env.add(envelope);
            }

            boolean hasUndefinedUnits = false;
            List<SampleDimension> sampleDimensions = resource.getSampleDimensions();
            if (this.sampleDimensions == null) {
                this.sampleDimensions = new ArrayList<>(sampleDimensions);
            } else {
                //check dimensions count
                if (sampleDimensions.size() != this.sampleDimensions.size()) {
                    throw new DataStoreException("Uncompatible sample dimensions, size differ.");
                }
                //check dimensions units
                for (int i = 0,n = sampleDimensions.size(); i < n; i++) {
                    SampleDimension baseDim = this.sampleDimensions.get(i);
                    SampleDimension dim = sampleDimensions.get(i);
                    Unit<?> baseUnit = baseDim.getUnits().orElse(null);
                    Unit<?> unit = baseDim.getUnits().orElse(null);

                    if (baseUnit == null) {
                        hasUndefinedUnits = true;
                        if (unit != null) {
                            //replace the declared unit, more accurate
                            this.sampleDimensions.set(i, dim);
                        }
                    } else if (unit == null) {
                        //we assume all datas have the implicite same unit
                        //if there is no conflict
                        hasUndefinedUnits = true;

                    } else {
                        if (!baseUnit.equals(unit)) {
                            throw new DataStoreException("Uncompatible sample dimensions, different units found : "+baseUnit +", "+unit);
                        }
                    }
                }
            }
        }

        gridGeometry = new GridGeometry(null, env);

    }

    @Override
    public Optional<GenericName> getIdentifier() throws DataStoreException {
        return Optional.empty();
    }

    @Override
    public Metadata getMetadata() throws DataStoreException {
        return new DefaultMetadata();
    }

    @Override
    public GridGeometry getGridGeometry() throws DataStoreException {
        return gridGeometry;
    }

    @Override
    public List<SampleDimension> getSampleDimensions() throws DataStoreException {
        return sampleDimensions;
    }

    @Override
    public Optional<Envelope> getEnvelope() throws DataStoreException {
        return Optional.of(getGridGeometry().getEnvelope());
    }

    public InterpolationCase getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(InterpolationCase interpolation) {
        this.interpolation = interpolation;
    }

    @Override
    public GridCoverage read(GridGeometry domain, int... range) throws DataStoreException {

        if (domain == null) domain = gridGeometry;

        GridGeometry canvas = domain;
        canvas = CoverageUtilities.forceLowerToZero(canvas);

        final Envelope envelope = domain.getEnvelope();
        final List<Map.Entry<Integer,GridCoverageResource>> results = new ArrayList(tree.query(new JTSEnvelope2D(envelope)));

        //single result
        if (results.size() == 1) {
            GridCoverageResource resource = results.get(0).getValue();
            return resource.read(canvas, range);
        }

        final List<GridCoverageResource> ordered;
        if (mode == Mode.ORDER) {
            //sort by user order
            Collections.sort(results, (Map.Entry<Integer, GridCoverageResource> o1, Map.Entry<Integer, GridCoverageResource> o2) -> o1.getKey().compareTo(o2.getKey()));
            ordered = results.stream().map(Map.Entry::getValue).collect(Collectors.toList());
        } else {
            //the most accurate order would be to render in order coverages
            //with scale going from 1.0 to 0.0 then from 1.0 to +N
            //filling only the gaps at each coverage

            ordered = new ArrayList<>();

            final Map<GridCoverageResource,Double> ratios = new HashMap<>();
            for (Map.Entry<Integer,GridCoverageResource> entry : results) {
                try {
                    double ratio = estimateRatio(entry.getValue().getGridGeometry(), canvas);
                    double order = ratio;
                    if (ratio <= 1.05) { //little tolerance du to crs deformations and math
                        order = 1.05 - ratio;
                    }
                    ratios.put(entry.getValue(), order);
                    ordered.add(entry.getValue());
                } catch (DisjointExtentException | FactoryException | TransformException ex) {
                    continue;
                }
            }
            ordered.sort((GridCoverageResource o1, GridCoverageResource o2) -> ratios.get(o1).compareTo(ratios.get(o2)));
        }

        //aggregate tiles
        BufferedImage result = null;
        BufferedImage intermediate = null;
        double[] fillValue = null;
        List<SampleDimension> sampleDimensions = null;

        //start by creating a mask of filled datas
        final BitSet2D mask = new BitSet2D((int) canvas.getExtent().getSize(0), (int) canvas.getExtent().getSize(1));

        for (GridCoverageResource resource : ordered) {
            try {
                //check the mask if we have finish
                final GridExtent maskExtent = mask.areaCleared().orElse(null);
                if (maskExtent == null) break;
                final GridGeometry maskGrid = new GridGeometry(maskExtent, PixelInCell.CELL_CENTER, canvas.getGridToCRS(PixelInCell.CELL_CENTER), canvas.getCoordinateReferenceSystem());

                //expend grid geometry a little for interpolation
                GridGeometry readGridGeom;
                GridGeometry coverageGridGeometry = resource.getGridGeometry();
                if (coverageGridGeometry.isDefined(GridGeometry.EXTENT)) {
                    readGridGeom = coverageGridGeometry.derive()
                            .margin(5,5)
                            .subgrid(maskGrid)
                            .build();
                } else {
                    readGridGeom = maskGrid.derive().margin(5,5).build();
                }

                final GridCoverage coverage = resource.read(readGridGeom, range).forConvertedValues(true);
                sampleDimensions = coverage.getSampleDimensions();
                final RenderedImage tileImage = coverage.render(null);

                final BufferedImage workImage;
                if (result == null) {
                    //create result image
                    GridExtent extent = canvas.getExtent();
                    int sizeX = Math.toIntExact(extent.getSize(0));
                    int sizeY = Math.toIntExact(extent.getSize(1));
                    result = BufferedImages.createImage(sizeX, sizeY, tileImage);
                    workImage = result;
                    fillValue = new double[result.getSampleModel().getNumBands()];
                    Arrays.fill(fillValue, Double.NaN);
                    BufferedImages.setAll(result, fillValue);
                } else {
                    if (intermediate == null) {
                        intermediate = BufferedImages.createImage(result.getWidth(), result.getHeight(), result);
                    }
                    workImage = intermediate;
                    BufferedImages.setAll(intermediate, fillValue);
                }

                //resample coverage
                MathTransform tileToTileCrs = coverage.getGridGeometry().getGridToCRS(PixelInCell.CELL_CENTER).inverse();
                MathTransform crsToCrs = CRS.findOperation(
                        canvas.getCoordinateReferenceSystem(),
                        coverage.getGridGeometry().getCoordinateReferenceSystem(),
                        null).getMathTransform();
                MathTransform canvasToCrs = canvas.getGridToCRS(PixelInCell.CELL_CENTER);

                final MathTransform targetToSource = MathTransforms.concatenate(canvasToCrs, crsToCrs, tileToTileCrs);

                final Resample resample = new Resample(targetToSource, workImage, tileImage,
                        interpolation, ResampleBorderComportement.FILL_VALUE, null);
                resample.fillImage(true);

                if (workImage != result) {
                    //we need to merge image, replacing only not-NaN values
                    PixelIterator read = PixelIterator.create(workImage);
                    WritablePixelIterator write = WritablePixelIterator.create(result);
                    final double[] pixelr = new double[read.getNumBands()];
                    final double[] pixelw = new double[read.getNumBands()];
                    final boolean monoBand = pixelr.length == 1;
                    while (read.next() & write.next()) {
                        if (monoBand) {
                            read.getPixel(pixelr);
                            if (Double.isNaN(pixelr[0])) continue;
                            write.getPixel(pixelw);
                            if (Double.isNaN(pixelw[0])) {
                                write.setPixel(pixelr);
                                //fill the mask
                                Point pt = read.getPosition();
                                mask.set2D(pt.x, pt.y);
                            }
                        } else {
                            //TODO what approach when a NaN is defined on one band and not another ?
                            //should we have one mask per band ?
                            read.getPixel(pixelr);
                            write.getPixel(pixelw);
                            for (int i=0;i<pixelr.length;i++) {
                                if (Double.isNaN(pixelr[i])) {
                                    //do nothing
                                } else if (Double.isNaN(pixelw[i])) {
                                    write.setPixel(pixelr);
                                    //fill the mask
                                    Point pt = read.getPosition();
                                    mask.set2D(pt.x, pt.y);
                                }
                            }
                        }
                    }
                } else {
                    //first resampled Image, fill the mask
                    PixelIterator read = PixelIterator.create(workImage);
                    final double[] pixelr = new double[read.getNumBands()];
                    while (read.next()) {
                        read.getPixel(pixelr);
                        if (!Double.isNaN(pixelr[0])) {
                            Point pt = read.getPosition();
                            mask.set2D(pt.x, pt.y);
                        }
                    }
                }

            } catch (DisjointCoverageDomainException | DisjointExtentException ex) {
                //may happen, enveloppe is larger then data or mask do not intersect anymore
                //quad tree may also return more results
            } catch (FactoryException ex) {
                throw new DataStoreException(ex.getMessage(), ex);
            } catch (TransformException ex) {
                throw new DataStoreException(ex.getMessage(), ex);
            }
        }

        if (result == null) {
            throw new DisjointCoverageDomainException();
        }

        final GridCoverageBuilder gcb = new GridCoverageBuilder();
        gcb.setName("Aggregated");
        gcb.setGridGeometry(canvas);
        gcb.setSampleDimensions(sampleDimensions);
        gcb.setRenderedImage(result);
        return gcb.build();
    }

    @Override
    public <T extends StoreEvent> void addListener(Class<T> eventType, StoreListener<? super T> listener) {
    }

    @Override
    public <T extends StoreEvent> void removeListener(Class<T> eventType, StoreListener<? super T> listener) {
    }

    /**
     * Estimate the ratio of coverage grid geometry intersection in target grid geometry.
     * The ratio is an estimation of the resolution difference from the target grid resolution.
     * A value lower then 1 means the coverage has a higher resolution then the target grid, coverage is more accurate.
     * A value higher then 1 means the coverage has a lower resolution then the target grid, coverage is less accurate.
     *
     * @param coverage
     * @param target
     * @return
     */
    private static double estimateRatio(GridGeometry coverage, GridGeometry target) throws FactoryException, TransformException {
        //intersect grid geometries
        final GridGeometry result = coverage.derive().subgrid(target.getEnvelope()).build();
        //compute transform
        final MathTransform gridToCRS = result.getGridToCRS(PixelInCell.CELL_CENTER);
        final CoordinateOperation crsToCrs = CRS.findOperation(result.getCoordinateReferenceSystem(), target.getCoordinateReferenceSystem(), null);
        final MathTransform trs = MathTransforms.concatenate(gridToCRS, crsToCrs.getMathTransform());
        //transform a unitary vector at most representative point
        double[] point = result.getExtent().getPointOfInterest();
        double[] vector = Arrays.copyOf(point, 4);
        double diagonal = 1.0 / Math.sqrt(2); //for a vector of length = 1
        vector[2] = point[0] + diagonal;
        vector[3] = point[1] + diagonal;
        trs.transform(vector, 0, vector, 0, 2);
        double x = Math.abs(vector[0] - vector[2]);
        double y = Math.abs(vector[1] - vector[3]);
        return Math.sqrt(x*x + y*y);
    }

}
