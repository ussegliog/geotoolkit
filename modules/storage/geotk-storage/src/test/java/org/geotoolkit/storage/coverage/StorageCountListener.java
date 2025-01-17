/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2012, Geomatys
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
package org.geotoolkit.storage.coverage;

import org.apache.sis.storage.event.StoreEvent;
import org.apache.sis.storage.event.StoreListener;
import org.geotoolkit.storage.event.CoverageStoreContentEvent;
import org.geotoolkit.storage.event.CoverageStoreManagementEvent;

/**
 * Test storage listener, count the number of events and store the last event objects.
 * @author Johann Sorel (Geomatys)
 */
public final class StorageCountListener implements StoreListener<StoreEvent> {

    public int numManageEvent = 0;
    public int numContentEvent = 0;
    public CoverageStoreManagementEvent lastManagementEvent = null;
    public CoverageStoreContentEvent lastContentEvent = null;

    @Override
    public void eventOccured(StoreEvent event) {
        if (event instanceof CoverageStoreManagementEvent) {
            numManageEvent++;
            this.lastManagementEvent = (CoverageStoreManagementEvent) event;
        } else if (event instanceof CoverageStoreContentEvent) {
            numContentEvent++;
            this.lastContentEvent = (CoverageStoreContentEvent) event;
        }
    }
}
