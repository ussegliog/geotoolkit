/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2010, Geomatys
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

package org.geotoolkit.data.memory;

import com.vividsolutions.jts.geom.Geometry;
import java.util.ArrayList;
import java.util.Collection;

import org.geotoolkit.data.FeatureIterator;
import org.geotoolkit.data.FeatureReader;
import org.geotoolkit.data.DataStoreRuntimeException;
import org.geotoolkit.factory.FactoryFinder;
import org.geotoolkit.factory.Hints;
import org.geotoolkit.feature.LenientFeatureFactory;
import org.geotoolkit.geometry.jts.transform.GeometryTransformer;
import org.geotoolkit.util.converter.Classes;

import org.opengis.feature.Feature;
import org.opengis.feature.FeatureFactory;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.operation.TransformException;

/**
 * Basic support for a  FeatureIterator that transform the geometry attribut.
 *
 * @author Johann Sorel (Geomatys)
 * @module pending
 */
public abstract class GenericTransformFeatureIterator<F extends Feature, R extends FeatureIterator<F>>
        implements FeatureIterator<F> {

    protected static final FeatureFactory FF = FactoryFinder
            .getFeatureFactory(new Hints(Hints.FEATURE_FACTORY, LenientFeatureFactory.class));

    protected final R iterator;
    protected final GeometryTransformer transformer;

    /**
     * Creates a new instance of GenericTransformFeatureIterator
     *
     * @param iterator FeatureReader to limit
     * @param transformer the transformer to use on each geometry
     */
    private GenericTransformFeatureIterator(final R iterator, GeometryTransformer transformer) {
        this.iterator = iterator;
        this.transformer = transformer;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public F next() throws DataStoreRuntimeException {
        final Feature next = iterator.next();

        final Collection<Property> properties = new ArrayList<Property>();
        for(Property prop : next.getProperties()){
            if(prop instanceof GeometryAttribute){
                Object value = prop.getValue();
                if(value != null){
                    //create a new property with the transformed geometry
                    prop = FF.createGeometryAttribute(value,
                            (GeometryDescriptor)prop.getDescriptor(), null, null);

                    try {
                        //transform the geometry
                        prop.setValue(transformer.transform((Geometry) value));
                    } catch (TransformException e) {
                        throw new DataStoreRuntimeException("A transformation exception occurred while reprojecting data on the fly", e);
                    }

                }
            }
            properties.add(prop);
        }
        return (F) FF.createFeature(properties, next.getType(), next.getIdentifier().getID());
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void close() throws DataStoreRuntimeException {
        iterator.close();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public boolean hasNext() throws DataStoreRuntimeException {
        return iterator.hasNext();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(Classes.getShortClassName(this));
        sb.append(" : ").append(transformer);
        sb.append('\n');
        String subIterator = "\u2514\u2500\u2500" + iterator.toString(); //move text to the right
        subIterator = subIterator.replaceAll("\n", "\n\u00A0\u00A0\u00A0"); //move text to the right
        sb.append(subIterator);
        return sb.toString();
    }

    /**
     * Wrap a FeatureReader with a reprojection.
     *
     * @param <T> extends FeatureType
     * @param <F> extends Feature
     * @param <R> extends FeatureReader<T,F>
     */
    private static final class GenericTransformFeatureReader<T extends FeatureType, F extends Feature, R extends FeatureReader<T,F>>
            extends GenericTransformFeatureIterator<F,R> implements FeatureReader<T,F>{


        private GenericTransformFeatureReader(R reader, GeometryTransformer transformer) {
            super(reader, transformer);            
        }

        @Override
        public T getFeatureType() {
            return iterator.getFeatureType();
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }

    /**
     * Wrap a FeatureReader with a reprojection.
     */
    public static <T extends FeatureType, F extends Feature> FeatureReader<T, F> wrap(
            FeatureReader<T, F> reader, GeometryTransformer transformer) {
        final GeometryDescriptor desc = reader.getFeatureType().getGeometryDescriptor();
        if (desc != null) {
            return new GenericTransformFeatureReader(reader, transformer);
        } else {
            return reader;
        }
    }

}
