package com.coderscollective.mapsreceiver;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class GPXGenerator {
    public static File createGPX(File outputFile, String routeName, List<WayPoint> wayPoints)  {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);

            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // root elements
            Document doc = docBuilder.newDocument();
            String namespace = "http://www.topografix.com/GPX/1/1";
            Element rootElement = doc.createElementNS(namespace, "gpx");
            doc.appendChild(rootElement);

            rootElement.setAttribute("xmlns", namespace);
            rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            rootElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation", "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd");

            rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:trp", "http://www.garmin.com/xmlschemas/TripExtensions/v1");

            rootElement.setAttribute("creator", "MapsReceiver");
            rootElement.setAttribute("version", "1.1");

            Element rte = doc.createElementNS(namespace, "rte");
            rootElement.appendChild(rte);

            Element name = doc.createElementNS(namespace, "name");
            name.setTextContent(routeName);
            rte.appendChild(name);

            Element ext = doc.createElementNS(namespace, "extensions");
            rte.appendChild(ext);
            Element trp = doc.createElementNS("http://www.garmin.com/xmlschemas/TripExtensions/v1", "trp:Trip");
            ext.appendChild(trp);
            Element mode = doc.createElementNS("http://www.garmin.com/xmlschemas/TripExtensions/v1", "trp:TransportationMode");
            mode.setTextContent("Automotive");
            trp.appendChild(mode);

            for (WayPoint wp : wayPoints) {
                Element xwp = doc.createElementNS(namespace, "rtept");
                xwp.setAttribute("lat", String.valueOf(wp.lat));
                xwp.setAttribute("lon", String.valueOf(wp.lon));
                Element wpext = doc.createElementNS(namespace, "extensions");
                if (wp.silent) {
                    wpext.appendChild(doc.createElementNS("http://www.garmin.com/xmlschemas/TripExtensions/v1", "trp:ShapingPoint"));
                } else {
                    wpext.appendChild(doc.createElementNS("http://www.garmin.com/xmlschemas/TripExtensions/v1", "trp:ViaPoint"));
                    if (wp.name != null) {
                        Element wpname = doc.createElementNS(namespace, "name");
                        wpname.setTextContent(wp.name);
                        xwp.appendChild(wpname);
                    }
                }
                xwp.appendChild(wpext);
                rte.appendChild(xwp);
            }

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            
            // Log a copy of the XML to Logcat for easy access
            try {
                java.io.StringWriter writer = new java.io.StringWriter();
                transformer.transform(source, new StreamResult(writer));
                Log.d("GPXGenerator", "Generated GPX Content:\n" + writer.toString());
            } catch (Exception e) {
                Log.e("GPXGenerator", "Failed to log XML", e);
            }

            StreamResult result = new StreamResult(outputFile);
            transformer.transform(source, result);
            Log.i("GPXGenerator", "File saved to: " + outputFile.getAbsolutePath());
            return outputFile;

        } catch (Exception e) {
            Log.e("GPXGenerator", "Error creating GPX", e);
            return null;
        }
    }

}
