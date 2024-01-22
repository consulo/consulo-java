/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.impl.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.java.language.psi.*;
import consulo.fileEditor.structureView.tree.InheritedMembersNodeProvider;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaInheritedMembersNodeProvider extends InheritedMembersNodeProvider {
  @Nonnull
  @Override
  public Collection<TreeElement> provideNodes(@jakarta.annotation.Nonnull TreeElement node) {
    if (!(node instanceof JavaClassTreeElement)) {
      return Collections.emptyList();
    }

    JavaClassTreeElement classNode = (JavaClassTreeElement) node;
    final PsiClass aClass = classNode.getElement();
    if (aClass == null) {
      return Collections.emptyList();
    }

    Collection<PsiElement> inherited = new LinkedHashSet<>();
    Collection<PsiElement> ownChildren = JavaClassTreeElement.getOwnChildren(aClass);

    aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);
    inherited.removeAll(ownChildren);
    if (aClass instanceof PsiAnonymousClass) {
      final PsiElement element = ((PsiAnonymousClass) aClass).getBaseClassReference().resolve();
      if (element instanceof PsiClass) {
        ContainerUtil.addAll(inherited, ((PsiClass) element).getInnerClasses());
      }
    }
    List<TreeElement> array = new ArrayList<>();
    for (PsiElement child : inherited) {
      if (!child.isValid()) {
        continue;
      }
      if (child instanceof PsiClass) {
        array.add(new JavaClassTreeElement((PsiClass) child, true));
      } else if (child instanceof PsiField) {
        array.add(new PsiFieldTreeElement((PsiField) child, true));
      } else if (child instanceof PsiMethod) {
        array.add(new PsiMethodTreeElement((PsiMethod) child, true));
      } else if (child instanceof PsiClassInitializer) {
        array.add(new ClassInitializerTreeElement((PsiClassInitializer) child));
      }
    }
    return array;
  }
}
