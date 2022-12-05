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

/*
 * User: anna
  * Date: 28-Feb-2007
  */
package com.intellij.java.analysis.codeInspection.ex;

import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.Button;

import javax.annotation.Nonnull;

public abstract class EntryPointsManager implements Disposable {
  public static EntryPointsManager getInstance(Project project) {
    return ServiceManager.getService(project, EntryPointsManager.class);
  }

  public abstract void resolveEntryPoints(@Nonnull RefManager manager);

  public abstract void addEntryPoint(@Nonnull RefElement newEntryPoint, boolean isPersistent);

  public abstract void removeEntryPoint(@Nonnull RefElement anEntryPoint);

  @Nonnull
  public abstract RefElement[] getEntryPoints();

  public abstract void cleanup();

  public abstract boolean isAddNonJavaEntries();

  public abstract void configureAnnotations();

  public abstract Button createConfigureAnnotationsBtn();

  public abstract boolean isEntryPoint(@Nonnull PsiElement element);
}
