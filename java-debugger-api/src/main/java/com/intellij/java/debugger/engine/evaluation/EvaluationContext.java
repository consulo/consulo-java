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
package com.intellij.java.debugger.engine.evaluation;

import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.SuspendContext;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.ClassLoaderReference;
import consulo.internal.com.sun.jdi.Value;

public interface EvaluationContext extends StackFrameContext{
  DebugProcess getDebugProcess();

  EvaluationContext createEvaluationContext(Value value);

  SuspendContext getSuspendContext();

  Project getProject();

  ClassLoaderReference getClassLoader() throws EvaluateException;

  Value getThisObject();
}
