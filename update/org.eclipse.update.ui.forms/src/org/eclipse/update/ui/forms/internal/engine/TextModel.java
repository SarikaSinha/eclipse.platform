/*
 * (c) Copyright 2001 MyCorporation.
 * All Rights Reserved.
 */
package org.eclipse.update.ui.forms.internal.engine;

import java.util.Vector;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.update.ui.forms.internal.*;
import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.*;
import java.io.*;
import org.w3c.dom.*;
import org.eclipse.jface.resource.JFaceResources;

/**
 * @version 	1.0
 * @author
 */
public class TextModel implements ITextModel {
	Vector paragraphs;
	IHyperlinkListener urlListener;
	IHyperlinkSegment[] hyperlinks;
	int selectedLinkIndex = -1;
	HyperlinkSettings hyperlinkSettings;
	
	class LocalHyperlinkSettings extends HyperlinkSettings {
	}

	/*
	 * @see ITextModel#getParagraphs()
	 */
	public IParagraph[] getParagraphs() {
		if (paragraphs == null)
			return new IParagraph[0];
		return (IParagraph[]) paragraphs.toArray(new IParagraph[paragraphs.size()]);
	}

	/*
	 * @see ITextModel#parse(String)
	 */
	public void parseTaggedText(String taggedText, boolean expandURLs)
		throws CoreException {
		try {
			InputStream stream = new ByteArrayInputStream(taggedText.getBytes("UTF8"));
			parseInputStream(stream, expandURLs);
		} catch (UnsupportedEncodingException e) {
		}
	}

	public void parseInputStream(InputStream is, boolean expandURLs)
		throws CoreException {
		DOMParser parser = new DOMParser();
		reset();
		try {
			InputSource source = new InputSource(is);
			parser.parse(source);
			/*
			if (errorHandler.getErrorCount()>0 ||
			errorHandler.getFatalErrorCount()>0) {
				throwParseErrorsException();
			*/
			processDocument(parser.getDocument(), expandURLs);
		} catch (SAXException e) {
		} catch (IOException e) {
		}
	}

