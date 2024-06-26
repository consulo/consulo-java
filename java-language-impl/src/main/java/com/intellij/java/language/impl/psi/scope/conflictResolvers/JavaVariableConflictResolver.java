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
package com.intellij.java.language.impl.psi.scope.conflictResolvers;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.impl.psi.scope.PsiConflictResolver;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 16:36:05
 * To change this template use Options | File Templates.
 */
public class JavaVariableConflictResolver implements PsiConflictResolver{
  @Override
  public CandidateInfo resolveConflict(@Nonnull List<CandidateInfo> conflicts){
    final int size = conflicts.size();
    if (size == 1){
      return conflicts.get(0);
    }
    if (size == 0) {
      return null;
    }
    final CandidateInfo[] uncheckedResult = conflicts.toArray(new CandidateInfo[size]);
    CandidateInfo currentResult = uncheckedResult[0];

    PsiElement currentElement = currentResult.getElement();
    if (currentElement instanceof PsiField currentField) {
      for (int i = 1; i < uncheckedResult.length; i++) {
        final CandidateInfo candidate = uncheckedResult[i];
        final PsiElement otherElement = candidate.getElement();
        if (otherElement == null) continue;

        if (!(otherElement instanceof PsiField)) {
          if (otherElement instanceof PsiLocalVariable) {
            return candidate;
          }
          else {
            if (!currentResult.isAccessible()) return candidate;
            conflicts.remove(candidate);
            continue;
          }
        }

        final PsiClass newClass = ((PsiField)otherElement).getContainingClass();
        final PsiClass oldClass = currentField.getContainingClass();

        final PsiElement scope = currentResult.getCurrentFileResolveScope();
        Boolean oldClassIsInheritor = null;
        if (newClass != null && oldClass != null) {
          if (newClass.isInheritor(oldClass, true)) {
            if (!(scope instanceof PsiClass) ||
                scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(oldClass, newClass)) {
              // candidate is better
              conflicts.remove(currentResult);
              currentResult = candidate;
              currentElement = currentResult.getElement();
              continue;
            }
          }
          else if (oldClassIsInheritor = oldClass.isInheritor(newClass, true)) {
            if (!(scope instanceof PsiClass) ||
                scope.equals(oldClass) ||
                scope.equals(newClass) ||
                !((PsiClass)scope).isInheritorDeep(newClass, oldClass)) {
              // candidate is worse
              conflicts.remove(candidate);
              continue;
            }
          }
        }

        if (!candidate.isAccessible()) {
          conflicts.remove(candidate);
          continue;
        }
        if (!currentResult.isAccessible()) {
          conflicts.remove(currentResult);
          currentResult = candidate;
          currentElement = currentResult.getElement();
          continue;
        }

        //This test should go last
        if (otherElement == currentElement) {
          conflicts.remove(candidate);
          continue;
        }

        if (oldClassIsInheritor == null) {
          oldClassIsInheritor = oldClass != null && newClass != null && oldClass.isInheritor(newClass, true);
        }
        if (oldClassIsInheritor) {
          // both fields are accessible
          // field in derived hides field in base
          conflicts.remove(candidate);
          continue;
        }
        return null;
      }
    }
    return currentResult;
  }
}
