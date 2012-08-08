/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2007 - 2008, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2008 - 2009, Johann Sorel
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
package org.geotoolkit.gui.swing.style;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;
import org.geotoolkit.gui.swing.resource.MessageBundle;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.style.StyleConstants;
import org.opengis.style.OverlapBehavior;
import org.opengis.style.RasterSymbolizer;
import org.opengis.style.Symbolizer;

/**
 * Raster Sybolizer edition panel
 *
 * @author  Johann Sorel
 * @module pending
 */
public class JRasterSymbolizerPane extends StyleElementEditor<RasterSymbolizer> {

    private MapLayer layer = null;
    private Symbolizer outLine = null;
    private RasterSymbolizer oldSymbolizer;

    /** Creates new form RasterStylePanel
     * @param layer the layer style to edit
     */
    public JRasterSymbolizerPane() {
        super(RasterSymbolizer.class);
        initComponents();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void setLayer(final MapLayer layer) {
        this.layer = layer;
        guiOpacity.setLayer(layer);
        guiGeom.setLayer(layer);
        guiOverLap.setLayer(layer);
        guiContrast.setLayer(layer);
        guiRelief.setLayer(layer);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public MapLayer getLayer() {
        return layer;
    }
    
    /**
     * {@inheritDoc }
     */
    @Override
    public void parse(final RasterSymbolizer symbol) {
        this.oldSymbolizer = symbol;
        
        if (symbol != null) {
            guiGeom.setGeom(symbol.getGeometryPropertyName());
            guiOpacity.parse(symbol.getOpacity());
            //guiOverLap.parse(symbol.getOverlapBehavior());
            guiContrast.parse(symbol.getContrastEnhancement());
            guiRelief.parse(symbol.getShadedRelief());
                                    
//            outLine = symbol.getImageOutline();
//            if(outLine == null){
//                guinone.setSelected(true);
//            }else if(outLine instanceof LineSymbolizer){
//                guiLine.setSelected(true);
//            }else if(outLine instanceof PolygonSymbolizer){
//                guiPolygon.setSelected(true);
//            }
//            testOutLine();
            
            //handle by a button
            //symbol.getChannelSelection();
            symbol.getColorMap();
            
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public RasterSymbolizer create() {
        return getStyleFactory().rasterSymbolizer(
                "RasterSymbolizer",
                guiGeom.getGeom(),
                (oldSymbolizer!=null) ? oldSymbolizer.getDescription() : StyleConstants.DEFAULT_DESCRIPTION,
                guiUOM.create(),
                guiOpacity.create(),
                (oldSymbolizer!=null) ? oldSymbolizer.getChannelSelection() : StyleConstants.DEFAULT_RASTER_CHANNEL_RGB,
                (oldSymbolizer!=null) ? oldSymbolizer.getOverlapBehavior() : OverlapBehavior.AVERAGE, 
                (oldSymbolizer!=null) ? oldSymbolizer.getColorMap() : null, 
                guiContrast.create(), 
                guiRelief.create(), 
                (oldSymbolizer!=null) ? oldSymbolizer.getImageOutline() : null );
    
    }

    private void testOutLine(){
//        if(guinone.isSelected()){
//            butLineSymbolizer.setEnabled(false);
//            butPolygonSymbolizer.setEnabled(false);
//            outLine = null;
//        }else if(guiLine.isSelected()){
//            butLineSymbolizer.setEnabled(true);
//            butPolygonSymbolizer.setEnabled(false);     
//            outLine = new StyleBuilder().createLineSymbolizer();
//        }else if(guiPolygon.isSelected()){
//            butLineSymbolizer.setEnabled(false);
//            butPolygonSymbolizer.setEnabled(true);      
//            outLine = new StyleBuilder().createPolygonSymbolizer();
//        }
    }
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        grpOutline = new ButtonGroup();
        guiContrast = new JContrastEnhancement();
        guiRelief = new JShadedReliefPane();
        jPanel1 = new JPanel();
        butChannels = new JButton();
        jLabel3 = new JLabel();
        guiOverLap = new JTextExpressionPane();
        jLabel2 = new JLabel();
        jLabel1 = new JLabel();
        guiOpacity = new JNumberExpressionPane();
        guiUOM = new JUOMPane();
        guiGeom = new JGeomPane();
        jPanel2 = new JPanel();
        guinone = new JRadioButton();
        guiLine = new JRadioButton();
        guiPolygon = new JRadioButton();
        butPolygonSymbolizer = new JButton();
        butLineSymbolizer = new JButton();

        setOpaque(false);



        guiContrast.setBorder(BorderFactory.createTitledBorder(MessageBundle.getString("contrast"))); // NOI18N
        guiRelief.setBorder(BorderFactory.createTitledBorder(MessageBundle.getString("relief"))); // NOI18N
        jPanel1.setBorder(BorderFactory.createTitledBorder(MessageBundle.getString("general"))); // NOI18N
        jPanel1.setOpaque(false);

        butChannels.setText(MessageBundle.getString("edit")); // NOI18N
        butChannels.setBorderPainted(false);



        butChannels.setPreferredSize(new Dimension(79, 22));
        butChannels.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                butChannelsActionPerformed(evt);
            }
        });

        jLabel3.setHorizontalAlignment(SwingConstants.RIGHT);
        jLabel3.setText(MessageBundle.getString("channels")); // NOI18N
        jLabel2.setHorizontalAlignment(SwingConstants.RIGHT);
        jLabel2.setText(MessageBundle.getString("overlap")); // NOI18N
        jLabel1.setHorizontalAlignment(SwingConstants.RIGHT);
        jLabel1.setText(MessageBundle.getString("opacity")); // NOI18N
        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(guiOpacity, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(guiOverLap, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(butChannels, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createParallelGroup(Alignment.TRAILING, false)
                        .addComponent(guiUOM, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(guiGeom, Alignment.LEADING, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(SwingConstants.HORIZONTAL, new Component[] {jLabel1, jLabel2, jLabel3});

        jPanel1Layout.linkSize(SwingConstants.HORIZONTAL, new Component[] {butChannels, guiOpacity, guiOverLap});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(guiGeom, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(guiUOM, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(guiOpacity, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(guiOverLap, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(butChannels, GroupLayout.PREFERRED_SIZE, 24, Short.MAX_VALUE)
                    .addComponent(jLabel3))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(SwingConstants.VERTICAL, new Component[] {guiOpacity, jLabel1});

        jPanel1Layout.linkSize(SwingConstants.VERTICAL, new Component[] {guiOverLap, jLabel2});

        jPanel1Layout.linkSize(SwingConstants.VERTICAL, new Component[] {butChannels, jLabel3});

        jPanel2.setBorder(BorderFactory.createTitledBorder(MessageBundle.getString("outline"))); // NOI18N
        jPanel2.setOpaque(false);

        grpOutline.add(guinone);
        guinone.setSelected(true);
        guinone.setText(MessageBundle.getString("none")); // NOI18N
        guinone.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                guinoneActionPerformed(evt);
            }
        });

        grpOutline.add(guiLine);
        guiLine.setText(MessageBundle.getString("line")); // NOI18N
        guiLine.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                guiLineActionPerformed(evt);
            }
        });

        grpOutline.add(guiPolygon);
        guiPolygon.setText(MessageBundle.getString("polygon")); // NOI18N
        guiPolygon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                guiPolygonActionPerformed(evt);
            }
        });

