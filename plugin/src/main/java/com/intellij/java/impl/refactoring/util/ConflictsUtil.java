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

/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.FileContextUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.application.util.function.Processor;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ConflictsUtil {
  private ConflictsUtil() {
  }

  @Nonnull
  public static PsiElement getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter)) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        PsiElement host = FileContextUtil.getFileContext((PsiFile)parent);
        if (host == null) {
          return parent;
        }
        parent = host;
      }
      parent = parent.getParent();
    }
  }

  public static void checkMethodConflicts(@Nullable PsiClass aClass,
                                          PsiMethod refactoredMethod,
                                          final PsiMethod prototype,
                                          final MultiMap<PsiElement,String> conflicts) {
    if (prototype == null) return;
    final String protoMethodInfo = getMethodPrototypeString(prototype);

    PsiMethod method = aClass != null ? aClass.findMethodBySignature(prototype, true) : null;

    if (method != null && method != refactoredMethod) {
      if (aClass.equals(method.getContainingClass())) {
        final String classDescr = aClass instanceof PsiAnonymousClass ?
                                  RefactoringBundle.message("current.class") :
                                  RefactoringUIUtil.getDescription(aClass, false);
        conflicts.putValue(method, RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                getMethodPrototypeString(prototype),
                                                classDescr));
      }
      else { // method somewhere in base class
        if (JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, aClass, null)) {
          String className = CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(method.getContainingClass()));
          if (PsiUtil.getAccessLevel(prototype.getModifierList()) >= PsiUtil.getAccessLevel(method.getModifierList()) ) {
            boolean isMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
            boolean isMyMethodAbstract = refactoredMethod != null && refactoredMethod.hasModifierProperty(PsiModifier.ABSTRACT);
            final String conflict = isMethodAbstract != isMyMethodAbstract ?
                                    RefactoringBundle.message("method.0.will.implement.method.of.the.base.class", protoMethodInfo, className) :
                                    RefactoringBundle.message("method.0.will.override.a.method.of.the.base.class", protoMethodInfo, className);
            conflicts.putValue(method, conflict);
          }
          else { // prototype is private, will be compile-error
            conflicts.putValue(method, RefactoringBundle.message("method.0.will.hide.method.of.the.base.class",
                                                    protoMethodInfo, className));
          }
        }
      }
    }
    if (aClass != null && prototype.hasModifierProperty(PsiModifier.PRIVATE)) {
      ClassInheritorsSearch.search(aClass).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass aClass) {
          final PsiMethod[] methods = aClass.findMethodsBySignature(prototype, false);
          for (PsiMethod method : methods) {
            conflicts.putValue(method, "Method " + RefactoringUIUtil.getDescription(method, true) + " will override method of the base class " + RefactoringUIUtil.getDescription(aClass, false));
          }
          return true;
        }
      });
    }
  }

  private static String getMethodPrototypeString(final PsiMethod prototype) {
    return PsiFormatUtil.formatMethod(
      prototype,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }

  public static void checkFieldConflicts(@Nullable PsiClass aClass, String newName, final MultiMap<PsiElement, String> conflicts) {
    PsiField existingField = aClass != null ? aClass.findFieldByName(newName, true) : null;
    if (existingField != null) {
      if (aClass.equals(existingField.getContainingClass())) {
        String className = aClass instanceof PsiAnonymousClass ?
                           RefactoringBundle.message("current.class") :
                           RefactoringUIUtil.getDescription(aClass, false);
        final String conflict = RefactoringBundle.message("field.0.is.already.defined.in.the.1",
                                                          existingField.getName(), className);
        conflicts.putValue(existingField, conflict);
      }
      else { // method somewhere in base class
        if (!existingField.hasModifierProperty(PsiModifier.PRIVATE)) {
          String fieldInfo = PsiFormatUtil.formatVariable(existingField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER, PsiSubstitutor.EMPTY);
          String className = RefactoringUIUtil.getDescription(existingField.getContainingClass(), false);
          final String descr = RefactoringBundle.message("field.0.will.hide.field.1.of.the.base.class",
                                                         newName, fieldInfo, className);
          conflicts.putValue(existingField, descr);
        }
      }
    }
  }
}
