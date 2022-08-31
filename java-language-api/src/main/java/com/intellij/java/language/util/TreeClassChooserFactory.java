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

import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public abstract class TreeClassChooserFactory {
  @Nonnull
  public static TreeClassChooserFactory getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, TreeClassChooserFactory.class);
  }

  @Nonnull
  public abstract TreeClassChooser createWithInnerClassesScopeChooser(String title, GlobalSearchScope scope, final ClassFilter classFilter, @Nullable PsiClass initialClass);


  @Nonnull
  public abstract TreeClassChooser createNoInnerClassesScopeChooser(String title, GlobalSearchScope scope, ClassFilter classFilter, @Nullable PsiClass initialClass);


  @Nonnull
  public abstract TreeClassChooser createProjectScopeChooser(String title, @Nullable PsiClass initialClass);


  @Nonnull
  public abstract TreeClassChooser createProjectScopeChooser(String title);


  @Nonnull
  public abstract TreeClassChooser createAllProjectScopeChooser(String title);


  @Nonnull
  public abstract TreeClassChooser createInheritanceClassChooser(String title,
                                                                 GlobalSearchScope scope,
                                                                 PsiClass base,
                                                                 boolean acceptsSelf,
                                                                 boolean acceptInner,
                                                                 Condition<? super PsiClass> additionalCondition);

  @Nonnull
  public abstract TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass);

  @Nonnull
  public abstract TreeClassChooser createInheritanceClassChooser(String title, GlobalSearchScope scope, PsiClass base, PsiClass initialClass, ClassFilter classFilter);


  @Nonnull
  public abstract TreeFileChooser createFileChooser(@Nonnull String title, @javax.annotation.Nullable PsiFile initialFile, @javax.annotation.Nullable FileType fileType, @javax.annotation.Nullable TreeFileChooser.PsiFileFilter filter);


  @Nonnull
  public abstract TreeFileChooser createFileChooser(@Nonnull String title,
                                                    @javax.annotation.Nullable PsiFile initialFile,
                                                    @javax.annotation.Nullable FileType fileType,
                                                    @javax.annotation.Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders);


  @Nonnull
  public abstract TreeFileChooser createFileChooser(@Nonnull String title,
                                                    @javax.annotation.Nullable PsiFile initialFile,
                                                    @javax.annotation.Nullable FileType fileType,
                                                    @javax.annotation.Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents);
}
