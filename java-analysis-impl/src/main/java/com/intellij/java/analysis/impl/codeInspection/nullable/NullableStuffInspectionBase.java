// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.MoveAnnotationOnStaticMemberQualifyingTypeFix;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.impl.codeInsight.intention.AddTypeAnnotationFix;
import com.intellij.java.analysis.impl.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.analysis.impl.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.java.indexing.impl.search.JavaOverridingMethodsSearcher;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.codeInsight.*;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.JavaTypeNullabilityUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.registry.Registry;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.content.GeneratedSourcesFilter;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.java.language.codeInsight.AnnotationUtil.*;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiMethod;
import static consulo.util.lang.ObjectUtil.tryCast;

public abstract class NullableStuffInspectionBase extends AbstractBaseJavaLocalInspectionTool<NullableStuffInspectionState> {
    private static Logger LOG = Logger.getInstance(NullableStuffInspectionBase.class);

    @Override
    @RequiredReadAction
    public PsiElementVisitor buildVisitorImpl(
        final ProblemsHolder holder,
        final boolean isOnTheFly,
        LocalInspectionToolSession session,
        NullableStuffInspectionState state
    ) {
        PsiFile file = holder.getFile();
        if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, file) || nullabilityAnnotationsNotAvailable(file)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            private NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
            private List<String> nullables = manager.getNullables();
            private List<String> notNulls = manager.getNotNulls();

            @Override
            @RequiredReadAction
            public void visitMethod(PsiMethod method) {
                checkNullableStuffForMethod(method, holder, state);
            }

            @Override
            public void visitClass(PsiClass aClass) {
                if (aClass.isRecord()) {
                    PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
                    if (constructor instanceof SyntheticElement) {
                        checkParameters(constructor, holder, List.of(), manager, state);
                    }
                }
                checkConflictingContainerAnnotations(holder, aClass.getModifierList());
            }

            @Override
            public void visitPackageStatement(PsiPackageStatement statement) {
                checkConflictingContainerAnnotations(holder, statement.getAnnotationList());
            }

            @Override
            public void visitModule(PsiJavaModule module) {
                checkConflictingContainerAnnotations(holder, module.getModifierList());
            }

            @Override
            @RequiredReadAction
            public void visitNewExpression(PsiNewExpression expression) {
                if (expression.isArrayCreation()) {
                    return;
                }
                super.visitNewExpression(expression);
                PsiJavaCodeReferenceElement ref = expression.getClassOrAnonymousClassReference();
                if (ref == null) {
                    return;
                }
                if (!(ref.resolve() instanceof PsiClass cls)) {
                    return;
                }
                String qualifiedName = cls.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                PsiExpressionList list = expression.getArgumentList();
                if (list == null) {
                    return;
                }
                if (!(expression.getType() instanceof PsiClassType type) || type.getParameterCount() != 1) {
                    return;
                }
                PsiType typeParameter = type.getParameters()[0];
                if (!(typeParameter instanceof PsiClassType classType) ||
                    DfaPsiUtil.getTypeNullability(typeParameter) != Nullability.NOT_NULL) {
                    return;
                }
                boolean matched = switch (qualifiedName) {
                    case "java.util.concurrent.atomic.AtomicReference" -> list.getExpressionCount() == 0;
                    case "java.util.concurrent.atomic.AtomicReferenceArray" ->
                        list.getExpressionCount() == 1 && PsiTypes.intType().equals(list.getExpressionTypes()[0]);
                    case "java.lang.ThreadLocal" -> list.getExpressionCount() == 0 && expression.getAnonymousClass() == null;
                    default -> false;
                };
                if (!matched) {
                    return;
                }

                AddTypeAnnotationFix fix = null;
                if (classType.getPsiContext() instanceof PsiJavaCodeReferenceElement typeRef
                    && typeRef.getParent() instanceof PsiTypeElement typeElement
                    && typeElement.getType().equals(classType)
                    && typeElement.acceptsAnnotations()) {
                    fix = new AddTypeAnnotationFix(typeElement, manager.getDefaultAnnotation(Nullability.NULLABLE, expression), notNulls);
                }
                holder.newProblem(JavaAnalysisLocalize.inspectionNullableProblemsConstructorNotCompatibleNonNullTypeArgument())
                    .range(expression)
                    .withOptionalFix(fix)
                    .create();
            }

            @Override
            @RequiredReadAction
            public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                checkMethodReference(expression, holder, state);

