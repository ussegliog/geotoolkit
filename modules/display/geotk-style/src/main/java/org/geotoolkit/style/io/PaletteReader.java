/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2013, Geomatys
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
package org.geotoolkit.style.io;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.sis.internal.system.DefaultFactories;
import org.geotoolkit.io.SimpleFileFilter;
import org.geotoolkit.style.MutableStyleFactory;
import org.geotoolkit.style.StyleConstants;
import static org.geotoolkit.style.StyleConstants.DEFAULT_CATEGORIZE_LOOKUP;
import static org.geotoolkit.style.StyleConstants.DEFAULT_FALLBACK;
import org.geotoolkit.style.function.InterpolationPoint;
import org.geotoolkit.style.function.Method;
import org.geotoolkit.style.function.Mode;
import org.geotoolkit.style.function.ThreshholdsBelongTo;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Function;
import org.opengis.style.ColorMap;
import org.opengis.style.StyleFactory;

/**
 * Palette reader based on pattern.
 * template :
 * - regex ignore line
 * (newline)
 * - values pattern (v1,r1,g1,b1,v2,r2,g2,b2)
 *
 * CPT are generated by tools : grd2cpt
 * CLR are generated by tools : Argis ColorRamp2.0
 * PAL are generated by various tools with various patterns, this is just one of the possibilities.
 *
 * @author Johann Sorel (Geomatys)
 */
public class PaletteReader {

    public static final SimpleFileFilter FILE_FILTER = new SimpleFileFilter("Palette",false,new String[]{"clr","cpt","pal"});

    protected static final FilterFactory FF = DefaultFactories.forBuildin(FilterFactory.class);
    protected static final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);

    private class Row implements Comparable<Row>{
        Double v1 = null;
        Double v2 = null;
        Integer r1 = null;
        Integer r2 = null;
        Integer g1 = null;
        Integer g2 = null;
        Integer b1 = null;
        Integer b2 = null;

        @Override
        public int compareTo(Row other) {
            if(v1==null) return -1;
            else if (other.v1 == null) return 1;
            return v1.compareTo(other.v1);
        }

    }

    public static final String PATTERN_CLR = "^ColorMap.* \n v1 r1 g1 b1";
    public static final String PATTERN_CPT = "^(#|B|F|N).* \n v1 r1 g1 b1 v2 r2 g2 b2";
    public static final String PATTERN_PAL = "r1,g1,b1,\"v2 ?- ?v1\"";

    private final Pattern[] ignorePatterns;
    private final String valuePattern;
    private final boolean categorize;
    private final Pattern valStart;

    public PaletteReader(String pattern) {
        final String[] parts = pattern.split("\n");
        ignorePatterns = new Pattern[parts.length-1];
        for(int i=0,n=parts.length-1;i<n;i++){
            ignorePatterns[i] = Pattern.compile(parts[i].trim());
        }
        valuePattern = parts[parts.length-1].trim();
        categorize = valuePattern.contains("v2");
        valStart = Pattern.compile("^(v1|v2|r1|r2|g1|g2|b1|b2).*");
    }

    public ColorMap read(String candidate) throws IOException{
        final String[] parts = candidate.split("\n");

        final List<Row> rows = new ArrayList<>();
        lines:
        for(String part : parts){
            part = part.trim();
            if(part.isEmpty()) continue lines;

            //check if we ignore this line
            for(Pattern p  : ignorePatterns){
                if(p.matcher(part).matches()){
                    continue lines;
                }
            }

            //parse values
            String pattern = valuePattern;
            final Row row = new Row();

            while(!part.isEmpty()){
                boolean optional = false;
                if(pattern.charAt(0) == '?'){
                    optional = true;
                    pattern = pattern.substring(1);
                }

                if(!valStart.matcher(pattern).matches()){
                    char c = pattern.charAt(0);
                    if(c==' '){
                        part = part.trim();
                    }else{
                        char v = part.charAt(0);
                        if(v==c){
                            part = part.substring(1);
                        }else if(!optional){
                            throw new IOException("Pattern do not match.");
                        }

                    }
                    pattern = pattern.substring(1);
                    continue;
                }

                //we work with a value
                final int numberEnd = numberEnd(part);
                if(numberEnd==0){
                    pattern = pattern.substring(2);
                    if(optional){
                        continue;
                    }else{
                        throw new IOException("Pattern do not match.");
                    }
                }

                Double val = parseDouble(part, numberEnd);
                part = part.substring(numberEnd);

                if(pattern.startsWith("v1")) row.v1 = val;
                if(pattern.startsWith("v2")) row.v2 = val;
                if(pattern.startsWith("r1")) row.r1 = val.intValue();
                if(pattern.startsWith("r2")) row.r2 = val.intValue();
                if(pattern.startsWith("g1")) row.g1 = val.intValue();
                if(pattern.startsWith("g2")) row.g2 = val.intValue();
                if(pattern.startsWith("b1")) row.b1 = val.intValue();
                if(pattern.startsWith("b2")) row.b2 = val.intValue();

                pattern = pattern.substring(2);
            }
            rows.add(row);
        }

        //sort values in ascending order
        Collections.sort(rows);

        final ColorMap colorMap;
        if(!categorize){
            //interpolated color model
            final List<InterpolationPoint> values = new ArrayList<>();
            for(Row row : rows){
                values.add( SF.interpolationPoint(row.v1, SF.literal(new Color(row.r1,row.g1,row.b1))));
            }
            final Function function = SF.interpolateFunction(DEFAULT_CATEGORIZE_LOOKUP,
                    values, Method.COLOR, Mode.LINEAR, DEFAULT_FALLBACK);
            colorMap = SF.colorMap(function);

        }else{
            //categorize color model
            final Map<Expression, Expression> values = new HashMap<>();
            for(int i=0,n=rows.size();i<n;i++){
                final Row row = rows.get(i);

                if(values.isEmpty()){
                    if(row.v1==null){
                        values.put( StyleConstants.CATEGORIZE_LESS_INFINITY, SF.literal(new Color(row.r1,row.g1,row.b1)));
                    }else{
                        //add a translucent range from -infinity to value
                        values.put( StyleConstants.CATEGORIZE_LESS_INFINITY, SF.literal(new Color(0f,0f,0f,0f)));
                        values.put( FF.literal(row.v1), SF.literal(new Color(row.r1,row.g1,row.b1)));
                    }
                }else{
                    //two values, two colors
                    values.put( FF.literal(row.v1), SF.literal(new Color(row.r1,row.g1,row.b1)));
                }

                //special case for last element
                if(i==n-1){
                    if(row.r2==null){
                        values.put( FF.literal(row.v2), SF.literal(new Color(0f,0f,0f,0f)));
                    }else{
                        values.put( FF.literal(row.v2), SF.literal(new Color(row.r2,row.g2,row.b2)));
                    }
                }
            }
            final Function function = SF.categorizeFunction(DEFAULT_CATEGORIZE_LOOKUP,
                    values, ThreshholdsBelongTo.SUCCEEDING, DEFAULT_FALLBACK);
            colorMap = SF.colorMap(function);
        }

        return colorMap;
    }

    private static double parseDouble(String candidate, int end){
        String str = candidate.substring(0,end);
        return Double.parseDouble(str);
    }

    private static int numberEnd(String candidate) throws IOException{
        int end=0;
        //possible negation
        if(candidate.charAt(0) == '-'){
            end++;
        }

        while(candidate.length()>end && (Character.isDigit(candidate.charAt(end)) || candidate.charAt(end)=='.')){
            end++;
        }
        return end;
    }

}
