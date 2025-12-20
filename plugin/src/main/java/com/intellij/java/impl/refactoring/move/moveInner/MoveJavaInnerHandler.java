/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.move.moveInner;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import jakarta.annotation.Nonnull;

@ExtensionImpl(id = "java")
public class MoveJavaInnerHandler implements MoveInnerHandler {
  @Nonnull
  @Override
  public PsiClass copyClass(@Nonnull MoveInnerOptions options) {
    PsiClass innerClass = options.getInnerClass();

    PsiClass newClass;
    if (options.getTargetContainer() instanceof PsiDirectory) {
      newClass = JavaDirectoryService.getInstance().createClass((PsiDirectory)options.getTargetContainer(), options.getNewClassName());
      PsiDocComment defaultDocComment = newClass.getDocComment();
      if (defaultDocComment != null && innerClass.getDocComment() == null) {
        innerClass = (PsiClass)innerClass.addAfter(defaultDocComment, null).getParent();
      }

      newClass = (PsiClass)newClass.replace(innerClass);
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, false);
      PsiUtil.setModifierProperty(newClass, PsiModifier.PRIVATE, false);
      PsiUtil.setModifierProperty(newClass, PsiModifier.PROTECTED, false);
      boolean makePublic = needPublicAccess(options.getOuterClass(), options.getTargetContainer());
      if (makePublic) {
        PsiUtil.setModifierProperty(newClass, PsiModifier.PUBLIC, true);
      }

      PsiMethod[] constructors = newClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        PsiModifierList modifierList = constructor.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
        modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
        if (makePublic && !newClass.isEnum()) {
          modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        }
      }
    }
    else {
      newClass = (PsiClass)options.getTargetContainer().add(innerClass);
    }

    newClass.setName(options.getNewClassName());

    return newClass;
  }

  protected static boolean needPublicAccess(PsiClass outerClass, PsiElement targetContainer) {
    if (outerClass.isInterface()) {
      return true;
    }
    if (targetContainer instanceof PsiDirectory) {
      PsiJavaPackage targetPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)targetContainer);
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(outerClass.getProject());
      if (targetPackage != null && !psiFacade.isInPackage(outerClass, targetPackage)) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
