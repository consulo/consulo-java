/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.spi.psi;

import java.util.ArrayList;
import java.util.List;

import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.util.collection.ArrayUtil;
import consulo.application.util.function.Processor;
import jakarta.annotation.Nonnull;

/**
 * User: anna
 */
public class SPIClassProviderReferenceElement extends SPIPackageOrClassReferenceElement {
  public SPIClassProviderReferenceElement(ASTNode node) {
    super(node);
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    String name = getContainingFile().getName();
    PsiClass superProvider = JavaPsiFacade.getInstance(getProject()).findClass(name, getResolveScope());
    if (superProvider != null) {
      final List<Object> result = new ArrayList<Object>();
      ClassInheritorsSearch.search(superProvider).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass psiClass) {
          String jvmClassName = ClassUtil.getJVMClassName(psiClass);
          if (jvmClassName != null) {
            result.add(LookupElementBuilder.create(psiClass, jvmClassName));
          }
          return false;
        }
      });
      return ArrayUtil.toObjectArray(result);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
