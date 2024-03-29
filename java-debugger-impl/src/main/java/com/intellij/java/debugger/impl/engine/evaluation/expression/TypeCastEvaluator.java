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
 * Class TypeCastEvaluator
 * @author Jeka
 */
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.internal.com.sun.jdi.BooleanValue;
import consulo.internal.com.sun.jdi.CharValue;
import consulo.internal.com.sun.jdi.PrimitiveValue;
import consulo.internal.com.sun.jdi.Value;

public class TypeCastEvaluator implements Evaluator {
  private final Evaluator myOperandEvaluator;
  private final String myCastType;
  private final boolean myIsPrimitive;

  public TypeCastEvaluator(Evaluator operandEvaluator, String castType, boolean isPrimitive) {
    myOperandEvaluator = operandEvaluator;
    myCastType = castType;
    myIsPrimitive = isPrimitive;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      if (myIsPrimitive) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.null", myCastType));
      }
      return null;
    }
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (DebuggerUtilsEx.isInteger(value)) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((PrimitiveValue)value).longValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.numeric", myCastType));
      }
    }
    else if (DebuggerUtilsEx.isNumeric(value)) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((PrimitiveValue)value).doubleValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.numeric", myCastType));
      }
    }
    else if (value instanceof BooleanValue) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((BooleanValue)value).booleanValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.boolean", myCastType));
      }
    }
    else if (value instanceof CharValue) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((CharValue)value).charValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.char", myCastType));
      }
    }
    return value;
  }
}
