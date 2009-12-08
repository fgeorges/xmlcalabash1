package com.xmlcalabash.functions;

import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.ExtensionFunctionCall;
import net.sf.saxon.functions.ExtensionFunctionDefinition;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.XdmNode;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcRuntime;

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by Norman Walsh are Copyright (C) Mark Logic Corporation. All Rights Reserved.
//
// Contributor(s): Norman Walsh.
//

/**
 * Implementation of the XSLT system-property() function
 */

public class BaseURI extends ExtensionFunctionDefinition {
    private static StructuredQName funcname = new StructuredQName("p", XProcConstants.NS_XPROC,"base-uri");
    private XProcRuntime runtime = null;

    protected BaseURI() {
        // you can't call this one
    }

    public BaseURI(XProcRuntime runtime) {
        this.runtime = runtime;
    }

    public StructuredQName getFunctionQName() {
        return funcname;
    }

    public int getMinimumNumberOfArguments() {
        return 0;
    }

    public int getMaximumNumberOfArguments() {
        return 1;
    }

    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.OPTIONAL_NODE};
    }

    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_ATOMIC;
    }

    public boolean dependsOnFocus() {
        return true;
    }

    public ExtensionFunctionCall makeCallExpression() {
        return new BaseURICall();
    }

    private class BaseURICall extends ExtensionFunctionCall {
        public SequenceIterator call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            String baseURI = null;

            if (arguments.length > 0) {
                SequenceIterator iter = arguments[0];
                NodeInfo item = (NodeInfo) iter.next();
                baseURI = item.getBaseURI();
            } else {
                NodeInfo item = (NodeInfo) context.getContextItem();
                baseURI = item.getBaseURI();
            }

            if (baseURI == null) {
                return SingletonIterator.makeIterator(
                        new AnyURIValue(""));
            }

            return SingletonIterator.makeIterator(
                    new AnyURIValue(baseURI));
        }
    }
}