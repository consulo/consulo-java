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

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeUtil;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.template.EmptyExpression;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import java.util.List;
import java.util.function.Function;

public class CreateEnumConstantFromUsageFix extends CreateVarFromUsageFix implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(CreateEnumConstantFromUsageFix.class);

  public CreateEnumConstantFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
    setText(JavaQuickFixLocalize.createConstantFromUsageFamily());
  }

  @Override
  protected LocalizeValue getText(String varName) {
    return JavaQuickFixLocalize.createEnumConstantFromUsageText(myReferenceExpression.getReferenceName());
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    LOG.assertTrue(targetClass.isEnum());
    String name = myReferenceExpression.getReferenceName();
    LOG.assertTrue(name != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory();
    PsiEnumConstant enumConstant = elementFactory.createEnumConstantFromText(name, null);
    enumConstant = (PsiEnumConstant) targetClass.add(enumConstant);

    PsiMethod[] constructors = targetClass.getConstructors();
    if (constructors.length > 0) {
      PsiMethod constructor = constructors[0];
      PsiParameter[] parameters = constructor.getParameterList().getParameters();
      if (parameters.length > 0) {
        String params = StringUtil.join(parameters, new Function<PsiParameter, String>() {
          @Override
          public String apply(PsiParameter psiParameter) {
            return psiParameter.getName();
          }
        }, ",");
        enumConstant = (PsiEnumConstant) enumConstant.replace(elementFactory.createEnumConstantFromText(name + "(" + params + ")", null));
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(enumConstant);

        PsiExpressionList argumentList = enumConstant.getArgumentList();
        LOG.assertTrue(argumentList != null);
        for (PsiExpression expression : argumentList.getExpressions()) {
          builder.replaceElement(expression, new EmptyExpression());
        }

        enumConstant = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(enumConstant);
        Template template = builder.buildTemplate();

        Project project = targetClass.getProject();
        Editor newEditor = positionCursor(project, targetClass.getContainingFile(), enumConstant);
        if (newEditor != null) {
          TextRange range = enumConstant.getTextRange();
          newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
          startTemplate(newEditor, template, project);
        }
      }
    }
  }


  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    PsiElement element = getElement();
    List<PsiClass> classes = getTargetClasses(element);
    if (classes.size() != 1 || !classes.get(0).isEnum()) return false;
    ExpectedTypeInfo[] typeInfos = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);
    PsiType enumType = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory().createType(classes.get(0));
    for (ExpectedTypeInfo typeInfo : typeInfos) {
      if (ExpectedTypeUtil.matches(enumType, typeInfo)) return true;
    }
    return false;
  }
}
