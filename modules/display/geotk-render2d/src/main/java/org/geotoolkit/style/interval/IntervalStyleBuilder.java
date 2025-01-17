/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2009 Geomatys
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
package org.geotoolkit.style.interval;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.table.AbstractTableModel;
import org.apache.sis.internal.feature.AttributeConvention;
import org.apache.sis.internal.system.DefaultFactories;
import org.geotoolkit.storage.feature.query.QueryBuilder;
import org.geotoolkit.map.FeatureMapLayer;
import org.apache.sis.storage.DataStoreException;
import org.geotoolkit.storage.feature.FeatureStoreRuntimeException;
import org.geotoolkit.display2d.GO2Utilities;
import org.geotoolkit.style.MutableFeatureTypeStyle;
import org.geotoolkit.style.MutableRule;
import org.geotoolkit.style.MutableStyle;
import org.geotoolkit.style.MutableStyleFactory;
import org.geotoolkit.style.StyleConstants;
import org.opengis.feature.AttributeType;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.feature.Operation;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.feature.PropertyType;
import org.opengis.filter.And;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.PropertyIsGreaterThanOrEqualTo;
import org.opengis.filter.PropertyIsLessThan;
import org.opengis.filter.PropertyIsLessThanOrEqualTo;
import org.opengis.filter.expression.Divide;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.style.Fill;
import org.opengis.style.Graphic;
import org.opengis.style.GraphicalSymbol;
import org.opengis.style.LineSymbolizer;
import org.opengis.style.Mark;
import org.opengis.style.PointSymbolizer;
import org.opengis.style.PolygonSymbolizer;
import org.opengis.style.Stroke;
import org.opengis.style.StyleFactory;
import org.opengis.style.Symbolizer;

/**
 *
 * @author Johann Sorel (Geomatys)
 * @module
 */
public class IntervalStyleBuilder extends AbstractTableModel{

    private static NumberFormat FORMAT = NumberFormat.getNumberInstance();

    public static enum METHOD{
        EL,
        QANTILE,
        MANUAL
    };


    public final PropertyName noValue;
    private final MutableStyleFactory sf;
    private final FilterFactory ff;

    private FeatureMapLayer layer;
    private PropertyName classification;
    private PropertyName normalize;
    private int nbClasses = 5;
    private double[] values = new double[0];
    private Double[] allValues = new Double[0];
    private METHOD method = METHOD.EL;

    private boolean genericAnalyze = false;
    private boolean analyze = false;
    private final List<PropertyName> properties = new ArrayList<>();
    private long count = 0;
    private double minimum = 0;
    private double maximum = 0;
    private double sum = 0;
    private double mean = 0;
    private double median = 0;
    private Symbolizer template = null;

    public IntervalStyleBuilder() {
        this(null,null);
    }

    public IntervalStyleBuilder(final MutableStyleFactory styleFactory, final FilterFactory filterFactory){
        if(styleFactory == null){
             sf = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);
        }else{
            sf = styleFactory;
        }

        if(filterFactory == null){
             ff = DefaultFactories.forBuildin(FilterFactory.class);
        }else{
            ff = filterFactory;
        }

