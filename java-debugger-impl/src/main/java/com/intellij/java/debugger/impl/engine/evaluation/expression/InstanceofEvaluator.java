/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.logging.Logger;
import com.intellij.java.language.psi.PsiType;
import consulo.internal.com.sun.jdi.*;

import java.util.LinkedList;
import java.util.List;

class InstanceofEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(InstanceofEvaluator.class);
  private final Evaluator myOperandEvaluator;
  private final TypeEvaluator myTypeEvaluator;

  public InstanceofEvaluator(Evaluator operandEvaluator, TypeEvaluator typeEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return DebuggerUtilsEx.createValue(context.getDebugProcess().getVirtualMachineProxy(), PsiType.BOOLEAN.getPresentableText(), false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      ReferenceType refType = (ReferenceType)myTypeEvaluator.evaluate(context);
      ClassObjectReference classObject = refType.classObject();
      ClassType classRefType = (ClassType)classObject.referenceType();
      //noinspection HardCodedStringLiteral
      Method method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z");
      List args = new LinkedList();
      args.add(((ObjectReference)value).referenceType().classObject());
      return context.getDebugProcess().invokeMethod(context, classObject, method, args);
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }
}
