/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2008-2011, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2009-2011, Geomatys
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
package org.geotoolkit.internal.jaxb.metadata;

import javax.xml.bind.annotation.XmlElement;
import org.opengis.metadata.lineage.Algorithm;
import org.geotoolkit.metadata.iso.lineage.DefaultAlgorithm;
import org.geotoolkit.xml.Namespaces;


/**
 * JAXB adapter mapping implementing class to the GeoAPI interface. See
 * package documentation for more information about JAXB and interface.
 *
 * @author Cédric Briançon (Geomatys)
 * @author Guilhem Legal (Geomatys)
 * @version 3.17
 *
 * @since 3.02
 * @module
 */
public final class AlgorithmAdapter extends MetadataAdapter<AlgorithmAdapter,Algorithm> {
    /**
     * Empty constructor for JAXB only.
     */
    public AlgorithmAdapter() {
    }

    /**
     * Wraps an Algorithm value with a {@code LE_Algorithm} element at marshalling time.
     *
     * @param metadata The metadata value to marshall.
     */
    private AlgorithmAdapter(final Algorithm metadata) {
        super(metadata);
    }

    /**
     * Returns the Algorithm value wrapped by a {@code LE_Algorithm} element.
     *
     * @param value The value to marshall.
     * @return The adapter which wraps the metadata value.
     */
    @Override
    protected AlgorithmAdapter wrap(final Algorithm value) {
        return new AlgorithmAdapter(value);
    }

    /**
     * Returns the {@link DefaultAlgorithm} generated from the metadata value.
     * This method is systematically called at marshalling time by JAXB.
     *
     * @return The metadata to be marshalled.
     */
    @Override
    @XmlElement(name = "LE_Algorithm", namespace = Namespaces.GMI)
    public DefaultAlgorithm getElement() {
        final Algorithm metadata = this.metadata;
        return (metadata instanceof DefaultAlgorithm) ?
            (DefaultAlgorithm) metadata : new DefaultAlgorithm(metadata);
    }

    /**
     * Sets the value for the {@link DefaultAlgorithm}. This method is systematically
     * called at unmarshalling time by JAXB.
     *
     * @param metadata The unmarshalled metadata.
     */
    public void setElement(final DefaultAlgorithm metadata) {
        this.metadata = metadata;
    }
}
