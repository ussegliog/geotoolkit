/*
 *    Geotoolkit - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2019
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

package org.geotoolkit.eop.xml.v201;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour MaskInformationPropertyType complex type.
 *
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.
 *
 * <pre>
 * &lt;complexType name="MaskInformationPropertyType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.opengis.net/eop/2.1}MaskInformation"/>
 *       &lt;/sequence>
 *       &lt;attGroup ref="{http://www.opengis.net/gml/3.2}OwnershipAttributeGroup"/>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MaskInformationPropertyType", propOrder = {
    "maskInformation"
})
public class MaskInformationPropertyType {

    @XmlElement(name = "MaskInformation", required = true)
    protected MaskInformationType maskInformation;
    @XmlAttribute(name = "owns")
    protected Boolean owns;

    /**
     * Obtient la valeur de la propriété maskInformation.
     *
     * @return
     *     possible object is
     *     {@link MaskInformationType }
     *
     */
    public MaskInformationType getMaskInformation() {
        return maskInformation;
    }

    /**
     * Définit la valeur de la propriété maskInformation.
     *
     * @param value
     *     allowed object is
     *     {@link MaskInformationType }
     *
     */
    public void setMaskInformation(MaskInformationType value) {
        this.maskInformation = value;
    }

    /**
     * Obtient la valeur de la propriété owns.
     *
     * @return
     *     possible object is
     *     {@link Boolean }
     *
     */
    public boolean isOwns() {
        if (owns == null) {
            return false;
        } else {
            return owns;
        }
    }

    /**
     * Définit la valeur de la propriété owns.
     *
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *
     */
    public void setOwns(Boolean value) {
        this.owns = value;
    }

}
