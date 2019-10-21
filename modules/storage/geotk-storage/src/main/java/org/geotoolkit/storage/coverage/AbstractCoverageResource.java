/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2012-2015, Geomatys
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

import org.geotoolkit.storage.event.CoverageStoreManagementEvent;
import org.geotoolkit.storage.event.CoverageStoreContentEvent;
import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.xml.bind.annotation.XmlTransient;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.metadata.iso.citation.DefaultCitation;
import org.apache.sis.metadata.iso.content.DefaultAttributeGroup;
import org.apache.sis.metadata.iso.content.DefaultCoverageDescription;
import org.apache.sis.metadata.iso.identification.DefaultDataIdentification;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.referencing.NamedIdentifier;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.logging.Logging;
import org.geotoolkit.coverage.grid.GridCoverage2D;
import org.geotoolkit.coverage.io.GridCoverageReader;
import org.geotoolkit.process.Process;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessFinder;
import org.geotoolkit.storage.AbstractResource;
import org.opengis.geometry.Envelope;
import org.opengis.metadata.Metadata;
import org.opengis.metadata.content.CoverageDescription;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.metadata.identification.Identification;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.GenericName;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
@XmlTransient
public abstract class AbstractCoverageResource extends AbstractResource implements GridCoverageResource {

    private static final int DEFAULT_SUBSET_SIZE = 256;

    protected final DataStore store;

    private DefaultCoverageDescription desc = null;

    /**
     *
     * @param store can be null
     * @param name never null
     */
    public AbstractCoverageResource(DataStore store, GenericName name) {
        super(name);
        this.store = store;
    }

    @Override
    public DataStore getOriginator() {
        return store;
    }

    @Override
    protected DefaultMetadata createMetadata() throws DataStoreException {
        final DefaultDataIdentification idf = new DefaultDataIdentification();
        final DefaultCitation citation = new DefaultCitation();
        getIdentifier().ifPresent((id) -> citation.getIdentifiers().add(NamedIdentifier.castOrCopy(id)));
        idf.setCitation(citation);

        GridCoverageReader reader = null;
        DefaultMetadata metadata = null;
        try {
            reader = acquireReader();
            metadata = DefaultMetadata.castOrCopy(reader.getMetadata());
        } catch (DataStoreException ex) {
            metadata = new DefaultMetadata();
        } finally {
            if (reader != null) recycle(reader);
        }

        metadata.setIdentificationInfo(Arrays.asList(idf));
        return metadata;
    }

    @Override
    public synchronized CoverageDescription getCoverageDescription() {
        if (desc!=null) return desc;

        //calculate image statistics
        try {

            final GridGeometry gridGeometry = getGridGeometry();
            // latest data slice
            final GridExtent extent = gridGeometry.getExtent();
            final MathTransform gridToCrs = gridGeometry.getGridToCRS(PixelInCell.CELL_CENTER);

            final long[] low = new long[extent.getDimension()];
            final long[] high = new long[extent.getDimension()];
            low[0] = extent.getLow(0);
            low[1] = extent.getLow(1);
            high[0] = extent.getHigh(0);
            high[1] = extent.getHigh(1);
            for (int i=2,n=low.length;i<n;i++) {
                low[i] = extent.getHigh(i);
                high[i] = extent.getHigh(i);
            }
            final GridExtent sliceExt = new GridExtent(null, low, high, true);
            GridGeometry slice = new GridGeometry(sliceExt, PixelInCell.CELL_CENTER, gridToCrs, gridGeometry.getCoordinateReferenceSystem());
            final double[] subsetResolution = new double[sliceExt.getDimension()];
            Arrays.fill(subsetResolution, 1.0);
            subsetResolution[0] = gridGeometry.getEnvelope().getSpan(0) / DEFAULT_SUBSET_SIZE;
            subsetResolution[1] = gridGeometry.getEnvelope().getSpan(1) / DEFAULT_SUBSET_SIZE;
            slice = slice.derive().subgrid(null, subsetResolution).build();

            GridCoverage coverage = org.geotoolkit.internal.coverage.CoverageUtilities.toGeotk(read(slice));

            if (coverage instanceof GridCoverage2D) {
                final ProcessDescriptor processDesc = ProcessFinder.getProcessDescriptor("geotoolkit", "coverage:statistic");
                final ParameterDescriptorGroup inputDesc = processDesc.getInputDescriptor();
                final Parameters processParam = Parameters.castOrWrap(inputDesc.createValue());
                processParam.parameter("inCoverage").setValue(coverage);
                processParam.parameter("inExcludeNoData").setValue(true);

                final Process process = processDesc.createProcess(processParam);
                final ParameterValueGroup result = process.call();
                final ImageStatistics stats = (ImageStatistics) result.parameter("outStatistic").getValue();
                desc = new CoverageDescriptionAdapter(stats);
            }

        } catch (Exception ex) {
            Logging.getLogger("org.geotoolkit.storage.coverage").log(Level.WARNING, "Cannot compute statistics on coverage content", ex);
        }

        if (desc == null) {
            desc = new DefaultCoverageDescription();
            final DefaultAttributeGroup attg = new DefaultAttributeGroup();
            desc.getAttributeGroups().add(attg);
        }

        return desc;
    }

