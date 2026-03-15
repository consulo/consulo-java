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
package com.intellij.java.language.util;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.editor.ui.TreeFileChooser;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;

import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class TreeClassChooserFactory {
  public static TreeClassChooserFactory getInstance(Project project) {
    return ServiceManager.getService(project, TreeClassChooserFactory.class);
  }

  public abstract TreeClassChooser createWithInnerClassesScopeChooser(String title, GlobalSearchScope scope, final ClassFilter classFilter, @Nullable PsiClass initialClass);


  public abstract TreeClassChooser createNoInnerClassesScopeChooser(String title, GlobalSearchScope scope, ClassFilter classFilter, @Nullable PsiClass initialClass);


  public abstract TreeClassChooser createProjectScopeChooser(String title, @Nullable PsiClass initialClass);


  public abstract TreeClassChooser createProjectScopeChooser(String title);


  public abstract TreeClassChooser createAllProjectScopeChooser(String title);


  public abstract TreeClassChooser createInheritanceClassChooser(String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 boolean acceptsSelf,
                                                                 boolean acceptInner,
                                                                 Predicate<? super PsiClass> additionalCondition);

  public abstract TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass);

  public abstract TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass, ClassFilter classFilter);


  public abstract TreeFileChooser createFileChooser(String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable Predicate<PsiFile> filter);


  public abstract TreeFileChooser createFileChooser(String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable Predicate<PsiFile> filter,
                                                    boolean disableStructureProviders);


  public abstract TreeFileChooser createFileChooser(String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable Predicate<PsiFile> filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents);
}
