/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2008 - 2009, Geomatys
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
package org.geotoolkit.style.function;

import java.awt.Color;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.media.jai.ImageLayout;
import javax.media.jai.NullOpImage;
import javax.media.jai.OpImage;

import org.geotoolkit.filter.AbstractExpression;
import org.geotoolkit.internal.coverage.CoverageUtilities;
import org.geotoolkit.internal.image.ColorUtilities;
import org.geotoolkit.resources.Errors;
import org.geotoolkit.util.Converters;
import org.geotoolkit.util.collection.UnmodifiableArrayList;

import org.opengis.feature.Feature;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.Literal;

import static org.geotoolkit.style.StyleConstants.*;
import org.geotoolkit.util.converter.Classes;
import static org.opengis.filter.expression.Expression.*;

/**
 * 
 * Implementation of "Interpolation" as a normal function.
 * <p>
 * This implementation is compatible with the Function
 * interface; the parameter list can be used to set the
 * threshold values etc...
 * <p>
 *
 * This function expects:
 * <ol>
 * <li>PropertyName; use "Rasterdata" to indicate this is a colour map
 * <li>Literal: lookup value
 * <li>Literal: InterpolationPoint : data 1
 * <li>Literal: InterpolationPoint : value 1
 * <li>Literal: InterpolationPoint : data 2
 * <li>Literal: InterpolationPoint : value 2
 * <li>Literal: Mode
 * <li>Literal: Method
 * </ol>
 * In reality any expression will do.
 * 
 * @author Johann Sorel (Geomatys)
 * @module pending
 */
public class DefaultInterpolate extends AbstractExpression implements Interpolate {

    private final Expression lookup;
    private final InterpolationPoint[] points;
    private final Method method;
    private final Mode mode;
    private final Literal fallback;
    
    
    /**
     * Make the instance of FunctionName available in
     * a consistent spot.
     */
    public static final FunctionName NAME = new Name();

    /**
     * Describe how this function works.
     * (should be available via FactoryFinder lookup...)
     */
    public static class Name implements FunctionName {

        @Override
        public int getArgumentCount() {
            return -2; // indicating unbounded, 2 minimum
        }

        @Override
        public List<String> getArgumentNames() {
            return Arrays.asList(new String[]{
                        "LookupValue",
                        "Data 1", "Value 1",
                        "Data 2", "Value 2",
                        "linear, cosine or cubic",
                        "numeric or color"
                    });
        }

        @Override
        public String getName() {
            return "Interpolate";
        }
    };

    
    public DefaultInterpolate(final Expression LookUpValue, List<InterpolationPoint> values, 
           final Method method, final Mode mode,final Literal fallback){
                
        if(values == null ){
            values = Collections.emptyList();
        }
        
        this.lookup = (LookUpValue == null || LookUpValue == NIL) ?  DEFAULT_CATEGORIZE_LOOKUP : LookUpValue;
        this.points = values.toArray(new InterpolationPoint[values.size()]);

        Arrays.sort(points, new Comparator<InterpolationPoint>(){
            @Override
            public int compare(InterpolationPoint t1, InterpolationPoint t2) {
                final Number v1 = t1.getData();
                final Number v2 = t2.getData();
                if(v1 instanceof Float && Float.isNaN(v1.floatValue())){
                    return -1;
                }else if(v1 instanceof Double && Double.isNaN(v1.doubleValue())){
                    return -1;
                }else if(v2 instanceof Float && Float.isNaN(v2.floatValue())){
                    return +1;
                }else if(v2 instanceof Double && Double.isNaN(v2.doubleValue())){
                    return +1;
                }
                
                final double diff = v1.doubleValue() - v2.doubleValue();
                if(diff < 0){
                    return -1;
                }else if(diff > 0){
                    return +1;
                }else{
                    return 0;
                }
            }
        });


        this.method = (method == null) ? Method.COLOR : method;
        this.mode = (mode == null) ? Mode.LINEAR : mode;
        this.fallback = (fallback == null) ? DEFAULT_FALLBACK : fallback;
    }
    

    @Override
    public String getName() {
        return "Interpolate";
    }

    @Override
    public List<Expression> getParameters() {
        final List<Expression> params = new ArrayList<Expression>();
        params.add(lookup);
        return params;
    }

    @Override
    public Object accept(final ExpressionVisitor visitor, final Object extraData) {
        return visitor.visit(this, extraData);
    }

    @Override
    public Object evaluate(final Object object) {
        
        if (object instanceof RenderedImage) {
            return evaluateImage((RenderedImage) object);
        }
        
        return evaluate(object, Object.class);
    }

