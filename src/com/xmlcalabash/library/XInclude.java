/*
 * XInclude.java
 *
 * Copyright 2008 Mark Logic Corporation.
 * Portions Copyright 2007 Sun Microsystems, Inc.
 * All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * https://xproc.dev.java.net/public/CDDL+GPL.html or
 * docs/CDDL+GPL.txt in the distribution. See the License for the
 * specific language governing permissions and limitations under the
 * License. When distributing the software, include this License Header
 * Notice in each file and include the License file at docs/CDDL+GPL.txt.
 */

package com.xmlcalabash.library;

import java.util.Stack;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Iterator;
import java.util.HashSet;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URISyntaxException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.util.*;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.model.*;
import net.sf.saxon.s9api.*;

import com.xmlcalabash.runtime.XAtomicStep;

/**
 *
 * @author ndw
 */
public class XInclude extends DefaultStep implements ProcessMatchingNodes {
    private static final QName xi_include = new QName("http://www.w3.org/2001/XInclude","include");
    private static final QName xi_fallback = new QName("http://www.w3.org/2001/XInclude","fallback");
    private static final QName _fixup_xml_base = new QName("", "fixup-xml-base");
    private static final QName _fixup_xml_lang = new QName("", "fixup-xml-lang");
    private ReadablePipe source = null;
    private WritablePipe result = null;
    private Stack<ProcessMatch> matcherStack = new Stack<ProcessMatch> ();
    private boolean fixupBase = false;
    private boolean fixupLang = false;
    private HashSet<String> seenURIs = new HashSet<String> ();
    private Exception mostRecentException = null;

