/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2011-2018, Geomatys
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
package org.geotoolkit.storage.multires;

import java.util.UUID;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.util.Classes;
import org.geotoolkit.util.StringUtilities;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Abstract pyramid
 *
 * @author Johann Sorel (Geomatys)
 * @module
 */
public abstract class AbstractPyramid implements Pyramid {

    protected final String id;
    protected final CoordinateReferenceSystem crs;
    protected String format = null;

    public AbstractPyramid(CoordinateReferenceSystem crs) {
        this(null,crs);
    }

    public AbstractPyramid(String id, CoordinateReferenceSystem crs) {
        this.crs = crs;
        if(id == null){
            this.id = UUID.randomUUID().toString();
        }else{
            this.id = id;
        }
    }

    @Override
    public String getIdentifier() {
        return id;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return crs;
    }

    @Override
    public String toString(){
        return StringUtilities.toStringTree(Classes.getShortClassName(this)
                +" "+IdentifiedObjects.getIdentifierOrName(getCoordinateReferenceSystem())
                +" "+getIdentifier(),
                getMosaics());
    }

    @Override
    public Envelope getEnvelope() {
        GeneralEnvelope env = null;
        for(Mosaic mosaic : getMosaics()){
            if(env==null){
                env = new GeneralEnvelope(mosaic.getEnvelope());
            }else{
                env.add(mosaic.getEnvelope());
            }
        }
        return env;
    }

}
