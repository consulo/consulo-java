/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiExpression;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;
import jakarta.annotation.Nonnull;

/**
 * User: lex
 * Date: Oct 8, 2003
 * Time: 5:08:07 PM
 */
public class ThrownExceptionValueDescriptorImpl extends ValueDescriptorImpl{
  @Nonnull
  private final ObjectReference myExceptionObj;

  public ThrownExceptionValueDescriptorImpl(Project project, @Nonnull ObjectReference exceptionObj) {
    super(project);
    myExceptionObj = exceptionObj;
    // deliberately force default renderer as it does not invoke methods for rendering
    // calling methods on exception object at this moment may lead to VM hang
    setRenderer(DebugProcessImpl.getDefaultRenderer(exceptionObj));
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myExceptionObj;
  }

  public String getName() {
    return "Exception";
  }

  @Nonnull
  @Override
  public Type getType() {
    return myExceptionObj.referenceType();
  }

  public String calcValueName() {
    return getName();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    throw new EvaluateException("Evaluation not supported for thrown exception object");
  }

  public boolean canSetValue() {
    return false;
  }
}
