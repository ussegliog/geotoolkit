/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2008 - 2009, Geomatys
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
package org.geotoolkit.display2d.canvas;

import java.awt.geom.Rectangle2D;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.geometry.GeneralDirectPosition;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.Resource;
import org.geotoolkit.coverage.io.CoverageStoreException;
import org.geotoolkit.display.SearchArea;
import org.geotoolkit.display.canvas.RenderingContext;
import org.geotoolkit.display2d.GraphicVisitor;
import org.geotoolkit.display2d.primitive.ProjectedCoverage;
import org.geotoolkit.display2d.primitive.ProjectedFeature;
import org.geotoolkit.display2d.primitive.SearchAreaJ2D;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.storage.coverage.CoverageExtractor;
import org.apache.sis.storage.GridCoverageResource;
import org.opengis.display.primitive.Graphic;
import org.opengis.referencing.operation.TransformException;

/**
 * A visitor which can be applied to the
 * {@link org.opengis.display.primitive.Graphic} objects of a scene and through
 * the {@code Graphic} objects, to the underlying
 * {@link org.opengis.feature.Feature} or
 * {@link org.geotoolkit.coverage.grid.GridCoverage}.
 *
 * @author Johann Sorel (Geomatys)
 * @module
 */
public abstract class AbstractGraphicVisitor implements GraphicVisitor {

    public abstract void visit(ProjectedFeature feature, RenderingContext2D context, SearchAreaJ2D area);

    public abstract void visit(ProjectedCoverage coverage, RenderingContext2D context, SearchAreaJ2D area);

    /**
     * {@inheritDoc }
     */
    @Override
    public void startVisit() {
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void endVisit() {
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void visit(final Graphic graphic, final RenderingContext context, final SearchArea area) {

        if(graphic == null ) return;

        if(graphic instanceof ProjectedFeature){
            visit((ProjectedFeature)graphic, (RenderingContext2D)context, (SearchAreaJ2D)area);
        }else if(graphic instanceof ProjectedCoverage){
            visit((ProjectedCoverage)graphic, (RenderingContext2D)context, (SearchAreaJ2D)area);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public boolean isStopRequested() {
        return false;
    }

    protected static CoverageExtractor.Ray rayExtraction(ProjectedCoverage projectedCoverage, RenderingContext2D context, SearchAreaJ2D area)
            throws DataStoreException, TransformException {

        //point in objective CRS
        final GeneralDirectPosition dp = new GeneralDirectPosition(context.getObjectiveCRS2D());
        final Rectangle2D bounds2D = area.getObjectiveShape().getBounds2D();
        dp.setOrdinate(0, bounds2D.getCenterX());
        dp.setOrdinate(1, bounds2D.getCenterY());

        final MapLayer layer = projectedCoverage.getLayer();
        final Resource resource = layer.getResource();
        if (resource instanceof GridCoverageResource) {
            final GridCoverageResource covRef = (GridCoverageResource) resource;
            final GridCoverage data = covRef.read(context.getGridGeometry()).forConvertedValues(true);
            return CoverageExtractor.rayExtraction(dp, data);
        } else {
            throw new CoverageStoreException("Resource is not a coverage.");
        }
    }
}