    /**
     * Creates a new instance of XInclude
     */
    public XInclude(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        source = pipe;
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void reset() {
        source.resetReader();
        result.resetWriter();
    }

    public void run() throws SaxonApiException {
        super.run();

        fixupBase = getOption(_fixup_xml_base, false);
        fixupLang = getOption(_fixup_xml_lang, false);

        XdmNode doc = source.read();
        XdmNode xdoc = expandXIncludes(doc);

        result.write(xdoc);
    }

    private XdmNode expandXIncludes(XdmNode doc) {
        finest(doc, "Starting expandXIncludes");
        ProcessMatch matcher = new ProcessMatch(runtime, this);
        matcherStack.push(matcher);
        matcher.match(doc, new RuntimeValue("/|*", step.getNode()));
        XdmNode result = matcher.getResult();
        matcherStack.pop();
        return result;
    }

    public boolean processStartDocument(XdmNode node) throws SaxonApiException {
        //finest(node, "Start document " + matcherStack.size());
        if (matcherStack.size() == 1) {
            matcherStack.peek().startDocument(node.getBaseURI());
        }
        return true;
    }

    public void processEndDocument(XdmNode node) throws SaxonApiException {
        //finest(node, "End document " + matcherStack.size());
        if (matcherStack.size() == 1) {
            matcherStack.peek().endDocument();
        }
    }

    public boolean processStartElement(XdmNode node) throws SaxonApiException {
        //finest(node, "Start element " + node.getNodeName());
        ProcessMatch matcher = matcherStack.peek();
        if (xi_include.equals(node.getNodeName())) {
            String href = node.getAttributeValue(new QName("","href"));
            String parse = node.getAttributeValue(new QName("","parse"));
            String xptr = node.getAttributeValue(new QName("","xpointer"));
            XPointer xpointer = null;
            XdmNode subdoc = null;

            if (xptr != null) {
                xpointer = new XPointer(xptr);
            }

            if ("text".equals(parse)) {
                if (xpointer != null) {
                    throw new XProcException("XPointer cannot be applied with XInclude parse=text: " + href);
                }
                String text = readText(href, node.getBaseURI().toASCIIString());
                if (text == null) {
                    finer(node, "XInclude text parse failed: " + href);
                    fallback(node, href);
                    return false;
                } else {
                    finer(node, "XInclude text parse: " + href);
                }
                matcher.addText(text);
                return false;
            } else {
                subdoc = readXML(href, node.getBaseURI().toASCIIString());
                if (subdoc == null) {
                    finer(node, "XInclude parse failed: " + href);
                    fallback(node, href);
                    return false;
                } else {
                    finer(node, "XInclude parse: " + href);
                }

                Vector<XdmNode> nodes = null;
                if (xpointer == null) {
                    nodes = new Vector<XdmNode> ();

                    // Put all the children of the document in there, so that we can add xml:base to the root...
                    XdmSequenceIterator iter = subdoc.axisIterator(Axis.CHILD);
                    while (iter.hasNext()) {
                        nodes.add((XdmNode) iter.next());
                    }
                } else {
                    String xpath = xpointer.xpathEquivalent();
                    Hashtable<String,String> nsBindings = xpointer.xpathNamespaces();
                    nodes = selectNodes(subdoc, xpath, nsBindings);
                }

                for (XdmNode snode : nodes) {
                    if ((fixupBase || fixupLang) && snode.getNodeKind() == XdmNodeKind.ELEMENT) {
                        Fixup fixup = new Fixup(runtime);
                        snode = fixup.fixup(snode);
                    }

                    XdmNode ex = expandXIncludes(snode);
                    matcher.addSubtree(ex);
                }

                return false;
            }
        } else {
            matcher.addStartElement(node);
            matcher.addAttributes(node);
            matcher.startContent();
            return true;
        }
    }

    public void processAttribute(XdmNode node) throws SaxonApiException {
        throw new UnsupportedOperationException("processAttribute can't happen in XInclude");
    }

    public void processEndElement(XdmNode node) throws SaxonApiException {
        if (xi_include.equals(node.getNodeName())) {
            // Do nothing, we've already output the subtree that replaced xi:include
        } else {
            //finest(node, "End element " + node.getNodeName());
            matcherStack.peek().addEndElement();
        }
    }

    public void processText(XdmNode node) throws SaxonApiException {
        throw new UnsupportedOperationException("processText can't happen in XInclude");
    }

    public void processComment(XdmNode node) throws SaxonApiException {
        throw new UnsupportedOperationException("processComment can't happen in XInclude");
    }

    public void processPI(XdmNode node) throws SaxonApiException {
        throw new UnsupportedOperationException("processPI can't happen in XInclude");
    }

    private Vector<XdmNode> selectNodes(XdmNode doc, String select, Hashtable<String,String> nsBindings) {
        Vector<XdmNode> selectedNodes = new Vector<XdmNode> ();

        XPathSelector selector = null;
        XPathCompiler xcomp = runtime.getProcessor().newXPathCompiler();
        for (String prefix : nsBindings.keySet()) {
            xcomp.declareNamespace(prefix, nsBindings.get(prefix));
        }

        try {
            XPathExecutable xexec = xcomp.compile(select);
            selector = xexec.load();
        } catch (SaxonApiException sae) {
            throw new XProcException(sae);
        }

        try {
            selector.setContextItem(doc);

            Iterator iter = selector.iterator();
            while (iter.hasNext()) {
                XdmItem item = (XdmItem) iter.next();
                XdmNode node = null;
                try {
                    node = (XdmNode) item;
                } catch (ClassCastException cce) {
                    throw new XProcException ("XInclude pointer matched non-node item?");
                }
                selectedNodes.add(node);
            }
        } catch (SaxonApiException sae) {
            throw new XProcException(sae);
        }

        return selectedNodes;
    }

    public String readText(String href, String base) {
        finest(null, "XInclude read text: " + href + " (" + base + ")");

        URI baseURI = null;
        try {
            baseURI = new URI(base);
        } catch (URISyntaxException use) {
            throw new XProcException(use);
        }

        URI hrefURI = baseURI.resolve(href);

        String data = "";

        try {
            URL url = hrefURI.toURL();
            URLConnection conn = url.openConnection();

            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String line;
            while ((line = rd.readLine()) != null) {
                data += line + "\n";
            }
            rd.close();
        } catch (Exception e) {
            finest(null, "XInclude read text failed");
            mostRecentException = e;
            return null;
        }

        return data;
    }

    public XdmNode readXML(String href, String base) {
        finest(null, "XInclude read XML: " + href + " (" + base + ")");
        try {
            XdmNode doc = runtime.parse(href, base);

            String uri = doc.getBaseURI().toASCIIString();
            if (seenURIs.contains(uri)) {
                throw XProcException.stepError(29,"XInclude document includes itself: " + href);
            }
            seenURIs.add(uri);

            return doc;
        } catch (Exception e) {
            finest(null, "XInclude read XML failed");
            mostRecentException = e;
            return null;
        }
    }

    public void fallback(XdmNode node, String href) {
        finest(node, "fallback: " + node.getNodeName());
        boolean valid = true;
        XdmNode fallback = null;
        for (XdmNode child : new RelevantNodes(runtime, node, Axis.CHILD)) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                valid = valid && xi_fallback.equals(child.getNodeName()) && (fallback == null);
                fallback = child;
            } else {
                valid = false;
            }
        }

