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
package com.intellij.java.debugger;

import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessListener;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.process.ProcessHandler;
import consulo.project.Project;

import java.util.function.Function;

/**
 * @author lex
 */
@ServiceAPI(value = ComponentScope.PROJECT)
public abstract class DebuggerManager {
  public static DebuggerManager getInstance(Project project) {
    return project.getInstance(DebuggerManager.class);
  }

  public abstract DebugProcess getDebugProcess(ProcessHandler processHandler);

  public abstract void addDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract void removeDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract boolean isDebuggerManagerThread();

  public abstract void addClassNameMapper(NameMapper mapper);

  public abstract void removeClassNameMapper(NameMapper mapper);

  public abstract String getVMClassQualifiedName(PsiClass aClass);

  /**
   * @deprecated use {@link PositionManagerFactory} extension point instead
   */
  @Deprecated
  public abstract void registerPositionManagerFactory(Function<DebugProcess, PositionManager> factory);

  @Deprecated
  public abstract void unregisterPositionManagerFactory(Function<DebugProcess, PositionManager> factory);
}
