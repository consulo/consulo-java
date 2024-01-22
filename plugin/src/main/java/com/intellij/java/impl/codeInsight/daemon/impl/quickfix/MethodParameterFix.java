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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MethodParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnFix");

  private final PsiType myParameterType;
  private final int myIndex;
  private final boolean myFixWholeHierarchy;
  private final String myName;

  public MethodParameterFix(PsiMethod method, PsiType type, int index, boolean fixWholeHierarchy) {
    super(method);
    myParameterType = type;
    myIndex = index;
    myFixWholeHierarchy = fixWholeHierarchy;
    myName = method.getName();
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("fix.parameter.type.text",
                                  myName,
                                  myParameterType.getCanonicalText() );
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("fix.parameter.type.family");
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull Project project,
                             @Nonnull PsiFile file,
                             @jakarta.annotation.Nonnull PsiElement startElement,
                             @jakarta.annotation.Nonnull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    return myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myParameterType != null
        && !TypeConversionUtil.isNullType(myParameterType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myParameterType, myMethod.getReturnType());
  }

  @Override
  public void invoke(@Nonnull final Project project,
                     @Nonnull final PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) return;
    try {
      PsiMethod method = myMethod;
      if (myFixWholeHierarchy) {
        method = myMethod.findDeepestSuperMethod();
        if (method == null) method = myMethod;
      }

      final PsiMethod finalMethod = method;
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(project,
                                                                        finalMethod,
                                                                        false, null,
                                                                        finalMethod.getName(),
                                                                        finalMethod.getReturnType(),
                                                                        getNewParametersInfo(finalMethod));

      processor.run();


      LanguageUndoUtil.markPsiFileForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private ParameterInfoImpl[] getNewParametersInfo(PsiMethod method) throws IncorrectOperationException {
    List<ParameterInfoImpl> result = new ArrayList<ParameterInfoImpl>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(method.getProject());
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, myParameterType);
    PsiParameter newParameter = factory.createParameter(nameInfo.names[0], myParameterType);
    if (method.getContainingClass().isInterface()) {
      PsiUtil.setModifierProperty(newParameter, PsiModifier.FINAL, false);
      }

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (i == myIndex) {
        newParameter.setName(parameter.getName());
        parameter = newParameter;
      }
      result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
    }
    if (parameters.length == myIndex) {
      result.add(new ParameterInfoImpl(-1, newParameter.getName(), newParameter.getType()));
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }
}
