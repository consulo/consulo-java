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
package com.intellij.java.impl.ide.projectView;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;

import java.util.List;

public interface PsiClassChildrenSource {
  void addChildren(PsiClass psiClass, List<PsiElement> children);

  PsiClassChildrenSource NONE = new PsiClassChildrenSource() {
    @Override
    public void addChildren(PsiClass psiClass, List<PsiElement> children) { }
  };

  PsiClassChildrenSource METHODS = new PsiClassChildrenSource() {
    @Override
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      ContainerUtil.addAll(children, psiClass.getMethods());
    }
  };

  PsiClassChildrenSource FIELDS = new PsiClassChildrenSource() {
    @Override
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      ContainerUtil.addAll(children, psiClass.getFields());
    }
  };

  PsiClassChildrenSource CLASSES = new PsiClassChildrenSource() {
    @Override
    public void addChildren(PsiClass psiClass, List<PsiElement> children) {
      ContainerUtil.addAll(children, psiClass.getInnerClasses());
    }
  };

  PsiClassChildrenSource DEFAULT_CHILDREN = new CompositePsiClasChildrenSource(new PsiClassChildrenSource[]{CLASSES, METHODS, FIELDS});
}
