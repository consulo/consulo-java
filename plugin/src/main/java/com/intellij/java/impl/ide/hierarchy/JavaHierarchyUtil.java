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
package com.intellij.java.impl.ide.hierarchy;

import consulo.ide.impl.idea.ide.hierarchy.HierarchyBrowserManager;
import consulo.ui.ex.tree.AlphaComparator;
import consulo.ui.ex.tree.NodeDescriptor;
import com.intellij.java.impl.ide.util.treeView.SourceComparator;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import consulo.project.Project;
import consulo.language.psi.PsiFile;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * @author yole
 */
public class JavaHierarchyUtil {
  private JavaHierarchyUtil() {
  }

  @Nullable
  public static String getPackageName(final PsiClass psiClass) {
    final PsiFile file = psiClass.getContainingFile();
    if (file instanceof PsiClassOwner) {
      return ((PsiClassOwner) file).getPackageName();
    } else {
      return null;
    }
  }

  public static Comparator<NodeDescriptor> getComparator(Project project) {
    if (HierarchyBrowserManager.getInstance(project).getState().SORT_ALPHABETICALLY) {
      return AlphaComparator.INSTANCE;
    } else {
      return SourceComparator.INSTANCE;
    }
  }
}
