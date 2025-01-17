
package org.geotoolkit.pending.demo.clients.tms;

import java.net.URL;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.GridCoverageResource;
import org.apache.sis.storage.Resource;
import org.apache.sis.util.iso.SimpleInternationalString;
import org.geotoolkit.gui.javafx.render2d.FXMapFrame;
import org.geotoolkit.map.MapBuilder;
import org.geotoolkit.map.MapContext;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.pending.demo.Demos;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.style.DefaultDescription;
import org.geotoolkit.style.DefaultStyleFactory;
import org.geotoolkit.style.MutableStyleFactory;
import org.geotoolkit.tms.TMSProvider;


public class TMSClientDemo {

    public static final MutableStyleFactory SF = new DefaultStyleFactory();

    public static void main(String[] args) throws Exception {
        Demos.init();

        final MapContext context = createOSMTMSContext();

        FXMapFrame.show(context,false,null);

    }

    public static MapContext createOSMTMSContext() throws Exception{
        final MapContext context = MapBuilder.createContext(CommonCRS.WGS84.normalizedGeographic());

        final Parameters params = Parameters.castOrWrap(TMSProvider.PARAMETERS.createValue());
        params.getOrCreate(TMSProvider.URL).setValue(new URL("http://tile.openstreetmap.org"));
        params.getOrCreate(TMSProvider.IMAGE_CACHE).setValue(true);
        params.getOrCreate(TMSProvider.NIO_QUERIES).setValue(true);
        params.getOrCreate(TMSProvider.MAX_ZOOM_LEVEL).setValue(18);

        final DataStore store = DataStores.open(params);

        for (Resource cr : DataStores.flatten(store, true, GridCoverageResource.class)) {
            final MapLayer cml = MapBuilder.createCoverageLayer(cr);
            cml.setDescription(new DefaultDescription(
                    new SimpleInternationalString(cr.getIdentifier().get().tip().toString()),
                    new SimpleInternationalString("")));
            context.layers().add(cml);
        }

        //Other available OSM TMS
        // https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/4/4/9

        //not available anymore
        // http://a.tah.openstreetmap.org/Tiles/tile/   17
        // http://tile.opencyclemap.org/cycle/ 18
        // http://tile.cloudmade.com/fd093e52f0965d46bb1c6c6281022199/3/256/ 18

        return context;
    }

}
