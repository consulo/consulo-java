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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.application.progress.ProgressManager;
import consulo.application.util.NotNullLazyValue;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.xml.psi.xml.XmlTag;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ik
 * @since 20:27:59 05.06.2003
 */
public class JavaClassListReferenceProvider extends JavaClassReferenceProvider {

  public JavaClassListReferenceProvider() {
    setOption(ADVANCED_RESOLVE, Boolean.TRUE);
  }

  @Override
  @Nonnull
  public PsiReference[] getReferencesByString(String str, @Nonnull final PsiElement position, int offsetInPosition){
    if (position instanceof XmlTag && ((XmlTag)position).getValue().getTextElements().length == 0) {
      return PsiReference.EMPTY_ARRAY; 
    }

    if (str.length() < 2) {
      return PsiReference.EMPTY_ARRAY;
    }

    int offset = position.getTextRange().getStartOffset() + offsetInPosition;
    for(PsiElement child = position.getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof OuterLanguageElement && child.getTextRange().contains(offset)) {
        return PsiReference.EMPTY_ARRAY;
      }
    }

    NotNullLazyValue<Set<String>> topLevelPackages = new NotNullLazyValue<Set<String>>() {
      @Nonnull
      @Override
      protected Set<String> compute() {
        final Set<String> knownTopLevelPackages = new HashSet<String>();
        final List<PsiElement> defaultPackages = getDefaultPackages(position.getProject());
        for (final PsiElement pack : defaultPackages) {
          if (pack instanceof PsiJavaPackage) {
            knownTopLevelPackages.add(((PsiJavaPackage)pack).getName());
          }
        }
        return knownTopLevelPackages;
      }
    };
    final List<PsiReference> results = new ArrayList<PsiReference>();

    for(int dot = str.indexOf('.'); dot > 0; dot = str.indexOf('.', dot + 1)) {
      int start = dot;
      while (start > 0 && Character.isLetterOrDigit(str.charAt(start - 1))) start--;
      if (dot == start) {
        continue;
      }
      String candidate = str.substring(start, dot);
      if (topLevelPackages.getValue().contains(candidate)) {
        int end = dot;
        while (end < str.length() - 1) {
          end++;
          char ch = str.charAt(end);
          if (ch != '.' && !Character.isJavaIdentifierPart(ch)) {
            break;
          }
        }
        String s = str.substring(start, end + 1);
        ContainerUtil.addAll(results, new JavaClassReferenceSet(s, position, offsetInPosition + start, false, this) {
          @Override
          public boolean isSoft() {
            return true;
          }
        }.getAllReferences());
        ProgressManager.checkCanceled();
      }
    }
    return results.toArray(new PsiReference[results.size()]);
  }

  @Override
  public GlobalSearchScope getScope(Project project) {
    return GlobalSearchScope.allScope(project);
  }
}
