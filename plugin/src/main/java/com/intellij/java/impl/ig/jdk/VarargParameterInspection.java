/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.jdk;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

@ExtensionImpl
public class VarargParameterInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "VariableArgumentMethod";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.variableArgumentMethodDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.variableArgumentMethodProblemDescriptor().get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new VarargParameterFix();
  }

  private static class VarargParameterFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.variableArgumentMethodQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)element.getParent();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (!lastParameter.isVarArgs()) {
        return;
      }
      final PsiEllipsisType type = (PsiEllipsisType)lastParameter.getType();
      final Query<PsiReference> query = ReferencesSearch.search(method);
      final PsiType componentType = type.getComponentType();
      final String typeText;
      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        typeText = classType.rawType().getCanonicalText();
      } else {
        typeText = componentType.getCanonicalText();
      }
      final Collection<PsiReference> references = query.findAll();
      for (PsiReference reference : references) {
        modifyCalls(reference, typeText, parameters.length - 1);
      }
      final PsiType arrayType = type.toArrayType();
      final PsiManager psiManager = lastParameter.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
      final PsiTypeElement newTypeElement = factory.createTypeElement(arrayType);
      final PsiTypeElement typeElement = lastParameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, JavaClassNames.JAVA_LANG_SAFE_VARARGS);
      if (annotation != null) {
        annotation.delete();
      }
      typeElement.replace(newTypeElement);
    }

    public static void modifyCalls(PsiReference reference, String arrayTypeText, int indexOfFirstVarargArgument) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference.getElement();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceExpression.getParent();
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      @NonNls final StringBuilder builder = new StringBuilder("new ");
      builder.append(arrayTypeText);
      builder.append("[]{");
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        final String firstArgumentText = firstArgument.getText();
        builder.append(firstArgumentText);
        for (int i = indexOfFirstVarargArgument + 1; i < arguments.length; i++) {
          builder.append(',').append(arguments[i].getText());
        }
      }
      builder.append('}');
      final Project project = referenceExpression.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression arrayExpression = factory.createExpressionFromText(builder.toString(), referenceExpression);
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        argumentList.deleteChildRange(firstArgument, arguments[arguments.length - 1]);
        argumentList.add(arrayExpression);
      }
      else {
        argumentList.add(arrayExpression);
      }
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(argumentList);
      codeStyleManager.reformat(argumentList);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new VarargParameterVisitor();
  }

  private static class VarargParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() < 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (lastParameter.isVarArgs()) {
        registerMethodError(method);
      }
    }
  }
}