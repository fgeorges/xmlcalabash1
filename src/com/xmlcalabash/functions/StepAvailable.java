package com.xmlcalabash.functions;

import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.ExtensionFunctionDefinition;
import net.sf.saxon.functions.ExtensionFunctionCall;
import net.sf.saxon.expr.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.ItemTypeFactory;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.runtime.XStep;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.model.DeclareStep;

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
 * Implementation of the XProc p:step-available function
 */

public class StepAvailable extends ExtensionFunctionDefinition {
    private XProcRuntime runtime;
    private static StructuredQName funcname = new StructuredQName("p", XProcConstants.NS_XPROC, "step-available");

    protected StepAvailable() {
        // you can't call this one
    }

    public StepAvailable(XProcRuntime runtime) {
        this.runtime = runtime;
    }

    public StructuredQName getFunctionQName() {
        return funcname;
    }

    public int getMinimumNumberOfArguments() {
        return 1;
    }

    public int getMaximumNumberOfArguments() {
        return 1;
    }

    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_STRING};
    }

    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_ATOMIC;
    }

    public ExtensionFunctionCall makeCallExpression() {
        return new StepAvailableCall();
    }

    private class StepAvailableCall extends ExtensionFunctionCall {
        private StaticContext staticContext = null;

        public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
            staticContext = context;
        }

        public SequenceIterator call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            StructuredQName stepName = null;

            try {
                SequenceIterator iter = arguments[0];
                String lexicalQName = iter.next().getStringValue();
                stepName = StructuredQName.fromLexicalQName(
                     lexicalQName,
                     false,
                     context.getConfiguration().getNameChecker(),
                     staticContext.getNamespaceResolver());
            } catch (XPathException e) {
                // FIXME: bad formatting
                throw new XProcException("Invalid step name. " + e.getMessage() + "XTDE1390");
            }

            boolean value = false;
            QName stepType = new QName("x", stepName.getNamespaceURI(), stepName.getLocalName());

            // FIXME: This doesn't seem terribly efficient...

            XStep step = runtime.getXProcData().getStep();

            while (! (step instanceof XPipeline)) {
                step = step.getParent();
            }

            DeclareStep decl = step.getDeclareStep();

            try {
                decl = decl.getStepDeclaration(stepType);
            } catch (XProcException e) {
                decl = null;
            }

            if (decl != null) {
                if (decl.isAtomic()) {
                    String className = runtime.getConfiguration().implementationClass(decl.getDeclaredType());
                    value = (className != null);
                } else {
                    value = true;
                }
            }

            return SingletonIterator.makeIterator(value ? BooleanValue.TRUE : BooleanValue.FALSE);
        }
    }
}