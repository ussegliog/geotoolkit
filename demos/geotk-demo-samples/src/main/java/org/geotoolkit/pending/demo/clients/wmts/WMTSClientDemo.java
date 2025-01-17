
package org.geotoolkit.pending.demo.clients.wmts;

import java.net.URL;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.storage.Resource;
import org.geotoolkit.storage.multires.MultiResolutionResource;
import org.geotoolkit.gui.javafx.render2d.FXMapFrame;
import org.geotoolkit.map.MapBuilder;
import org.geotoolkit.map.MapContext;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.pending.demo.Demos;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.style.DefaultStyleFactory;
import org.geotoolkit.style.MutableStyleFactory;
import org.geotoolkit.style.StyleConstants;
import org.geotoolkit.wmts.WebMapTileClient;
import org.geotoolkit.wmts.xml.WMTSVersion;
import org.opengis.util.GenericName;


public class WMTSClientDemo {

    public static final MutableStyleFactory SF = new DefaultStyleFactory();

    public static void main(String[] args) throws Exception {
        Demos.init();

        final MapContext context = createContext();

        FXMapFrame.show(context);

    }

    public static MapContext createContext() throws Exception{
        final MapContext context = MapBuilder.createContext(CommonCRS.WGS84.normalizedGeographic());

        final WebMapTileClient server = new WebMapTileClient(
                new URL("http://localhost:8080/constellation/WS/wmts/test"),
                WMTSVersion.v100);

        for(final Resource ref : DataStores.flatten(server, false)){
            final GenericName n = ref.getIdentifier().get();
            System.out.println(n);

            final MapLayer layer = MapBuilder.createCoverageLayer(
                    ref,
                    new DefaultStyleFactory().style(StyleConstants.DEFAULT_RASTER_SYMBOLIZER));

            MultiResolutionResource model = (MultiResolutionResource) ref;
            System.out.println(model);

            layer.setDescription(SF.description(n.tip().toString(), n.tip().toString()));
            context.layers().add(layer);

        }

        return context;
    }

}
