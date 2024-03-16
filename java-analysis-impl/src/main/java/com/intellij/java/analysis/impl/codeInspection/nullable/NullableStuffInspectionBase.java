// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.impl.codeInspection.AnnotateMethodFix;
import com.intellij.java.analysis.impl.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.analysis.impl.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.java.indexing.impl.search.JavaOverridingMethodsSearcher;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.*;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.registry.Registry;
import consulo.language.editor.inspection.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.content.GeneratedSourcesFilter;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_TYPE;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiMethod;

public abstract class NullableStuffInspectionBase extends AbstractBaseJavaLocalInspectionTool<NullableStuffInspectionState> {
  private static final Logger LOG = Logger.getInstance(NullableStuffInspectionBase.class);

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            NullableStuffInspectionState state) {
    final PsiFile file = holder.getFile();
    if (!PsiUtil.isLanguageLevel5OrHigher(file) || nullabilityAnnotationsNotAvailable(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        checkNullableStuffForMethod(method, holder, isOnTheFly, state);
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        checkMethodReference(expression, holder, state);

        JavaResolveResult result = expression.advancedResolve(false);
        PsiElement target = result.getElement();
        if (target instanceof PsiMethod) {
          checkCollectionNullityOnAssignment(expression,
                                             LambdaUtil.getFunctionalInterfaceReturnType(expression),
                                             result.getSubstitutor().substitute(((PsiMethod)target).getReturnType()));
        }
      }

      @Override
      public void visitField(PsiField field) {
        final PsiType type = field.getType();
        final Annotated annotated = check(field, holder, type);
        if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
          return;
        }
        Project project = holder.getProject();
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        if (annotated.isDeclaredNotNull ^ annotated.isDeclaredNullable) {
          final String anno = annotated.isDeclaredNotNull ? manager.getDefaultNotNull() : manager.getDefaultNullable();
          final List<String> annoToRemove = annotated.isDeclaredNotNull ? manager.getNullables() : manager.getNotNulls();

          if (!checkNonStandardAnnotations(field, annotated, manager, anno, holder)) {
            return;
          }

          checkAccessors(field, annotated, project, manager, anno, annoToRemove, holder, state);

          checkConstructorParameters(field, annotated, manager, anno, annoToRemove, holder, state);
        }
      }

      @Override
      public void visitParameter(PsiParameter parameter) {
        check(parameter, holder, parameter.getType());
      }

      @Override
      public void visitTypeElement(PsiTypeElement type) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(type.getProject());
        List<PsiAnnotation> annotations = getExclusiveAnnotations(type);

        checkType(null, holder, type.getType(),
                  ContainerUtil.find(annotations, a -> manager.getNotNulls().contains(a.getQualifiedName())),
                  ContainerUtil.find(annotations, a -> manager.getNullables().contains(a.getQualifiedName())));
      }

      private List<PsiAnnotation> getExclusiveAnnotations(PsiTypeElement type) {
        List<PsiAnnotation> annotations = ContainerUtil.newArrayList(type.getAnnotations());
        PsiTypeElement topMost = Objects.requireNonNull(SyntaxTraverser.psiApi().parents(type).filter(PsiTypeElement.class).last());
        PsiElement parent = topMost.getParent();
        if (parent instanceof PsiModifierListOwner && type.getType().equals(topMost.getType().getDeepComponentType())) {
          PsiModifierList modifierList = ((PsiModifierListOwner)parent).getModifierList();
          if (modifierList != null) {
            PsiAnnotation.TargetType[] targets =
              ArrayUtil.remove(AnnotationTargetUtil.getTargetsForLocation(modifierList), PsiAnnotation.TargetType.TYPE_USE);
            annotations.addAll(ContainerUtil.filter(modifierList.getAnnotations(),
                                                    a -> AnnotationTargetUtil.isTypeAnnotation(a) && AnnotationTargetUtil.findAnnotationTarget(
                                                      a,
                                                      targets) == null));
          }
        }
        return annotations;
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (!AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName())) {
          return;
        }

        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("exception");
        if (value instanceof PsiClassObjectAccessExpression) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
          if (psiClass != null && !hasStringConstructor(psiClass)) {
            holder.registerProblem(value,
                                   "Custom exception class should have a constructor with a single message parameter of String type",
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

          }
        }
      }

      private boolean hasStringConstructor(PsiClass aClass) {
        for (PsiMethod method : aClass.getConstructors()) {
          PsiParameterList list = method.getParameterList();
          if (list.getParametersCount() == 1 &&
            list.getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        checkNullableNotNullInstantiationConflict(reference);

        PsiElement list = reference.getParent();
        PsiElement psiClass = list instanceof PsiReferenceList ? list.getParent() : null;
        PsiElement intf = reference.resolve();
        if (psiClass instanceof PsiClass && list == ((PsiClass)psiClass).getImplementsList() &&
          intf instanceof PsiClass && ((PsiClass)intf).isInterface()) {
          String error = checkIndirectInheritance(psiClass, (PsiClass)intf, state);
          if (error != null) {
            holder.registerProblem(reference, error);
          }
        }
      }

      private void checkNullableNotNullInstantiationConflict(PsiJavaCodeReferenceElement reference) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass) {
          PsiTypeParameter[] typeParameters = ((PsiClass)element).getTypeParameters();
          PsiTypeElement[] typeArguments = getReferenceTypeArguments(reference);
          if (typeParameters.length > 0 && typeParameters.length == typeArguments.length && !(typeArguments[0].getType() instanceof PsiDiamondType)) {
            for (int i = 0; i < typeParameters.length; i++) {
              if (DfaPsiUtil.getTypeNullability(JavaPsiFacade.getElementFactory(element.getProject()).createType(typeParameters[i])) ==
                Nullability.NOT_NULL && DfaPsiUtil.getTypeNullability(typeArguments[i].getType()) != Nullability.NOT_NULL) {
                holder.registerProblem(typeArguments[i],
                                       "Non-null type argument is expected",
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
              }
            }
          }
        }
      }

      private PsiTypeElement[] getReferenceTypeArguments(PsiJavaCodeReferenceElement reference) {
        PsiReferenceParameterList typeArgList = reference.getParameterList();
        return typeArgList == null ? PsiTypeElement.EMPTY_ARRAY : typeArgList.getTypeParameterElements();
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkCollectionNullityOnAssignment(expression.getOperationSign(),
                                           expression.getLExpression().getType(),
                                           expression.getRExpression());
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier != null) {
          checkCollectionNullityOnAssignment(identifier, variable.getType(), variable.getInitializer());
        }
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) {
          return;
        }

        checkCollectionNullityOnAssignment(statement.getReturnValue(), PsiTypesUtil.getMethodReturnType(statement), returnValue);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkCollectionNullityOnAssignment(body, LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body);
        }
      }

      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        PsiExpressionList argList = callExpression.getArgumentList();
        JavaResolveResult result = callExpression.resolveMethodGenerics();
        PsiMethod method = (PsiMethod)result.getElement();
        if (method == null || argList == null) {
          return;
        }

        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiExpression[] arguments = argList.getExpressions();
        for (int i = 0; i < arguments.length; i++) {
          PsiExpression argument = arguments[i];
          if (i < parameters.length &&
            (i < parameters.length - 1 || !MethodCallInstruction.isVarArgCall(method, substitutor, arguments, parameters))) {
            checkCollectionNullityOnAssignment(argument, substitutor.substitute(parameters[i].getType()), argument);
          }
        }
      }

      private void checkCollectionNullityOnAssignment(@Nonnull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiExpression assignedExpression) {
        if (assignedExpression == null) {
          return;
        }

        checkCollectionNullityOnAssignment(errorElement, expectedType, assignedExpression.getType());
      }

      private void checkCollectionNullityOnAssignment(@Nonnull PsiElement errorElement,
                                                      @Nullable PsiType expectedType,
                                                      @Nullable PsiType assignedType) {
        if (isNullableNotNullCollectionConflict(expectedType, assignedType, new HashSet<>())) {
          holder.registerProblem(errorElement,
                                 "Assigning a collection of nullable elements into a collection of non-null elements",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

        }
      }

      private boolean isNullableNotNullCollectionConflict(@Nullable PsiType expectedType,
                                                          @Nullable PsiType assignedType,
                                                          @Nonnull Set<? super Couple<PsiType>> visited) {
        if (!visited.add(Couple.of(expectedType, assignedType))) {
          return false;
        }

        GlobalSearchScope scope = holder.getFile().getResolveScope();
        if (isNullityConflict(JavaGenericsUtil.getCollectionItemType(expectedType, scope),
                              JavaGenericsUtil.getCollectionItemType(assignedType, scope))) {
          return true;
        }

        for (int i = 0; i <= 1; i++) {
          PsiType expectedArg = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
          PsiType assignedArg = PsiUtil.substituteTypeParameter(assignedType, CommonClassNames.JAVA_UTIL_MAP, i, false);
          if (isNullityConflict(expectedArg, assignedArg) ||
            expectedArg != null && assignedArg != null && isNullableNotNullCollectionConflict(expectedArg, assignedArg, visited)) {
            return true;
          }
        }

        return false;
      }

      private boolean isNullityConflict(PsiType expected, PsiType assigned) {
        return DfaPsiUtil.getTypeNullability(expected) == Nullability.NOT_NULL && DfaPsiUtil.getTypeNullability(assigned) == Nullability.NULLABLE;
      }
    };
  }

  @Nullable
  private String checkIndirectInheritance(PsiElement psiClass, PsiClass intf, NullableStuffInspectionState state) {
    for (PsiMethod intfMethod : intf.getAllMethods()) {
      PsiClass intfMethodClass = intfMethod.getContainingClass();
      PsiMethod overridingMethod = intfMethodClass == null ? null :
        JavaOverridingMethodsSearcher.findOverridingMethod((PsiClass)psiClass, intfMethod, intfMethodClass);
      PsiClass overridingMethodClass = overridingMethod == null ? null : overridingMethod.getContainingClass();
      if (overridingMethodClass != null && overridingMethodClass != psiClass) {
        String error = checkIndirectInheritance(intfMethod, intfMethodClass, overridingMethod, overridingMethodClass, state);
        if (error != null) {
          return error;
        }
      }
    }
    return null;
  }

  @Nullable
  @RequiredReadAction
  private String checkIndirectInheritance(PsiMethod intfMethod,
                                          PsiClass intfMethodClass,
                                          PsiMethod overridingMethod,
                                          PsiClass overridingMethodClass,
                                          NullableStuffInspectionState state) {
    if (isNullableOverridingNotNull(Annotated.from(overridingMethod), intfMethod, state)) {
      return "Nullable method '" + overridingMethod.getName() +
        "' from '" + overridingMethodClass.getName() +
        "' implements non-null method from '" + intfMethodClass.getName() + "'";
    }
    if (isNonAnnotatedOverridingNotNull(overridingMethod, intfMethod, state)) {
      return "Non-annotated method '" + overridingMethod.getName() +
        "' from '" + overridingMethodClass.getName() +
        "' implements non-null method from '" + intfMethodClass.getName() + "'";
    }

    PsiParameter[] overridingParameters = overridingMethod.getParameterList().getParameters();
    PsiParameter[] superParameters = intfMethod.getParameterList().getParameters();
    if (overridingParameters.length == superParameters.length) {
      NullableNotNullManager manager = getNullityManager(intfMethod);
      for (int i = 0; i < overridingParameters.length; i++) {
        PsiParameter parameter = overridingParameters[i];
        List<PsiParameter> supers = Collections.singletonList(superParameters[i]);
        if (findNullableSuperForNotNullParameter(parameter, supers, state) != null) {
          return "Non-null parameter '" + parameter.getName() +
            "' in method '" + overridingMethod.getName() +
            "' from '" + overridingMethodClass.getName() +
            "' should not override nullable parameter from '" + intfMethodClass.getName() + "'";
        }
        if (findNotNullSuperForNonAnnotatedParameter(manager, parameter, supers, state) != null) {
          return "Non-annotated parameter '" + parameter.getName() +
            "' in method '" + overridingMethod.getName() +
            "' from '" + overridingMethodClass.getName() +
            "' should not override non-null parameter from '" + intfMethodClass.getName() + "'";
        }
        if (isNotNullParameterOverridingNonAnnotated(manager, parameter, supers, state)) {
          return "Non-null parameter '" + parameter.getName() +
            "' in method '" + overridingMethod.getName() +
            "' from '" + overridingMethodClass.getName() +
            "' should not override non-annotated parameter from '" + intfMethodClass.getName() + "'";
        }
      }
    }

    return null;
  }

  private void checkMethodReference(PsiMethodReferenceExpression expression, @Nonnull ProblemsHolder holder,
                                    NullableStuffInspectionState state) {
    PsiMethod superMethod = LambdaUtil.getFunctionalInterfaceMethod(expression);
    PsiMethod targetMethod = ObjectUtil.tryCast(expression.resolve(), PsiMethod.class);
    if (superMethod == null || targetMethod == null) {
      return;
    }

    PsiElement refName = expression.getReferenceNameElement();
    assert refName != null;
    if (isNullableOverridingNotNull(check(targetMethod, holder, expression.getType()), superMethod, state)) {
      holder.registerProblem(refName,
                             InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                       getPresentableAnnoName(targetMethod), getPresentableAnnoName(superMethod)),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    else if (isNonAnnotatedOverridingNotNull(targetMethod, superMethod, state)) {
      holder.registerProblem(refName,
                             "Not annotated method is used as an override for a method annotated with " + getPresentableAnnoName(superMethod),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             createFixForNonAnnotatedOverridesNotNull(targetMethod, superMethod));
    }
  }

  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return null;
  }

  private static boolean nullabilityAnnotationsNotAvailable(final PsiFile file) {
    final Project project = file.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return ContainerUtil.find(NullableNotNullManager.getInstance(project).getNullables(), s -> facade.findClass(s, scope) != null) == null;
  }

  private static boolean checkNonStandardAnnotations(PsiField field,
                                                     Annotated annotated,
                                                     NullableNotNullManager manager, String anno, @Nonnull ProblemsHolder holder) {
    if (!AnnotationUtil.isAnnotatingApplicable(field, anno)) {
      String message = "Not \'";
      PsiAnnotation annotation = Objects.requireNonNull(annotated.isDeclaredNullable ? annotated.nullable : annotated.notNull);
      message += annotation.getQualifiedName();
      message += "\' but \'" + anno + "\' would be used for code generation.";
      final PsiJavaCodeReferenceElement annotationNameReferenceElement = annotation.getNameReferenceElement();
      holder.registerProblem(annotationNameReferenceElement != null && annotationNameReferenceElement.isPhysical() ? annotationNameReferenceElement : field
                               .getNameIdentifier(),
                             message,
                             ProblemHighlightType.WEAK_WARNING,
                             new ChangeNullableDefaultsFix(annotated.notNull, annotated.nullable, manager));
      return false;
    }
    return true;
  }

  private void checkAccessors(PsiField field,
                              Annotated annotated,
                              Project project,
                              NullableNotNullManager manager,
                              final String anno,
                              final List<String> annoToRemove,
                              @Nonnull ProblemsHolder holder,
                              NullableStuffInspectionState state) {
    String propName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiMethod getter = PropertyUtilBase.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
    final PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
    if (nameIdentifier != null && nameIdentifier.isPhysical()) {
      if (PropertyUtil.getFieldOfGetter(getter) == field) {
        AnnotateMethodFix getterAnnoFix = new AnnotateMethodFix(anno, ArrayUtil.toStringArray(annoToRemove));
        if (state.REPORT_NOT_ANNOTATED_GETTER) {
          if (!manager.hasNullability(getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
            holder.registerProblem(nameIdentifier, InspectionsBundle
                                     .message("inspection.nullable.problems.annotated.field.getter.not.annotated", getPresentableAnnoName(field)),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
          }
        }
        if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
          annotated.isDeclaredNullable && isNotNullNotInferred(getter, false, false)) {
          holder.registerProblem(nameIdentifier, InspectionsBundle.message(
            "inspection.nullable.problems.annotated.field.getter.conflict", getPresentableAnnoName(field), getPresentableAnnoName(getter)),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getterAnnoFix);
        }
      }
    }

    final PsiClass containingClass = field.getContainingClass();
    final PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propName, isStatic, false);
    if (setter != null && setter.isPhysical() && PropertyUtil.getFieldOfSetter(setter) == field) {
      final PsiParameter[] parameters = setter.getParameterList().getParameters();
      assert parameters.length == 1 : setter.getText();
      final PsiParameter parameter = parameters[0];
      LOG.assertTrue(parameter != null, setter.getText());
      AddAnnotationPsiFix addAnnoFix = createAddAnnotationFix(anno, annoToRemove, parameter);
      if (state.REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
        final PsiIdentifier parameterName = parameter.getNameIdentifier();
        assertValidElement(setter, parameter, parameterName);
        holder.registerProblem(parameterName,
                               InspectionsBundle.message("inspection.nullable.problems.annotated.field.setter.parameter.not.annotated",
                                                         getPresentableAnnoName(field)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               addAnnoFix);
      }
      if (PropertyUtil.isSimpleSetter(setter)) {
        if (annotated.isDeclaredNotNull && isNullableNotInferred(parameter, false)) {
          final PsiIdentifier parameterName = parameter.getNameIdentifier();
          assertValidElement(setter, parameter, parameterName);
          holder.registerProblem(parameterName, InspectionsBundle.message(
            "inspection.nullable.problems.annotated.field.setter.parameter.conflict",
            getPresentableAnnoName(field), getPresentableAnnoName(parameter)),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 addAnnoFix);
        }
      }
    }
  }

  @Nonnull
  private static AddAnnotationPsiFix createAddAnnotationFix(String anno, List<String> annoToRemove, PsiParameter parameter) {
    return new AddAnnotationPsiFix(anno, parameter, PsiNameValuePair.EMPTY_ARRAY, ArrayUtil.toStringArray(annoToRemove));
  }

  @Contract("_,_,null -> fail")
  private static void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
    LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
    LOG.assertTrue(parameter.isPhysical(), setter.getText());
  }

  private void checkConstructorParameters(PsiField field,
                                          Annotated annotated,
                                          NullableNotNullManager manager,
                                          String anno,
                                          List<String> annoToRemove,
                                          @Nonnull ProblemsHolder holder,
                                          NullableStuffInspectionState state) {
    List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
    if (initializers.isEmpty()) {
      return;
    }

    List<PsiParameter> notNullParams = new ArrayList<>();

    boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);

    for (PsiExpression rhs : initializers) {
      if (rhs instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression)rhs).resolve();
        if (isConstructorParameter(target) && target.isPhysical()) {
          PsiParameter parameter = (PsiParameter)target;
          if (state.REPORT_NOT_ANNOTATED_GETTER && !manager.hasNullability(parameter) && !TypeConversionUtil.isPrimitiveAndNotNull(parameter
                                                                                                                                     .getType())) {
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier != null && nameIdentifier.isPhysical()) {
              holder.registerProblem(
                nameIdentifier,
                InspectionsBundle.message("inspection.nullable.problems.annotated.field.constructor.parameter.not.annotated",
                                          getPresentableAnnoName(field)),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createAddAnnotationFix(anno, annoToRemove, parameter));
              continue;
            }
          }

          if (isFinal && annotated.isDeclaredNullable && isNotNullNotInferred(parameter, false, false)) {
            notNullParams.add(parameter);
          }

        }
      }
    }

    if (notNullParams.size() != initializers.size()) {
      // it's not the case that the field is final and @Nullable and always initialized via @NotNull parameters
      // so there might be other initializers that could justify it being nullable
      // so don't highlight field and constructor parameter annotation inconsistency
      return;
    }

    PsiIdentifier nameIdentifier = field.getNameIdentifier();
    if (nameIdentifier.isPhysical()) {
      holder.registerProblem(nameIdentifier, "@" + getPresentableAnnoName(field) + " field is always initialized not-null",
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, AddAnnotationPsiFix.createAddNotNullFix(field));
    }
  }

  private static boolean isConstructorParameter(@Nullable PsiElement parameter) {
    return parameter instanceof PsiParameter && psiElement(PsiParameterList.class).withParent(psiMethod().constructor(true))
                                                                                  .accepts(parameter.getParent());
  }

  @Nonnull
  private static String getPresentableAnnoName(@Nonnull PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    String name = info == null ? null : info.getAnnotation().getQualifiedName();
    if (name == null) {
      return "???";
    }
    return StringUtil.getShortName(name);
  }

  public static String getPresentableAnnoName(@Nonnull PsiAnnotation annotation) {
    return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
  }

  private static class Annotated {
    private final boolean isDeclaredNotNull;
    private final boolean isDeclaredNullable;
    @Nullable
    private final PsiAnnotation notNull;
    @Nullable
    private final PsiAnnotation nullable;

    private Annotated(@Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
      this.isDeclaredNotNull = notNull != null;
      this.isDeclaredNullable = nullable != null;
      this.notNull = notNull;
      this.nullable = nullable;
    }

    @Nonnull
    static Annotated from(@Nonnull PsiModifierListOwner owner) {
      NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
      return new Annotated(manager.findExplicitNullabilityAnnotation(owner, Collections.singleton(Nullability.NOT_NULL)),
                           manager.findExplicitNullabilityAnnotation(owner, Collections.singleton(Nullability.NULLABLE)));
    }
  }

  private static Annotated check(final PsiModifierListOwner owner, final ProblemsHolder holder, PsiType type) {
    Annotated annotated = Annotated.from(owner);
    checkType(owner, holder, type, annotated.notNull, annotated.nullable);
    return annotated;
  }

  private static void checkType(@Nullable PsiModifierListOwner listOwner,
                                ProblemsHolder holder,
                                PsiType type,
                                @Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
    if (nullable != null && notNull != null) {
      reportNullableNotNullConflict(holder, listOwner, nullable, notNull);
    }
    if ((notNull != null || nullable != null) && type != null && TypeConversionUtil.isPrimitive(type.getCanonicalText())) {
      PsiAnnotation annotation = notNull == null ? nullable : notNull;
      reportPrimitiveType(holder, annotation, listOwner);
    }
    if (listOwner instanceof PsiParameter psiParameter) {
      checkLoopParameterNullability(holder, notNull, nullable, DfaPsiUtil.inferParameterNullability((PsiParameter)listOwner), psiParameter);
    }
  }

  private static void checkLoopParameterNullability(ProblemsHolder holder,
                                                    @Nullable PsiAnnotation notNull,
                                                    @Nullable PsiAnnotation nullable,
                                                    Nullability expectedNullability,
                                                    PsiParameter owner) {
    if (notNull != null && expectedNullability == Nullability.NULLABLE) {
      holder.registerProblem(notNull, "Parameter can be null",
                             new RemoveAnnotationQuickFix(notNull, null));
    }
    else if (nullable != null && expectedNullability == Nullability.NOT_NULL) {
      if (nullable.getContainingFile() != owner.getContainingFile()) {
        return;
      }

      holder.registerProblem(nullable, "Parameter is always not-null",
                             new RemoveAnnotationQuickFix(nullable, null));
    }
  }

  private static void reportPrimitiveType(ProblemsHolder holder, PsiAnnotation annotation,
                                          @Nullable PsiModifierListOwner listOwner) {
    holder.registerProblem(!annotation.isPhysical() && listOwner != null ? listOwner.getNavigationElement() : annotation,
                           InspectionsBundle.message("inspection.nullable.problems.primitive.type.annotation"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(annotation, listOwner));
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.nullable.problems.display.name");
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "NullableProblems";
  }

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder, boolean isOnFly,
                                           NullableStuffInspectionState state) {
    Annotated annotated = check(method, holder, method.getReturnType());

    List<PsiMethod> superMethods = ContainerUtil.map(
      method.findSuperMethodSignaturesIncludingStatic(true), MethodSignatureBackedByPsiMethod::getMethod);

    final NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());

    checkSupers(method, holder, annotated, superMethods, state);
    checkParameters(method, holder, superMethods, nullableManager, isOnFly, state);
    checkOverriders(method, holder, annotated, nullableManager, state);
  }

  private void checkSupers(PsiMethod method,
                           ProblemsHolder holder,
                           Annotated annotated,
                           List<? extends PsiMethod> superMethods,
                           NullableStuffInspectionState state) {
    for (PsiMethod superMethod : superMethods) {
      if (isNullableOverridingNotNull(annotated, superMethod, state)) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, getNullityManager(method).getNullables(), true);
        holder.registerProblem(annotation != null ? annotation : method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull",
                                                         getPresentableAnnoName(method), getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        break;
      }

      if (isNonAnnotatedOverridingNotNull(method, superMethod, state)) {
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle
                                 .message("inspection.nullable.problems.method.overrides.NotNull", getPresentableAnnoName(superMethod)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               createFixForNonAnnotatedOverridesNotNull(method, superMethod));
        break;
      }
    }
  }

  private static NullableNotNullManager getNullityManager(PsiMethod method) {
    return NullableNotNullManager.getInstance(method.getProject());
  }

  @Nullable
  private static LocalQuickFix createFixForNonAnnotatedOverridesNotNull(PsiMethod method,
                                                                        PsiMethod superMethod) {
    NullableNotNullManager nullableManager = getNullityManager(method);
    return AnnotationUtil.isAnnotatingApplicable(method, nullableManager.getDefaultNotNull())
      ? AddAnnotationPsiFix.createAddNotNullFix(method)
      : createChangeDefaultNotNullFix(nullableManager, superMethod);
  }

  private boolean isNullableOverridingNotNull(Annotated methodInfo, PsiMethod superMethod, NullableStuffInspectionState state) {
    return state.REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && methodInfo.isDeclaredNullable && isNotNullNotInferred(superMethod, true, false);
  }

  private boolean isNonAnnotatedOverridingNotNull(PsiMethod method, PsiMethod superMethod, NullableStuffInspectionState state) {
    return state.REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL &&
      !(method.getReturnType() instanceof PsiPrimitiveType) &&
      !method.isConstructor() &&
      !getNullityManager(method).hasNullability(method) &&
      isNotNullNotInferred(superMethod, true, state.IGNORE_EXTERNAL_SUPER_NOTNULL) &&
      !hasInheritableNotNull(superMethod);
  }

  private static boolean hasInheritableNotNull(PsiModifierListOwner owner) {
    return AnnotationUtil.isAnnotated(owner, "javax.annotation.constraints.NotNull", CHECK_HIERARCHY | CHECK_TYPE);
  }

  private void checkParameters(PsiMethod method,
                               ProblemsHolder holder,
                               List<? extends PsiMethod> superMethods,
                               NullableNotNullManager nullableManager,
                               boolean isOnFly, NullableStuffInspectionState state) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getType() instanceof PsiPrimitiveType) {
        continue;
      }

      List<PsiParameter> superParameters = new ArrayList<>();
      for (PsiMethod superMethod : superMethods) {
        PsiParameter[] _superParameters = superMethod.getParameterList().getParameters();
        if (_superParameters.length == parameters.length) {
          superParameters.add(_superParameters[i]);
        }
      }

      PsiParameter nullableSuper = findNullableSuperForNotNullParameter(parameter, superParameters, state);
      if (nullableSuper != null) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, nullableManager.getNotNulls(), true);
        holder.registerProblem(annotation != null ? annotation : parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
                                                         getPresentableAnnoName(parameter),
                                                         getPresentableAnnoName(nullableSuper)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      PsiParameter notNullSuper = findNotNullSuperForNonAnnotatedParameter(nullableManager, parameter, superParameters, state);
      if (notNullSuper != null) {
        LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, nullableManager.getDefaultNotNull())
          ? AddAnnotationPsiFix.createAddNotNullFix(parameter)
          : createChangeDefaultNotNullFix(nullableManager, notNullSuper);
        holder.registerProblem(parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull",
                                                         getPresentableAnnoName(notNullSuper)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }
      if (isNotNullParameterOverridingNonAnnotated(nullableManager, parameter, superParameters, state)) {
        NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
        assert info != null;
        PsiAnnotation notNullAnnotation = info.getAnnotation();
        boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
        final LocalQuickFix fix = physical ? new RemoveAnnotationQuickFix(notNullAnnotation, parameter) : null;
        holder.registerProblem(physical ? notNullAnnotation : parameter.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.not.annotated",
                                                         getPresentableAnnoName(parameter)),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }

      checkNullLiteralArgumentOfNotNullParameterUsages(method, holder, nullableManager, isOnFly, i, parameter, state);
    }
  }

  @Nullable
  private PsiParameter findNotNullSuperForNonAnnotatedParameter(NullableNotNullManager nullableManager,
                                                                PsiParameter parameter,
                                                                List<? extends PsiParameter> superParameters,
                                                                NullableStuffInspectionState state) {
    return state.REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !nullableManager.hasNullability(parameter)
      ? ContainerUtil.find(superParameters,
                           sp -> isNotNullNotInferred(sp, false, state.IGNORE_EXTERNAL_SUPER_NOTNULL) && !hasInheritableNotNull(sp))
      : null;
  }

  @Nullable
  private PsiParameter findNullableSuperForNotNullParameter(PsiParameter parameter, List<? extends PsiParameter> superParameters,
                                                            NullableStuffInspectionState state) {
    return state.REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE && isNotNullNotInferred(parameter, false, false)
      ? ContainerUtil.find(superParameters, sp -> isNullableNotInferred(sp, false))
      : null;
  }

  private boolean isNotNullParameterOverridingNonAnnotated(NullableNotNullManager nullableManager,
                                                           PsiParameter parameter,
                                                           List<? extends PsiParameter> superParameters,
                                                           NullableStuffInspectionState state) {
    if (!state.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED) {
      return false;
    }
    NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
    return info != null && info.getNullability() == Nullability.NOT_NULL && !info.isInferred() &&
      ContainerUtil.exists(superParameters, sp -> !nullableManager.hasNullability(sp));
  }

  private void checkNullLiteralArgumentOfNotNullParameterUsages(PsiMethod method,
                                                                ProblemsHolder holder,
                                                                NullableNotNullManager nullableManager,
                                                                boolean isOnFly,
                                                                int parameterIdx,
                                                                PsiParameter parameter,
                                                                NullableStuffInspectionState state) {
    if (!state.REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER || !isOnFly) {
      return;
    }

    PsiElement elementToHighlight;
    if (DfaPsiUtil.getTypeNullability(getMemberType(parameter)) == Nullability.NOT_NULL) {
      elementToHighlight = parameter.getNameIdentifier();
    }
    else {
      NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameter);
      if (info == null || info.getNullability() != Nullability.NOT_NULL || info.isInferred()) {
        return;
      }
      PsiAnnotation notNullAnnotation = info.getAnnotation();
      boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
      elementToHighlight = physical ? notNullAnnotation : parameter.getNameIdentifier();
    }
    if (elementToHighlight == null || !JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) {
      return;
    }

    holder.registerProblem(elementToHighlight,
                           InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.receives.null.literal",
                                                     getPresentableAnnoName(parameter)),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           createNavigateToNullParameterUsagesFix(parameter));
  }

  private void checkOverriders(@Nonnull PsiMethod method,
                               @Nonnull ProblemsHolder holder,
                               @Nonnull Annotated annotated,
                               @Nonnull NullableNotNullManager nullableManager,
                               NullableStuffInspectionState state) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (state.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] checkParameter = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        checkParameter[i] = isNotNullNotInferred(parameter, false, false) &&
          !hasInheritableNotNull(parameter) &&
          !(parameter.getType() instanceof PsiPrimitiveType);
        hasAnnotatedParameter |= checkParameter[i];
      }
      boolean checkReturnType =
        annotated.isDeclaredNotNull && !hasInheritableNotNull(method) && !(method.getReturnType() instanceof PsiPrimitiveType);
      if (hasAnnotatedParameter || checkReturnType) {
        final String defaultNotNull = nullableManager.getDefaultNotNull();
        final boolean superMethodApplicable = AnnotationUtil.isAnnotatingApplicable(method, defaultNotNull);
        PsiMethod[] overridings =
          OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (shouldSkipOverriderAsGenerated(overriding)) {
            continue;
          }

          if (!methodQuickFixSuggested
            && checkReturnType
            && !isNotNullNotInferred(overriding, false, false)
            && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))
            && AddAnnotationPsiFix.isAvailable(overriding, defaultNotNull)) {
            PsiIdentifier identifier = method.getNameIdentifier();//load tree
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, nullableManager.getNotNulls());
            final String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());

            LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(overriding, defaultNotNull)
              ? new MyAnnotateMethodFix(defaultNotNull, annotationsToRemove)
              : superMethodApplicable ? null : createChangeDefaultNotNullFix(nullableManager, method);

            PsiElement psiElement = annotation;
            if (annotation != null && !annotation.isPhysical()) {
              psiElement = identifier;
            }
            if (psiElement == null) continue;
            holder.registerProblem(psiElement, InspectionsBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   fix);
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) {
                continue;
              }
              PsiParameter parameter = psiParameters[i];
              if (checkParameter[i] &&
                !isNotNullNotInferred(parameter, false, false) &&
                !isNullableNotInferred(parameter, false) &&
                AddAnnotationPsiFix.isAvailable(parameter, defaultNotNull)) {
                PsiIdentifier identifier = parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameters[i], nullableManager.getNotNulls());
                PsiElement psiElement = annotation;
                if (annotation == null || !annotation.isPhysical()) {
                  psiElement = identifier;
                  if (psiElement == null) {
                    continue;
                  }
                }
                LocalQuickFix fix = AnnotationUtil.isAnnotatingApplicable(parameter, defaultNotNull)
                  ? new AnnotateOverriddenMethodParameterFix(defaultNotNull, nullableManager.getDefaultNullable())
                  : createChangeDefaultNotNullFix(nullableManager, parameters[i]);
                holder.registerProblem(psiElement,
                                       InspectionsBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       fix);
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  public static boolean shouldSkipOverriderAsGenerated(PsiMethod overriding) {
    if (Registry.is("idea.report.nullity.missing.in.generated.overriders", true)) {
      return false;
    }

    PsiFile file = overriding.getContainingFile();
    VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
    return virtualFile != null && GeneratedSourcesFilter.isGenerated(overriding.getProject(), virtualFile);
  }

  private static boolean isNotNullNotInferred(@Nonnull PsiModifierListOwner owner, boolean checkBases, boolean skipExternal) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info == null || info.isInferred() || info.getNullability() != Nullability.NOT_NULL) return false;
    if (!checkBases && info.getInheritedFrom() != null) return false;
    if (skipExternal && info.isExternal()) return false;
    return true;
  }

  public static boolean isNullableNotInferred(@Nonnull PsiModifierListOwner owner, boolean checkBases) {
    Project project = owner.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    return info != null && !info.isInferred() && info.getNullability() == Nullability.NULLABLE &&
      (checkBases || info.getInheritedFrom() == null);
  }

  private static PsiType getMemberType(@Nonnull PsiModifierListOwner owner) {
    return owner instanceof PsiMethod ? ((PsiMethod)owner).getReturnType() : owner instanceof PsiVariable ? ((PsiVariable)owner).getType() : null;
  }

  private static LocalQuickFix createChangeDefaultNotNullFix(NullableNotNullManager nullableManager,
                                                             PsiModifierListOwner modifierListOwner) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, nullableManager.getNotNulls());
    if (annotation != null) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        JavaResolveResult resolveResult = referenceElement.advancedResolve(false);
        if (resolveResult.getElement() != null &&
          resolveResult.isValidResult() &&
          !nullableManager.getDefaultNotNull().equals(annotation.getQualifiedName())) {
          return new ChangeNullableDefaultsFix(annotation.getQualifiedName(), null, nullableManager);
        }
      }
    }
    return null;
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder,
                                                    final PsiModifierListOwner listOwner,
                                                    final PsiAnnotation declaredNullable,
                                                    final PsiAnnotation declaredNotNull) {
    final String bothNullableNotNullMessage = InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict",
                                                                        getPresentableAnnoName(declaredNullable),
                                                                        getPresentableAnnoName(declaredNotNull));
    holder.registerProblem(declaredNotNull.isPhysical() ? declaredNotNull : listOwner.getNavigationElement(),
                           bothNullableNotNullMessage,
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNotNull, listOwner));
    holder.registerProblem(declaredNullable.isPhysical() ? declaredNullable : listOwner.getNavigationElement(),
                           bothNullableNotNullMessage,
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveAnnotationQuickFix(declaredNullable, listOwner));
  }

  @Nonnull
  @Override
  public NullableStuffInspectionState createStateProvider() {
    return new NullableStuffInspectionState();
  }

  private static class MyAnnotateMethodFix extends AnnotateMethodFix {
    MyAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
      super(defaultNotNull, annotationsToRemove);
    }

    @Nonnull
    @Override
    protected String getPreposition() {
      return "as";
    }

    @Override
    protected boolean annotateOverriddenMethods() {
      return true;
    }

    @Override
    protected boolean annotateSelf() {
      return false;
    }
  }
}