    /**
     * Returns the spatio-temporal envelope of this resource.
     * The default implementation computes the union of all {@link GeographicBoundingBox} in the resource metadata,
     * assuming the {@linkplain org.apache.sis.referencing.CommonCRS#defaultGeographic() default geographic CRS}
     * (usually WGS 84).
     *
     * @return the spatio-temporal resource extent.
     * @throws DataStoreException if an error occurred while reading or computing the envelope.
     */
    @Override
    public Optional<Envelope> getEnvelope() throws DataStoreException {
        final Metadata metadata = getMetadata();
        GeneralEnvelope bounds = null;
        if (metadata != null) {
            for (final Identification identification : metadata.getIdentificationInfo()) {
                if (identification != null) {                                               // Paranoiac check.
                    for (final Extent extent : identification.getExtents()) {
                        if (extent != null) {                                               // Paranoiac check.
                            for (final GeographicExtent ge : extent.getGeographicElements()) {
                                if (ge instanceof GeographicBoundingBox) {
                                    final GeneralEnvelope env = new GeneralEnvelope((GeographicBoundingBox) ge);
                                    if (bounds == null) {
                                        bounds = env;
                                    } else {
                                        bounds.add(env);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (bounds == null) {
            final GridGeometry gridGeometry = getGridGeometry();
            if (gridGeometry != null && gridGeometry.isDefined(GridGeometry.ENVELOPE)) {
                bounds = new GeneralEnvelope(gridGeometry.getEnvelope());
            }
        }
        return Optional.ofNullable(bounds);
    }

    protected CoverageStoreManagementEvent firePyramidAdded(final String pyramidId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createPyramidAddEvent(this, getIdentifier().orElse(null), pyramidId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreManagementEvent firePyramidUpdated(final String pyramidId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createPyramidUpdateEvent(this, getIdentifier().orElse(null), pyramidId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreManagementEvent firePyramidDeleted(final String pyramidId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createPyramidDeleteEvent(this, getIdentifier().orElse(null), pyramidId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreManagementEvent fireMosaicAdded(final String pyramidId, final String mosaicId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createMosaicAddEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreManagementEvent fireMosaicUpdated(final String pyramidId, final String mosaicId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createMosaicUpdateEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreManagementEvent fireMosaicDeleted(final String pyramidId, final String mosaicId){
        final CoverageStoreManagementEvent event = CoverageStoreManagementEvent.createMosaicDeleteEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreContentEvent fireDataUpdated(){
        final CoverageStoreContentEvent event = CoverageStoreContentEvent.createDataUpdateEvent(this, getIdentifier().orElse(null));
        sendEvent(event);
        return event;
    }

    protected CoverageStoreContentEvent fireTileAdded(final String pyramidId, final String mosaicId, final List<Point> tiles){
        final CoverageStoreContentEvent event = CoverageStoreContentEvent.createTileAddEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId, tiles);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreContentEvent fireTileUpdated(final String pyramidId, final String mosaicId, final List<Point> tiles){
        final CoverageStoreContentEvent event = CoverageStoreContentEvent.createTileUpdateEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId, tiles);
        sendEvent(event);
        return event;
    }

    protected CoverageStoreContentEvent fireTileDeleted(final String pyramidId, final String mosaicId, final List<Point> tiles){
        final CoverageStoreContentEvent event = CoverageStoreContentEvent.createTileDeleteEvent(this, getIdentifier().orElse(null), pyramidId, mosaicId, tiles);
        sendEvent(event);
        return event;
    }

    /**
     * Dispose a reader, trying to properly release sub resources.
     * Best effort.
     */
    protected void dispose(GridCoverageReader reader) {
        try {
//            //try to close sub stream
//            Object input = reader.getInput();
//            if(input instanceof ImageReader){
//                final ImageReader ireader = (ImageReader)input;
//                ImageIOUtilities.releaseReader(ireader);
//            }else if(input instanceof InputStream){
//                final InputStream stream = (InputStream) input;
//                stream.close();
//            }else if(input instanceof ImageInputStream){
//                final ImageInputStream stream = (ImageInputStream) input;
//                stream.close();
//            }

            reader.dispose();

        } catch (DataStoreException ex) {
            Logging.getLogger("org.geotoolkit.storage.coverage").log(Level.WARNING, ex.getMessage(), ex);
        }
    }

}
