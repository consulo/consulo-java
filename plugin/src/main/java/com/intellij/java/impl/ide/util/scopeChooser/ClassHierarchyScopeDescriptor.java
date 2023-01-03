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

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.java.impl.ide.util.scopeChooser;

import consulo.ide.IdeBundle;
import consulo.content.scope.ScopeDescriptor;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.editor.util.PsiUtilBase;
import consulo.java.impl.JavaBundle;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

public class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
  private SearchScope myCachedScope;
  private final Project myProject;

  public ClassHierarchyScopeDescriptor(final Project project) {
    super(null);
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return JavaBundle.message("java.scope.class.hierarchy");
  }

  @Nullable
  public SearchScope getScope() {
    if (myCachedScope == null) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(IdeBundle.message("prompt.choose.base.class.of.the.hierarchy"));

      chooser.showDialog();

      PsiClass aClass = chooser.getSelected();
      if (aClass == null) return null;

      List<PsiElement> classesToSearch = new LinkedList<>();
      classesToSearch.add(aClass);

      classesToSearch.addAll(ClassInheritorsSearch.search(aClass, true).findAll());

      myCachedScope = new LocalSearchScope(PsiUtilBase.toPsiElementArray(classesToSearch),
                                           IdeBundle.message("scope.hierarchy", ClassPresentationUtil.getNameForClass(aClass, true)));
    }

    return myCachedScope;
  }
}