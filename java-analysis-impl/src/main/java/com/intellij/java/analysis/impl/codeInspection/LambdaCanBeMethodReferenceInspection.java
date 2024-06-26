/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.Conditions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * User: anna
 */
@ExtensionImpl
public class LambdaCanBeMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(LambdaCanBeMethodReferenceInspection.class);

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids().get();
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Lambda can be replaced with method reference";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "Convert2MethodRef";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (PsiUtil.getLanguageLevel(expression).isAtLeast(LanguageLevel.JDK_1_8)) {
          final PsiElement body = expression.getBody();
          final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
          if (functionalInterfaceType != null) {
            PsiExpression methodRefCandidate = extractMethodReferenceCandidateExpression(body, false);
            final PsiExpression candidate = canBeMethodReferenceProblem(expression.getParameterList().getParameters(), functionalInterfaceType, null, methodRefCandidate);
            if (candidate != null) {
              PsiExpression qualifier = methodRefCandidate instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression) methodRefCandidate).getMethodExpression()
                  .getQualifierExpression() : methodRefCandidate instanceof PsiNewExpression ? ((PsiNewExpression) methodRefCandidate).getQualifier() : null;
              boolean safeQualifier = checkQualifier(qualifier);
              ProblemHighlightType errorOrWarning = safeQualifier ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION;
              holder.registerProblem(InspectionProjectProfileManager.isInformationLevel(getShortName(), expression) ? expression : candidate, "Can be replaced with method reference",
                  errorOrWarning, new ReplaceWithMethodRefFix(safeQualifier ? "" : " (may change semantics)"));
            }
          }
        }
      }
    };
  }

  @Nullable
  public static PsiExpression canBeMethodReferenceProblem(@Nullable final PsiElement body, final PsiVariable[] parameters, PsiType functionalInterfaceType, @Nullable PsiElement context) {
    PsiExpression methodRefCandidate = extractMethodReferenceCandidateExpression(body, true);
    return canBeMethodReferenceProblem(parameters, functionalInterfaceType, context, methodRefCandidate);
  }

  @Nullable
  public static PsiExpression canBeMethodReferenceProblem(final PsiVariable[] parameters, PsiType functionalInterfaceType, @Nullable PsiElement context, final PsiExpression methodRefCandidate) {
    if (methodRefCandidate instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression) methodRefCandidate;
      if (newExpression.getAnonymousClass() != null || newExpression.getArrayInitializer() != null) {
        return null;
      }
    }

    final String methodReferenceText = createMethodReferenceText(methodRefCandidate, functionalInterfaceType, parameters);
    if (methodReferenceText != null) {
      LOG.assertTrue(methodRefCandidate != null);
      if (!(methodRefCandidate instanceof PsiCallExpression)) {
        return methodRefCandidate;
      }
      PsiCallExpression callExpression = (PsiCallExpression) methodRefCandidate;
      final PsiMethod method = callExpression.resolveMethod();
      if (method != null) {
        if (!isSimpleCall(parameters, callExpression, method)) {
          return null;
        }
      } else {
        LOG.assertTrue(callExpression instanceof PsiNewExpression);
        if (((PsiNewExpression) callExpression).getQualifier() != null) {
          return null;
        }

        final PsiExpression[] dims = ((PsiNewExpression) callExpression).getArrayDimensions();
        if (dims.length == 1 && parameters.length == 1) {
          if (!resolvesToParameter(dims[0], parameters[0])) {
            return null;
          }
        } else if (dims.length > 0) {
          return null;
        }

        if (callExpression.getTypeArguments().length > 0) {
          return null;
        }
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
      PsiMethodReferenceExpression methodReferenceExpression;
      try {
        methodReferenceExpression = (PsiMethodReferenceExpression) elementFactory.createExpressionFromText(methodReferenceText, context != null ? context : callExpression);
      } catch (IncorrectOperationException e) {
        LOG.error(callExpression.getText(), e);
        return null;
      }
      final Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
      try {
        map.put(methodReferenceExpression, functionalInterfaceType);
        final JavaResolveResult result = methodReferenceExpression.advancedResolve(false);
        final PsiElement element = result.getElement();
        if (element != null && result.isAccessible() &&
            !(result instanceof MethodCandidateInfo && !((MethodCandidateInfo) result).isApplicable())) {
          if (!(element instanceof PsiMethod)) {
            return callExpression;
          }

          return method != null && MethodSignatureUtil.areSignaturesEqual((PsiMethod) element, method) ? callExpression : null;
        }
      } finally {
        map.remove(methodReferenceExpression);
      }
    }
    return null;
  }

  private static boolean isSimpleCall(final PsiVariable[] parameters, PsiCallExpression callExpression, PsiMethod psiMethod) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList == null) {
      return false;
    }

    final int calledParametersCount = psiMethod.getParameterList().getParametersCount();
    final PsiExpression[] expressions = argumentList.getExpressions();

    final PsiExpression qualifier;
    if (callExpression instanceof PsiMethodCallExpression) {
      qualifier = ((PsiMethodCallExpression) callExpression).getMethodExpression().getQualifierExpression();
    } else if (callExpression instanceof PsiNewExpression) {
      qualifier = ((PsiNewExpression) callExpression).getQualifier();
    } else {
      qualifier = null;
    }

    if (expressions.length == 0 && parameters.length == 0) {
      return !(callExpression instanceof PsiNewExpression && qualifier != null);
    }

    final int offset = parameters.length - calledParametersCount;
    if (expressions.length > calledParametersCount || offset < 0) {
      return false;
    }

    for (int i = 0; i < expressions.length; i++) {
      if (!resolvesToParameter(expressions[i], parameters[i + offset])) {
        return false;
      }
    }

    if (offset == 0) {
      if (qualifier != null) {
        final boolean[] parameterUsed = new boolean[]{false};
        qualifier.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (parameterUsed[0]) {
              return;
            }
            super.visitElement(element);
          }

          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            parameterUsed[0] |= ArrayUtil.find(parameters, expression.resolve()) >= 0;
          }
        });
        return !parameterUsed[0];
      }
      return true;
    }

    return resolvesToParameter(qualifier, parameters[0]);
  }

  @Contract("null, _ -> false")
  private static boolean resolvesToParameter(PsiExpression expression, PsiVariable parameter) {
    return expression instanceof PsiReferenceExpression && ((PsiReferenceExpression) expression).resolve() == parameter;
  }

  @Nullable
  public static PsiExpression extractMethodReferenceCandidateExpression(PsiElement body, boolean checkSideEffectPureQualifier) {
    final PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression == null) {
      return null;
    }
    if (expression instanceof PsiNewExpression) {
      if (!checkSideEffectPureQualifier || checkQualifier(((PsiNewExpression) expression).getQualifier())) {
        return expression;
      }
    } else if (expression instanceof PsiMethodCallExpression) {
      if (!checkSideEffectPureQualifier || checkQualifier(((PsiMethodCallExpression) expression).getMethodExpression().getQualifier())) {
        return expression;
      }
    }

    if (expression instanceof PsiInstanceOfExpression && CodeStyleSettingsManager.getSettings(expression.getProject()).REPLACE_INSTANCEOF) {
      return expression;
    } else if (expression instanceof PsiBinaryExpression && CodeStyleSettingsManager.getSettings(expression.getProject()).REPLACE_NULL_CHECK) {
      if (ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression) expression) != null) {
        return expression;
      }
    } else if (expression instanceof PsiTypeCastExpression && CodeStyleSettingsManager.getSettings(expression.getProject()).REPLACE_CAST) {
      PsiTypeElement typeElement = ((PsiTypeCastExpression) expression).getCastType();
      if (typeElement != null) {
        PsiJavaCodeReferenceElement refs = typeElement.getInnermostComponentReferenceElement();
        if (refs != null && refs.getParameterList() != null && refs.getParameterList().getTypeParameterElements().length != 0) {
          return null;
        }
        PsiType type = typeElement.getType();
        if (type instanceof PsiPrimitiveType || PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) {
          return null;
        }
        return expression;
      }
    }
    return null;
  }

  public static void replaceAllLambdasWithMethodReferences(PsiElement root) {
    Collection<PsiLambdaExpression> lambdas = PsiTreeUtil.findChildrenOfType(root, PsiLambdaExpression.class);
    if (!lambdas.isEmpty()) {
      for (PsiLambdaExpression lambda : lambdas) {
        replaceLambdaWithMethodReference(lambda);
      }
    }
  }

  @Nonnull
  public static PsiExpression replaceLambdaWithMethodReference(@Nonnull PsiLambdaExpression lambda) {
    PsiElement body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
    final PsiExpression candidate = canBeMethodReferenceProblem(body, lambda.getParameterList().getParameters(), lambda.getFunctionalInterfaceType(), lambda);
    return tryConvertToMethodReference(lambda, candidate);
  }

  public static boolean checkQualifier(@Nullable PsiElement qualifier) {
    if (qualifier == null) {
      return true;
    }
    final Condition<PsiElement> callExpressionCondition = Conditions.instanceOf(PsiCallExpression.class);
    final Condition<PsiElement> nonFinalFieldRefCondition = expression -> {
      if (expression instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression) expression).resolve();
        if (element instanceof PsiField && !((PsiField) element).hasModifierProperty(PsiModifier.FINAL)) {
          return true;
        }
      }
      return false;
    };
    return SyntaxTraverser.psiTraverser().withRoot(qualifier).filter(Conditions.or(callExpressionCondition, nonFinalFieldRefCondition)).toList().isEmpty();
  }

  @Nullable
  private static PsiMethod getNonAmbiguousReceiver(PsiVariable[] parameters, @Nonnull PsiMethod psiMethod) {
    String methodName = psiMethod.getName();
    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return null;
    }

    final PsiMethod[] psiMethods = containingClass.findMethodsByName(methodName, false);
    if (psiMethods.length == 1) {
      return psiMethod;
    }

    final PsiType receiverType = parameters[0].getType();
    for (PsiMethod method : psiMethods) {
      if (isPairedNoReceiver(parameters, receiverType, method)) {
        final PsiMethod[] deepestSuperMethods = psiMethod.findDeepestSuperMethods();
        if (deepestSuperMethods.length > 0) {
          for (PsiMethod superMethod : deepestSuperMethods) {
            PsiMethod validSuperMethod = getNonAmbiguousReceiver(parameters, superMethod);
            if (validSuperMethod != null) {
              return validSuperMethod;
            }
          }
        }
        return null;
      }
    }
    return psiMethod;
  }

  private static boolean isPairedNoReceiver(PsiVariable[] parameters, PsiType receiverType, PsiMethod method) {
    final PsiParameter[] nonReceiverCandidateParams = method.getParameterList().getParameters();
    return nonReceiverCandidateParams.length == parameters.length &&
        method.hasModifierProperty(PsiModifier.STATIC) &&
        TypeConversionUtil.areTypesConvertible(nonReceiverCandidateParams[0].getType(), receiverType);
  }

  private static boolean isSoleParameter(@Nonnull PsiVariable[] parameters, @Nullable PsiExpression expression) {
    return parameters.length == 1 &&
        expression instanceof PsiReferenceExpression &&
        parameters[0] == ((PsiReferenceExpression) expression).resolve();
  }

  @Nullable
  static String createMethodReferenceText(final PsiElement element, final PsiType functionalInterfaceType, final PsiVariable[] parameters) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;

      JavaResolveResult result = methodCall.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod) result.getElement();
      if (psiMethod == null) {
        return null;
      }

      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final String qualifierByMethodCall = getQualifierTextByMethodCall(methodCall, functionalInterfaceType, parameters, psiMethod, result.getSubstitutor());
      if (qualifierByMethodCall != null) {
        return qualifierByMethodCall + "::" + ((PsiMethodCallExpression) element).getTypeArgumentList().getText() + methodExpression.getReferenceName();
      }
    } else if (element instanceof PsiNewExpression) {
      final String qualifierByNew = getQualifierTextByNewExpression((PsiNewExpression) element);
      if (qualifierByNew != null) {
        return qualifierByNew + ((PsiNewExpression) element).getTypeArgumentList().getText() + "::new";
      }
    } else if (element instanceof PsiInstanceOfExpression) {
      if (isSoleParameter(parameters, ((PsiInstanceOfExpression) element).getOperand())) {
        PsiTypeElement type = ((PsiInstanceOfExpression) element).getCheckType();
        if (type != null) {
          return type.getText() + ".class::isInstance";
        }
      }
    } else if (element instanceof PsiBinaryExpression) {
      PsiBinaryExpression nullCheck = (PsiBinaryExpression) element;
      PsiExpression operand = ExpressionUtils.getValueComparedWithNull(nullCheck);
      if (operand != null && isSoleParameter(parameters, operand)) {
        IElementType tokenType = nullCheck.getOperationTokenType();
        if (JavaTokenType.EQEQ.equals(tokenType)) {
          return "java.util.Objects::isNull";
        } else if (JavaTokenType.NE.equals(tokenType)) {
          return "java.util.Objects::nonNull";
        }
      }
    } else if (element instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression castExpression = (PsiTypeCastExpression) element;
      if (isSoleParameter(parameters, castExpression.getOperand())) {
        PsiTypeElement type = castExpression.getCastType();
        if (type != null) {
          return type.getText() + ".class::cast";
        }
      }
    }
    return null;
  }

  private static String getQualifierTextByNewExpression(PsiNewExpression element) {
    final PsiType newExprType = element.getType();
    if (newExprType == null) {
      return null;
    }

    PsiClass containingClass = null;
    final PsiJavaCodeReferenceElement classReference = element.getClassOrAnonymousClassReference();
    if (classReference != null) {
      final JavaResolveResult resolve = classReference.advancedResolve(false);
      final PsiElement resolveElement = resolve.getElement();
      if (resolveElement instanceof PsiClass) {
        containingClass = (PsiClass) resolveElement;
      }
    }

    String classOrPrimitiveName = null;
    if (containingClass != null) {
      classOrPrimitiveName = getClassReferenceName(containingClass);
    } else if (newExprType instanceof PsiArrayType) {
      final PsiType deepComponentType = newExprType.getDeepComponentType();
      if (deepComponentType instanceof PsiPrimitiveType) {
        classOrPrimitiveName = deepComponentType.getCanonicalText();
      }
    }

    if (classOrPrimitiveName == null) {
      return null;
    }

    int dim = newExprType.getArrayDimensions();
    while (dim-- > 0) {
      classOrPrimitiveName += "[]";
    }
    return classOrPrimitiveName;
  }

  @Nullable
  private static String getQualifierTextByMethodCall(final PsiMethodCallExpression methodCall,
                                                     final PsiType functionalInterfaceType,
                                                     final PsiVariable[] parameters,
                                                     final PsiMethod psiMethod,
                                                     final PsiSubstitutor substitutor) {

    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();

    final PsiClass containingClass = psiMethod.getContainingClass();
    LOG.assertTrue(containingClass != null);

    if (qualifierExpression != null) {
      boolean isReceiverType = false;
      if (qualifierExpression instanceof PsiReferenceExpression && ArrayUtil.find(parameters, ((PsiReferenceExpression) qualifierExpression).resolve()) > -1) {
        isReceiverType = PsiMethodReferenceUtil.isReceiverType(PsiMethodReferenceUtil.getFirstParameterType(functionalInterfaceType, qualifierExpression), containingClass, substitutor);
      }
      return isReceiverType ? composeReceiverQualifierText(parameters, psiMethod, containingClass, qualifierExpression) : qualifierExpression.getText();
    } else {
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return getClassReferenceName(containingClass);
      } else {
        PsiClass parentContainingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
        if (parentContainingClass instanceof PsiAnonymousClass) {
          parentContainingClass = PsiTreeUtil.getParentOfType(parentContainingClass, PsiClass.class, true);
        }
        PsiClass treeContainingClass = parentContainingClass;
        while (treeContainingClass != null && !InheritanceUtil.isInheritorOrSelf(treeContainingClass, containingClass, true)) {
          treeContainingClass = PsiTreeUtil.getParentOfType(treeContainingClass, PsiClass.class, true);
        }
        if (treeContainingClass != null && containingClass != parentContainingClass && treeContainingClass != parentContainingClass) {
          final String treeContainingClassName = treeContainingClass.getName();
          if (treeContainingClassName == null) {
            return null;
          }
          return treeContainingClassName + ".this";
        } else {
          return "this";
        }
      }
    }
  }

  @Nullable
  private static String composeReceiverQualifierText(PsiVariable[] parameters, PsiMethod psiMethod, PsiClass containingClass, @Nonnull PsiExpression qualifierExpression) {
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }

    final PsiMethod nonAmbiguousMethod = getNonAmbiguousReceiver(parameters, psiMethod);
    if (nonAmbiguousMethod == null) {
      return null;
    }

    final PsiClass nonAmbiguousContainingClass = nonAmbiguousMethod.getContainingClass();
    if (!containingClass.equals(nonAmbiguousContainingClass)) {
      return getClassReferenceName(nonAmbiguousContainingClass);
    }

    if (containingClass.isPhysical() &&
        qualifierExpression instanceof PsiReferenceExpression &&
        !PsiTypesUtil.isGetClass(psiMethod) &&
        ArrayUtil.find(parameters, ((PsiReferenceExpression) qualifierExpression).resolve()) > -1) {
      return getClassReferenceName(containingClass);
    }

    final PsiType qualifierExpressionType = qualifierExpression.getType();
    if (qualifierExpressionType != null && !FunctionalInterfaceParameterizationUtil.isWildcardParameterized(qualifierExpressionType)) {
      try {
        final String canonicalText = qualifierExpressionType.getCanonicalText();
        JavaPsiFacade.getElementFactory(containingClass.getProject()).createExpressionFromText(canonicalText + "::foo", qualifierExpression);
        return canonicalText;
      } catch (IncorrectOperationException ignore) {
      }
    }
    return getClassReferenceName(containingClass);
  }

  private static String getClassReferenceName(PsiClass containingClass) {
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    } else {
      return containingClass.getName();
    }
  }

  private static class ReplaceWithMethodRefFix implements LocalQuickFix {
    private String mySuffix;

    public ReplaceWithMethodRefFix(String suffix) {
      mySuffix = suffix;
    }

    @Nls
    @Nonnull
    @Override
    public String getName() {
      return getFamilyName() + mySuffix;
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return "Replace lambda with method reference";
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiLambdaExpression) {
        element = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression) element).getBody());
      }
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression == null) {
        return;
      }
      tryConvertToMethodReference(lambdaExpression, element);
    }
  }

  @Nonnull
  static PsiExpression tryConvertToMethodReference(@Nonnull PsiLambdaExpression lambda, PsiElement body) {
    Project project = lambda.getProject();
    PsiType functionalInterfaceType = lambda.getFunctionalInterfaceType();
    if (functionalInterfaceType == null || !functionalInterfaceType.isValid()) {
      return lambda;
    }
    final PsiType denotableFunctionalInterfaceType = RefactoringChangeUtil.getTypeByExpression(lambda);
    if (denotableFunctionalInterfaceType == null) {
      return lambda;
    }

    Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(lambda, PsiComment.class), (comment) -> (PsiComment) comment.copy());

    final String methodRefText = createMethodReferenceText(body, functionalInterfaceType, lambda.getParameterList().getParameters());

    if (methodRefText != null) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression psiExpression = factory.createExpressionFromText(methodRefText, lambda);
      final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(denotableFunctionalInterfaceType);
      PsiExpression replace = (PsiExpression) lambda.replace(psiExpression);
      final PsiType functionalTypeAfterReplacement = GenericsUtil.getVariableTypeByExpressionType(((PsiMethodReferenceExpression) replace).getFunctionalInterfaceType());
      functionalInterfaceType = typePointer.getType();
      if (functionalInterfaceType != null && (functionalTypeAfterReplacement == null || !functionalTypeAfterReplacement.equals(functionalInterfaceType))) { //ambiguity
        final PsiTypeCastExpression cast = (PsiTypeCastExpression) factory.createExpressionFromText("(A)a", replace);
        PsiTypeElement castType = cast.getCastType();
        LOG.assertTrue(castType != null);
        castType.replace(factory.createTypeElement(functionalInterfaceType));
        PsiExpression castOperand = cast.getOperand();
        LOG.assertTrue(castOperand != null);
        castOperand.replace(replace);
        replace = (PsiExpression) replace.replace(cast);
      }

      AnonymousCanBeLambdaInspection.restoreComments(comments, replace);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replace);
      return replace;
    }
    return lambda;
  }
}
