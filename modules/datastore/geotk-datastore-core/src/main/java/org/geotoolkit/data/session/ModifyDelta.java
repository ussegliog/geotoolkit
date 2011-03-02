/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2009, Open Source Geospatial Foundation (OSGeo)
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

package org.geotoolkit.data.session;

import java.util.HashMap;
import java.util.Map;

import org.geotoolkit.data.DataStore;
import org.geotoolkit.storage.DataStoreException;
import org.geotoolkit.data.FeatureIterator;
import org.geotoolkit.data.memory.GenericFilterFeatureIterator;
import org.geotoolkit.data.memory.GenericModifyFeatureIterator;
import org.geotoolkit.data.query.Query;
import org.geotoolkit.data.query.QueryBuilder;
import org.geotoolkit.util.NullArgumentException;

import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.Id;
import org.opengis.geometry.Envelope;

import static org.geotoolkit.util.ArgumentChecks.*;

/**
 * Delta which modify a collection of features.
 *
 * @author Johann Sorel (Geomatys)
 * @module pending
 * @todo make this concurrent
 */
class ModifyDelta extends AbstractDelta{

    private final Name type;
    private final Id filter;
    private final Map<AttributeDescriptor,Object> values = new HashMap<AttributeDescriptor, Object>();

    ModifyDelta(final Session session, final Name typeName, final Id filter, final Map<? extends AttributeDescriptor,? extends Object> values){
        super(session);
        ensureNonNull("type name", typeName);
        if(filter == null){
            throw new NullArgumentException("Filter can not be null. Did you mean Filter.INCLUDE ?");
        }
        if(values == null || values.isEmpty()){
            throw new IllegalArgumentException("Modified values can not be null or empty. A modify delta is useless in this case.");
        }

        this.type = typeName;
        this.filter = filter;
        this.values.putAll(values);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Query modify(final Query query) {
        if(!query.getTypeName().equals(type)) return query;

        //we always include the modified features
        //they will be filtered at return time in the other modified methods
        //todo we should modify this query for count and envelope
        final QueryBuilder builder = new QueryBuilder(query);
        builder.setFilter(FF.or(builder.getFilter(),filter));

        return builder.buildQuery();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public FeatureIterator modify(final Query query, final FeatureIterator reader) throws DataStoreException {

        //modify the features that match the filter
        final FeatureIterator modified = GenericModifyFeatureIterator.wrap(reader, filter, values);

        //ensure that the modified feature still match the original request
        final Filter alterationFilter = FF.or(FF.not(filter), FF.and(filter, query.getFilter()));
        final FeatureIterator secondCheck = GenericFilterFeatureIterator.wrap(modified, alterationFilter);

        return secondCheck;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public long modify(final Query query, final long count) throws DataStoreException{
        //todo must find a correct wayto alterate the count
        //the send request should be modified
        return count;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Envelope modify(final Query query, final Envelope env) throws DataStoreException {
        //todo must find a correct wayto alterate the envelope
        //the send request should be modified
        return env;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void commit(final DataStore store) throws DataStoreException {
        store.updateFeatures(type, filter, values);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void dispose() {
    }

}
