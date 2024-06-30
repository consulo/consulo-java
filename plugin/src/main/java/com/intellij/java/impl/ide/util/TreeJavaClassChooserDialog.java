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
package com.intellij.java.impl.ide.util;

import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import consulo.application.Application;
import consulo.application.util.function.Computable;
import consulo.application.util.query.Query;
import consulo.ide.impl.idea.ide.util.AbstractTreeClassChooserDialog;
import consulo.language.editor.ui.TreeClassInheritorsProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Conditions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author traff
 */
public class TreeJavaClassChooserDialog extends AbstractTreeClassChooserDialog<PsiClass> implements TreeClassChooser {
  public TreeJavaClassChooserDialog(String title, Project project) {
    super(title, project, PsiClass.class);
  }

  public TreeJavaClassChooserDialog(String title, Project project, @Nullable PsiClass initialClass) {
    super(title, project, PsiClass.class, initialClass);
  }

  public TreeJavaClassChooserDialog(
    String title,
    @Nonnull Project project,
    GlobalSearchScope scope,
    final ClassFilter classFilter,
    @Nullable PsiClass initialClass
  ) {
    super(title, project, scope, PsiClass.class, createFilter(classFilter), initialClass);
  }

  public TreeJavaClassChooserDialog(
    String title,
    @Nonnull Project project,
    GlobalSearchScope scope,
    @Nullable ClassFilter classFilter,
    PsiClass baseClass,
    @Nullable PsiClass initialClass,
    boolean isShowMembers
  ) {
    super(title, project, scope, PsiClass.class, createFilter(classFilter), baseClass, initialClass, isShowMembers, true);
  }

  @Override
  @Nullable
  protected PsiClass getSelectedFromTreeUserObject(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ClassTreeNode)) return null;
    ClassTreeNode descriptor = (ClassTreeNode) userObject;
    return descriptor.getPsiClass();
  }

  public static TreeJavaClassChooserDialog withInnerClasses(
    String title,
    @Nonnull Project project,
    GlobalSearchScope scope,
    final ClassFilter classFilter,
    @Nullable PsiClass initialClass
  ) {
    return new TreeJavaClassChooserDialog(title, project, scope, classFilter, null, initialClass, true);
  }

  @Nullable
  private static Predicate<PsiClass> createFilter(@Nullable final ClassFilter classFilter) {
    if (classFilter == null) {
      return null;
    } else {
      return element -> Application.get().runReadAction((Computable<Boolean>)() -> classFilter.isAccepted(element));
    }
  }

  @Nonnull
  protected List<PsiClass> getClassesByName(
    final String name,
    final boolean checkBoxState,
    final String pattern,
    final ProjectAwareSearchScope searchScope
  ) {
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(getProject());
    PsiClass[] classes = cache.getClassesByName(
      name,
      checkBoxState ? (GlobalSearchScope) searchScope
        : (GlobalSearchScope) GlobalSearchScope.projectScope(getProject()).intersectWith(searchScope)
    );
    return ContainerUtil.newArrayList(classes);
  }

  @Nonnull
  @Override
  protected TreeClassInheritorsProvider<PsiClass> getInheritorsProvider(@Nonnull PsiClass baseClass) {
    return new JavaInheritorsProvider(getProject(), baseClass, (GlobalSearchScope) getScope());
  }

  @Override
  @RequiredUIAccess
  public void selectDirectory(PsiDirectory directory) {
    selectElementInTree(directory);
  }

  private static class JavaInheritorsProvider extends TreeClassInheritorsProvider<PsiClass> {
    private final Project myProject;

    public JavaInheritorsProvider(Project project, PsiClass baseClass, GlobalSearchScope scope) {
      super(baseClass, scope);
      myProject = project;
    }

    @Nonnull
    @Override
    public Query<PsiClass> searchForInheritors(PsiClass baseClass, ProjectAwareSearchScope searchScope, boolean checkDeep) {
      return ClassInheritorsSearch.search(baseClass, searchScope, checkDeep);
    }

    @Override
    public boolean isInheritor(PsiClass clazz, PsiClass baseClass, boolean checkDeep) {
      return clazz.isInheritor(baseClass, checkDeep);
    }

    @Override
    public String[] getNames() {
      return PsiShortNamesCache.getInstance(myProject).getAllClassNames();
    }
  }

  public static class InheritanceJavaClassFilterImpl implements ClassFilter {
    private final PsiClass myBase;
    private final boolean myAcceptsSelf;
    private final boolean myAcceptsInner;
    private final Predicate<? super PsiClass> myAdditionalCondition;

    public InheritanceJavaClassFilterImpl(
      PsiClass base,
      boolean acceptsSelf,
      boolean acceptInner,
      Predicate<? super PsiClass> additionalCondition
    ) {
      myAcceptsSelf = acceptsSelf;
      myAcceptsInner = acceptInner;
      if (additionalCondition == null) {
        additionalCondition = Conditions.alwaysTrue();
      }
      myAdditionalCondition = additionalCondition;
      myBase = base;
    }

    public boolean isAccepted(PsiClass aClass) {
      if (!myAcceptsInner && !(aClass.getParent() instanceof PsiJavaFile)) return false;
      if (!myAdditionalCondition.test(aClass)) return false;
      // we've already checked for inheritance
      return myAcceptsSelf || !aClass.getManager().areElementsEquivalent(aClass, myBase);
    }
  }
}
