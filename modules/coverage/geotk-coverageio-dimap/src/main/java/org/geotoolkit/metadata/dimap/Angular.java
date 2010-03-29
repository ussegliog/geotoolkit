//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.03.29 at 11:05:09 AM CEST 
//


package org.geotoolkit.metadata.dimap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for Angular complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Angular">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>double">
 *       &lt;attribute name="unit" type="{}Angular_Unit" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Angular", propOrder = {
    "value"
})
@XmlSeeAlso({
    TMAGNETICDECLANNUALCHANGE.class,
    TGRIDDECLINATION.class,
    TMAGNETICDECLINATION.class,
    TPRIMEMERIDIANOFFSET.class,
    Latitude.class,
    Elevation.class,
    Longitude.class,
    Azimuth.class,
    ViewingDirection.class
})
public class Angular {

    @XmlValue
    protected double value;
    @XmlAttribute
    protected AngularUnit unit;

    /**
     * Gets the value of the value property.
     * 
     */
    public double getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     */
    public void setValue(double value) {
        this.value = value;
    }

    /**
     * Gets the value of the unit property.
     * 
     * @return
     *     possible object is
     *     {@link AngularUnit }
     *     
     */
    public AngularUnit getUnit() {
        return unit;
    }

    /**
     * Sets the value of the unit property.
     * 
     * @param value
     *     allowed object is
     *     {@link AngularUnit }
     *     
     */
    public void setUnit(AngularUnit value) {
        this.unit = value;
    }

}
