/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.filter.FilterPositionUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ik
 * @since 17.02.2003
 */
public class ModifierChooser {
  private static final String[][] CLASS_MODIFIERS = {
    {PsiKeyword.PUBLIC},
    {
      PsiKeyword.FINAL,
      PsiKeyword.ABSTRACT
    }
  };
  private static final String[][] CLASS_MEMBER_MODIFIERS = {
    {
      PsiKeyword.PUBLIC,
      PsiKeyword.PROTECTED,
      PsiKeyword.PRIVATE
    },
    {PsiKeyword.STATIC},
    {
      PsiKeyword.FINAL,
      PsiKeyword.ABSTRACT
    },
    {PsiKeyword.NATIVE},
    {PsiKeyword.SYNCHRONIZED},
    {PsiKeyword.STRICTFP},
    {PsiKeyword.VOLATILE},
    {PsiKeyword.TRANSIENT}
  };
  private static final String[][] INTERFACE_MEMBER_MODIFIERS = {
    {
      PsiKeyword.PUBLIC,
      PsiKeyword.PROTECTED
    },
    {PsiKeyword.STATIC},
    {
      PsiKeyword.FINAL,
      PsiKeyword.ABSTRACT
    }
  };

  static String[] getKeywords(@Nonnull PsiElement position) {
    final PsiModifierList list = findModifierList(position);
    if (list == null && !shouldSuggestModifiers(position)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    PsiElement scope = position.getParent();
    while (scope != null) {
      if (scope instanceof PsiJavaFile) {
        return addClassModifiers(list);
      }
      if (scope instanceof PsiClass) {
        return addMemberModifiers(list, ((PsiClass)scope).isInterface());
      }

      scope = scope.getParent();
      if (scope instanceof PsiDirectory) {
        break;
      }
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public static String[] addClassModifiers(PsiModifierList list) {
    return addKeywords(list, CLASS_MODIFIERS);
  }

  public static String[] addMemberModifiers(PsiModifierList list, final boolean inInterface) {
    return addKeywords(list, inInterface ? INTERFACE_MEMBER_MODIFIERS : CLASS_MEMBER_MODIFIERS);
  }

  private static String[] addKeywords(PsiModifierList list, String[][] keywordSets) {
    final List<String> ret = new ArrayList<String>();
    for (int i = 0; i < keywordSets.length; i++) {
      final String[] keywords = keywordSets[keywordSets.length - i - 1];
      boolean containModifierFlag = false;
      if (list != null) {
        for (@PsiModifier.ModifierConstant String keyword : keywords) {
          if (list.hasExplicitModifier(keyword)) {
            containModifierFlag = true;
            break;
          }
        }
      }
      if (!containModifierFlag) {
        ContainerUtil.addAll(ret, keywords);
      }
    }
    return ArrayUtil.toStringArray(ret);
  }

  @Nullable
  public static PsiModifierList findModifierList(@Nonnull PsiElement element) {
    if (element.getParent() instanceof PsiModifierList) {
      return (PsiModifierList)element.getParent();
    }

    return PsiTreeUtil.getParentOfType(FilterPositionUtil.searchNonSpaceNonCommentBack(element), PsiModifierList.class);
  }

  private static boolean shouldSuggestModifiers(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement ||
      parent instanceof PsiErrorElement || parent instanceof PsiTypeElement ||
      parent instanceof PsiMethod || parent instanceof PsiVariable ||
      parent instanceof PsiDeclarationStatement || parent instanceof PsiImportList ||
      parent instanceof PsiDocComment) {
      parent = parent.getParent();

//      if (parent instanceof JspClassLevelDeclarationStatement) {
//        parent = parent.getContext();
//      }
    }

    if (parent == null) return false;

    return (parent instanceof PsiJavaFile || parent instanceof PsiClass) &&
      JavaKeywordCompletion.isEndOfBlock(element);
  }
}
