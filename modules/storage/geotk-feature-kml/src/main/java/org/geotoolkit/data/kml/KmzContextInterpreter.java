/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2010, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotoolkit.data.kml;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.FeatureSet;
import org.apache.sis.storage.GridCoverageResource;
import org.geotoolkit.data.kml.model.AbstractGeometry;
import org.geotoolkit.data.kml.model.AbstractStyleSelector;
import org.geotoolkit.data.kml.model.Boundary;
import org.geotoolkit.data.kml.model.EnumAltitudeMode;
import org.geotoolkit.data.kml.model.Icon;
import org.geotoolkit.data.kml.model.Kml;
import org.geotoolkit.data.kml.model.LabelStyle;
import org.geotoolkit.data.kml.model.LatLonBox;
import org.geotoolkit.data.kml.model.LineStyle;
import org.geotoolkit.data.kml.model.PolyStyle;
import org.geotoolkit.data.kml.model.Style;
import org.geotoolkit.data.kml.xml.KmlConstants;
import org.geotoolkit.data.kml.xml.KmlWriter;
import org.geotoolkit.display2d.GO2Utilities;
import org.geotoolkit.image.interpolation.InterpolationCase;
import org.geotoolkit.internal.coverage.CoverageUtilities;
import org.geotoolkit.map.CoverageMapLayer;
import org.geotoolkit.map.FeatureMapLayer;
import org.geotoolkit.map.MapContext;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.nio.ZipUtilities;
import org.geotoolkit.processing.coverage.resample.ResampleProcess;
import org.geotoolkit.style.MutableFeatureTypeStyle;
import org.geotoolkit.style.MutableRule;
import org.geotoolkit.style.MutableStyle;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.PropertyType;
import org.opengis.filter.expression.Expression;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.style.ExtensionSymbolizer;
import org.opengis.style.LineSymbolizer;
import org.opengis.style.PointSymbolizer;
import org.opengis.style.PolygonSymbolizer;
import org.opengis.style.RasterSymbolizer;
import org.opengis.style.Rule;
import org.opengis.style.Symbolizer;
import org.opengis.style.TextSymbolizer;

/**
 *
 * @author Samuel Andrés
 * @module
 */
public class KmzContextInterpreter {

    private static final KmlFactory KML_FACTORY = DefaultKmlFactory.getInstance();
    private static final AtomicInteger increment = new AtomicInteger();
    private static final List<Entry<Rule, URI>> IDENTIFICATORS_MAP = new ArrayList<>();

    private final Path tempDirectory;
    private final Path filesDirectory;

    public KmzContextInterpreter() throws IOException {
        tempDirectory = Files.createTempDirectory("geotk_kmz");
        filesDirectory = tempDirectory.resolve("files");
        Files.createDirectories(filesDirectory);
    }

    public void writeKmz(MapContext context, Path kmzOutput)
            throws Exception {

        final Kml kml = KML_FACTORY.createKml();
        final Feature folder = KML_FACTORY.createFolder();
        kml.setAbstractFeature(folder);

        // Creating KML file
        final Path docKml = tempDirectory.resolve("doc.kml");
        final List<Feature> fs = new ArrayList<>();
        for (final MapLayer layer : context.layers()) {
            this.writeStyle(layer.getStyle(), folder);
            if (layer instanceof CoverageMapLayer) {
                fs.add(writeCoverageMapLayer((CoverageMapLayer) layer));
            } else if (layer instanceof FeatureMapLayer) {
                fs.add(writeFeatureMapLayer((FeatureMapLayer) layer));
            }
        }
        folder.setPropertyValue(KmlConstants.TAG_FEATURES, fs);

        // Writing KML file
        final KmlWriter writer = new KmlWriter();
        writer.setOutput(docKml);
        writer.write(kml);
        writer.dispose();

        // Creating KMZ
        ZipUtilities.zip(kmzOutput, ZipOutputStream.DEFLATED, 9, null, filesDirectory, docKml);
    }

    /**
     * Retrieves a style identifier.
     */
    private String getIdentificator() {
        return "id" + increment.incrementAndGet();
    }

