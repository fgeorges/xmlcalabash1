/*
 * EscapeMarkup.java
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

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.util.RelevantNodes;
import com.xmlcalabash.util.TreeWriter;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.ByteArrayOutputStream;

import com.xmlcalabash.runtime.XAtomicStep;

/**
 *
 * @author ndw
 */
public class EscapeMarkup extends DefaultStep {
    private ReadablePipe source = null;
    private WritablePipe result = null;

    /** Creates a new instance of EscapeMarkup */
    public EscapeMarkup(XProcRuntime runtime, XAtomicStep step) {
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

        Serializer serializer = makeSerializer();

        XdmNode doc = source.read();

        TreeWriter tree = new TreeWriter(runtime);
        tree.startDocument(doc.getBaseURI());
        for (XdmNode child : new RelevantNodes(runtime, doc, Axis.CHILD)) {
            if (child.getNodeKind() == XdmNodeKind.COMMENT) {
                tree.addComment(child.getStringValue());
            } else if (child.getNodeKind() == XdmNodeKind.PROCESSING_INSTRUCTION) {
                tree.addPI(child.getNodeName().getLocalName(), child.getStringValue());
            } else if (child.getNodeKind() == XdmNodeKind.TEXT) {
                tree.addText(child.getStringValue());
            } else {
                tree.addStartElement(child);
                tree.addAttributes(child);
                tree.startContent();

                Processor qtproc = runtime.getProcessor();
                DocumentBuilder builder = qtproc.newDocumentBuilder();
                try {
                    builder.setBaseURI(new URI("http://example.com/"));
                } catch (URISyntaxException ex) {
                    // can't happen
                }

                // Serialize the *whole* thing, then strip off the start and end tags, because
                // otherwise namespace fixup messes with the namespace bindings
                XQueryCompiler xqcomp = qtproc.newXQueryCompiler();
                XQueryExecutable xqexec = xqcomp.compile(".");
                XQueryEvaluator xqeval = xqexec.load();
                xqeval.setContextItem(child);

                ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                serializer.setOutputStream(outstr);

                xqeval.setDestination(serializer);
                xqeval.run();

                String data = outstr.toString();

                data = data.replaceAll("^<.*?>",""); // Strip off the start tag...
                data = data.replaceAll("<[^<>]*?>$",""); // Strip off the end tag

                tree.addText(data);
                tree.addEndElement();
            }
        }
        tree.endDocument();

        result.write(tree.getResult());
    }
}