	private void processDocument(Document doc, boolean expandURLs) {
		Node root = doc.getDocumentElement();
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.TEXT_NODE) {
				// Make an implicit paragraph
				String text = child.getNodeValue();
				if (text != null && !isIgnorableWhiteSpace(text)) {
					Paragraph p = new Paragraph(true);
					p.parseRegularText(text, expandURLs, getHyperlinkSettings(), null);
					paragraphs.add(p);
				}
			} else if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (child.getNodeName().equalsIgnoreCase("p")) {
					IParagraph p = processParagraph(child, expandURLs);
					if (p != null)
						paragraphs.add(p);
				}
			}
		}
	}
	private IParagraph processParagraph(Node paragraph, boolean expandURLs) {
		NodeList children = paragraph.getChildNodes();
		NamedNodeMap atts = paragraph.getAttributes();
		Node addSpaceAtt = atts.getNamedItem("addVerticalSpace");
		boolean addSpace = true;
		
		if (addSpaceAtt != null) {
			String value = addSpaceAtt.getNodeValue();
			addSpace = value.equalsIgnoreCase("true");
		}
		Paragraph p = new Paragraph(addSpace);
		
		processSegments(p, children, expandURLs);
		return p;
	}

	private void processSegments(
		Paragraph p,
		NodeList children,
		boolean expandURLs) {
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			IParagraphSegment segment = null;

			if (child.getNodeType() == Node.TEXT_NODE) {
				String value = child.getNodeValue();

				if (value != null && !isIgnorableWhiteSpace(value)) {
					p.parseRegularText(value, expandURLs, getHyperlinkSettings(), null);
				}
			} else if (child.getNodeType() == Node.ELEMENT_NODE) {
				String name = child.getNodeName();
				if (name.equalsIgnoreCase("img")) {
					segment = processImageSegment(child);
				} else if (name.equalsIgnoreCase("a")) {
					segment = processHyperlinkSegment(child, getHyperlinkSettings());
				}
				else if (name.equalsIgnoreCase("text")) {
					processTextSegment(p, expandURLs, child);
				}
				else if (name.equalsIgnoreCase("b")) {
					String text = getNodeText(child).trim();
					String fontId = JFaceResources.BANNER_FONT;
					p.parseRegularText(text, expandURLs, getHyperlinkSettings(), fontId);
				}
			}
			if (segment != null) {
				p.addSegment(segment);
			}
		}
	}
	
	private boolean isIgnorableWhiteSpace(String text) {
		for (int i=0; i<text.length(); i++) {
			char c = text.charAt(i);
			if (c=='\n' || c=='\r' || c=='\f') continue;
			return false;
		}
		return true;
	}

	private IParagraphSegment processImageSegment(Node image) {
		ImageSegment segment = new ImageSegment();
		NamedNodeMap atts = image.getAttributes();
		Node id = atts.getNamedItem("href");
		Node align = atts.getNamedItem("align");
		if (id != null) {
			String value = id.getNodeValue();
			segment.setObjectId(value);
		}
		if (align != null) {
			String value = align.getNodeValue().toLowerCase();
			if (value.equals("top"))
				segment.setVerticalAlignment(ImageSegment.TOP);
			else if (value.equals("middle"))
				segment.setVerticalAlignment(ImageSegment.MIDDLE);
			else if (value.equals("bottom"))
				segment.setVerticalAlignment(ImageSegment.BOTTOM);
		}
		return segment;
	}
	
	private String getNodeText(Node node) {
		NodeList children = node.getChildNodes();
		String text = "";
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.TEXT_NODE) {
				text += child.getNodeValue();
			}
		}
		return text;
	}

	private IParagraphSegment processHyperlinkSegment(
		Node link,
		HyperlinkSettings settings) {
		String text = getNodeText(link);
		HyperlinkSegment segment = new HyperlinkSegment(text, settings, null);
		NamedNodeMap atts = link.getAttributes();
		Node href = atts.getNamedItem("href");
		if (href != null) {
			String value = href.getNodeValue();
			segment.setActionId(value);
		}
		return segment;
	}
	
	private void processTextSegment(Paragraph p, boolean expandURLs, Node textNode) {
		String text = getNodeText(textNode).trim();

		NamedNodeMap atts = textNode.getAttributes();
		Node font = atts.getNamedItem("font");
		String fontId = null;
		if (font != null) {
			fontId = font.getNodeValue();
		}
		p.parseRegularText(text, expandURLs, getHyperlinkSettings(), fontId);
	}

	public void parseRegularText(String regularText, boolean convertURLs)
		throws CoreException {
		reset();
		StringBuffer buf = new StringBuffer();

		Paragraph p = new Paragraph(true);
		paragraphs.add(p);
		int pstart = 0;

		for (int i = 0; i < regularText.length(); i++) {
			char c = regularText.charAt(i);
			if (p == null) {
				p = new Paragraph(true);
				paragraphs.add(p);
			}
			if (c == '\n') {
				String text = regularText.substring(pstart, i);
				pstart = i + 1;
				p.parseRegularText(text, convertURLs, getHyperlinkSettings(), null);
				p = null;
			}
		}
		if (p != null) {
			// no new line
			String text = regularText.substring(pstart);
			p.parseRegularText(text, convertURLs, getHyperlinkSettings(), null);
		}
	}

	public HyperlinkSettings getHyperlinkSettings() {
		if (hyperlinkSettings == null) {
			hyperlinkSettings = new LocalHyperlinkSettings();
		}
		return hyperlinkSettings;
	}

	public void setHyperlinkSettings(HyperlinkSettings settings) {
		this.hyperlinkSettings = settings;
	}

	public void setURLListener(IHyperlinkListener urlListener) {
		this.urlListener = urlListener;
	}

	private void reset() {
		if (paragraphs == null)
			paragraphs = new Vector();
		paragraphs.clear();
		selectedLinkIndex = -1;
		hyperlinks = null;
	}

	IHyperlinkSegment[] getHyperlinks() {
		if (hyperlinks != null || paragraphs == null)
			return hyperlinks;
		Vector result = new Vector();
		for (int i = 0; i < paragraphs.size(); i++) {
			IParagraph p = (IParagraph) paragraphs.get(i);
			IParagraphSegment[] segments = p.getSegments();
			for (int j = 0; j < segments.length; j++) {
				if (segments[j] instanceof IHyperlinkSegment)
					result.add(segments[j]);
			}
		}
		hyperlinks =
			(IHyperlinkSegment[]) result.toArray(new IHyperlinkSegment[result.size()]);
		return hyperlinks;
	}

	public IHyperlinkSegment findHyperlinkAt(int x, int y) {
		IHyperlinkSegment[] links = getHyperlinks();
		for (int i = 0; i < links.length; i++) {
			if (links[i].contains(x, y))
				return links[i];
		}
		return null;
	}

	public IHyperlinkSegment getSelectedLink() {
		if (selectedLinkIndex == -1)
			return null;
		return hyperlinks[selectedLinkIndex];
	}

	public boolean traverseLinks(boolean next) {
		IHyperlinkSegment[] links = getHyperlinks();
		if (links == null)
			return false;
		int size = links.length;
		if (next) {
			selectedLinkIndex++;
		} else
			selectedLinkIndex--;

		if (selectedLinkIndex < 0 || selectedLinkIndex > size - 1) {
			selectedLinkIndex = -1;
		}
		return selectedLinkIndex != -1;
	}

	public void selectLink(IHyperlinkSegment link) {
		if (link==null) selectedLinkIndex = -1;
		else {
			IHyperlinkSegment[] links = getHyperlinks();
			selectedLinkIndex = -1;
			if (links==null) return;
			for (int i=0; i<links.length; i++) {
				if (links[i].equals(link)) {
					selectedLinkIndex = i;
					break;
				}
			}
		}
	}
	
	public boolean hasFocusSegments() {
		IHyperlinkSegment [] links = getHyperlinks();
		if (links.length>0) return true;
		return true;
	}

	public void dispose() {
		if (hyperlinkSettings instanceof LocalHyperlinkSettings)
		   hyperlinkSettings.dispose();
	}
}