    //--------------------------------------------------------------------------
    // STYLES CONVERSION METHODS
    //--------------------------------------------------------------------------

    /**
     * Writes KML styles elements mapping a layer style.
     *
     * <p style="color: red; font-weight: bold; font-style: italic;">BE CAREFUL:
     * SLD Styles reference thei associated features BUT KML specification is different
     * because each feature references its own style.</p>
     */
    private Feature writeStyle(MutableStyle style, Feature container) throws URISyntaxException {
        final List<MutableFeatureTypeStyle> featureTypeStyles = style.featureTypeStyles();
        for (int i = 0, num = featureTypeStyles.size(); i < num; i++) {
            container = this.writeFeatureTypeStyle(featureTypeStyles.get(i), container);
        }
        return container;
    }

    /**
     * Writes a feature type style.
     */
    private Feature writeFeatureTypeStyle(MutableFeatureTypeStyle featureTypeStyle, Feature container)
            throws URISyntaxException
    {
        final List<MutableRule> rules = featureTypeStyle.rules();
        final List<AbstractStyleSelector> fs = new ArrayList<>();
        for (int i = 0, num = rules.size(); i < num; i++) {
            fs.add(this.writeRule(rules.get(i)));
        }
        container.setPropertyValue(KmlConstants.TAG_STYLE_SELECTOR, fs);
        return container;
    }

    /**
     * Retrieves a KML StyleSelector element mapping SLD Rule.
     */
    private AbstractStyleSelector writeRule(MutableRule rule) throws URISyntaxException {
        final Style styleSelector = KML_FACTORY.createStyle();
        final List<Symbolizer> symbolizers = rule.symbolizers();

        for (int i = 0, num = symbolizers.size(); i < num; i++) {
            this.writeSymbolizer(symbolizers.get(i), styleSelector);
        }

        // Links rule filter with Style URI
        final String id = this.getIdentificator();
        styleSelector.setIdAttributes(KML_FACTORY.createIdAttributes(id, null));
        IDENTIFICATORS_MAP.add(new SimpleEntry<Rule, URI>(rule, new URI("#" + id)));
        return styleSelector;
    }

    /**
     * Writes KML color styles mapping SLD Symbolizers.
     * Color styles are written into KML Style selector.
     */
    private AbstractStyleSelector writeSymbolizer(
            Symbolizer symbolizer, Style styleSelector) {

        if (symbolizer instanceof ExtensionSymbolizer) {
        }

        // LineSymbolizer mapping
        else if (symbolizer instanceof LineSymbolizer) {
            final LineSymbolizer lineSymbolizer = (LineSymbolizer) symbolizer;
            final LineStyle lineStyle = ((styleSelector.getLineStyle() == null)
                    ? KML_FACTORY.createLineStyle() : styleSelector.getLineStyle());
            lineStyle.setWidth((Double) this.writeExpression(
                    lineSymbolizer.getStroke().getWidth(), Double.class, null));
            lineStyle.setColor((Color) this.writeExpression(
                    lineSymbolizer.getStroke().getColor(), Color.class, null));
            styleSelector.setLineStyle(lineStyle);
        }

        // PointSymbolizezr mapping
        else if (symbolizer instanceof PointSymbolizer) {
//            PointSymbolizer pointSymbolizer = (PointSymbolizer) symbolizer;
//            IconStyle iconStyle = KML_FACTORY.createIconStyle();
//            GraphicalSymbol gs = ((GraphicalSymbol) pointSymbolizer.getGraphic().graphicalSymbols().get(0));
//            gs.
        }

        // PolygonSymbolizer mapping
        else if (symbolizer instanceof PolygonSymbolizer) {
            final PolygonSymbolizer polygonSymbolizer = (PolygonSymbolizer) symbolizer;
            final PolyStyle polyStyle = KML_FACTORY.createPolyStyle();

            // Fill
            if (polygonSymbolizer.getFill() == null) {
                polyStyle.setFill(false);
            } else {
                polyStyle.setFill(true);
                polyStyle.setColor((Color) this.writeExpression(
                        polygonSymbolizer.getFill().getColor(), Color.class, null));
            }

            // Outline
            if (polygonSymbolizer.getStroke() == null) {
                polyStyle.setOutline(false);
            } else if(styleSelector.getLineStyle() == null) {
                polyStyle.setOutline(true);
                final LineStyle lineStyle = KML_FACTORY.createLineStyle();
                lineStyle.setColor((Color) this.writeExpression(
                        polygonSymbolizer.getStroke().getColor(), Color.class, null));
                lineStyle.setWidth((Double) this.writeExpression(
                        polygonSymbolizer.getStroke().getWidth(), Double.class, null));
                styleSelector.setLineStyle(lineStyle);
            }
            styleSelector.setPolyStyle(polyStyle);
        } else if (symbolizer instanceof RasterSymbolizer) {
        } else if (symbolizer instanceof TextSymbolizer) {
            final TextSymbolizer textSymbolizer = (TextSymbolizer) symbolizer;
            final LabelStyle labelStyle = KML_FACTORY.createLabelStyle();
            if (textSymbolizer.getFont() != null) {
                textSymbolizer.getFont().getSize();
            }
            if (textSymbolizer.getFill() != null) {
                labelStyle.setColor((Color) this.writeExpression(
                        textSymbolizer.getFill().getColor(), Color.class, null));
            }
            styleSelector.setLabelStyle(labelStyle);
        }
        return styleSelector;
    }

