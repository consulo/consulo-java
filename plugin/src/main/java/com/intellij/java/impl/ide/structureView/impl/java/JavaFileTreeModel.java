/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.Filter;
import consulo.fileEditor.structureView.tree.Grouper;
import consulo.fileEditor.structureView.tree.NodeProvider;
import consulo.fileEditor.structureView.tree.Sorter;
import consulo.ide.impl.idea.ide.util.treeView.smartTree.TreeStructureUtil;
import consulo.language.editor.structureView.TextEditorBasedStructureViewModel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.ui.ex.PlaceHolder;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class JavaFileTreeModel extends TextEditorBasedStructureViewModel implements StructureViewModel.ElementInfoProvider, PlaceHolder<String> {
  private static final Collection<NodeProvider> NODE_PROVIDERS = Arrays.asList(new JavaInheritedMembersNodeProvider(), new JavaAnonymousClassesNodeProvider(), new JavaLambdaNodeProvider());
  private String myPlace;

  public JavaFileTreeModel(@Nonnull PsiClassOwner file, @Nullable Editor editor) {
    super(editor, file);
  }

  @Override
  @jakarta.annotation.Nonnull
  public Filter[] getFilters() {
    return new Filter[]{
        new FieldsFilter(),
        new PublicElementsFilter()
    };
  }

  @jakarta.annotation.Nonnull
  @Override
  public Collection<NodeProvider> getNodeProviders() {
    return NODE_PROVIDERS;
  }

  @Override
  @jakarta.annotation.Nonnull
  public Grouper[] getGroupers() {
    return new Grouper[]{
        new SuperTypesGrouper(),
        new PropertiesGrouper()
    };
  }

  @Override
  @jakarta.annotation.Nonnull
  public StructureViewTreeElement getRoot() {
    return new JavaFileTreeElement(getPsiFile());
  }

  @Override
  public boolean shouldEnterElement(final Object element) {
    return element instanceof PsiClass;
  }

  @Override
  @Nonnull
  public Sorter[] getSorters() {
    return new Sorter[]{
        TreeStructureUtil.isInStructureViewPopup(this) ? KindSorter.POPUP_INSTANCE : KindSorter.INSTANCE,
        VisibilitySorter.INSTANCE,
        AnonymousClassesSorter.INSTANCE,
        Sorter.ALPHA_SORTER
    };
  }

  @Override
  protected PsiClassOwner getPsiFile() {
    return (PsiClassOwner) super.getPsiFile();
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    Object value = element.getValue();
    return value instanceof PsiClass || value instanceof PsiFile;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    Object value = element.getValue();
    return value instanceof PsiMethod || value instanceof PsiField;
  }

  @Override
  protected boolean isSuitable(final PsiElement element) {
    if (super.isSuitable(element)) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        PsiClass parent = method.getContainingClass();
        return parent != null && (parent.getQualifiedName() != null || parent instanceof PsiAnonymousClass);
      }

      if (element instanceof PsiField) {
        PsiField field = (PsiField) element;
        PsiClass parent = field.getContainingClass();
        return parent != null && parent.getQualifiedName() != null;
      }

      if (element instanceof PsiClass) {
        return ((PsiClass) element).getQualifiedName() != null;
      }

      return element instanceof PsiLambdaExpression;
    }
    return false;
  }

  @Override
  @jakarta.annotation.Nonnull
  protected Class[] getSuitableClasses() {
    return new Class[]{
        PsiClass.class,
        PsiMethod.class,
        PsiField.class,
        PsiLambdaExpression.class,
        PsiJavaFile.class
    };
  }

  @Override
  public void setPlace(@jakarta.annotation.Nonnull String place) {
    myPlace = place;
  }

  @Override
  public String getPlace() {
    return myPlace;
  }
}
