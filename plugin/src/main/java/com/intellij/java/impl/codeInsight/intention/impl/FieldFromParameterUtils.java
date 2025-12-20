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

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Danila Ponomarenko
 */
public final class FieldFromParameterUtils {
  @Nullable
  public static PsiParameter findParameterAtCursor(@Nonnull PsiFile file, @Nonnull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) {
      return null;
    }
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) {
        return parameter;
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getType(@Nullable PsiParameter myParameter) {
    if (myParameter == null) {
      return null;
    }
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType) type).toArrayType() : type;
  }

  @Nullable
  public static PsiType getSubstitutedType(@Nullable PsiParameter parameter) {
    if (parameter == null) {
      return null;
    }

    PsiType type = getType(parameter);

    if (type instanceof PsiArrayType) {
      return type;
    }

    PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    PsiClass psiClass = result.getElement();
    if (psiClass == null) {
      return type;
    }
    HashSet<PsiTypeParameter> usedTypeParameters = new HashSet<PsiTypeParameter>();
    RefactoringUtil.collectTypeParameters(usedTypeParameters, parameter);
    for (Iterator<PsiTypeParameter> iterator = usedTypeParameters.iterator(); iterator.hasNext(); ) {
      PsiTypeParameter usedTypeParameter = iterator.next();
      if (parameter.getDeclarationScope() != usedTypeParameter.getOwner()) {
        iterator.remove();
      }
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parameter.getProject());
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      subst = subst.put(usedTypeParameter, TypeConversionUtil.typeParameterErasure(usedTypeParameter));
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }
    return psiClass instanceof PsiTypeParameter ? subst.substitute((PsiTypeParameter) psiClass) : elementFactory.createType(psiClass, substitutor);
  }

  @Nullable
  public static PsiField getParameterAssignedToField(PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false)) {
      if (!(reference instanceof PsiReferenceExpression)) {
        continue;
      }
      PsiReferenceExpression expression = (PsiReferenceExpression) reference;
      if (!(expression.getParent() instanceof PsiAssignmentExpression)) {
        continue;
      }
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression.getParent();
      if (assignmentExpression.getRExpression() != expression) {
        continue;
      }
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) {
        continue;
      }
      PsiElement element = ((PsiReferenceExpression) lExpression).resolve();
      if (element instanceof PsiField) {
        return (PsiField) element;
      }
    }
    return null;
  }

  public static int findFieldAssignmentAnchor(PsiStatement[] statements,
                                              @Nullable Ref<Pair<PsiField, Boolean>> anchorRef,
                                              PsiClass targetClass,
                                              PsiParameter myParameter) {
    int i = 0;
    for (; i < statements.length; i++) {
      PsiStatement psiStatement = statements[i];

      if (psiStatement instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement = (PsiExpressionStatement) psiStatement;
        PsiExpression expression = expressionStatement.getExpression();

        if (expression instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
          @NonNls String text = methodCallExpression.getMethodExpression().getText();

          if (text.equals("super") || text.equals("this")) {
            continue;
          }
        } else if (expression instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
          PsiExpression lExpression = assignmentExpression.getLExpression();
          PsiExpression rExpression = assignmentExpression.getRExpression();

          if (!(lExpression instanceof PsiReferenceExpression)) {
            break;
          }
          if (!(rExpression instanceof PsiReferenceExpression)) {
            break;
          }

          PsiReferenceExpression lReference = (PsiReferenceExpression) lExpression;
          PsiReferenceExpression rReference = (PsiReferenceExpression) rExpression;

          PsiElement lElement = lReference.resolve();
          PsiElement rElement = rReference.resolve();

          if (!(lElement instanceof PsiField) || ((PsiField) lElement).getContainingClass() != targetClass) {
            break;
          }
          if (!(rElement instanceof PsiParameter)) {
            break;
          }

          if (myParameter.getTextRange().getStartOffset() < rElement.getTextRange().getStartOffset()) {
            if (anchorRef != null) {
              anchorRef.set(Pair.create((PsiField) lElement, Boolean.TRUE));
            }
            break;
          }

          if (anchorRef != null) {
            anchorRef.set(Pair.create((PsiField) lElement, Boolean.FALSE));
          }
          continue;
        }
      }

      break;
    }
    return i;
  }

  public static void createFieldAndAddAssignment(@Nonnull Project project,
                                                 @Nonnull PsiClass targetClass,
                                                 @Nonnull PsiMethod method,
                                                 @Nonnull PsiParameter parameter,
                                                 @Nonnull PsiType fieldType, @Nonnull String fieldName,
                                                 boolean isStatic, boolean isFinal) throws IncorrectOperationException {

    PsiManager psiManager = PsiManager.getInstance(project);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    PsiElementFactory factory = psiFacade.getElementFactory();

    PsiField field = factory.createField(fieldName, fieldType);

    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager.copyNullableAnnotation(parameter, field) == null && isFinal) {
      manager.copyNotNullAnnotation(parameter, field);
    }

    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      return;
    }
    PsiStatement[] statements = methodBody.getStatements();

    Ref<Pair<PsiField, Boolean>> anchorRef = new Ref<>();
    int i = findFieldAssignmentAnchor(statements, anchorRef, targetClass, parameter);
    Pair<PsiField, Boolean> fieldAnchor = anchorRef.get();

    String stmtText = fieldName + " = " + parameter.getName() + ";";

    PsiVariable variable = psiFacade.getResolveHelper().resolveReferencedVariable(fieldName, methodBody);
    if (variable != null && !(variable instanceof PsiField)) {
      String prefix = isStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
      stmtText = prefix + stmtText;
    }

    PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
    assignmentStmt = (PsiStatement) CodeStyleManager.getInstance(project).reformat(assignmentStmt);

    if (i == statements.length) {
      methodBody.add(assignmentStmt);
    } else {
      methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
    }

    if (fieldAnchor != null) {
      PsiVariable psiVariable = fieldAnchor.getFirst();
      psiVariable.normalizeDeclaration();
    }

    if (targetClass.findFieldByName(fieldName, false) == null) {
      if (fieldAnchor != null) {
        Boolean insertBefore = fieldAnchor.getSecond();
        PsiField inField = fieldAnchor.getFirst();
        if (insertBefore.booleanValue()) {
          targetClass.addBefore(field, inField);
        } else {
          targetClass.addAfter(field, inField);
        }
      } else {
        targetClass.add(field);
      }
    }
  }

  public static boolean isAvailable(@Nullable PsiParameter myParameter,
                                    @Nullable PsiType type,
                                    @Nullable PsiClass targetClass) {
    return myParameter != null
        && myParameter.isValid()
        && myParameter.getManager().isInProject(myParameter)
        && myParameter.getDeclarationScope() instanceof PsiMethod
        && ((PsiMethod) myParameter.getDeclarationScope()).getBody() != null
        && type != null
        && type.isValid()
        && targetClass != null
        && !targetClass.isInterface()
        && getParameterAssignedToField(myParameter) == null;
  }

  private FieldFromParameterUtils() {
  }
}