    @Override
    public Object evaluate(final Object object, final Class c) {
                
        final Number value;        
        if(object instanceof Feature){            
            final Feature f = (Feature)object;
            value = lookup.evaluate(f,Number.class);
        }else if(object instanceof Number){
            value = (Number)object;
        }else{
            return fallback.evaluate(object,c);
        }
        
        final double dval = value.doubleValue();
        
        InterpolationPoint before = null;
        InterpolationPoint after = null;
        for(InterpolationPoint ip : points){
            final Number ipnum = ip.getData();            
            final double ipval;
            
            if(ipnum instanceof Double && Double.isNaN(ipnum.doubleValue())){                
                if(!(value instanceof Double)){
                    continue;
                }
                
                ipval = ipnum.doubleValue();
                
                if(Double.isNaN(ipval)){
                    //if we want exact NaN match use doubleToRawLongBits
                    if(Double.doubleToLongBits(ipval) == Double.doubleToLongBits(dval)){
                        before = ip;
                        break;
                    }else{
                        continue;
                    }
                }
            }else if(ipnum instanceof Float && Float.isNaN(ipnum.floatValue())){
                if(!(value instanceof Float)){
                    continue;
                }
                
                ipval = ipnum.doubleValue();
                final float ipfloat = ipnum.floatValue();
                
                if(Float.isNaN(ipfloat)){
                    //if we want exact NaN match use floatToRawLongBits
                    if(Float.floatToIntBits(ipfloat) == Float.floatToIntBits(value.floatValue())){
                        before = ip;
                        break;
                    }else{
                        continue;
                    }
                }
            }else{
                ipval = ipnum.doubleValue();
            }
            
            
            if(ipval < dval){
                before = ip;
            }else if(ipval > dval){
                after = ip;
                break;
            }else{
                //exact match
                return ip.getValue().evaluate(object,c);
            }
        }

        if(before == null && after == null){
            //no value associated, surely an NaN value
            //return a translucent color
            return Converters.convert( new Color(0,0,0,0) , c);
            
        }else if(before == null){
            //only have an over value
            return after.getValue().evaluate(object,c);
        }else if(after == null){
            //only have an under value
            return before.getValue().evaluate(object,c);
        }else{
            //must interpolate
            final double d1 = before.getData().doubleValue();
            final double d2 = after.getData().doubleValue();
            final double pourcent = (dval - d1)/ (d2 - d1);

            final Object o1 = before.getValue().evaluate(object,c);
            final Object o2 = after.getValue().evaluate(object,c);
            if(o1 instanceof Color && o2 instanceof Color){
                //datas are not numbers, looks like we deal with colors
                final Color c1 = before.getValue().evaluate(object,Color.class);
                final Color c2 = after.getValue().evaluate(object,Color.class);
                final int argb1 = c1.getRGB();
                final int argb2 = c2.getRGB();

                final int lastAlpha     = (argb1>>>24) & 0xFF;
                final int lastRed       = (argb1>>>16) & 0xFF;
                final int lastGreen     = (argb1>>> 8) & 0xFF;
                final int lastBlue      = (argb1>>> 0) & 0xFF;
                final int alphaInterval = ((argb2>>>24) & 0xFF) - lastAlpha;
                final int redInterval   = ((argb2>>>16) & 0xFF) - lastRed;
                final int greenInterval = ((argb2>>> 8) & 0xFF) - lastGreen;
                final int blueInterval  = ((argb2>>> 0) & 0xFF) - lastBlue;

                //calculate interpolated color
                int a = lastAlpha + (int)(pourcent*alphaInterval);
                int r = lastRed   + (int)(pourcent*redInterval);
                int g = lastGreen + (int)(pourcent*greenInterval);
                int b = lastBlue  + (int)(pourcent*blueInterval);
                return Converters.convert( new Color(r, g, b, a) , c);
            }else{
                final Double n1 = before.getValue().evaluate(object,Double.class);
                final Double n2 = after.getValue().evaluate(object,Double.class);
                return Converters.convert( (n1 + pourcent*(n2-n1)) , c);
            }

        }
        
    }
    
