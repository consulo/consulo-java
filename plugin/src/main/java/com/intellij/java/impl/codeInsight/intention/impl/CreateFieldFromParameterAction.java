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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CreateFieldFromParameterAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class CreateFieldFromParameterAction extends CreateFieldFromParameterActionBase {

  @Override
  protected boolean isAvailable(PsiParameter psiParameter) {
    final PsiType type = getSubstitutedType(psiParameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  protected PsiType getSubstitutedType(PsiParameter parameter) {
    return FieldFromParameterUtils.getSubstitutedType(parameter);
  }

  @Override
  protected void performRefactoring(Project project,
                                    PsiClass targetClass,
                                    PsiMethod method,
                                    PsiParameter myParameter,
                                    PsiType type,
                                    String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    FieldFromParameterUtils.createFieldAndAddAssignment(project, targetClass, method, myParameter, type, fieldName, methodStatic, isFinal);
  }
}
