
package org.geotoolkit.pending.demo.rendering.customgraphicbuilder;

import java.io.File;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.storage.DataStoreException;
import org.geotoolkit.storage.feature.FeatureCollection;
import org.geotoolkit.storage.feature.FeatureStore;
import org.geotoolkit.storage.feature.query.QueryBuilder;
import org.geotoolkit.gui.javafx.render2d.FXMapFrame;
import org.geotoolkit.image.io.plugin.WorldFileImageReader;
import org.geotoolkit.map.MapBuilder;
import org.geotoolkit.map.MapContext;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.pending.demo.Demos;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.style.MutableStyle;
import org.geotoolkit.style.MutableStyleFactory;
import org.opengis.style.StyleFactory;


public class GraphicBuilderDemo {


    private static final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);

    public static void main(String[] args) throws Exception {
        Demos.init();

        final MapContext context = createContext();

        FXMapFrame.show(context);

    }

    private static MapContext createContext() throws DataStoreException, URISyntaxException {
        WorldFileImageReader.Spi.registerDefaults(null);

        //create a map context
        final MapContext context = MapBuilder.createContext();

        //create a feature layer
        Map<String,Serializable> params = new HashMap<String,Serializable>();
        params.put( "path", GraphicBuilderDemo.class.getResource("/data/weather/stations2.shp").toURI() );
        FeatureStore store = (FeatureStore) DataStores.open(params);
        FeatureCollection fs = store.createSession(true).getFeatureCollection(QueryBuilder.all(store.getNames().iterator().next()));
        MutableStyle style = SF.style();
        MapLayer layer = MapBuilder.createFeatureLayer(fs, style);
        layer.setDescription(SF.description("stations", ""));
        layer.setName("stations");

        //create a coverage layer
        File cloudFile = new File(GraphicBuilderDemo.class.getResource("/data/coverage/clouds.jpg").toURI());
        final MapLayer coverageLayer = MapBuilder.createCoverageLayer(cloudFile);

        //set our graphic builder
        layer.graphicBuilders().add(new LinksGraphicBuilder());

        //add all layers in the context
        context.layers().add(coverageLayer);
        context.layers().add(layer);
        return context;
    }

}
