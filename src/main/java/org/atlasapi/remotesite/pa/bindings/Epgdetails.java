//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.11.01 at 02:20:03 PM GMT 
//


package org.atlasapi.remotesite.pa.bindings;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "epg"
})
@XmlRootElement(name = "epgdetails")
public class Epgdetails {

    @XmlElement(required = true)
    protected List<Epg> epg;

    /**
     * Gets the value of the epg property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the epg property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEpg().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Epg }
     * 
     * 
     */
    public List<Epg> getEpg() {
        if (epg == null) {
            epg = new ArrayList<Epg>();
        }
        return this.epg;
    }

}
