/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2012-2019, Geomatys
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
package org.geotoolkit.display2d.service;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.coverage.grid.GridRoundingMode;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.internal.storage.AbstractGridResource;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.Utilities;
import org.geotoolkit.coverage.grid.GridCoverageBuilder;
import org.geotoolkit.coverage.io.CoverageStoreException;
import org.geotoolkit.display.PortrayalException;
import org.geotoolkit.referencing.ReferencingUtilities;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.GenericName;
import org.opengis.util.NameFactory;
import org.opengis.util.NameSpace;

/**
 * Manipulate a SceneDef as a CoverageResource.
 * This allow to manipulate an aggregation of several different layers as if
 * it was a single coverage.
 *
 * @author Johann Sorel (Geomatys)
 * @module
 */
final class PortrayalCoverageResource extends AbstractGridResource {

    private final SceneDef scene;
    private final GenericName name;
    private String contextName;

    public PortrayalCoverageResource(final SceneDef scene) {
        super(null);
        this.scene = scene;

        contextName = scene.getContext().getName();
        if (contextName == null) {
            contextName = "portrayal";
        }

        final NameFactory dnf = DefaultFactories.forBuildin(NameFactory.class);
        final NameSpace ns = dnf.createNameSpace(dnf.createGenericName(null, contextName), null);
        name = dnf.createLocalName(ns, contextName);
    }

    @Override
    public GridGeometry getGridGeometry() throws CoverageStoreException, CancellationException {
        //we only know the envelope
        final GridGeometry gridGeom;
        try {
            gridGeom = new GridGeometry(null, null, scene.getContext().getBounds(), GridRoundingMode.ENCLOSING);
        } catch (IOException ex) {
            throw new CoverageStoreException(ex.getMessage(),ex);
        }
        return gridGeom;
    }

    @Override
    public List<SampleDimension> getSampleDimensions() throws CoverageStoreException, CancellationException {
        return null;
    }

    @Override
    public GridCoverage read(GridGeometry domain, int... range) throws DataStoreException {

        if (domain == null) {
            domain = getGridGeometry();
        }

        if (range != null && range.length != 0) {
            throw new CoverageStoreException("Source or destination bands can not be used on portrayal images.");
        }

        CoordinateReferenceSystem crs = domain.getCoordinateReferenceSystem();
        Envelope paramEnv = domain.getEnvelope();


        //verify envelope and crs
        if (crs == null && paramEnv == null) {
            //use the max extent
            paramEnv = getGridGeometry().getEnvelope();
            crs = paramEnv.getCoordinateReferenceSystem();
        } else if (crs != null && paramEnv != null) {
            //check the envelope crs matches given crs
            if (!Utilities.equalsIgnoreMetadata(paramEnv.getCoordinateReferenceSystem(),crs)) {
                throw new CoverageStoreException("Invalid parameters : envelope crs do not match given crs.");
            }
        } else if (paramEnv != null) {
            //use the envelope crs
            crs = paramEnv.getCoordinateReferenceSystem();
        } else if (crs != null) {
            //use the given crs
            paramEnv = getGridGeometry().getEnvelope();
            try {
                paramEnv = Envelopes.transform(paramEnv, crs);
            } catch (TransformException ex) {
                throw new CoverageStoreException("Could not transform coverage envelope to given crs.");
            }
        }

        //estimate dimension if not given
        final Dimension dim;
        if (domain.isDefined(GridGeometry.EXTENT)) {
            GridExtent extent = domain.getExtent();
            dim = new Dimension((int) extent.getSize(0), (int) extent.getSize(1));
        } else {
            //we arbitrarly choose 1000 pixel on first axis, layers can have an infinite resolution.
            double[] resolution = new double[2];
            resolution[0] = paramEnv.getSpan(0)/1000;
            resolution[1] = resolution[0] * (paramEnv.getSpan(1)/paramEnv.getSpan(0));
            dim = new Dimension(
                (int) (paramEnv.getSpan(0) / resolution[0]),
                (int) (paramEnv.getSpan(1) / resolution[1]));
        }

        //calculate final grid to crs transform
        final AffineTransform gridToCRS = ReferencingUtilities.toAffine(dim, paramEnv);


        final CanvasDef canvas = new CanvasDef(dim, null);
        final ViewDef view = new ViewDef(paramEnv);

        final RenderedImage image;
        try {
            image = DefaultPortrayalService.portray(canvas, scene, view);
        } catch (PortrayalException ex) {
            throw new CoverageStoreException(ex.getMessage(),ex);
        }

        //build the coverage ---------------------------------------------------
        final GridCoverageBuilder gcb = new GridCoverageBuilder();
        gcb.setName(contextName);
        gcb.setRenderedImage(image);
        gcb.setPixelAnchor(PixelInCell.CELL_CORNER);
        gcb.setGridToCRS(gridToCRS);
        gcb.setCoordinateReferenceSystem(crs);
        return gcb.build();
    }

}
