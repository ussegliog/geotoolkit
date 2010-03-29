//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.03.29 at 11:05:09 AM CEST 
//


package org.geotoolkit.metadata.dimap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for t_Coordinate_Reference_System complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="t_Coordinate_Reference_System">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element ref="{}GEO_TABLES"/>
 *         &lt;element ref="{}Horizontal_CS"/>
 *         &lt;element ref="{}Vertical_CS" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_Coordinate_Reference_System", propOrder = {

})
public class TCoordinateReferenceSystem {

    @XmlElement(name = "GEO_TABLES", required = true)
    protected TGEOTABLES geotables;
    @XmlElement(name = "Horizontal_CS", required = true)
    protected THorizontalCS horizontalCS;
    @XmlElement(name = "Vertical_CS")
    protected TVerticalCS verticalCS;

    /**
     * Gets the value of the geotables property.
     * 
     * @return
     *     possible object is
     *     {@link TGEOTABLES }
     *     
     */
    public TGEOTABLES getGEOTABLES() {
        return geotables;
    }

    /**
     * Sets the value of the geotables property.
     * 
     * @param value
     *     allowed object is
     *     {@link TGEOTABLES }
     *     
     */
    public void setGEOTABLES(TGEOTABLES value) {
        this.geotables = value;
    }

    /**
     * Gets the value of the horizontalCS property.
     * 
     * @return
     *     possible object is
     *     {@link THorizontalCS }
     *     
     */
    public THorizontalCS getHorizontalCS() {
        return horizontalCS;
    }

    /**
     * Sets the value of the horizontalCS property.
     * 
     * @param value
     *     allowed object is
     *     {@link THorizontalCS }
     *     
     */
    public void setHorizontalCS(THorizontalCS value) {
        this.horizontalCS = value;
    }

    /**
     * Gets the value of the verticalCS property.
     * 
     * @return
     *     possible object is
     *     {@link TVerticalCS }
     *     
     */
    public TVerticalCS getVerticalCS() {
        return verticalCS;
    }

    /**
     * Sets the value of the verticalCS property.
     * 
     * @param value
     *     allowed object is
     *     {@link TVerticalCS }
     *     
     */
    public void setVerticalCS(TVerticalCS value) {
        this.verticalCS = value;
    }

}
