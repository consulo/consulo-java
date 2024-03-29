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
package com.intellij.java.impl.refactoring.changeSignature.inCallers;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.PsiMethod;
import consulo.ide.impl.idea.refactoring.changeSignature.CallerChooserBase;
import consulo.project.Project;
import consulo.ui.ex.awt.tree.Tree;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class JavaCallerChooser extends CallerChooserBase<PsiMethod> {

  public JavaCallerChooser(PsiMethod method, Project project, String title, Tree previousTree, Consumer<Set<PsiMethod>> callback) {
    super(method, project, title, previousTree, "dummy." + JavaFileType.INSTANCE.getDefaultExtension(), callback);
  }

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod method, HashSet<PsiMethod> called, Runnable cancelCallback) {
    return new JavaMethodNode(method, called, myProject, cancelCallback);
  }

  @Override
  protected PsiMethod[] findDeepestSuperMethods(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }
}