    /**
     * Writes a static expression.
     */
    private Object writeExpression(Expression expression, Class<?> type, Object object) {
        if (GO2Utilities.isStatic(Expression.NIL)) {
            return expression.evaluate(object, type);
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // FEATURES TRANSFORMATIONS
    //--------------------------------------------------------------------------

    /**
     * Transforms a FeatureMapLAyer in KML Folder.
     */
    private Feature writeFeatureMapLayer(final FeatureMapLayer featureMapLayer) throws URISyntaxException, DataStoreException {
        final FeatureSet resource = featureMapLayer.getResource();
        final Feature folder = KML_FACTORY.createFolder();
        final List<Feature> fs;
        try (Stream<Feature> stream = resource.features(false)) {
            fs = stream.map(this::writeFeature).collect(Collectors.toList());
        }
        folder.setPropertyValue(KmlConstants.TAG_FEATURES, fs);
        return folder;
    }

    /**
     * Transforms a feature into KML feature (Placemak if original
     * features contents a geometry, or Folder otherwise).
     */
    private Feature writeFeature(final Feature feature) {
        Feature kmlFeature = null;
        for (final PropertyType type : feature.getType().getProperties(true)) {
            final Object val = feature.getPropertyValue(type.getName().toString());
            if (val instanceof Feature) {
                kmlFeature = KML_FACTORY.createFolder();
                kmlFeature.setPropertyValue(KmlConstants.TAG_FEATURES, writeFeature((Feature) val));
            } else if (val instanceof Geometry) {
                kmlFeature = KML_FACTORY.createPlacemark();
                kmlFeature.setPropertyValue(KmlConstants.TAG_GEOMETRY, val);
            } else {
                //System.out.println("PAS FEATURE.");
            }
        }

        // Search feature style URI
        for (Entry<Rule, URI> e : IDENTIFICATORS_MAP) {
            final Rule rule = e.getKey();
            if (rule.getFilter().evaluate(feature)) {
                kmlFeature.setPropertyValue(KmlConstants.TAG_STYLE_URL, e.getValue());
                for (Symbolizer s : rule.symbolizers()) {
                    if (s instanceof TextSymbolizer) {
                        final Expression label = ((TextSymbolizer) s).getLabel();
                        if (label != null) {
                            kmlFeature.setPropertyValue(KmlConstants.TAG_NAME, writeExpression(label, String.class, feature));
                        }
                    }
                }
                break;
            }
        }
        return kmlFeature;
    }

    /**
     * Transforms a JTS Geometry into KML Geometry.
     */
    private AbstractGeometry writeGeometry(Geometry geometry) {
        final AbstractGeometry resultat;

        if (geometry instanceof GeometryCollection) {
            final List<AbstractGeometry> liste = new ArrayList<>();
            if (geometry instanceof MultiPolygon) {
                final MultiPolygon multipolygon = (MultiPolygon) geometry;
                for (int i = 0, num = multipolygon.getNumGeometries(); i < num; i++) {
                    liste.add(this.writeGeometry(multipolygon.getGeometryN(i)));
                }
            }
            resultat = KML_FACTORY.createMultiGeometry();
            ((org.geotoolkit.data.kml.model.MultiGeometry) resultat).setGeometries(liste);
        } else if (geometry instanceof Polygon) {
            final Polygon polygon = (Polygon) geometry;
            final Boundary externBound = KML_FACTORY.createBoundary(
                    (org.geotoolkit.data.kml.model.LinearRing) writeGeometry(polygon.getExteriorRing()), null, null);
            final List<Boundary> internBounds = new ArrayList<>();
            for (int i = 0, num = polygon.getNumInteriorRing(); i < num; i++) {
                internBounds.add(KML_FACTORY.createBoundary((org.geotoolkit.data.kml.model.LinearRing) this.writeGeometry(polygon.getInteriorRingN(i)), null, null));
            }
            resultat = KML_FACTORY.createPolygon(externBound, internBounds);
        } else if (geometry instanceof LineString) {
            if (geometry instanceof LinearRing) {
                resultat = KML_FACTORY.createLinearRing(((LinearRing) geometry).getCoordinateSequence());
            } else {
                resultat = KML_FACTORY.createLineString(((LineString) geometry).getCoordinateSequence());
            }
        } else {
            resultat = null;
        }
        return resultat;
    }

    /**
     * Transforms a CoverageMapLayer into KML GroundOverlay.
     */
    private Feature writeCoverageMapLayer(MapLayer coverageMapLayer) throws Exception {
        final Feature groundOverlay = KML_FACTORY.createGroundOverlay();
        final CoordinateReferenceSystem targetCrs = CommonCRS.WGS84.normalizedGeographic();

        final GridCoverageResource ref = (GridCoverageResource) coverageMapLayer.getResource();
        final GridCoverage coverage = ref.read(null);
        final GridCoverage targetCoverage = new ResampleProcess(coverage, targetCrs, null, InterpolationCase.NEIGHBOR, null).executeNow();
        final Envelope envelope = targetCoverage.getGridGeometry().getEnvelope();
        final CharSequence name = CoverageUtilities.getName(targetCoverage);

        // Creating image file and Writting referenced image into.
        final Path img = filesDirectory.resolve(name.toString()+".png");
        try (OutputStream outputStream = Files.newOutputStream(img, CREATE, WRITE, TRUNCATE_EXISTING)) {
            ImageIO.write(targetCoverage.render(null), "png", outputStream);
        }

        final Icon image = KML_FACTORY.createIcon(KML_FACTORY.createLink());
        image.setHref(filesDirectory.getFileName().toString() + File.separator + name);
        groundOverlay.setPropertyValue(KmlConstants.TAG_NAME, name.toString());
        groundOverlay.setPropertyValue(KmlConstants.TAG_ICON, image);
        groundOverlay.setPropertyValue(KmlConstants.TAG_ALTITUDE, 1.0);
        groundOverlay.setPropertyValue(KmlConstants.TAG_ALTITUDE_MODE, EnumAltitudeMode.CLAMP_TO_GROUND);

        final LatLonBox latLonBox = KML_FACTORY.createLatLonBox();
        latLonBox.setNorth(envelope.getMaximum(1));
        latLonBox.setSouth(envelope.getMinimum(1));
        latLonBox.setEast(envelope.getMaximum(0));
        latLonBox.setWest(envelope.getMinimum(0));

        groundOverlay.setPropertyValue(KmlConstants.TAG_LAT_LON_BOX, latLonBox);

        return groundOverlay;
    }
}
