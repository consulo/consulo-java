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
package com.intellij.java.impl.refactoring.listeners;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.Project;
import consulo.ide.ServiceManager;

/**
 * @author yole
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class JavaRefactoringListenerManager {
  /**
   * Registers a listener for moving member by pull up, push down and extract super class/interface refactorings.
   * @param moveMembersListener listener to register
   */
  public abstract void addMoveMembersListener(MoveMemberListener moveMembersListener);

  /**
   * Unregisters a previously registered listener.
   * @param moveMembersListener listener to unregister
   */
  public abstract void removeMoveMembersListener(MoveMemberListener moveMembersListener);

  public static JavaRefactoringListenerManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaRefactoringListenerManager.class);
  }
}
