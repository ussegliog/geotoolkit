/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2008 - 2010, Geomatys
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

package org.geotoolkit.sml.xml;

/**
 *
 * @author Mehdi Sidhoum (Geomatys).
 */
public interface AbstractContactListMember {

    public AbstractPerson getPerson();

    public AbstractResponsibleParty getResponsibleParty();

    public String getRemoteSchema();

    public String getActuate();

    public String getArcrole();

    public String getHref();

    public String getRole();

    public String getShow();

    public String getTitle();

    public String getType();
}
