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
package com.intellij.java.impl.ide.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.ClassFilter;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.ide.util.TreeFileChooserDialog;
import consulo.language.editor.ui.TreeFileChooser;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;
import java.util.function.Predicate;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
@Singleton
@ServiceImpl
public class TreeClassChooserFactoryImpl extends TreeClassChooserFactory {
  private final Project myProject;

  @Inject
  public TreeClassChooserFactoryImpl(@jakarta.annotation.Nonnull Project project) {
    myProject = project;
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createWithInnerClassesScopeChooser(String title,
                                                             GlobalSearchScope scope,
                                                             final ClassFilter classFilter,
                                                             PsiClass initialClass) {
    return TreeJavaClassChooserDialog.withInnerClasses(title, myProject, scope, classFilter, initialClass);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createNoInnerClassesScopeChooser(String title,
                                                           GlobalSearchScope scope,
                                                           ClassFilter classFilter,
                                                           PsiClass initialClass) {
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, initialClass);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass) {
    return new TreeJavaClassChooserDialog(title, myProject, initialClass);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createProjectScopeChooser(String title) {
    return new TreeJavaClassChooserDialog(title, myProject);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createAllProjectScopeChooser(String title) {
    return new TreeJavaClassChooserDialog(title, myProject, GlobalSearchScope.allScope(myProject), null, null);
  }

  @Override
  @Nonnull
  public TreeClassChooser createInheritanceClassChooser(String title,
                                                        GlobalSearchScope scope,
                                                        PsiClass base,
                                                        boolean acceptsSelf,
                                                        boolean acceptInner,
                                                        Predicate<? super PsiClass> additionalCondition) {
    ClassFilter classFilter = new TreeJavaClassChooserDialog.InheritanceJavaClassFilterImpl(base, acceptsSelf, acceptInner, additionalCondition);
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, base, null, false);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass) {
    return createInheritanceClassChooser(title, scope, base, initialClass, null);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass,
                                                        ClassFilter classFilter) {
    return new TreeJavaClassChooserDialog(title, myProject, scope, classFilter, base, initialClass, false);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeFileChooser createFileChooser(@jakarta.annotation.Nonnull String title,
                                           final PsiFile initialFile,
                                           FileType fileType,
                                           Predicate<PsiFile> filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, false, false);
  }

  @Override
  @jakarta.annotation.Nonnull
  public TreeFileChooser createFileChooser(@jakarta.annotation.Nonnull String title,
                                           @Nullable PsiFile initialFile,
                                           @jakarta.annotation.Nullable FileType fileType,
                                           @Nullable Predicate<PsiFile> filter,
                                           boolean disableStructureProviders) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, false);
  }


  @Override
  @jakarta.annotation.Nonnull
  public TreeFileChooser createFileChooser(@jakarta.annotation.Nonnull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType,
                                           @Nullable Predicate<PsiFile> filter,
                                           boolean disableStructureProviders,
                                           boolean showLibraryContents) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, showLibraryContents);
  }
}
