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
 * <p>Java class for t_Raster_CS complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="t_Raster_CS">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element ref="{}RASTER_CS_TYPE"/>
 *         &lt;element ref="{}PIXEL_ORIGIN" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_Raster_CS", propOrder = {

})
public class TRasterCS {

    @XmlElement(name = "RASTER_CS_TYPE", required = true)
    protected RasterCSTypes rastercstype;
    @XmlElement(name = "PIXEL_ORIGIN")
    protected Integer pixelorigin;

    /**
     * Gets the value of the rastercstype property.
     * 
     * @return
     *     possible object is
     *     {@link RasterCSTypes }
     *     
     */
    public RasterCSTypes getRASTERCSTYPE() {
        return rastercstype;
    }

    /**
     * Sets the value of the rastercstype property.
     * 
     * @param value
     *     allowed object is
     *     {@link RasterCSTypes }
     *     
     */
    public void setRASTERCSTYPE(RasterCSTypes value) {
        this.rastercstype = value;
    }

    /**
     * Gets the value of the pixelorigin property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPIXELORIGIN() {
        return pixelorigin;
    }

    /**
     * Sets the value of the pixelorigin property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPIXELORIGIN(Integer value) {
        this.pixelorigin = value;
    }

}
