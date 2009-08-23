/*
 * eID Applet Project.
 * Copyright (C) 2009 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package be.fedict.eid.applet.service.signer.ooxml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xml.security.utils.Constants;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import be.fedict.eid.applet.service.signer.KeyInfoKeySelector;

/**
 * Signature verifier util class for Office Open XML file format.
 * 
 * @author Frank Cornelis
 * 
 */
public class OOXMLSignatureVerifier {

	private static final Log LOG = LogFactory
			.getLog(OOXMLSignatureVerifier.class);

	private OOXMLSignatureVerifier() {
		super();
	}

	/**
	 * Checks whether the file referred by the given URL is an OOXML document.
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static boolean isOOXML(URL url) throws IOException {
		ZipInputStream zipInputStream = new ZipInputStream(url.openStream());
		ZipEntry zipEntry;
		while (null != (zipEntry = zipInputStream.getNextEntry())) {
			if (false == "[Content_Types].xml".equals(zipEntry.getName())) {
				continue;
			}
			if (zipEntry.getSize() > 0) {
				return true;
			}
		}
		return false;
	}

	public static X509Certificate getSigner(URL url) throws IOException,
			ParserConfigurationException, SAXException, TransformerException,
			MarshalException, XMLSignatureException {
		String signatureResourceName = getSignatureResourceName(url);
		if (null == signatureResourceName) {
			return null;
		}

		Document signatureDocument = getSignatureDocument(url,
				signatureResourceName);
		if (null == signatureDocument) {
			return null;
		}

		NodeList signatureNodeList = signatureDocument.getElementsByTagNameNS(
				XMLSignature.XMLNS, "Signature");
		if (0 == signatureNodeList.getLength()) {
			return null;
		}
		Node signatureNode = signatureNodeList.item(0);

		KeyInfoKeySelector keySelector = new KeyInfoKeySelector();
		DOMValidateContext domValidateContext = new DOMValidateContext(
				keySelector, signatureNode);
		domValidateContext.setProperty("org.jcp.xml.dsig.validateManifests",
				Boolean.TRUE);
		OOXMLURIDereferencer dereferencer = new OOXMLURIDereferencer(url);
		domValidateContext.setURIDereferencer(dereferencer);

		XMLSignatureFactory xmlSignatureFactory = XMLSignatureFactory
				.getInstance();
		XMLSignature xmlSignature = xmlSignatureFactory
				.unmarshalXMLSignature(domValidateContext);
		boolean validity = xmlSignature.validate(domValidateContext);

		if (false == validity) {
			return null;
		}
		X509Certificate signer = keySelector.getCertificate();
		return signer;
	}

	private static Document getSignatureDocument(URL url,
			String signatureResourceName) throws IOException,
			ParserConfigurationException, SAXException {
		ZipInputStream zipInputStream = new ZipInputStream(url.openStream());
		ZipEntry zipEntry;
		while (null != (zipEntry = zipInputStream.getNextEntry())) {
			if (false == signatureResourceName.equals(zipEntry.getName())) {
				continue;
			}
			Document signatureDocument = loadDocument(zipInputStream);
			return signatureDocument;
		}
		return null;
	}

	private static String getSignatureResourceName(URL url) throws IOException,
			ParserConfigurationException, SAXException, TransformerException {
		InputStream inputStream = url.openStream();
		ZipInputStream zipInputStream = new ZipInputStream(inputStream);
		ZipEntry zipEntry;
		while (null != (zipEntry = zipInputStream.getNextEntry())) {
			if (false == "[Content_Types].xml".equals(zipEntry.getName())) {
				continue;
			}
			Document contentTypesDocument = loadDocument(zipInputStream);
			Element nsElement = contentTypesDocument.createElement("ns");
			nsElement
					.setAttributeNS(Constants.NamespaceSpecNS, "xmlns:tns",
							"http://schemas.openxmlformats.org/package/2006/content-types");
			NodeList nodeList = XPathAPI
					.selectNodeList(
							contentTypesDocument,
							"/tns:Types/tns:Override[@ContentType='application/vnd.openxmlformats-package.digital-signature-xmlsignature+xml']/@PartName",
							nsElement);
			if (nodeList.getLength() == 0) {
				return null;
			}
			String partName = nodeList.item(0).getTextContent();
			LOG.debug("part name: " + partName);
			partName = partName.substring(1); // remove '/'
			return partName;
		}
		return null;
	}

	private static Document loadDocument(InputStream documentInputStream)
			throws ParserConfigurationException, SAXException, IOException {
		InputSource inputSource = new InputSource(documentInputStream);
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
				.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		DocumentBuilder documentBuilder = documentBuilderFactory
				.newDocumentBuilder();
		Document document = documentBuilder.parse(inputSource);
		return document;
	}
}
