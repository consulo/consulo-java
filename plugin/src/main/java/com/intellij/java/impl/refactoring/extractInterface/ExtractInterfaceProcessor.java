/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.extractInterface;

import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

/**
 * @author dsl
 */
public class ExtractInterfaceProcessor extends ExtractSuperBaseProcessor {
  public ExtractInterfaceProcessor(Project project,
               boolean replaceInstanceOf,
               PsiDirectory targetDirectory,
               String newClassName,
               PsiClass aClass,
               MemberInfo[] memberInfos,
               DocCommentPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, targetDirectory, newClassName, aClass, memberInfos, javaDocPolicy);
  }

  protected PsiClass extractSuper(String superClassName) throws IncorrectOperationException {
    return ExtractInterfaceHandler.extractInterface(myClass.getContainingFile().getContainingDirectory(), myClass, superClassName, myMemberInfos, myJavaDocPolicy);
  }

  protected boolean isSuperInheritor(PsiClass aClass) {
    if (!aClass.isInterface()) {
      return myClass.isInheritor(aClass, true);
    }
    else {
      return doesAnyExtractedInterfaceExtends(aClass);
    }
  }

  protected boolean isInSuper(PsiElement member) {
    if (member instanceof PsiField) {
      PsiField field = ((PsiField)member);
      return doMemberInfosContain(field);
    }
    else if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) member;
      return doMemberInfosContain(method);
    }
    return false;
  }
}
