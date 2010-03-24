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

package org.geotoolkit.data.osm.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.geotoolkit.feature.AbstractFeature;
import org.geotoolkit.filter.identity.DefaultFeatureId;
import org.geotoolkit.util.collection.UnmodifiableArrayList;

import org.opengis.feature.type.AttributeDescriptor;

/**
 *
 * @author Johann Sorel (Geomatys)
 * @module pending
 */
public abstract class IdentifiedElement extends AbstractFeature implements Serializable {

    protected final long id;
    protected final int version;
    protected final int changeset;
    protected final User user;
    protected final long timestamp;
    protected final List<Tag> tags;

    public IdentifiedElement(AttributeDescriptor desc, long id, int version, int changeset, User user,
            long timestamp, Map<String,String> tags) {
        super(desc,new DefaultFeatureId(String.valueOf(id)));
        this.id = id;
        this.version = version;
        this.changeset = changeset;
        this.user = user;
        this.timestamp = timestamp;

        if(tags == null || tags.isEmpty()){
            this.tags = Collections.EMPTY_LIST;
        }else{
            final Tag[] array = new Tag[tags.size()];
            int i=0;
            for(Map.Entry<String,String> entry : tags.entrySet()){
                array[i] = new Tag(entry.getKey(), entry.getValue());
                i++;
            }
            this.tags = UnmodifiableArrayList.wrap(array);
        }
    }

    public long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public int getChangeset() {
        return changeset;
    }

    public User getUser() {
        return user;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Tag> getTags() {
        return tags;
    }

}
