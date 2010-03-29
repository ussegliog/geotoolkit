//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.03.29 at 11:05:09 AM CEST 
//


package org.geotoolkit.metadata.dimap;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for t_Data_File complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="t_Data_File">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element ref="{}DATA_FILE_PATH"/>
 *         &lt;element ref="{}BAND_INDEX" minOccurs="0"/>
 *         &lt;element ref="{}SUPER_TILE_INDEX_COL" minOccurs="0"/>
 *         &lt;element ref="{}SUPER_TILE_INDEX_ROW" minOccurs="0"/>
 *         &lt;element ref="{}PYRAMID_LEVEL_INDEX" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "t_Data_File", propOrder = {

})
public class TDataFile {

    @XmlElement(name = "DATA_FILE_PATH", required = true)
    protected TDATAFILEPATH datafilepath;
    @XmlElement(name = "BAND_INDEX")
    protected TBANDINDEX bandindex;
    @XmlElement(name = "SUPER_TILE_INDEX_COL")
    protected BigInteger supertileindexcol;
    @XmlElement(name = "SUPER_TILE_INDEX_ROW")
    protected BigInteger supertileindexrow;
    @XmlElement(name = "PYRAMID_LEVEL_INDEX")
    protected BigInteger pyramidlevelindex;

    /**
     * Gets the value of the datafilepath property.
     * 
     * @return
     *     possible object is
     *     {@link TDATAFILEPATH }
     *     
     */
    public TDATAFILEPATH getDATAFILEPATH() {
        return datafilepath;
    }

    /**
     * Sets the value of the datafilepath property.
     * 
     * @param value
     *     allowed object is
     *     {@link TDATAFILEPATH }
     *     
     */
    public void setDATAFILEPATH(TDATAFILEPATH value) {
        this.datafilepath = value;
    }

    /**
     * Gets the value of the bandindex property.
     * 
     * @return
     *     possible object is
     *     {@link TBANDINDEX }
     *     
     */
    public TBANDINDEX getBANDINDEX() {
        return bandindex;
    }

    /**
     * Sets the value of the bandindex property.
     * 
     * @param value
     *     allowed object is
     *     {@link TBANDINDEX }
     *     
     */
    public void setBANDINDEX(TBANDINDEX value) {
        this.bandindex = value;
    }

    /**
     * Gets the value of the supertileindexcol property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getSUPERTILEINDEXCOL() {
        return supertileindexcol;
    }

    /**
     * Sets the value of the supertileindexcol property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setSUPERTILEINDEXCOL(BigInteger value) {
        this.supertileindexcol = value;
    }

    /**
     * Gets the value of the supertileindexrow property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getSUPERTILEINDEXROW() {
        return supertileindexrow;
    }

    /**
     * Sets the value of the supertileindexrow property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setSUPERTILEINDEXROW(BigInteger value) {
        this.supertileindexrow = value;
    }

    /**
     * Gets the value of the pyramidlevelindex property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getPYRAMIDLEVELINDEX() {
        return pyramidlevelindex;
    }

    /**
     * Sets the value of the pyramidlevelindex property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setPYRAMIDLEVELINDEX(BigInteger value) {
        this.pyramidlevelindex = value;
    }

}