    /**
     * Recolor image
     * @param image
     * @return recolored image
     */
    private RenderedImage evaluateImage (final RenderedImage image) {
        final int visibleBand = CoverageUtilities.getVisibleBand(image);
            final ColorModel candidate = image.getColorModel();

            //TODO : this should be used when the index color model can not handle signed values
            //
            //final SampleModel sm = image.getSampleModel();
            //final int datatype = sm.getDataType();
            //if(datatype == DataBuffer.TYPE_SHORT){
            //    final ColorModel model = new CompatibleColorModel(16, function);
            //    final ImageLayout layout = new ImageLayout().setColorModel(model);
            //    return new NullOpImage(image, layout, null, OpImage.OP_COMPUTE_BOUND);
            //}


            /*
             * Extracts the ARGB codes from the ColorModel and invokes the
             * transformColormap(...) method.
             */
            final int[] ARGB;
            final ColorModel model;
            if(candidate instanceof IndexColorModel) {
                final IndexColorModel colors = (IndexColorModel) candidate;
                final int mapSize = colors.getMapSize();
                ARGB = new int[mapSize];
                colors.getRGBs(ARGB);

                transformColormap(ARGB);
                model = ColorUtilities.getIndexColorModel(ARGB, 1, visibleBand, -1);

            } else if(candidate instanceof ComponentColorModel) {
                final ComponentColorModel colors = (ComponentColorModel) candidate;
                final int nbbit = colors.getPixelSize();
                final int type = image.getSampleModel().getDataType();

                if(type == DataBuffer.TYPE_BYTE || type == DataBuffer.TYPE_USHORT){
                    final int mapSize = 1 << nbbit;
                    ARGB = new int[mapSize];

                    for(int j=0; j<mapSize;j++){
                        int v = j*255/mapSize;
                        int a = 255 << 24;
                        int r = v << 16;
                        int g = v <<  8;
                        int b = v <<  0;
                        ARGB[j] = a|r|g|b;
                    }

                    transformColormap(ARGB);
                    model = ColorUtilities.getIndexColorModel(ARGB, 1, visibleBand, -1);

                } else {
                    //we can't handle a index color model when values exceed int max value
                    model = new CompatibleColorModel(nbbit, this);
                }

            } else {
                // Current implementation supports only sources that use of index color model
                // and component color model
                throw new IllegalArgumentException(Errors.format(Errors.Keys.ILLEGAL_CLASS_$2,
                        Classes.getClass(candidate), IndexColorModel.class));
            }
            
            /*
            * Gives the color model to the image layout and creates a new image using the Null
            * operation, which merely propagates its first source along the operation chain
            * unmodified (except for the ColorModel given in the layout in this case).
            */
           final ImageLayout layout = new ImageLayout().setColorModel(model);
           return new NullOpImage(image, layout, null, OpImage.OP_COMPUTE_BOUND);
    }
    
    private int[] transformColormap(final int[] ARGB) {
        final List<InterpolationPoint> points = getInterpolationPoints();
        final double[] SE_VALUES = new double[points.size()];
        final int[] SE_ARGB = new int[points.size()];
        for (int i = 0, n = points.size(); i < n; i++) {
            final InterpolationPoint point = points.get(i);
            SE_VALUES[i] = point.getData().doubleValue();
            SE_ARGB[i] = point.getValue().evaluate(null, Color.class).getRGB();
        }

        int lastStep = -1;
        int lastColor = -1;
        for (int k = 0; k < SE_VALUES.length; k++) {
            final double geoValue = SE_VALUES[k];
            final int currentColor = SE_ARGB[k];
            final int currentStep = (int) geoValue;

            //first element, dont interpolate colors
            if (k == 0) {
                lastColor = currentColor;
                lastStep = -1;
            }

            final int stepInterval = currentStep - lastStep;
            final int lastAlpha = (lastColor >>> 24) & 0xFF;
            final int lastRed = (lastColor >>> 16) & 0xFF;
            final int lastGreen = (lastColor >>> 8) & 0xFF;
            final int lastBlue = (lastColor >>> 0) & 0xFF;
            final int alphaInterval = ((currentColor >>> 24) & 0xFF) - lastAlpha;
            final int redInterval = ((currentColor >>> 16) & 0xFF) - lastRed;
            final int greenInterval = ((currentColor >>> 8) & 0xFF) - lastGreen;
            final int blueInterval = ((currentColor >>> 0) & 0xFF) - lastBlue;
            for (int i = lastStep + 1; (i <= currentStep && i < ARGB.length); i++) {
                //calculate interpolated color
                final int relativePosition = i - lastStep;
                final double pourcent = (double) ((double) relativePosition / (double) stepInterval);
                int a = lastAlpha + (int) (pourcent * alphaInterval);
                int r = lastRed + (int) (pourcent * redInterval);
                int g = lastGreen + (int) (pourcent * greenInterval);
                int b = lastBlue + (int) (pourcent * blueInterval);
                a <<= 24;
                r <<= 16;
                g <<= 8;
                b <<= 0;
                ARGB[i] = a | r | g | b;
            }

            lastStep = (int) currentStep;
            lastColor = currentColor;

            //last element, fill the remaining cell with the color
            if (k == SE_VALUES.length - 1) {
                for (int i = lastStep; i < ARGB.length; i++) {
                    ARGB[i] = currentColor;
                }
            }

        }
        return ARGB;
    }

    @Override
    public Literal getFallbackValue() {
        return fallback;
    }

    @Override
    public Expression getLookupValue() {
        return lookup;
    }

    @Override
    public List<InterpolationPoint> getInterpolationPoints() {
        return UnmodifiableArrayList.wrap(points);
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    @Override
    public Method getMethod() {
        return method;
    }
    
}
