//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.03.29 at 11:05:09 AM CEST 
//


package org.geotoolkit.metadata.dimap;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Vertical_CS_Types.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="Vertical_CS_Types">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="HEIGHT"/>
 *     &lt;enumeration value="DEPTH"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "Vertical_CS_Types")
@XmlEnum
public enum VerticalCSTypes {

    HEIGHT,
    DEPTH;

    public String value() {
        return name();
    }

    public static VerticalCSTypes fromValue(String v) {
        return valueOf(v);
    }

}