        noValue = ff.property("-");
    }

    public long getCount() {
        analyze();
        return count;
    }

    public double getSum() {
        analyze();
        return sum;
    }

    public double getMinimum() {
        analyze();
        return minimum;
    }

    public double getMaximum() {
        analyze();
        return maximum;
    }

    public double getMean() {
        analyze();
        return mean;
    }

    public double getMedian() {
        analyze();
        return median;
    }

    public double[] getValues() {
        analyze();
        return values;
    }

    public Double[] getAllValues(){
        return allValues.clone();
    }

    public void setValues(final double[] values) {
        this.values = values;
    }

    private void reset(){
        analyze = false;
    }

    public void setLayer(final FeatureMapLayer layer) {
        this.layer = layer;
        genericAnalyze = false;
        reset();
        isIntervalStyle(layer.getStyle());
    }

    public boolean isIntervalStyle(final MutableStyle style){
        if(style.featureTypeStyles().size() != 1) return false;
        final MutableFeatureTypeStyle fts = style.featureTypeStyles().get(0);
        return isIntervalStyle(fts);
    }

    public boolean isIntervalStyle(final MutableFeatureTypeStyle fts){

        if(fts.rules().isEmpty()) return false;

        for(MutableRule r : fts.rules()){

            Filter f = r.getFilter();

            if(f == null || r.isElseFilter()) return false;

            if(r.symbolizers().size() != 1) return false;

            if(f instanceof And){
                And and = (And) f;

                if(and.getChildren().size() != 2) return false;

                Filter op1 = and.getChildren().get(0);
                Filter op2 = and.getChildren().get(1);

                if(op2 instanceof PropertyIsGreaterThanOrEqualTo){
                    //flip order
                    op1 = op2;
                    op2 = and.getChildren().get(0);
                }

                if(op1 instanceof PropertyIsGreaterThanOrEqualTo){
                    PropertyIsGreaterThanOrEqualTo under = (PropertyIsGreaterThanOrEqualTo) op1;
                    Expression exp1 = under.getExpression1();
                    Expression exp2 = under.getExpression2();

                    if(exp1 instanceof Divide){
                        Divide div = (Divide) exp1;
                        if(!properties.contains(div.getExpression1())) return false;
                        if(!properties.contains(div.getExpression2())) return false;
                    }else if(exp1 instanceof PropertyName){
                        PropertyName name = (PropertyName) exp1;
                        if(!properties.contains(name)) return false;
                    }else{
                        return false;
                    }

                    if(!(exp2 instanceof Literal)){
                        return false;
                    }

                    if(op2 instanceof PropertyIsLessThan || op2 instanceof PropertyIsLessThanOrEqualTo){
                        BinaryComparisonOperator bc = (BinaryComparisonOperator)op2;
                        Expression ex1 = under.getExpression1();
                        Expression ex2 = under.getExpression2();

                        if(ex1 instanceof Divide){
                            Divide div = (Divide) ex1;
                            if(!properties.contains(div.getExpression1())) return false;
                            if(!properties.contains(div.getExpression2())) return false;
                        }else if(ex1 instanceof PropertyName){
                            PropertyName name = (PropertyName) ex1;
                            if(!properties.contains(name)) return false;
                        }else{
                            return false;
                        }

                        if(!(ex2 instanceof Literal)){
                            return false;
                        }


                    }else{
                        return false;
                    }

                }else{
                    return false;
                }


            }

            template = r.symbolizers().get(0);
        }

        method = METHOD.MANUAL;
        nbClasses = fts.rules().size()+1;

        return true;
    }


    public FeatureMapLayer getLayer() {
        return layer;
    }

    public void setClassification(final PropertyName classification) {
        this.classification = classification;
        genericAnalyze = false;
        reset();
    }

    public void setNormalize(final PropertyName normalize) {
        this.normalize = normalize;
        genericAnalyze = false;
        reset();
    }

    public void setMethod(final METHOD method) {
        this.method = method;
        reset();
    }

    public METHOD getMethod() {
        return method;
    }

    public void setNbClasses(final int nbClasses) {
        this.nbClasses = nbClasses;
        reset();
    }

    public int getNbClasses() {
        return nbClasses;
    }

    public List<PropertyName> getProperties() {
        analyze();
        return properties;
    }

    public Symbolizer getTemplate() {
        analyze();
        return template;
    }

    public void setTemplate(final Symbolizer template) {
        this.template = template;
    }

    private void genericAnalyze(){
        if(genericAnalyze) return;
        genericAnalyze = true;
        properties.clear();

        minimum = Double.POSITIVE_INFINITY;
        maximum = Double.NEGATIVE_INFINITY;
        count = 0;
        sum = 0;
        median = 0;
        mean = 0;


        //search the different numeric attributs
        final FeatureType schema;
        try {
            schema = layer.getResource().getType();
        } catch (DataStoreException ex) {
            throw new FeatureStoreRuntimeException(ex.getMessage(), ex);
        }

        for(PropertyType desc : schema.getProperties(true)){
            if(desc instanceof AttributeType){
                Class<?> type = ((AttributeType)desc).getValueClass();

                if(Number.class.isAssignableFrom(type) || type == byte.class || type==short.class ||
                   type==int.class || type==long.class || type==float.class || type == double.class){
                    properties.add(ff.property(desc.getName().tip().toString()));
                }
            }
        }

        //find the geometry class for template
        Class<?> geoClass = null;
        try{
            PropertyType geo = schema.getProperty(AttributeConvention.GEOMETRY_PROPERTY.toString());
            geoClass = ((AttributeType)((Operation)geo).getResult()).getValueClass();
        }catch(PropertyNotFoundException ex){
        }

        if(geoClass==null){
            return;
        }


        if(template==null){
            if(Polygon.class.isAssignableFrom(geoClass) || MultiPolygon.class.isAssignableFrom(geoClass)){
                template = createPolygonTemplate();
            }else if(LineString.class.isAssignableFrom(geoClass) || MultiLineString.class.isAssignableFrom(geoClass)){
                template = createLineTemplate();
            }else{
                template = createPointTemplate();
            }
        }


        //search the extreme values
        final QueryBuilder query = new QueryBuilder(schema.getName().toString());

        if(classification == null || layer == null) return;
        if(!properties.contains(classification)) return;

        final Set<String> qp = new HashSet<String>();
        qp.add(classification.getPropertyName());
        if(normalize != null && !normalize.equals(noValue)){
            qp.add(normalize.getPropertyName());
        }
        query.setProperties(qp.toArray(new String[0]));

        Iterator<Feature> features = null;
        try(Stream<Feature> stream = layer.getResource().subset(query.buildQuery()).features(false)){
            features = stream.iterator();
            List<Double> values = new ArrayList<Double>();

            while(features.hasNext()){
                Feature sf = features.next();
                count++;

                Number classifValue = classification.evaluate(sf, Number.class);
                double value;

                if(classifValue==null){
                    //skip null values in analyze
                    continue;
                }

                if(normalize == null || normalize.equals(noValue)){
                    value = classifValue.doubleValue();
                }else{
                    Number normalizeValue = normalize.evaluate(sf, Number.class);
                    value = classifValue.doubleValue() / normalizeValue.doubleValue();
                }

                values.add(value);
                sum += value;

                if(value < minimum){
                    minimum = value;
                }
                if(value > maximum){
                    maximum = value;
                }

            }

            mean = (minimum+maximum) / 2;

            //find the median
            allValues = values.toArray(new Double[values.size()]);
            Arrays.sort(allValues);

            if (values.isEmpty()) {
                median = 0;
            } else if (values.size() % 2 == 0) {
                median = (allValues[(allValues.length / 2) - 1] + allValues[allValues.length / 2]) / 2.0;
            } else {
                median = allValues[allValues.length / 2];
            }

        }catch(DataStoreException ex){
            ex.printStackTrace();
        }

    }

    private void analyze(){
        if(analyze) return;
        reset();
        genericAnalyze();
        analyze = true;

        if(method == METHOD.EL){
            values = new double[nbClasses+1];
            for(int i=0;i<values.length;i++){
                values[i] = minimum + (float)i / (values.length-1) * (maximum-minimum);
            }

        }else if(method == METHOD.QANTILE){
            values = new double[nbClasses+1];
            for(int i=0;i<values.length;i++){
                values[i] = allValues[i*(allValues.length-1)/(values.length-1)];
            }
        }else{
            if(values.length != nbClasses+1){
                values = Arrays.copyOf(values, nbClasses+1);
            }
        }


        analyze = true;
    }

    private Symbolizer createSymbolizer(final IntervalPalette palette,final double step){
        return derivateSymbolizer(template, palette.interpolate(step));
    }

    /**
     * Derivate a symbolizer with a new color.
     */
    private Symbolizer derivateSymbolizer(final Symbolizer symbol, final Color color){

        if(symbol instanceof PolygonSymbolizer){
            PolygonSymbolizer ps = (PolygonSymbolizer)symbol;
            Fill fill = sf.fill(sf.literal(color),ps.getFill().getOpacity());
            return sf.polygonSymbolizer(ps.getName(), ps.getGeometryPropertyName(),
                    ps.getDescription(), ps.getUnitOfMeasure(),
                    ps.getStroke(),fill,ps.getDisplacement(),ps.getPerpendicularOffset());
        }else if(symbol instanceof LineSymbolizer){
            LineSymbolizer ls = (LineSymbolizer) symbol;
            Stroke oldStroke = ls.getStroke();
            Stroke stroke = sf.stroke(sf.literal(color),oldStroke.getOpacity(),oldStroke.getWidth(),
                    oldStroke.getLineJoin(),oldStroke.getLineCap(),oldStroke.getDashArray(),oldStroke.getDashOffset());
            return sf.lineSymbolizer(ls.getName(), ls.getGeometryPropertyName(),
                    ls.getDescription(), ls.getUnitOfMeasure(), stroke, ls.getPerpendicularOffset());
        }else if(symbol instanceof PointSymbolizer){
            PointSymbolizer ps = (PointSymbolizer) symbol;
            Graphic oldGraphic = ps.getGraphic();
            Mark oldMark = (Mark) oldGraphic.graphicalSymbols().get(0);
            Fill fill = sf.fill(sf.literal(color),oldMark.getFill().getOpacity());
            List<GraphicalSymbol> symbols = new ArrayList<GraphicalSymbol>();
            symbols.add(sf.mark(oldMark.getWellKnownName(), fill, oldMark.getStroke()));
            Graphic graphic = sf.graphic(symbols, oldGraphic.getOpacity(),oldGraphic.getSize(),
                    oldGraphic.getRotation(),oldGraphic.getAnchorPoint(),oldGraphic.getDisplacement());
            return sf.pointSymbolizer(graphic,ps.getGeometryPropertyName());
        }else{
            throw new IllegalArgumentException("unexpected symbolizer type : " + symbol);
        }

    }

    public List<MutableRule> generateRules(final IntervalPalette palette){
        return generateRules(palette, Filter.INCLUDE);
    }

    public List<MutableRule> generateRules(final IntervalPalette palette, Filter combinedFilter){
        analyze();
        List<MutableRule> rules = new ArrayList<MutableRule>();

        final Expression exp;
        if(normalize == null || normalize.equals(noValue)){
            exp = classification;
        }else{
            exp = ff.divide(classification, normalize);
        }

        for(int i=1;i<values.length;i++){
            final MutableRule rule = sf.rule();

            double start = values[i-1];
            double end = values[i];

            //create the filter and title
            final Filter interval;
            final String title;
            if(i == values.length-1){
                //last element
                Filter above = ff.greaterOrEqual(exp, ff.literal(start));
                Filter under = ff.lessOrEqual(exp, ff.literal(end));
                interval = ff.and(above, under);
                title = "[ " + FORMAT.format(start) + " -> " + FORMAT.format(end) + " ]";
            }else{
                Filter above = ff.greaterOrEqual(exp, ff.literal(start));
                Filter under = ff.less(exp, ff.literal(end));
                interval = ff.and(above, under);
                title = "[ " + FORMAT.format(start) + " -> " + FORMAT.format(end) + " [";
            }
            if(Filter.INCLUDE.equals(combinedFilter) || combinedFilter==null){
                rule.setFilter(interval);
            }else{
                rule.setFilter(ff.and(combinedFilter,interval));
            }
            rule.setName(title);
            rule.setDescription(sf.description(title, title));

            //create the style
            Symbolizer symbol = createSymbolizer(palette,(double)(i-1)/(values.length-2));
            rule.symbolizers().add(symbol);

            rules.add(rule);
        }

        return rules;
    }

    ////////////// TABLE MODEL /////////////////////////////////////////////////

    @Override
    public int getRowCount() {
        return nbClasses;
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        analyze();
        return values[rowIndex];
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return method == METHOD.MANUAL;
    }

    public static PointSymbolizer createPointTemplate(){
        final MutableStyleFactory sf = GO2Utilities.STYLE_FACTORY;
        final FilterFactory ff = GO2Utilities.FILTER_FACTORY;
        final Stroke stroke = sf.stroke(Color.BLACK, 1);
        final Fill fill = sf.fill(Color.BLUE);
        final List<GraphicalSymbol> symbols = new ArrayList<GraphicalSymbol>();
        symbols.add(sf.mark(StyleConstants.MARK_CIRCLE, fill, stroke));
        final Graphic gra = sf.graphic(symbols, ff.literal(1), ff.literal(12), ff.literal(0), sf.anchorPoint(), sf.displacement());
        return sf.pointSymbolizer(gra, null);
    }

    public static LineSymbolizer createLineTemplate(){
        final MutableStyleFactory sf = GO2Utilities.STYLE_FACTORY;
        final FilterFactory ff = GO2Utilities.FILTER_FACTORY;
        final Stroke stroke = sf.stroke(Color.BLUE, 2);
        return sf.lineSymbolizer(stroke,null);
    }

    public static PolygonSymbolizer createPolygonTemplate(){
        final MutableStyleFactory sf = GO2Utilities.STYLE_FACTORY;
        final FilterFactory ff = GO2Utilities.FILTER_FACTORY;
        final Stroke stroke = sf.stroke(Color.BLACK, 1);
        final Fill fill = sf.fill(Color.BLUE);
        return sf.polygonSymbolizer(stroke,fill,null);
    }

}
