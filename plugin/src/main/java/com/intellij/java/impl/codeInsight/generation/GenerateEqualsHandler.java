/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.impl.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.impl.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.language.psi.*;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.java.impl.codeInsight.JavaCodeInsightSettings;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author dsl
 */
public class GenerateEqualsHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GenerateEqualsHandler.class);
  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;
  private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1]; //cannot return empty array, but this result won't be used anyway

  public GenerateEqualsHandler() {
    super("");
  }

  @Override
  @RequiredUIAccess
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;

    GlobalSearchScope scope = aClass.getResolveScope();
    final PsiMethod equalsMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getEqualsSignature(project, scope));
    final PsiMethod hashCodeMethod = GenerateEqualsHelper.findMethod(aClass, GenerateEqualsHelper.getHashCodeSignature());

    boolean needEquals = equalsMethod == null;
    boolean needHashCode = hashCodeMethod == null;
    if (!needEquals && !needHashCode) {
      LocalizeValue text = aClass instanceof PsiAnonymousClass
        ? CodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedWarningAnonymous()
        : CodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedWarning(aClass.getQualifiedName());

      if (Messages.showYesNoDialog(
        project,
        text.get(),
        CodeInsightLocalize.generateEqualsAndHashcodeAlreadyDefinedTitle().get(),
        UIUtil.getQuestionIcon()
      ) == Messages.YES) {
        if (!project.getApplication().runWriteAction((Computable<Boolean>)() -> {
          try {
            equalsMethod.delete();
            hashCodeMethod.delete();
            return Boolean.TRUE;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return Boolean.FALSE;
          }
        })) {
          return null;
        } else {
          needEquals = needHashCode = true;
        }
      } else {
        return null;
      }
    }
    boolean hasNonStaticFields = hasNonStaticFields(aClass);
    if (!hasNonStaticFields) {
      HintManager.getInstance().showErrorHint(editor, "No fields to include in equals/hashCode have been found");
      return null;
    }

    GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
    if (!wizard.showAndGet()) {
      return null;
    }
    myEqualsFields = wizard.getEqualsFields();
    myHashCodeFields = wizard.getHashCodeFields();
    myNonNullFields = wizard.getNonNullFields();
    return DUMMY_RESULT;
  }

  private static boolean hasNonStaticFields(PsiClass aClass) {
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean hasMembers(@Nonnull PsiClass aClass) {
    return hasNonStaticFields(aClass);
  }

  @Override
  @Nonnull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] originalMembers) throws IncorrectOperationException {
    Project project = aClass.getProject();
    final boolean useInstanceofToCheckParameterType = JavaCodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;
    final boolean useAccessors = JavaCodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE;

    GenerateEqualsHelper helper = new GenerateEqualsHelper(project, aClass, myEqualsFields, myHashCodeFields, myNonNullFields, useInstanceofToCheckParameterType, useAccessors);
    return OverrideImplementUtil.convert2GenerationInfos(helper.generateMembers());
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    return null;
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) {
    return null;
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = null;
  }
}
