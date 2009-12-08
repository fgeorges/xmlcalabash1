/*
 * LabelElements.java
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

import java.util.Map;
import java.util.Iterator;

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.util.ProcessMatchingNodes;
import com.xmlcalabash.util.ProcessMatch;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.model.RuntimeValue;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmAtomicValue;

import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;

/**
 *
 * @author ndw
 */
public class LabelElements extends DefaultStep implements ProcessMatchingNodes {
    private static final QName _attribute = new QName("attribute");
    private static final QName _match = new QName("match");
    private static final QName _label = new QName("label");
    private static final QName _replace = new QName("replace");
    private static final QName p_index = new QName("p", XProcConstants.NS_XPROC, "index");
    private ReadablePipe source = null;
    private WritablePipe result = null;
    private Map<QName, RuntimeValue> inScopeOptions = null;
    private ProcessMatch matcher = null;
    private QName attribute = null;
    private String label = null;
    private boolean replace = true;
    private String matchPattern = null;
    private int count = 1;

    /** Creates a new instance of LabelElements */
    public LabelElements(XProcRuntime runtime, XAtomicStep step) {
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

        attribute = getOption(_attribute).getQName();
        label = getOption(_label).getString();
        replace = getOption(_replace).getBoolean();

        matcher = new ProcessMatch(runtime, this);
        matcher.match(source.read(), getOption(_match));

        result.write(matcher.getResult());
    }

    public boolean processStartDocument(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(24);
    }

    public void processEndDocument(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(24);
    }

    public boolean processStartElement(XdmNode node) throws SaxonApiException {
        matcher.addStartElement(node);

        boolean found = false;
        XdmSequenceIterator iter = node.axisIterator(Axis.ATTRIBUTE);
        while (iter.hasNext()) {
            XdmNode attr = (XdmNode) iter.next();
            if (attribute.equals(attr.getNodeName())) {
                found = true;
                if (replace) {
                    matcher.addAttribute(attr, computedLabel());
                } else {
                    matcher.addAttribute(attr);
                }
            } else {
                matcher.addAttribute(attr);
            }
        }

        if (!found) {
            matcher.addAttribute(attribute, computedLabel());
        }

        return true;
    }

    public void processEndElement(XdmNode node) throws SaxonApiException {
        matcher.addEndElement();
    }

    public void processText(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(23);
    }

    public void processComment(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(23);
    }

    public void processPI(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(23);
    }

    public void processAttribute(XdmNode node) throws SaxonApiException {
        throw XProcException.stepError(23);
    }

    private String computedLabel() throws SaxonApiException {
        XPathCompiler xcomp = runtime.getProcessor().newXPathCompiler();
        xcomp.declareNamespace("p", XProcConstants.NS_XPROC);
        xcomp.declareVariable(p_index);

        XPathExecutable xexec = xcomp.compile(label);
        XPathSelector selector = xexec.load();

        selector.setVariable(p_index,new XdmAtomicValue(count++));

        Iterator<XdmItem> values = selector.iterator();
        XdmAtomicValue item = (XdmAtomicValue) values.next();
        return item.getStringValue();
    }
}
