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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.SuspendContext;
import com.intellij.java.debugger.engine.managerThread.SuspendContextCommand;
import consulo.internal.com.sun.jdi.Value;

/**
 * User: lex
 * Date: Sep 16, 2003
 * Time: 10:58:26 AM
 */
public abstract class ToStringCommand implements SuspendContextCommand {
  private final EvaluationContext myEvaluationContext;
  private final Value myValue;

  private boolean myIsEvaluated = false;

  protected ToStringCommand(EvaluationContext evaluationContext, Value value) {
    myEvaluationContext = evaluationContext;
    myValue = value;
  }

  public void action() {
    if(myIsEvaluated) return;
    try {
      final String valueAsString = DebuggerUtils.getValueAsString(myEvaluationContext, myValue);
      evaluationResult(valueAsString);
    } 
    catch(final EvaluateException ex) {
      evaluationError(ex.getMessage());
    }
  }

  public void commandCancelled() {
  }

  public void setEvaluated() {
    myIsEvaluated = true;
  }

  public SuspendContext getSuspendContext() {
    return myEvaluationContext.getSuspendContext();
  }

  public abstract void evaluationResult(String message);
  public abstract void evaluationError (String message);

  public Value getValue() {
    return myValue;
  }

  public EvaluationContext getEvaluationContext() {
    return myEvaluationContext;
  }
}

