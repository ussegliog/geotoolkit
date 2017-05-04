/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2014, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2014.04.20 at 07:08:32 PM CEST
//


package org.geotoolkit.owc.xml.v10;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for KnownOfferingTypeCodeType.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="KnownOfferingTypeCodeType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/csw"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/wcs"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/wfs"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/wms"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/wmts"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/wps"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/gml"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/kml"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/geotiff"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/gmljp2"/>
 *     &lt;enumeration value="http://www.opengis.net/spec/owc-atom/1.0/req/gmlcov"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 *
 */
@XmlType(name = "KnownOfferingTypeCodeType")
@XmlEnum
public enum KnownOfferingTypeCodeType {

    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/csw")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_CSW("http://www.opengis.net/spec/owc-atom/1.0/req/csw"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/wcs")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_WCS("http://www.opengis.net/spec/owc-atom/1.0/req/wcs"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/wfs")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_WFS("http://www.opengis.net/spec/owc-atom/1.0/req/wfs"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/wms")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_WMS("http://www.opengis.net/spec/owc-atom/1.0/req/wms"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/wmts")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_WMTS("http://www.opengis.net/spec/owc-atom/1.0/req/wmts"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/wps")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_WPS("http://www.opengis.net/spec/owc-atom/1.0/req/wps"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/gml")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_GML("http://www.opengis.net/spec/owc-atom/1.0/req/gml"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/kml")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_KML("http://www.opengis.net/spec/owc-atom/1.0/req/kml"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/geotiff")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_GEOTIFF("http://www.opengis.net/spec/owc-atom/1.0/req/geotiff"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/gmljp2")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_GMLJP_2("http://www.opengis.net/spec/owc-atom/1.0/req/gmljp2"),
    @XmlEnumValue("http://www.opengis.net/spec/owc-atom/1.0/req/gmlcov")
    HTTP_WWW_OPENGIS_NET_SPEC_OWC_ATOM_1_0_REQ_GMLCOV("http://www.opengis.net/spec/owc-atom/1.0/req/gmlcov");
    private final String value;

    KnownOfferingTypeCodeType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static KnownOfferingTypeCodeType fromValue(String v) {
        for (KnownOfferingTypeCodeType c: KnownOfferingTypeCodeType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
