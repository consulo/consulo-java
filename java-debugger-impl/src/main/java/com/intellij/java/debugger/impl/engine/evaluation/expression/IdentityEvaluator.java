/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.internal.com.sun.jdi.Value;

/**
* @author Eugene Zhuravlev
*         Date: Feb 9, 2010
*/
public class IdentityEvaluator implements Evaluator {
  private final Value myValue;

  public IdentityEvaluator(Value value) {
    myValue = value;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return myValue;
  }

  public Modifier getModifier() {
    return null;
  }
}
