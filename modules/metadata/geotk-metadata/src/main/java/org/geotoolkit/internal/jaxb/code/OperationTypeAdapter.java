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
package org.geotoolkit.internal.jaxb.code;

import javax.xml.bind.annotation.XmlElement;
import org.opengis.metadata.acquisition.OperationType;
import org.geotoolkit.xml.Namespaces;


/**
 * JAXB adapter for {@link OperationType}, in order to integrate the value in an element respecting
 * the ISO-19139 standard. See package documentation for more information about the handling
 * of {@code CodeList} in ISO-19139.
 *
 * @author Cédric Briançon (Geomatys)
 * @version 3.07
 *
 * @since 3.02
 * @module
 */
public final class OperationTypeAdapter extends CodeListAdapter<OperationTypeAdapter, OperationType> {
    /**
     * Ensures that the adapted code list class is loaded.
     */
    static {
        ensureClassLoaded(OperationType.class);
    }

    /**
     * Empty constructor for JAXB only.
     */
    public OperationTypeAdapter() {
    }

    /**
     * Creates a new adapter for the given proxy.
     */
    private OperationTypeAdapter(final CodeListProxy proxy) {
        super(proxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected OperationTypeAdapter wrap(CodeListProxy proxy) {
        return new OperationTypeAdapter(proxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<OperationType> getCodeListClass() {
        return OperationType.class;
    }

    /**
     * Invoked by JAXB on marshalling.
     *
     * @return The value to be marshalled.
     */
    @Override
    @XmlElement(name = "MI_OperationTypeCode", namespace = Namespaces.GMI)
    public CodeListProxy getElement() {
        return proxy;
    }

    /**
     * Invoked by JAXB on unmarshalling.
     *
     * @param proxy The unmarshalled value.
     */
    public void setElement(final CodeListProxy proxy) {
        this.proxy = proxy;
    }
}
