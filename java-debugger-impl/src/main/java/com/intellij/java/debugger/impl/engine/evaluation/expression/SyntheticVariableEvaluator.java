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
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;

/**
 * @author lex
 */
public class SyntheticVariableEvaluator implements Evaluator{
  private static final Logger LOG = Logger.getInstance(SyntheticVariableEvaluator.class);

  private final CodeFragmentEvaluator myCodeFragmentEvaluator;
  private final String myLocalName;

  public SyntheticVariableEvaluator(CodeFragmentEvaluator codeFragmentEvaluator, String localName) {
    myCodeFragmentEvaluator = codeFragmentEvaluator;
    myLocalName = localName;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return myCodeFragmentEvaluator.getValue(myLocalName, context.getDebugProcess().getVirtualMachineProxy());
  }

  public Modifier getModifier() {
    return new Modifier() {
      public boolean canInspect() {
        return false;
      }

      public boolean canSetValue() {
        return false;
      }

      public void setValue(Value value) throws EvaluateException {
        myCodeFragmentEvaluator.setValue(myLocalName, value);
      }

      public Type getExpectedType() {
        LOG.assertTrue(false);
        return null;
      }

      public NodeDescriptorImpl getInspectItem(Project project) {
        return null;
      }
    };
  }
}