        butPolygonSymbolizer.setText(MessageBundle.getString("edit")); // NOI18N
        butPolygonSymbolizer.setBorderPainted(false);
        butPolygonSymbolizer.setEnabled(false);
        butPolygonSymbolizer.setPreferredSize(new Dimension(79, 20));
        butPolygonSymbolizer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                butPolygonSymbolizerActionPerformed(evt);
            }
        });

        butLineSymbolizer.setText(MessageBundle.getString("edit")); // NOI18N
        butLineSymbolizer.setBorderPainted(false);
        butLineSymbolizer.setEnabled(false);
        butLineSymbolizer.setPreferredSize(new Dimension(79, 20));
        butLineSymbolizer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                butLineSymbolizerActionPerformed(evt);
            }
        });

        GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(Alignment.LEADING)
                    .addComponent(guinone)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(Alignment.TRAILING)
                            .addComponent(guiLine)
                            .addComponent(guiPolygon))
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(Alignment.LEADING)
                            .addComponent(butLineSymbolizer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                            .addComponent(butPolygonSymbolizer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(41, Short.MAX_VALUE))
        );

        jPanel2Layout.linkSize(SwingConstants.HORIZONTAL, new Component[] {guiLine, guiPolygon, guinone});

        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(guinone)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(butLineSymbolizer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(guiLine))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(Alignment.BASELINE)
                    .addComponent(butPolygonSymbolizer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(guiPolygon))
                .addContainerGap(14, Short.MAX_VALUE))
        );

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(Alignment.LEADING)
            .addComponent(jPanel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(guiRelief, GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
            .addComponent(guiContrast, GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(jPanel2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(guiRelief, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(guiContrast, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void butPolygonSymbolizerActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_butPolygonSymbolizerActionPerformed
//        JDialog dia = new JDialog();
//        dia.setModal(true);
//        
//        JPolygonSymbolizerPane pane = new JPolygonSymbolizerPane();
//        pane.setEdited((PolygonSymbolizer)outLine);
//        pane.setLayer(layer);
//        
//        dia.getContentPane().add(pane);
//        
//        dia.pack();
//        dia.setLocationRelativeTo(butLineSymbolizer);
//        dia.setVisible(true);
//        
//        outLine = pane.getEdited();
    }//GEN-LAST:event_butPolygonSymbolizerActionPerformed

    private void butLineSymbolizerActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_butLineSymbolizerActionPerformed
//        JDialog dia = new JDialog();
//        dia.setModal(true);
//        
//        JLineSymbolizerPane pane = new JLineSymbolizerPane();
//        pane.setEdited((LineSymbolizer)outLine);
//        pane.setLayer(layer);
//        
//        dia.getContentPane().add(pane);
//        
//        dia.pack();
//        dia.setLocationRelativeTo(butLineSymbolizer);
//        dia.setVisible(true);
//        
//        outLine = pane.getEdited();
    }//GEN-LAST:event_butLineSymbolizerActionPerformed

    private void guiLineActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_guiLineActionPerformed
        testOutLine();
}//GEN-LAST:event_guiLineActionPerformed

    private void guinoneActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_guinoneActionPerformed
       testOutLine();
    }//GEN-LAST:event_guinoneActionPerformed

    private void guiPolygonActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_guiPolygonActionPerformed
        testOutLine();
    }//GEN-LAST:event_guiPolygonActionPerformed

    private void butChannelsActionPerformed(final ActionEvent evt) {//GEN-FIRST:event_butChannelsActionPerformed
        
//        JDialog dia = new JDialog();
//        
//        JChannelSelectionPane pane = new JChannelSelectionPane();
//        pane.setLayer(layer);
//        
//        if(symbol != null){
//            pane.setEdited(symbol.getChannelSelection());
//        }
//        
//        dia.setContentPane(pane);
//        dia.pack();
//        dia.setLocationRelativeTo(butChannels);
//        dia.setModal(true);
//        dia.setVisible(true);
//        
//        if(symbol == null){
//            symbol =  new StyleBuilder().createRasterSymbolizer();
//        }
//        symbol.setChannelSelection(pane.getEdited());        
        
    }//GEN-LAST:event_butChannelsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton butChannels;
    private JButton butLineSymbolizer;
    private JButton butPolygonSymbolizer;
    private ButtonGroup grpOutline;
    private JContrastEnhancement guiContrast;
    private JGeomPane guiGeom;
    private JRadioButton guiLine;
    private JNumberExpressionPane guiOpacity;
    private JTextExpressionPane guiOverLap;
    private JRadioButton guiPolygon;
    private JShadedReliefPane guiRelief;
    private JUOMPane guiUOM;
    private JRadioButton guinone;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JLabel jLabel3;
    private JPanel jPanel1;
    private JPanel jPanel2;
    // End of variables declaration//GEN-END:variables
    
}