        if (!valid) {
            throw new XProcException("XInclude element must contain exactly one xi:fallback element.");
        }

        if (fallback == null) {
            if (mostRecentException != null) {
                throw new XProcException("XInclude resource error (" + href + ") and no fallback provided.", mostRecentException);
            } else {
                throw new XProcException("XInclude resource error (" + href + ") and no fallback provided.");
            }
        }

        XdmSequenceIterator iter = fallback.axisIterator(Axis.CHILD);
        while (iter.hasNext()) {
            XdmNode fbc = (XdmNode) iter.next();
            if (fbc.getNodeKind() == XdmNodeKind.ELEMENT) {
                fbc = expandXIncludes(fbc);
            }
            matcherStack.peek().addSubtree(fbc);
        }
    }

    private class Fixup implements ProcessMatchingNodes {
        private XProcRuntime runtime = null;
        private ProcessMatch matcher = null;
        private boolean root = true;

        public Fixup(XProcRuntime runtime) {
            this.runtime = runtime;
        }

        public XdmNode fixup(XdmNode node) {
            matcher = new ProcessMatch(runtime, this);
            matcher.match(node, new RuntimeValue("*", step.getNode()));
            return matcher.getResult();
        }

        public boolean processStartDocument(XdmNode node) throws SaxonApiException {
            matcher.startDocument(node.getBaseURI());
            return true;
        }

        public void processEndDocument(XdmNode node) throws SaxonApiException {
            matcher.endDocument();
        }

        public boolean processStartElement(XdmNode node) throws SaxonApiException {
            matcher.addStartElement(node);

            if (root) {
                root = false;
                XdmSequenceIterator iter = node.axisIterator(Axis.ATTRIBUTE);
                while (iter.hasNext()) {
                    XdmNode child = (XdmNode) iter.next();
                    if ((XProcConstants.xml_base.equals(child.getNodeName()) && fixupBase)
                        || (XProcConstants.xml_lang.equals(child.getNodeName()) && fixupLang)) {
                        // nop;
                    } else {
                        matcher.addAttribute(child);
                    }
                }
                if (fixupBase) {
                    matcher.addAttribute(XProcConstants.xml_base, node.getBaseURI().toASCIIString());
                }
                String lang = getLang(node);
                if (fixupLang && lang != null) {
                    matcher.addAttribute(XProcConstants.xml_lang, lang);
                }
            } else {
                matcher.addAttributes(node);
            }

            matcher.startContent();
            return true;
        }

        public void processAttribute(XdmNode node) throws SaxonApiException {
            throw new XProcException("This can't happen!?");
        }

        public void processEndElement(XdmNode node) throws SaxonApiException {
            matcher.addEndElement();
        }

        public void processText(XdmNode node) throws SaxonApiException {
            throw new XProcException("This can't happen!?");
        }

        public void processComment(XdmNode node) throws SaxonApiException {
            throw new XProcException("This can't happen!?");
        }

        public void processPI(XdmNode node) throws SaxonApiException {
            throw new XProcException("This can't happen!?");
        }

        private String getLang(XdmNode node) {
            String lang = null;
            while (lang == null && node.getNodeKind() == XdmNodeKind.ELEMENT) {
                lang = node.getAttributeValue(XProcConstants.xml_lang);
                node = node.getParent();
            }
            return lang;
        }
    }

}

