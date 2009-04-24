/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 *    (C) 2007 - 2008, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 3 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */


package org.constellation.swe.v100;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import org.geotoolkit.util.Utilities;


/**
 * <p>Java class for SquareMatrixType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SquareMatrixType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://www.opengis.net/swe/1.0}AbstractMatrixType">
 *       &lt;sequence>
 *         &lt;element name="elementType" type="{http://www.opengis.net/swe/1.0}QuantityPropertyType"/>
 *         &lt;group ref="{http://www.opengis.net/swe/1.0}EncodedValuesGroup" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SquareMatrixType", propOrder = {
    "elementType",
    "encoding",
    "values"
})
public class SquareMatrixType extends AbstractMatrixType {

    @XmlElement(required = true)
    private QuantityPropertyType elementType;
    private BlockEncodingPropertyType encoding;
    private DataValuePropertyType values;

    /**
     * Gets the value of the elementType property.
     * 
     * @return
     *     possible object is
     *     {@link QuantityPropertyType }
     *     
     */
    public QuantityPropertyType getElementType() {
        return elementType;
    }

    /**
     * Sets the value of the elementType property.
     * 
     * @param value
     *     allowed object is
     *     {@link QuantityPropertyType }
     *     
     */
    public void setElementType(QuantityPropertyType value) {
        this.elementType = value;
    }

    /**
     * Gets the value of the encoding property.
     * 
     * @return
     *     possible object is
     *     {@link BlockEncodingPropertyType }
     *     
     */
    public BlockEncodingPropertyType getEncoding() {
        return encoding;
    }

    /**
     * Sets the value of the encoding property.
     * 
     * @param value
     *     allowed object is
     *     {@link BlockEncodingPropertyType }
     *     
     */
    public void setEncoding(BlockEncodingPropertyType value) {
        this.encoding = value;
    }

    /**
     * Gets the value of the values property.
     * 
     * @return
     *     possible object is
     *     {@link DataValuePropertyType }
     *     
     */
    public DataValuePropertyType getValues() {
        return values;
    }

    /**
     * Sets the value of the values property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataValuePropertyType }
     *     
     */
    public void setValues(DataValuePropertyType value) {
        this.values = value;
    }

    /**
     * Verify if this entry is identical to specified object.
     */
    @Override
    public boolean equals(final Object object) {
        if (object == this) {
            return true;
        }

        if (object instanceof SquareMatrixType && super.equals(object)) {
            final SquareMatrixType  that = (SquareMatrixType) object;
            return Utilities.equals(this.elementType, that.elementType) &&
                   Utilities.equals(this.encoding,    that.encoding)    &&
                   Utilities.equals(this.values,      that.values);

        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + (this.elementType != null ? this.elementType.hashCode() : 0);
        hash = 37 * hash + (this.encoding != null ? this.encoding.hashCode() : 0);
        hash = 37 * hash + (this.values != null ? this.values.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(super.toString());
        if (elementType != null) {
            s.append("elementType:").append(elementType).append('\n');
        }
        if (encoding != null) {
            s.append("encoding:").append(encoding).append('\n');
        }
        if (values != null) {
            s.append("values:").append(values).append('\n');
        }
        return s.toString();
    }

}