                JavaResolveResult result = expression.advancedResolve(false);
                if (result.getElement() instanceof PsiMethod method) {
                    checkNestedGenericClasses(
                        holder,
                        expression,
                        LambdaUtil.getFunctionalInterfaceReturnType(expression),
                        result.getSubstitutor().substitute(method.getReturnType()),
                        ConflictNestedTypeProblem.ASSIGNMENT_NESTED_TYPE_PROBLEM,
                        state
                    );
                }
            }

            @Override
            @RequiredReadAction
            public void visitField(PsiField field) {
                PsiType type = field.getType();
                Annotated annotated = check(field, holder, type);
                if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
                    return;
                }
                Project project = holder.getProject();
                if (annotated.isDeclaredNotNull ^ annotated.isDeclaredNullable) {
                    String anno =
                        manager.getDefaultAnnotation(annotated.isDeclaredNotNull ? Nullability.NOT_NULL : Nullability.NULLABLE, field);
                    List<String> annoToRemove = annotated.isDeclaredNotNull ? nullables : notNulls;

                    checkAccessors(field, annotated, project, manager, anno, annoToRemove, holder, state);

                    checkConstructorParameters(field, annotated, anno, annoToRemove, holder, state);
                }
                PsiExpression initializer = field.getInitializer();
                PsiElement identifyingElement = field.getIdentifyingElement();
                if (initializer != null && identifyingElement != null) {
                    checkNestedGenericClasses(holder, identifyingElement, field.getType(), initializer.getType(),
                        ConflictNestedTypeProblem.ASSIGNMENT_NESTED_TYPE_PROBLEM, state
                    );
                }
            }

            @Override
            @RequiredReadAction
            public void visitMethodCallExpression(PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                PsiReferenceParameterList parameterList = call.getMethodExpression().getParameterList();
                if (parameterList == null) {
                    return;
                }
                PsiType[] parameterization = parameterList.getTypeArguments();
                if (parameterization.length == 0) {
                    return;
                }
                PsiMethod method = call.resolveMethod();
                if (method == null) {
                    return;
                }
                PsiTypeParameter[] typeParameters = method.getTypeParameters();
                if (typeParameters.length != parameterization.length) {
                    return;
                }
                for (int i = 0; i < typeParameters.length; i++) {
                    PsiTypeParameter typeParameter = typeParameters[i];
                    PsiType instance = parameterization[i];
                    TypeNullability nullability = TypeNullability.ofTypeParameter(typeParameter);
                    if (nullability.nullability() != Nullability.NOT_NULL) {
                        continue;
                    }
                    TypeNullability instanceNullability = instance.getNullability();
                    if (instanceNullability.nullability() == Nullability.NOT_NULL) {
                        continue;
                    }
                    NullabilitySource source = instanceNullability.source();
                    if (source instanceof NullabilitySource.ExplicitAnnotation explicit) {
                        PsiAnnotation anchor = explicit.annotation();
                        PsiJavaCodeReferenceElement ref = anchor.getNameReferenceElement();
                        if (ref != null) {
                            reportProblem(
                                holder,
                                anchor,
                                JavaAnalysisLocalize.inspectionNullableProblemsNullableInstantiationOfNotnull(
                                    typeParameter.getName(),
                                    ref.getReferenceName()
                                )
                            );
                        }
                    }
                    else if (source instanceof NullabilitySource.ContainerAnnotation container) {
                        PsiElement anchor = parameterList.getTypeParameterElements()[i];
                        PsiJavaCodeReferenceElement ref = container.annotation().getNameReferenceElement();
                        if (ref != null) {
                            reportProblem(
                                holder,
                                anchor,
                                JavaAnalysisLocalize.inspectionNullableProblemsNullableInstantiationOfNotnullContainer(
                                    typeParameter.getName(),
                                    ref.getReferenceName()
                                )
                            );
                        }
                    }
                }
            }

            @Override
            @RequiredReadAction
            public void visitParameter(PsiParameter parameter) {
                check(parameter, holder, parameter.getType());
            }

            @Override
            @RequiredReadAction
            public void visitAnnotation(PsiAnnotation annotation) {
                NullabilityAnnotationWrapper wrapper = NullabilityAnnotationWrapper.from(annotation);
                if (wrapper == null) {
                    return;
                }
                PsiType targetType = wrapper.targetType();
                PsiType type = wrapper.type();
                PsiModifierListOwner listOwner = wrapper.listOwner();
                checkRedundantInContainerScope(wrapper, state);
                if (type != null
                    && wrapper.nullability() == Nullability.NOT_NULL
                    && PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter) {
                    PsiType notAnnotated = type.annotate(TypeAnnotationProvider.EMPTY);
                    TypeNullability notAnnotatedNullability = notAnnotated.getNullability();
                    if (notAnnotatedNullability.nullability() == Nullability.NOT_NULL
                        && notAnnotatedNullability.source() instanceof NullabilitySource.ExtendsBound) {
                        reportProblem(
                            holder,
                            annotation,
                            new RemoveAnnotationQuickFix(annotation, null),
                            JavaAnalysisLocalize.inspectionNullableProblemsRedundantAnnotationInheritedNotnull()
                        );
                    }
                }
                if (type instanceof PsiPrimitiveType) {
                    LocalQuickFix additionalFix = null;
                    if (targetType instanceof PsiArrayType && !targetType.hasAnnotations()) {
                        additionalFix = new MoveAnnotationToArrayFix();
                    }
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsPrimitiveTypeAnnotation(),
                        additionalFix == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{additionalFix}
                    );
                }
                if (type instanceof PsiClassType classType) {
                    PsiElement context = classType.getPsiContext();
                    // outer type/package
                    if (context instanceof PsiJavaCodeReferenceElement outerCtx) {
                        PsiElement parent = context.getParent();
                        if (parent instanceof PsiJavaCodeReferenceElement) {
                            if (outerCtx.resolve() instanceof PsiPackage) {
                                reportIncorrectLocation(
                                    holder,
                                    annotation,
                                    listOwner,
                                    JavaAnalysisLocalize.inspectionNullableProblemsAppliedToPackage(),
                                    new MoveAnnotationOnStaticMemberQualifyingTypeFix()
                                );
                            }
                            else {
                                // If outer is qualifier of static member then don't report problem as it is already reported
                                // as ANNOTATION_NOT_ALLOWED_STATIC which contains exactly the same fix "Move annotation".
                                if (!PsiImplUtil.isTypeQualifierOfStaticMember(outerCtx)) {
                                    reportIncorrectLocation(
                                        holder,
                                        annotation,
                                        listOwner,
                                        JavaAnalysisLocalize.inspectionNullableProblemsOuterType(),
                                        new MoveAnnotationOnStaticMemberQualifyingTypeFix()
                                    );
                                }
                            }
                        }
                        if (parent instanceof PsiReferenceList) {
                            PsiElement firstChild = parent.getFirstChild();
                            if ((PsiUtil.isJavaToken(firstChild, JavaTokenType.EXTENDS_KEYWORD) ||
                                PsiUtil.isJavaToken(firstChild, JavaTokenType.IMPLEMENTS_KEYWORD))
                                && !(parent.getParent() instanceof PsiTypeParameter)) {
                                reportIncorrectLocation(holder, annotation, listOwner, JavaAnalysisLocalize.inspectionNullableProblemsAtReferenceList());
                            }
                            if (PsiUtil.isJavaToken(firstChild, JavaTokenType.THROWS_KEYWORD)) {
                                reportIncorrectLocation(holder, annotation, listOwner, JavaAnalysisLocalize.inspectionNullableProblemsAtThrows());
                            }
                        }
                    }
                }
                if (type instanceof PsiArrayType
                    && annotation.getParent() instanceof PsiTypeElement parent
                    && parent.getType().equals(type)
                    && !manager.canAnnotateLocals(wrapper.qualifiedName())) {
                    checkIllegalLocalAnnotation(annotation, parent.getParent(), state);
                }
                if (listOwner instanceof PsiMethod method && method.isConstructor()) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtConstructor()
                    );
                }
                if (listOwner instanceof PsiClass
                    && AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE) == null) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtClass()
                    );
                }
                if (listOwner instanceof PsiEnumConstant) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtEnumConstant()
                    );
                }
                if (!manager.canAnnotateLocals(wrapper.qualifiedName()) && !(targetType instanceof PsiArrayType)) {
                    checkIllegalLocalAnnotation(annotation, listOwner, state);
                }
                if (type instanceof PsiWildcardType && manager.isTypeUseAnnotationLocationRestricted(wrapper.qualifiedName())) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtWildcard()
                    );
                }
                if (wrapper.owner() instanceof PsiTypeParameter && manager.isTypeUseAnnotationLocationRestricted(wrapper.qualifiedName())) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtTypeParameter()
                    );
                }
                if (listOwner instanceof PsiReceiverParameter && wrapper.nullability() != Nullability.NOT_NULL) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        listOwner,
                        JavaAnalysisLocalize.inspectionNullableProblemsReceiverAnnotation()
                    );
                }
                checkOppositeAnnotationConflict(annotation, wrapper.nullability());
                if (NOT_NULL.equals(wrapper.qualifiedName())) {
                    PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("exception");
                    if (value instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
                        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression.getOperand().getType());
                        if (psiClass != null && !hasStringConstructor(psiClass)) {
                            reportProblem(holder, value, JavaAnalysisLocalize.customExceptionClassShouldHaveAConstructor());
                        }
                    }
                }
            }

            private void checkRedundantInContainerScope(NullabilityAnnotationWrapper wrapper, NullableStuffInspectionState state) {
                if (state.REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER) {
                    NullabilityAnnotationInfo containerInfo = wrapper.findContainerInfoForRedundantAnnotation();
                    if (containerInfo != null) {
                        if (containerInfo.getNullability() == Nullability.NOT_NULL && manager.isNonNullUsedForInstrumentation(wrapper.annotation())) {
                            return;
                        }
                        reportRedundantInContainerScope(wrapper.annotation(), containerInfo);
                    }
                }
            }

            private void reportRedundantInContainerScope(PsiAnnotation annotation, NullabilityAnnotationInfo containerInfo) {
                PsiJavaCodeReferenceElement containerName = containerInfo.getAnnotation().getNameReferenceElement();
                if (containerName != null) {
                    LocalQuickFix updateOptionFix = new UpdateInspectionOptionFix<>(
                        NullableStuffInspectionBase.this,
                        JavaAnalysisLocalize.inspectionNullableProblemsTurnOffRedundantAnnotationUnderContainer(),
                        (NullableStuffInspectionState state) ->
                            state.REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER = false
                    );
                    reportProblem(holder,
                        annotation,
                        new LocalQuickFix[]{new RemoveAnnotationQuickFix(annotation, null), updateOptionFix},
                        JavaAnalysisLocalize.inspectionNullableProblemsRedundantAnnotationUnderContainer(containerName.getReferenceName())
                    );
                }
            }

            private void checkIllegalLocalAnnotation(
                PsiAnnotation annotation,
                @Nullable PsiElement owner,
                NullableStuffInspectionState state
            ) {
                if (!state.REPORT_NULLABILITY_ANNOTATION_ON_LOCALS) {
                    return;
                }
                if (owner instanceof PsiLocalVariable ||
                    owner instanceof PsiParameter parameter
                        && parameter.getDeclarationScope() instanceof PsiCatchSection) {
                    reportIncorrectLocation(
                        holder,
                        annotation,
                        (PsiVariable) owner,
                        JavaAnalysisLocalize.inspectionNullableProblemsAtLocalVariable()
                    );
                }
            }

            private void checkOppositeAnnotationConflict(PsiAnnotation annotation, Nullability nullability) {
                PsiAnnotationOwner owner = annotation.getOwner();
                if (owner == null) {
                    return;
                }
                PsiModifierListOwner listOwner = owner instanceof PsiModifierList modifierList
                    ? tryCast(modifierList.getParent(), PsiModifierListOwner.class)
                    : null;
                Predicate<PsiAnnotation> filter = anno ->
                    anno != annotation && manager.getAnnotationNullability(anno.getQualifiedName())
                        .filter(n -> n != nullability)
                        .isPresent();
                PsiAnnotation oppositeAnno = ContainerUtil.find(owner.getAnnotations(), filter);
                if (oppositeAnno == null && listOwner != null) {
                    NullabilityAnnotationInfo result = manager.findNullabilityAnnotationInfo(
                        listOwner, ContainerUtil.filter(Nullability.values(), n -> n != nullability));
                    oppositeAnno = result == null || result.isContainer() ? null : result.getAnnotation();
                }
                if (oppositeAnno != null &&
                    Objects.equals(getRelatedType(annotation), getRelatedType(oppositeAnno))) {
                    reportProblem(
                        holder,
                        annotation,
                        new RemoveAnnotationQuickFix(annotation, listOwner),
                        JavaAnalysisLocalize.inspectionNullableProblemsNullableNotnullConflict(
                            getPresentableAnnoName(annotation),
                            getPresentableAnnoName(oppositeAnno)
                        )
                    );
                }
            }

            private static boolean hasStringConstructor(PsiClass aClass) {
                for (PsiMethod method : aClass.getConstructors()) {
                    PsiParameterList list = method.getParameterList();
                    if (list.getParametersCount() == 1 &&
                        Objects.requireNonNull(list.getParameter(0)).getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            @RequiredReadAction
            public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);

                checkNullableNotNullInstantiationConflict(reference);

                PsiElement list = reference.getParent();
                PsiElement parent = list instanceof PsiReferenceList ? list.getParent() : null;
                if (parent instanceof PsiClass psiClass
                    && list == psiClass.getImplementsList()
                    && reference.resolve() instanceof PsiClass intf
                    && intf.isInterface()) {
                    LocalizeValue error = checkIndirectInheritance(psiClass, intf, state);
                    if (error != null && error.isNotEmpty()) {
                        holder.newProblem(error).range(reference).create();
                    }
                }
            }

            @RequiredReadAction
            private void checkNullableNotNullInstantiationConflict(PsiJavaCodeReferenceElement reference) {
                if (reference.resolve() instanceof PsiClass psiClass) {
                    PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
                    PsiTypeElement[] typeArguments = getReferenceTypeArguments(reference);
                    if (typeParameters.length > 0 && typeParameters.length == typeArguments.length && !(typeArguments[0].getType() instanceof PsiDiamondType)) {
                        for (int i = 0; i < typeParameters.length; i++) {
                            PsiTypeElement typeArgument = typeArguments[i];
                            Project project = psiClass.getProject();
                            PsiType type = typeArgument.getType();
                            if (TypeNullability.ofTypeParameter(typeParameters[i]).nullability() != Nullability.NOT_NULL) {
                                continue;
                            }
                            TypeNullability nullability = type.getNullability();
                            Nullability typeNullability = nullability.nullability();
                            if (typeNullability != Nullability.NOT_NULL &&
                                !(typeNullability == Nullability.UNKNOWN && type instanceof PsiWildcardType wildcardType && !wildcardType.isExtends())) {
                                String annotationToAdd = manager.getDefaultAnnotation(Nullability.NOT_NULL, reference);
                                PsiClass annotationClass =
                                    JavaPsiFacade.getInstance(project).findClass(annotationToAdd, psiClass.getResolveScope());
                                List<LocalQuickFix> fixes = new ArrayList<>();
                                if (annotationClass != null &&
                                    AnnotationTargetUtil.findAnnotationTarget(annotationClass, PsiAnnotation.TargetType.TYPE_USE) != null) {
                                    fixes.add(new AddTypeAnnotationFix(
                                        typeArgument,
                                        annotationToAdd,
                                        manager.getNullables()
                                    ));
                                }
                                ProblemHighlightType level =
                                    nullability == TypeNullability.UNKNOWN && !state.REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE
                                        ? ProblemHighlightType.INFORMATION
                                        : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
                                if (!isOnTheFly && level == ProblemHighlightType.INFORMATION) {
                                    continue;
                                }
                                holder.newProblem(JavaAnalysisLocalize.nonNullTypeArgumentIsExpected())
                                    .range(typeArgument)
                                    .withFixes(fixes)
                                    .highlightType(level)
                                    .create();
                            }
                        }
                    }
                }
            }

            private static PsiTypeElement[] getReferenceTypeArguments(PsiJavaCodeReferenceElement reference) {
                PsiReferenceParameterList typeArgList = reference.getParameterList();
                return typeArgList == null ? PsiTypeElement.EMPTY_ARRAY : typeArgList.getTypeParameterElements();
            }

            @Override
            public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                PsiExpression rExpression = expression.getRExpression();
                if (rExpression == null) {
                    return;
                }
                checkNestedGenericClasses(holder, expression.getOperationSign(),
                    expression.getLExpression().getType(),
                    rExpression.getType(),
                    ConflictNestedTypeProblem.ASSIGNMENT_NESTED_TYPE_PROBLEM, state
                );
            }

            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
                PsiIdentifier identifier = variable.getNameIdentifier();
                if (identifier == null) {
                    return;
                }
                PsiExpression initializer = variable.getInitializer();
                if (initializer == null) {
                    return;
                }
                checkNestedGenericClasses(holder, identifier, variable.getType(), initializer.getType(),
                    ConflictNestedTypeProblem.ASSIGNMENT_NESTED_TYPE_PROBLEM, state
                );
            }

            @Override
            public void visitReturnStatement(PsiReturnStatement statement) {
                PsiExpression returnValue = statement.getReturnValue();
                if (returnValue == null) {
                    return;
                }
                checkNestedGenericClasses(holder, returnValue,
                    PsiTypesUtil.getMethodReturnType(statement), returnValue.getType(),
                    ConflictNestedTypeProblem.RETURN_NESTED_TYPE_PROBLEM, state
                );
            }

            @Override
            public void visitLambdaExpression(PsiLambdaExpression lambda) {
                super.visitLambdaExpression(lambda);
                PsiElement body = lambda.getBody();
                if (body instanceof PsiExpression psiExpression) {
                    checkNestedGenericClasses(
                        holder,
                        body,
                        LambdaUtil.getFunctionalInterfaceReturnType(lambda),
                        psiExpression.getType(),
                        ConflictNestedTypeProblem.RETURN_NESTED_TYPE_PROBLEM,
                        state
                    );
                }
            }

            @Override
            public void visitCallExpression(PsiCallExpression callExpression) {
                PsiExpressionList argList = callExpression.getArgumentList();
                JavaResolveResult result = callExpression.resolveMethodGenerics();
                PsiMethod method = (PsiMethod) result.getElement();
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
                        PsiType expectedType = substitutor.substitute(parameters[i].getType());
                        checkNestedGenericClasses(holder, argument, expectedType, argument.getType(),
                            ConflictNestedTypeProblem.ASSIGNMENT_NESTED_TYPE_PROBLEM, state
                        );
                    }
                }
            }
        };
    }

    private boolean checkNestedGenericClasses(
        ProblemsHolder holder,
        PsiElement errorElement,
        @Nullable PsiType expectedType,
        @Nullable PsiType actualType,
        ConflictNestedTypeProblem problem,
        NullableStuffInspectionState state
    ) {
        if (expectedType == null || actualType == null) {
            return false;
        }
        JavaTypeNullabilityUtil.NullabilityConflictContext context = JavaTypeNullabilityUtil.getNullabilityConflictInAssignment(
            expectedType,
            actualType,
            state.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS
        );
        JavaTypeNullabilityUtil.NullabilityConflict conflict = context.nullabilityConflict();

        if (conflict == JavaTypeNullabilityUtil.NullabilityConflict.UNKNOWN) {
            return false;
        }

        Function<Object, LocalizeValue> messageKey = switch (conflict) {
            case NOT_NULL_TO_NULL -> (Function<Object, LocalizeValue>) problem::notNullToNullProblem;
            case NULL_TO_NOT_NULL -> (Function<Object, LocalizeValue>) problem::nullToNotNullProblem;
            default -> (Function<Object, LocalizeValue>) problem::complexProblem;
        };

        reportProblem(
            holder,
            errorElement,
            LocalQuickFix.EMPTY_ARRAY,
            messageKey.apply(""),
            messageKey.apply(NullableStuffInspectionUtil.getNullabilityConflictPresentation(context))
        );
        return true;
    }

    private void checkConflictingContainerAnnotations(ProblemsHolder holder, @Nullable PsiModifierList list) {
        if (list == null || list.getAnnotations().length == 0) {
            return;
        }
        NullableNotNullManager manager = NullableNotNullManager.getInstance(holder.getProject());
        List<PsiAnnotation> conflictingAnnotations = manager.getConflictingContainerAnnotations(list);
        if (!conflictingAnnotations.isEmpty()) {
            for (PsiAnnotation annotation : conflictingAnnotations) {
                reportProblem(holder, annotation, JavaAnalysisLocalize.conflictingNullabilityAnnotations());
            }
        }
    }

    private void reportProblem(ProblemsHolder holder, PsiElement anchor, LocalizeValue message) {
        reportProblem(holder, anchor, LocalQuickFix.EMPTY_ARRAY, message);
    }

    private void reportProblem(
        ProblemsHolder holder,
        PsiElement anchor,
        @Nullable LocalQuickFix fix,
        LocalizeValue message
    ) {
        reportProblem(holder, anchor, fix == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{fix}, message);
    }

    protected void reportProblem(
        ProblemsHolder holder,
        PsiElement anchor,
        LocalQuickFix[] fixes,
        LocalizeValue message
    ) {
        reportProblem(holder, anchor, fixes, message, message);
    }

    @Override
    public InspectionToolState<? extends NullableStuffInspectionState> createStateProvider() {
        return new NullableStuffInspectionState();
    }

    protected void reportProblem(
        ProblemsHolder holder,
        PsiElement anchor,
        LocalQuickFix[] fixes,
        LocalizeValue description,
        LocalizeValue tooltip
    ) {
        ProblemBuilder builder = holder.newProblem(description)
            .range(anchor)
            .highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        for (LocalQuickFix quickFix : fixes) {
            builder.withFix(quickFix);
        }
        builder.create();
    }

    @RequiredReadAction
    private LocalizeValue checkIndirectInheritance(PsiClass psiClass, PsiClass intf, NullableStuffInspectionState state) {
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(intf, psiClass, PsiSubstitutor.EMPTY);
        for (PsiMethod intfMethod : intf.getAllMethods()) {
            PsiClass intfMethodClass = intfMethod.getContainingClass();
            PsiMethod overridingMethod = intfMethodClass == null ? null :
                JavaOverridingMethodsSearcher.findOverridingMethod(psiClass, intfMethod, intfMethodClass);
            PsiClass overridingMethodClass = overridingMethod == null ? null : overridingMethod.getContainingClass();
            if (overridingMethodClass != null && overridingMethodClass != psiClass) {
                LocalizeValue error =
                    checkIndirectInheritance(intfMethod, intfMethodClass, overridingMethod, overridingMethodClass, substitutor, state);
                if (error.isNotEmpty()) {
                    return error;
                }
            }
        }
        return LocalizeValue.empty();
    }

    @RequiredReadAction
    private LocalizeValue checkIndirectInheritance(
        PsiMethod intfMethod,
        PsiClass intfMethodClass,
        PsiMethod overridingMethod,
        PsiClass overridingMethodClass,
        PsiSubstitutor substitutor,
        NullableStuffInspectionState state
    ) {
        if (isNullableOverridingNotNull(Annotated.from(overridingMethod), intfMethod, state)) {
            return JavaAnalysisLocalize.inspectionMessageNullableMethodImplementsNonNullMethod(
                overridingMethod.getName(),
                overridingMethodClass.getName(),
                intfMethodClass.getName()
            );
        }
        if (isNonAnnotatedOverridingNotNull(overridingMethod, intfMethod, state)) {
            return JavaAnalysisLocalize.inspectionMessageNonAnnotatedMethodImplementsNonNullMethod(
                overridingMethod.getName(),
                overridingMethodClass.getName(),
                intfMethodClass.getName()
            );
        }

        PsiParameter[] overridingParameters = overridingMethod.getParameterList().getParameters();
        PsiParameter[] superParameters = intfMethod.getParameterList().getParameters();
        if (overridingParameters.length == superParameters.length) {
            NullableNotNullManager manager = getNullityManager(intfMethod);
            for (int i = 0; i < overridingParameters.length; i++) {
                PsiParameter parameter = overridingParameters[i];
                List<PsiParameter> supers = Collections.singletonList(superParameters[i]);
                if (findNullableSuperForNotNullParameter(parameter, supers, substitutor, state) != null) {
                    return JavaAnalysisLocalize.inspectionMessageNonNullParameterShouldNotOverrideNullableParameter(
                        parameter.getName(),
                        overridingMethod.getName(),
                        overridingMethodClass.getName(),
                        intfMethodClass.getName()
                    );
                }
                if (findNotNullSuperForNonAnnotatedParameter(manager, parameter, supers, state) != null) {
                    return JavaAnalysisLocalize.inspectionMessageNonAnnotatedParameterShouldNotOverrideNonNullParameter(
                        parameter.getName(),
                        overridingMethod.getName(),
                        overridingMethodClass.getName(),
                        intfMethodClass.getName()
                    );
                }
                if (isNotNullParameterOverridingNonAnnotated(manager, parameter, supers, substitutor, state)) {
                    return JavaAnalysisLocalize.inspectionMessageNonNullParameterShouldNotOverrideNonAnnotatedParameter(
                        parameter.getName(),
                        overridingMethod.getName(),
                        overridingMethodClass.getName(),
                        intfMethodClass.getName()
                    );
                }
            }
        }

        return LocalizeValue.empty();
    }

    @RequiredReadAction
    private void checkMethodReference(PsiMethodReferenceExpression expression, ProblemsHolder holder, NullableStuffInspectionState state) {
        PsiMethod superMethod = LambdaUtil.getFunctionalInterfaceMethod(expression);
        PsiMethod targetMethod = tryCast(expression.resolve(), PsiMethod.class);
        if (superMethod == null || targetMethod == null) {
            return;
        }

        PsiElement refName = expression.getReferenceNameElement();
        assert refName != null;
        if (isNullableOverridingNotNull(check(targetMethod, holder, expression.getType()), superMethod, state)) {
            reportProblem(
                holder,
                refName,
                JavaAnalysisLocalize.inspectionNullableProblemsNullableMethodOverridesNotnull(
                    getPresentableAnnoName(targetMethod),
                    getPresentableAnnoName(superMethod)
                )
            );
        }
    }

    protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
        return null;
    }

    private static boolean nullabilityAnnotationsNotAvailable(PsiFile file) {
        Project project = file.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        return ContainerUtil.find(
            NullableNotNullManager.getInstance(project).getNullables(),
            s -> facade.findClass(s, scope) != null
        ) == null;
    }

    @RequiredReadAction
    private void checkAccessors(
        PsiField field,
        Annotated annotated,
        Project project,
        NullableNotNullManager manager,
        String anno,
        List<String> annoToRemove,
        ProblemsHolder holder,
        NullableStuffInspectionState state
    ) {
        String propName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        boolean isStatic = field.isStatic();
        PsiMethod getter = PropertyUtilBase.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
        PsiIdentifier nameIdentifier = getter == null ? null : getter.getNameIdentifier();
        if (nameIdentifier != null && nameIdentifier.isPhysical()) {
            if (PropertyUtil.getFieldOfGetter(getter) == field) {
                AddAnnotationPsiFix getterAnnoFix =
                    new AddAnnotationPsiFix(anno, getter, ArrayUtil.toStringArray(annoToRemove));
                if (state.REPORT_NOT_ANNOTATED_GETTER) {
                    if (!hasNullability(manager, getter) && !TypeConversionUtil.isPrimitiveAndNotNull(getter.getReturnType())) {
                        reportProblem(
                            holder,
                            nameIdentifier,
                            getterAnnoFix,
                            JavaAnalysisLocalize.inspectionNullableProblemsAnnotatedFieldGetterNotAnnotated(StringUtil.getShortName(anno))
                        );
                    }
                }
                if (annotated.isDeclaredNotNull && isNullableNotInferred(getter, false) ||
                    annotated.isDeclaredNullable && isNotNullNotInferred(getter, false, false)) {
                    reportProblem(
                        holder,
                        nameIdentifier,
                        getterAnnoFix,
                        JavaAnalysisLocalize.inspectionNullableProblemsAnnotatedFieldGetterConflict(
                            getPresentableAnnoName(field),
                            getPresentableAnnoName(getter)
                        )
                    );
                }
            }
        }

        PsiClass containingClass = field.getContainingClass();
        PsiMethod setter = PropertyUtilBase.findPropertySetter(containingClass, propName, isStatic, false);
        if (setter != null && setter.isPhysical() && PropertyUtil.getFieldOfSetter(setter) == field) {
            PsiParameter[] parameters = setter.getParameterList().getParameters();
            assert parameters.length == 1 : setter.getText();
            PsiParameter parameter = parameters[0];
            LOG.assertTrue(parameter != null, setter.getText());
            AddAnnotationFix addAnnoFix = createAddAnnotationFix(anno, annoToRemove, parameter);
            if (state.REPORT_NOT_ANNOTATED_GETTER && !hasNullability(manager, parameter)
                && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
                PsiIdentifier parameterName = parameter.getNameIdentifier();
                assertValidElement(setter, parameter, parameterName);
                reportProblem(
                    holder,
                    parameterName,
                    addAnnoFix,
                    JavaAnalysisLocalize.inspectionNullableProblemsAnnotatedFieldSetterParameterNotAnnotated(getPresentableAnnoName(field))
                );
            }
            if (PropertyUtil.isSimpleSetter(setter)
                && annotated.isDeclaredNotNull
                && isNullableNotInferred(parameter, false)) {
                PsiIdentifier parameterName = parameter.getNameIdentifier();
                assertValidElement(setter, parameter, parameterName);
                reportProblem(
                    holder,
                    parameterName,
                    addAnnoFix,
                    JavaAnalysisLocalize.inspectionNullableProblemsAnnotatedFieldSetterParameterConflict(
                        getPresentableAnnoName(field),
                        getPresentableAnnoName(parameter)
                    )
                );
            }
        }
    }

    @RequiredReadAction
    private static AddAnnotationFix createAddAnnotationFix(String anno, List<String> annoToRemove, PsiParameter parameter) {
        return new AddAnnotationFix(anno, parameter, annoToRemove.toArray(ArrayUtil.EMPTY_STRING_ARRAY));
    }

    @Contract("_,_,null -> fail")
    @RequiredReadAction
    private static void assertValidElement(PsiMethod setter, PsiParameter parameter, PsiIdentifier nameIdentifier1) {
        LOG.assertTrue(nameIdentifier1 != null && nameIdentifier1.isPhysical(), setter.getText());
        LOG.assertTrue(parameter.isPhysical(), setter.getText());
    }

    @RequiredReadAction
    private void checkConstructorParameters(
        PsiField field,
        Annotated annotated,
        String anno,
        List<String> annoToRemove,
        ProblemsHolder holder,
        NullableStuffInspectionState state
    ) {
        List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers(field);
        if (initializers.isEmpty()) {
            return;
        }

        if (state.REPORT_NOT_ANNOTATED_GETTER) {
            reportConstructorParameterFromField(field, anno, annoToRemove, holder, initializers);
        }

        if (field.isFinal()) {
            checkFinalFieldInitializedNotNull(field, annotated, holder, initializers);
        }
    }

    @RequiredReadAction
    private void checkFinalFieldInitializedNotNull(
        PsiField field,
        Annotated annotated,
        ProblemsHolder holder,
        List<PsiExpression> initializers
    ) {
        List<PsiParameter> notNullParams = new ArrayList<>();
        for (PsiExpression rhs : initializers) {
            if (rhs instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiParameter parameter
                && isConstructorParameter(parameter)
                && parameter.isPhysical()
                && annotated.isDeclaredNullable
                && isNotNullNotInferred(parameter, false, false)) {
                notNullParams.add(parameter);
            }
        }

        if (notNullParams.size() != initializers.size()) {
            // it's not the case that the field is and @Nullable and always initialized via parameters,
            // so there might be other initializers that could justify it being nullable,
            // so don't highlight field and constructor parameter annotation inconsistency
            return;
        }

        PsiIdentifier nameIdentifier = field.getNameIdentifier();
        if (nameIdentifier.isPhysical()) {
            reportProblem(
                holder,
                nameIdentifier,
                AddAnnotationPsiFix.createAddNotNullFix(field),
                JavaAnalysisLocalize.zeroFieldIsAlwaysInitializedNotNull(getPresentableAnnoName(field))
            );
        }
    }

    @RequiredReadAction
    private void reportConstructorParameterFromField(
        PsiField field,
        String anno,
        List<String> annoToRemove,
        ProblemsHolder holder,
        List<PsiExpression> initializers
    ) {
        Map<PsiMethod, List<PsiExpression>> ctorToInitializers = StreamEx.of(initializers)
            .mapToEntry(e -> PsiTreeUtil.getParentOfType(e, PsiMethod.class), e -> e)
            .nonNullKeys()
            .filterKeys(PsiMethod::isConstructor)
            .grouping();

        ctorToInitializers.forEach((ctor, exprs) -> {
            List<PsiParameter> parameters = StreamEx.of(exprs)
                .map(
                    e -> PsiUtil.skipParenthesizedExprDown(e) instanceof PsiReferenceExpression ref
                        ? tryCast(ref.resolve(), PsiParameter.class)
                        : null
                )
                .distinct()
                .limit(2)
                .toList();
            if (parameters.size() != 1) {
                return;
            }
            PsiParameter parameter = parameters.getFirst();
            if (parameter != null && parameter.getDeclarationScope() == ctor
                && !hasNullability(NullableNotNullManager.getInstance(field.getProject()), parameter)
                && !TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
                PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
                if (nameIdentifier != null && nameIdentifier.isPhysical()) {
                    reportProblem(
                        holder,
                        nameIdentifier,
                        createAddAnnotationFix(anno, annoToRemove, parameter),
                        JavaAnalysisLocalize.inspectionNullableProblemsAnnotatedFieldConstructorParameterNotAnnotated(
                            StringUtil.getShortName(anno)
                        )
                    );
                }
            }
        });
    }

    private static boolean isConstructorParameter(@Nullable PsiElement parameter) {
        return parameter instanceof PsiParameter
            && psiElement(PsiParameterList.class).withParent(psiMethod().constructor(true)).accepts(parameter.getParent());
    }

    private static String getPresentableAnnoName(PsiModifierListOwner owner) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        String name = info == null ? null : info.getAnnotation().getQualifiedName();
        if (name == null) {
            return "???";
        }
        return StringUtil.getShortName(name);
    }

    public static String getPresentableAnnoName(PsiAnnotation annotation) {
        return StringUtil.getShortName(StringUtil.notNullize(annotation.getQualifiedName(), "???"));
    }

    /**
     * @return true if the owner has a or @Nullable annotation,
     * or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
     */
    private static boolean hasNullability(NullableNotNullManager manager, PsiModifierListOwner owner) {
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        return info != null && info.getNullability() != Nullability.UNKNOWN && info.getInheritedFrom() == null;
    }

    private static class Annotated {
        private boolean isDeclaredNotNull;
        private boolean isDeclaredNullable;
        private @Nullable PsiAnnotation notNull;
        private @Nullable PsiAnnotation nullable;

        private Annotated(@Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
            this.isDeclaredNotNull = notNull != null;
            this.isDeclaredNullable = nullable != null;
            this.notNull = notNull;
            this.nullable = nullable;
        }

        static Annotated from(PsiModifierListOwner owner) {
            NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
            NullabilityAnnotationInfo notNullInfo =
                manager.findNullabilityAnnotationInfo(owner, Collections.singleton(Nullability.NOT_NULL));
            NullabilityAnnotationInfo nullableInfo =
                manager.findNullabilityAnnotationInfo(owner, Collections.singleton(Nullability.NULLABLE));
            PsiAnnotation notNullAnno =
                notNullInfo == null || (notNullInfo.isContainer() && nullableInfo != null && !nullableInfo.isContainer())
                    ? null : notNullInfo.getAnnotation();
            PsiAnnotation nullableAnno =
                nullableInfo == null || (nullableInfo.isContainer() && notNullInfo != null && !notNullInfo.isContainer())
                    ? null : nullableInfo.getAnnotation();
            return new Annotated(notNullAnno, nullableAnno);
        }
    }

    @RequiredReadAction
    private Annotated check(PsiModifierListOwner owner, ProblemsHolder holder, PsiType type) {
        Annotated annotated = Annotated.from(owner);
        PsiAnnotation annotation = annotated.notNull == null ? annotated.nullable : annotated.notNull;
        if (annotation != null && !annotation.isPhysical() && type instanceof PsiPrimitiveType) {
            reportIncorrectLocation(holder, annotation, owner, JavaAnalysisLocalize.inspectionNullableProblemsPrimitiveTypeAnnotation());
        }
        if (owner instanceof PsiParameter parameter) {
            Nullability expectedNullability = DfaPsiUtil.inferParameterNullability(parameter);
            if (annotated.notNull != null && expectedNullability == Nullability.NULLABLE) {
                reportParameterNullabilityMismatch(parameter, annotated.notNull, holder, JavaAnalysisLocalize.parameterCanBeNull());
            }
            else if (annotated.nullable != null && expectedNullability == Nullability.NOT_NULL) {
                reportParameterNullabilityMismatch(parameter, annotated.nullable, holder, JavaAnalysisLocalize.parameterIsAlwaysNotNull());
            }
        }
        return annotated;
    }

    @RequiredReadAction
    private void reportParameterNullabilityMismatch(
        PsiParameter owner,
        PsiAnnotation annotation,
        ProblemsHolder holder,
        LocalizeValue message
    ) {
        if (annotation.isPhysical() && !PsiTreeUtil.isAncestor(owner, annotation, true)) {
            return;
        }
        PsiElement anchor = annotation.isPhysical() ? annotation : owner.getNameIdentifier();
        if (anchor != null && !anchor.getTextRange().isEmpty()) {
            reportProblem(holder, anchor, new RemoveAnnotationQuickFix(annotation, owner), message);
        }
    }

    private void reportIncorrectLocation(
        ProblemsHolder holder,
        PsiAnnotation annotation,
        @Nullable PsiModifierListOwner listOwner,
        LocalizeValue message,
        LocalQuickFix... additionalFixes
    ) {
        RemoveAnnotationQuickFix removeFix = new RemoveAnnotationQuickFix(annotation, listOwner, true);
        MoveAnnotationToBoundFix moveToBoundFix = MoveAnnotationToBoundFix.create(annotation);
        LocalQuickFix[] fixes = moveToBoundFix == null ? new LocalQuickFix[]{removeFix} : new LocalQuickFix[]{moveToBoundFix, removeFix};
        reportProblem(
            holder,
            !annotation.isPhysical() && listOwner != null ? listOwner.getNavigationElement() : annotation,
            ArrayUtil.mergeArrays(additionalFixes, fixes),
            message
        );
    }

    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionNullableProblemsDisplayName();
    }

    @Override
    public String getShortName() {
        return "NullableProblems";
    }

    @RequiredReadAction
    private void checkNullableStuffForMethod(PsiMethod method, ProblemsHolder holder, NullableStuffInspectionState state) {
        Annotated annotated = check(method, holder, method.getReturnType());

        List<PsiMethod> superMethods = ContainerUtil.map(
            method.findSuperMethodSignaturesIncludingStatic(true),
            MethodSignatureBackedByPsiMethod::getMethod
        );

        NullableNotNullManager nullableManager = NullableNotNullManager.getInstance(holder.getProject());

        checkSupers(method, holder, annotated, superMethods, state);
        checkParameters(method, holder, superMethods, nullableManager, state);
        checkOverriders(method, holder, annotated, nullableManager, state);
        checkConflictingContainerAnnotations(holder, method.getModifierList());
    }

    private void checkSupers(
        PsiMethod method,
        ProblemsHolder holder,
        Annotated annotated,
        List<? extends PsiMethod> superMethods,
        NullableStuffInspectionState state
    ) {
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
            return;
        }
        for (PsiMethod superMethod : superMethods) {
            if (isNullableOverridingNotNull(annotated, superMethod, state)) {
                PsiAnnotation annotation = findAnnotation(method, getNullityManager(method).getNullables(), true);
                reportProblem(
                    holder,
                    annotation != null ? annotation : identifier,
                    JavaAnalysisLocalize.inspectionNullableProblemsNullableMethodOverridesNotnull(
                        getPresentableAnnoName(method),
                        getPresentableAnnoName(superMethod)
                    )
                );
                break;
            }

            if (isNonAnnotatedOverridingNotNull(method, superMethod, state)) {
                reportProblem(
                    holder,
                    identifier,
                    createFixForNonAnnotatedOverridesNotNull(method),
                    JavaAnalysisLocalize.inspectionNullableProblemsMethodOverridesNotnull(getPresentableAnnoName(superMethod))
                );
                break;
            }

            PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            if (returnTypeElement != null
                && checkNestedGenericClasses(
                holder,
                returnTypeElement,
                superMethod.getReturnType(),
                method.getReturnType(),
                ConflictNestedTypeProblem.OVERRIDING_NESTED_TYPE_PROBLEM,
                state
            )) {
                break;
            }
        }
    }

    private static NullableNotNullManager getNullityManager(PsiMethod method) {
        return NullableNotNullManager.getInstance(method.getProject());
    }

    private static @Nullable LocalQuickFix createFixForNonAnnotatedOverridesNotNull(PsiMethod method) {
        NullableNotNullManager nullableManager = getNullityManager(method);
        return isAnnotatingApplicable(method, nullableManager.getDefaultNotNull())
            ? AddAnnotationPsiFix.createAddNotNullFix(method)
            : null;
    }

    private boolean isNullableOverridingNotNull(Annotated methodInfo, PsiMethod superMethod, NullableStuffInspectionState state) {
        return state.REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE
            && methodInfo.isDeclaredNullable
            && isNotNullNotInferred(superMethod, true, false);
    }

    private boolean isNonAnnotatedOverridingNotNull(PsiMethod method, PsiMethod superMethod, NullableStuffInspectionState state) {
        if (state.REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL
            && !(method.getReturnType() instanceof PsiPrimitiveType)
            && !method.isConstructor()) {
            NullableNotNullManager manager = getNullityManager(method);
            NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
            if ((info == null || info.isInferred() ||
                (info.getInheritedFrom() != null && manager.findContainerAnnotation(method) == null))
                && isNotNullNotInferred(superMethod, true, state.IGNORE_EXTERNAL_SUPER_NOTNULL)
                && !hasInheritableNotNull(superMethod)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInheritableNotNull(PsiModifierListOwner owner) {
        return isAnnotated(owner, "javax.annotation.constraints.NotNull", CHECK_HIERARCHY | CHECK_TYPE);
    }

    private void checkParameters(
        PsiMethod method,
        ProblemsHolder holder,
        List<? extends PsiMethod> superMethods,
        NullableNotNullManager nullableManager,
        NullableStuffInspectionState state
    ) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (parameter.getType() instanceof PsiPrimitiveType) {
                continue;
            }

            List<PsiParameter> superParameters = getSuperParameters(superMethods, parameters, i);

            checkSuperParameterAnnotations(holder, nullableManager, parameter, superParameters, state);

            checkNullLiteralArgumentOfNotNullParameterUsages(method, holder, nullableManager, i, parameter, state);
        }
    }

    private static List<PsiParameter> getSuperParameters(List<? extends PsiMethod> superMethods, PsiParameter[] parameters, int i) {
        List<PsiParameter> superParameters = new ArrayList<>();
        for (PsiMethod superMethod : superMethods) {
            PsiParameter[] _superParameters = superMethod.getParameterList().getParameters();
            if (_superParameters.length == parameters.length) {
                superParameters.add(_superParameters[i]);
            }
        }
        return superParameters;
    }

    private void checkSuperParameterAnnotations(
        ProblemsHolder holder,
        NullableNotNullManager nullableManager,
        PsiParameter parameter,
        List<PsiParameter> superParameters,
        NullableStuffInspectionState state
    ) {
        PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
        if (nameIdentifier == null) {
            return;
        }
        PsiParameter nullableSuper = findNullableSuperForNotNullParameter(parameter, superParameters, PsiSubstitutor.EMPTY, state);
        if (nullableSuper != null) {
            PsiAnnotation annotation = findAnnotation(parameter, nullableManager.getNotNulls(), true);
            reportProblem(
                holder,
                annotation != null ? annotation : nameIdentifier,
                JavaAnalysisLocalize.inspectionNullableProblemsNotnullParameterOverridesNullable(
                    getPresentableAnnoName(parameter),
                    getPresentableAnnoName(nullableSuper)
                )
            );
        }
        PsiParameter notNullSuper = findNotNullSuperForNonAnnotatedParameter(nullableManager, parameter, superParameters, state);
        if (notNullSuper != null) {
            LocalQuickFix fix = isAnnotatingApplicable(parameter, nullableManager.getDefaultAnnotation(Nullability.NOT_NULL, parameter))
                ? AddAnnotationPsiFix.createAddNotNullFix(parameter)
                : null;
            reportProblem(
                holder,
                nameIdentifier,
                fix,
                JavaAnalysisLocalize.inspectionNullableProblemsParameterOverridesNotnull(getPresentableAnnoName(notNullSuper))
            );
        }
        if (isNotNullParameterOverridingNonAnnotated(nullableManager, parameter, superParameters, PsiSubstitutor.EMPTY, state)) {
            NullabilityAnnotationInfo info = nullableManager.findEffectiveNullabilityInfo(parameter);
            assert info != null;
            PsiAnnotation notNullAnnotation = info.getAnnotation();
            boolean physical = PsiTreeUtil.isAncestor(parameter, notNullAnnotation, true);
            LocalQuickFix fix = physical ? new RemoveAnnotationQuickFix(notNullAnnotation, parameter) : null;
            reportProblem(
                holder,
                physical ? notNullAnnotation : nameIdentifier,
                fix,
                JavaAnalysisLocalize.inspectionNullableProblemsNotnullParameterOverridesNotAnnotated(getPresentableAnnoName(parameter))
            );
        }

        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
            for (PsiParameter superParameter : superParameters) {
                if (checkNestedGenericClasses(
                    holder,
                    typeElement,
                    parameter.getType(),
                    superParameter.getType(),
                    ConflictNestedTypeProblem.OVERRIDING_NESTED_TYPE_PROBLEM,
                    state
                )) {
                    break;
                }
            }
        }
    }

    private @Nullable PsiParameter findNotNullSuperForNonAnnotatedParameter(
        NullableNotNullManager nullableManager,
        PsiParameter parameter,
        List<? extends PsiParameter> superParameters,
        NullableStuffInspectionState state
    ) {
        return state.REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL && !hasNullability(nullableManager, parameter)
            ? ContainerUtil.find(
            superParameters,
            sp -> isNotNullNotInferred(sp, false, state.IGNORE_EXTERNAL_SUPER_NOTNULL) && !hasInheritableNotNull(sp)
        )
            : null;
    }

    private @Nullable PsiParameter findNullableSuperForNotNullParameter(
        PsiParameter parameter,
        List<? extends PsiParameter> superParameters,
        PsiSubstitutor superSubstitutor,
        NullableStuffInspectionState state
    ) {
        if (!state.REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE || !isNotNullNotInferred(parameter, false, false)) {
            return null;
        }
        PsiClass derived = PsiUtil.getContainingClass(parameter);
        for (PsiParameter superParameter : superParameters) {
            PsiClass base = PsiUtil.getContainingClass(superParameter);
            PsiSubstitutor substitutor = base == null || derived == null ? PsiSubstitutor.EMPTY :
                TypeConversionUtil.getMaybeSuperClassSubstitutor(base, derived, superSubstitutor);
            if (substitutor == null) {
                // may happen if A extends B implements C and methods are declared in B and C
                substitutor = superSubstitutor;
            }
            PsiType superType = substitutor.substitute(superParameter.getType());
            if (DfaPsiUtil.getElementNullabilityForRead(superType, superParameter) == Nullability.NULLABLE) {
                return superParameter;
            }
        }
        return null;
    }

    private boolean isNotNullParameterOverridingNonAnnotated(
        NullableNotNullManager nullableManager,
        PsiParameter parameter,
        List<? extends PsiParameter> superParameters,
        PsiSubstitutor substitutor,
        NullableStuffInspectionState state
    ) {
        if (!state.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED) {
            return false;
        }
        NullabilityAnnotationInfo info = nullableManager.findEffectiveNullabilityInfo(parameter);
        return info != null
            && info.getNullability() == Nullability.NOT_NULL
            && info.getInheritedFrom() == null
            && !info.isInferred()
            && ContainerUtil.exists(superParameters, sp -> isSuperNotAnnotated(nullableManager, parameter, sp, substitutor));
    }

    private static boolean isSuperNotAnnotated(
        NullableNotNullManager nullableManager,
        PsiParameter parameter,
        PsiParameter superParameter,
        PsiSubstitutor superSubstitutor
    ) {
        if (hasNullability(nullableManager, superParameter)) {
            return false;
        }
        if (ContainerUtil.exists(
            getSuperAnnotationOwners(superParameter),
            superSuperParameter -> hasNullability(nullableManager, superSuperParameter)
        )) {
            return false;
        }
        PsiType type = superParameter.getType();
        if (TypeUtils.isTypeParameter(type)) {
            PsiClass childClass = PsiUtil.getContainingClass(parameter);
            PsiClass superClass = PsiUtil.getContainingClass(superParameter);
            if (superClass != null && childClass != null) {
                PsiSubstitutor substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(superClass, childClass, superSubstitutor);
                if (substitutor == null) {
                    // may happen if A extends B implements C and methods are declared in B and C
                    substitutor = superSubstitutor;
                }
                PsiType substituted = substitutor.substitute(type);
                return DfaPsiUtil.getTypeNullability(substituted) == Nullability.UNKNOWN;
            }
        }
        return true;
    }

    private void checkNullLiteralArgumentOfNotNullParameterUsages(
        PsiMethod method,
        ProblemsHolder holder,
        NullableNotNullManager nullableManager,
        int parameterIdx,
        PsiParameter parameter,
        NullableStuffInspectionState state
    ) {
        if (!state.REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER || !holder.isOnTheFly()) {
            return;
        }

        PsiVariable owner = parameter.isPhysical() ? parameter : JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter);
        if (owner == null) {
            return;
        }

        PsiElement elementToHighlight = null;
        NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(owner);
        if (info != null && !info.isInferred()) {
            if (info.getNullability() == Nullability.NOT_NULL) {
                PsiAnnotation notNullAnnotation = info.getAnnotation();
                boolean physical = PsiTreeUtil.isAncestor(owner, notNullAnnotation, true);
                elementToHighlight = physical ? notNullAnnotation : owner.getNameIdentifier();
            }
        }
        else {
            info = DfaPsiUtil.getTypeNullabilityInfo(owner.getType());
            if (info != null && info.getNullability() == Nullability.NOT_NULL) {
                elementToHighlight = owner.getNameIdentifier();
            }
        }
        if (elementToHighlight == null || !JavaNullMethodArgumentUtil.hasNullArgument(method, parameterIdx)) {
            return;
        }

        reportProblem(
            holder,
            elementToHighlight,
            createNavigateToNullParameterUsagesFix(parameter),
            JavaAnalysisLocalize.inspectionNullableProblemsNotnullParameterReceivesNullLiteral(
                StringUtil.getShortName(Objects.requireNonNull(info.getAnnotation().getQualifiedName()))
            )
        );
    }

    @RequiredReadAction
    private void checkOverriders(
        PsiMethod method,
        ProblemsHolder holder,
        Annotated annotated,
        NullableNotNullManager nullableManager,
        NullableStuffInspectionState state
    ) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (state.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
            boolean[] checkParameter = new boolean[parameters.length];
            boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
            boolean hasAnnotatedParameter = false;
            for (int i = 0; i < parameters.length; i++) {
                PsiParameter parameter = parameters[i];
                checkParameter[i] = isNotNullNotInferred(parameter, false, false)
                    && !hasInheritableNotNull(parameter)
                    && !(parameter.getType() instanceof PsiPrimitiveType);
                hasAnnotatedParameter |= checkParameter[i];
            }
            boolean checkReturnType =
                annotated.isDeclaredNotNull && !hasInheritableNotNull(method) && !(method.getReturnType() instanceof PsiPrimitiveType);
            if (hasAnnotatedParameter || checkReturnType) {
                PsiMethod[] overridings =
                    OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
                boolean methodQuickFixSuggested = false;
                for (PsiMethod overriding : overridings) {
                    if (shouldSkipOverriderAsGenerated(overriding)) {
                        continue;
                    }

                    String defaultNotNull = nullableManager.getDefaultAnnotation(Nullability.NOT_NULL, overriding);
                    if (!methodQuickFixSuggested
                        && checkReturnType
                        && !isNotNullNotInferred(overriding, false, false)
                        && (isNullableNotInferred(overriding, false) || !isNullableNotInferred(overriding, true))
                        && AddAnnotationPsiFix.isAvailable(overriding, defaultNotNull)) {
                        PsiIdentifier identifier = method.getNameIdentifier();//load tree
                        NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(method);
                        if (info != null) {
                            PsiAnnotation annotation = info.getAnnotation();
                            String[] annotationsToRemove = ArrayUtil.toStringArray(nullableManager.getNullables());

                            LocalQuickFix fix = isAnnotatingApplicable(overriding, defaultNotNull)
                                ? new MyAnnotateMethodFix(Nullability.NOT_NULL, annotationsToRemove)
                                : null;

                            PsiElement psiElement = annotation;
                            if (!annotation.isPhysical() || !PsiTreeUtil.isAncestor(method, annotation, true)) {
                                psiElement = identifier;
                                if (psiElement == null) {
                                    continue;
                                }
                            }
                            reportProblem(
                                holder,
                                psiElement,
                                fix,
                                JavaAnalysisLocalize.nullableStuffProblemsOverriddenMethodsAreNotAnnotated()
                            );
                            methodQuickFixSuggested = true;
                        }
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
                                PsiIdentifier identifier =
                                    parameters[i].getNameIdentifier(); //be sure that corresponding tree element available
                                NullabilityAnnotationInfo info = nullableManager.findOwnNullabilityInfo(parameters[i]);
                                PsiElement psiElement = info == null ? null : info.getAnnotation();
                                if (psiElement == null || !psiElement.isPhysical()) {
                                    psiElement = identifier;
                                    if (psiElement == null) {
                                        continue;
                                    }
                                }
                                LocalQuickFix fix = isAnnotatingApplicable(parameter, defaultNotNull)
                                    ? new AnnotateOverriddenMethodParameterFix(Nullability.NOT_NULL)
                                    : null;
                                reportProblem(
                                    holder,
                                    psiElement,
                                    fix,
                                    JavaAnalysisLocalize.nullableStuffProblemsOverriddenMethodParametersAreNotAnnotated()
                                );
                                parameterQuickFixSuggested[i] = true;
                            }
                        }
                    }
                }
            }
        }
    }

    @RequiredReadAction
    public static boolean shouldSkipOverriderAsGenerated(PsiMethod overriding) {
        if (Boolean.TRUE) {
            return false;
        }

        PsiFile file = overriding.getContainingFile();
        VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
        return virtualFile != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(virtualFile, overriding.getProject());
    }

    private static boolean isNotNullNotInferred(PsiModifierListOwner owner, boolean checkBases, boolean skipExternal) {
        Project project = owner.getProject();
        NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        if (info == null || info.isInferred() || info.getNullability() != Nullability.NOT_NULL) {
            return false;
        }
        if (!checkBases && info.getInheritedFrom() != null) {
            return false;
        }
        if (skipExternal && info.isExternal()) {
            return false;
        }
        return true;
    }

    public static boolean isNullableNotInferred(PsiModifierListOwner owner, boolean checkBases) {
        Project project = owner.getProject();
        NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        return info != null
            && !info.isInferred()
            && info.getNullability() == Nullability.NULLABLE
            && (checkBases || info.getInheritedFrom() == null);
    }

    private static class MyAnnotateMethodFix implements LocalQuickFix {
        private Nullability myNullability;
        private String[] myAnnotationsToRemove;

        MyAnnotateMethodFix(Nullability nullability, String... annotationsToRemove) {
            myNullability = nullability;
            myAnnotationsToRemove = annotationsToRemove.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : annotationsToRemove;
        }

//        @Override
//        public String getFamilyName() {
//            return JavaAnalysisLocalize.inspectionAnnotateOverriddenMethodQuickfixFamilyName().get();
//        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        @RequiredUIAccess
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement psiElement = descriptor.getPsiElement();

            PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
            if (method == null) {
                return;
            }
            List<PsiMethod> toAnnotate = new ArrayList<>();
            NullableNotNullManager manager = NullableNotNullManager.getInstance(project);

            if (!AnnotateOverriddenMethodParameterFix.processModifiableInheritorsUnderProgress(
                method,
                (Consumer<? super PsiMethod>) psiMethod -> {
                    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(psiMethod);
                    if (info != null && info.getNullability() == myNullability && !info.isInferred() && info.getInheritedFrom() == null) {
                        return;
                    }
                    String annotation = manager.getDefaultAnnotation(myNullability, psiMethod);
                    if (isAnnotatingApplicable(psiMethod, annotation)
                        && !isAnnotated(psiMethod, annotation, CHECK_EXTERNAL | CHECK_TYPE)) {
                        toAnnotate.add(psiMethod);
                    }
                }
            )) {
                return;
            }

            FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
            for (PsiMethod psiMethod : toAnnotate) {
                String annotation = manager.getDefaultAnnotation(myNullability, psiMethod);
                AddAnnotationPsiFix fix = new AddAnnotationPsiFix(annotation, psiMethod, myAnnotationsToRemove);
                fix.invoke(psiMethod.getProject(), psiMethod.getContainingFile(), psiMethod, psiMethod);
            }
            LanguageUndoUtil.markPsiFileForUndo(method.getContainingFile());
        }

        @Override
        public LocalizeValue getName() {
            return JavaAnalysisLocalize.inspectionAnnotateOverriddenMethodNullableQuickfixName(
                myNullability == Nullability.NOT_NULL ? "NotNull" : "Nullable"
            );
        }
    }

    private enum ConflictNestedTypeProblem {
        RETURN_NESTED_TYPE_PROBLEM(
            JavaAnalysisLocalize::returningAClassWithNotnullArguments,
            JavaAnalysisLocalize::returningAClassWithNullableArguments
        ),
        ASSIGNMENT_NESTED_TYPE_PROBLEM(
            JavaAnalysisLocalize::assigningAClassWithNotnullElements,
            JavaAnalysisLocalize::assigningAClassWithNullableElements
        ),
        OVERRIDING_NESTED_TYPE_PROBLEM(
            JavaAnalysisLocalize::overridingAClassWithNotnullElements,
            JavaAnalysisLocalize::overridingAClassWithNullableElements
        );

        private Function<Object, LocalizeValue> notNullToNullProblemMessage;
        private Function<Object, LocalizeValue> nullToNotNullProblemMessage;
        private Function<Object, LocalizeValue> myComplexProblem;

        ConflictNestedTypeProblem(Function<Object, LocalizeValue> notNullToNullProblemMessage, Function<Object, LocalizeValue> nullToNotNullProblemMessage) {
            this.notNullToNullProblemMessage = notNullToNullProblemMessage;
            this.nullToNotNullProblemMessage = nullToNotNullProblemMessage;
            this.myComplexProblem = JavaAnalysisLocalize::complexProblemWithNullability;
        }

        LocalizeValue notNullToNullProblem(Object param) {
            return notNullToNullProblemMessage.apply(param);
        }

        LocalizeValue nullToNotNullProblem(Object param) {
            return nullToNotNullProblemMessage.apply(param);
        }

        LocalizeValue complexProblem(Object param) {
            return myComplexProblem.apply(param);
        }
    }
}