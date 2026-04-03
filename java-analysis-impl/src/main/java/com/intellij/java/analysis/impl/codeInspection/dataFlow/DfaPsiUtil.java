// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.impl.codeInspection.util.OptionalUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.*;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.IElementType;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.Stack;
import consulo.util.collection.*;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.Ref;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.java.language.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class DfaPsiUtil {

    private static final CallMatcher NON_NULL_VAR_ARG = CallMatcher.anyOf(
        staticCall(JAVA_UTIL_LIST, "of"),
        staticCall(JAVA_UTIL_SET, "of"),
        staticCall(JAVA_UTIL_MAP, "ofEntries"));

    public static boolean isFinalField(PsiVariable var) {
        return var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && var instanceof PsiField;
    }

    /**
     * Returns nullability of variable or method, when it's expected to read from it.
     * This method takes into account various sources of nullability information, like method annotations,
     * type annotations, container annotations, inferred annotations, or external annotations.
     * Automatic inference of method parameter nullability is ignored, which is useful when analyzing the method body (as it's
     * inferred from method body as well, so both analyses may produce conflicting results).
     *
     * @param resultType concrete type of particular variable access or method call (an instantiation of generic method return type,
     *                   or variable type), if known.
     * @param owner      method or variable to get its nullability
     * @return nullability of the owner; {@link Nullability#UNKNOWN} is both parameters are null.
     */
    public static @NotNull Nullability getElementNullabilityForRead(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
        return getElementNullability(resultType, owner, true);
    }

    public static Nullability getElementNullabilityIgnoringParameterInference(@Nullable PsiType resultType,
                                                                              @Nullable PsiModifierListOwner owner) {
        return getElementNullability(resultType, owner, true);
    }

    private static @NotNull Nullability getElementNullability(@Nullable PsiType resultType,
                                                              @Nullable PsiModifierListOwner owner,
                                                              boolean forRead) {
        if (owner == null) return getTypeNullability(resultType, forRead);

        if (resultType instanceof PsiPrimitiveType) {
            return Nullability.UNKNOWN;
        }

        if (owner instanceof PsiEnumConstant) {
            return Nullability.NOT_NULL;
        }

        // Annotation manager requires index
        Project project = owner.getProject();
        if (DumbService.isDumb(project)) return Nullability.UNKNOWN;
        NullabilityAnnotationInfo fromAnnotation = getNullabilityFromAnnotation(owner, forRead);
        if (fromAnnotation != null) {
            if (resultType != null && fromAnnotation.getNullability() != Nullability.NOT_NULL) {
                PsiType type = PsiUtil.getTypeByPsiElement(owner);
                if (type != null) {
                    PsiAnnotationOwner annotationOwner = fromAnnotation.getAnnotation().getOwner();
                    if (PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter tp &&
                        annotationOwner instanceof PsiType && annotationOwner != type &&
                        !tp.equals(PsiUtil.resolveClassInClassTypeOnly(resultType))) {
                        // Nullable/Unknown from type hierarchy: should check the instantiation, as it could be more concrete
                        return getTypeNullability(resultType, forRead);
                    }
                }
            }
            return fromAnnotation.getNullability();
        }

        if (owner instanceof PsiMethod method) {
            if (isEnumPredefinedMethod(method)) {
                return Nullability.NOT_NULL;
            }
            if (isMapMethodWithUnknownNullity(method)) {
                return getTypeNullability(resultType) == Nullability.NULLABLE ? Nullability.NULLABLE : Nullability.UNKNOWN;
            }
        }

        if (owner instanceof PsiParameter parameter) {
            Nullability nullability = inferParameterNullability(parameter);
            if (nullability != Nullability.UNKNOWN) {
                return nullability;
            }
        }

        Nullability fromType = getNullabilityFromType(resultType, owner);
        if (fromType != null) return fromType;

        if (owner instanceof PsiMethod method && method.getParameterList().isEmpty() && forRead) {
            PsiField field = PropertyUtil.getFieldOfGetter(method);
            if (field != null && getElementNullabilityForRead(resultType, field) == Nullability.NULLABLE) {
                return Nullability.NULLABLE;
            }
        }

        return Nullability.UNKNOWN;
    }

    private static @NotNull Nullability getTypeNullability(@Nullable PsiType type, boolean forRead) {
        if (type == null) return Nullability.UNKNOWN;
        if (type instanceof PsiCapturedWildcardType captured) {
            if (!forRead) {
                TypeNullability nullability = captured.getLowerBound().getNullability();
                if (nullability.source() instanceof NullabilitySource.ExtendsBound) {
                    PsiElement context = captured.getContext();
                    NullableNotNullManager manager = NullableNotNullManager.getInstance(context.getProject());
                    if (manager != null) {
                        NullabilityAnnotationInfo defaultNullability = manager.findDefaultTypeUseNullability(context);
                        if (defaultNullability != null && defaultNullability.getNullability() == Nullability.NOT_NULL) {
                            return Nullability.NOT_NULL;
                        }
                    }
                }
                return nullability.nullability();
            }
        }
        return type.getNullability().nullability();
    }

    private static @Nullable Nullability getNullabilityFromType(@Nullable PsiType resultType, @NotNull PsiModifierListOwner owner) {
        if (resultType == null) return null;
        TypeNullability typeNullability = resultType.getNullability();
        if (typeNullability.equals(TypeNullability.UNKNOWN)) return null;
        Nullability fromType = typeNullability.nullability();
        if (fromType == Nullability.NOT_NULL && hasNullContract(owner)) {
            return Nullability.UNKNOWN;
        }
        return fromType;
    }

    private static boolean hasNullContract(@NotNull PsiModifierListOwner owner) {
        if (owner instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            int index = method.getParameterList().getParameterIndex(parameter);
            List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
            return ContainerUtil.exists(contracts,
                c -> c.getParameterConstraint(index) == StandardMethodContract.ValueConstraint.NULL_VALUE);
        }
        return false;
    }

    private static @Nullable NullabilityAnnotationInfo getNullabilityFromAnnotation(@NotNull PsiModifierListOwner owner,
                                                                                    boolean ignoreParameterNullabilityInference) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        if (info == null || shouldIgnoreAnnotation(info.getAnnotation())) {
            return null;
        }
        if (ignoreParameterNullabilityInference && owner instanceof PsiParameter && info.isInferred()) {
            List<PsiParameter> supers = AnnotationUtil.getSuperAnnotationOwners((PsiParameter) owner);
            return StreamEx.of(supers).map(param -> manager.findEffectiveNullabilityInfo(param))
                .findFirst(i -> i != null && i.getInheritedFrom() == null && i.getNullability() == Nullability.NULLABLE)
                .orElse(null);
        }
        return info;
    }

    private static boolean isMapMethodWithUnknownNullity(PsiMethod method) {
        String name = method.getName();
        if (!"get".equals(name) && !"remove".equals(name)) {
            return false;
        }
        PsiMethod superMethod = DeepestSuperMethodsSearch.search(method).findFirst();
        return ("java.util.Map." + name).equals(PsiUtil.getMemberQualifiedName(superMethod != null ? superMethod : method));
    }

    public static Nullability inferParameterNullability(PsiParameter parameter) {
        PsiElement parent = parameter.getParent();
        if (parent instanceof PsiParameterList) {
            PsiElement gParent = parent.getParent();
            if (gParent instanceof PsiLambdaExpression) {
                return getFunctionalParameterNullability((PsiLambdaExpression) gParent, ((PsiParameterList) parent).getParameterIndex(parameter));
            }
            else if (gParent instanceof PsiMethod && OptionalUtil.OPTIONAL_OF_NULLABLE.methodMatches((PsiMethod) gParent)) {
                return Nullability.NULLABLE;
            }
        }
        if (parent instanceof PsiForeachStatement) {
            return getTypeNullability(inferLoopParameterTypeWithNullability((PsiForeachStatement) parent));
        }
        return Nullability.UNKNOWN;
    }

    @Nullable
    private static PsiType inferLoopParameterTypeWithNullability(PsiForeachStatement loop) {
        PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(loop.getIteratedValue());
        if (iteratedValue == null) {
            return null;
        }

        PsiType iteratedType = iteratedValue.getType();
        if (iteratedValue instanceof PsiReferenceExpression) {
            PsiElement target = ((PsiReferenceExpression) iteratedValue).resolve();
            if (target instanceof PsiParameter && target.getParent() instanceof PsiForeachStatement) {
                PsiForeachStatement targetLoop = (PsiForeachStatement) target.getParent();
                if (PsiTreeUtil.isAncestor(targetLoop, loop, true) &&
                    !HighlightControlFlowUtil.isReassigned((PsiParameter) target, new HashMap<>())) {
                    iteratedType = inferLoopParameterTypeWithNullability(targetLoop);
                }
            }
        }
        return JavaGenericsUtil.getCollectionItemType(iteratedType, iteratedValue.getResolveScope());
    }

    public static @NotNull Nullability getTypeNullability(@Nullable PsiType type) {
        if (type == null) return Nullability.UNKNOWN;
        return type.getNullability().nullability();
    }

    @Nullable
    public static NullabilityAnnotationInfo getTypeNullabilityInfo(@Nullable PsiType type) {
        if (type == null || type instanceof PsiPrimitiveType) {
            return null;
        }

        Ref<NullabilityAnnotationInfo> result = Ref.create(null);
        InheritanceUtil.processSuperTypes(type, true, eachType -> {
            result.set(getTypeOwnNullability(eachType));
            return result.get() == null &&
                (!(type instanceof PsiClassType) || PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter);
        });
        return result.get();
    }

    @Nullable
    private static NullabilityAnnotationInfo getTypeOwnNullability(PsiType eachType) {
        for (PsiAnnotation annotation : eachType.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            NullableNotNullManager nnn = NullableNotNullManager.getInstance(annotation.getProject());
            if (nnn.getNullables().contains(qualifiedName) && !shouldIgnoreAnnotation(annotation)) {
                return new NullabilityAnnotationInfo(annotation, Nullability.NULLABLE, false);
            }
            if (nnn.getNotNulls().contains(qualifiedName)) {
                return new NullabilityAnnotationInfo(annotation, Nullability.NOT_NULL, false);
            }
        }
        if (eachType instanceof PsiClassType) {
            PsiElement context = ((PsiClassType) eachType).getPsiContext();
            if (context != null) {
                return NullableNotNullManager.getInstance(context.getProject()).findDefaultTypeUseNullability(context);
            }
        }
        return null;
    }

    private static boolean shouldIgnoreAnnotation(PsiAnnotation annotation) {
        PsiClass containingClass = ClassUtils.getContainingClass(annotation);
        if (containingClass == null) {
            return false;
        }
        String qualifiedName = containingClass.getQualifiedName();
        // We deliberately ignore nullability annotations on Guava functional interfaces to avoid noise warnings
        // See IDEA-170548 for details
        return "com.google.common.base.Predicate".equals(qualifiedName) || "com.google.common.base.Function".equals(qualifiedName);
    }

    /**
     * Returns the nullability of functional expression parameter
     *
     * @param function functional expression
     * @param index    parameter index
     * @return nullability, defined by SAM parameter annotations or known otherwise
     */
    public static Nullability getFunctionalParameterNullability(PsiFunctionalExpression function, int index) {
        PsiClassType type = ObjectUtil.tryCast(LambdaUtil.getFunctionalInterfaceType(function, true), PsiClassType.class);
        PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(type);
        if (sam != null) {
            PsiParameter parameter = sam.getParameterList().getParameter(index);
            if (parameter != null) {
                PsiType parameterType = type.resolveGenerics().getSubstitutor().substitute(parameter.getType());
                if (parameterType instanceof PsiWildcardType wildcardType) {
                    parameterType = wildcardType.getBound();
                }
                if (parameterType != null) {
                    TypeNullability typeNullability = parameterType.getNullability();
                    if (!typeNullability.equals(TypeNullability.UNKNOWN)) {
                        return typeNullability.nullability();
                    }
                }
                return getElementNullabilityForWrite(null, parameter);
            }
        }
        return Nullability.UNKNOWN;
    }

    /**
     * Returns nullability of variable when it's expected to write to it, or nullability of method
     * when it's expected to return a value from it.
     * For method parameter, it's expected that either the method is called (so the parameter is written by a call),
     * or the parameter is modified inside the method, like a local variable.
     * This method takes into account various sources of nullability information,
     * like method annotations, type annotations, container annotations, inferred annotations, or external annotations.
     *
     * @param resultType concrete type of particular variable access or method call (an instantiation of generic method return type,
     *                   or variable type), if known.
     * @param owner      method or variable to get its nullability
     * @return nullability of the owner; {@link Nullability#UNKNOWN} is both parameters are null.
     */
    public static @NotNull Nullability getElementNullabilityForWrite(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
        return getElementNullability(resultType, owner, false);
    }

    private static boolean isEnumPredefinedMethod(PsiMethod method) {
        return CallMatcher.enumValueOf().methodMatches(method) || CallMatcher.enumValues().methodMatches(method);
    }

    public static boolean isInitializedNotNull(PsiField field) {
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        PsiMethod[] constructors = containingClass.getConstructors();
        if (constructors.length == 0) {
            return false;
        }

        for (PsiMethod method : constructors) {
            if (!getNotNullInitializedFields(method, containingClass).contains(field)) {
                return false;
            }
        }
        return true;
    }

    private static Set<PsiField> getNotNullInitializedFields(final PsiMethod constructor, final PsiClass containingClass) {
        if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            return Collections.emptySet();
        }

        final PsiCodeBlock body = constructor.getBody();
        if (body == null) {
            return Collections.emptySet();
        }

        return LanguageCachedValueUtil.getCachedValue(constructor, new CachedValueProvider<>() {
            @Override
            public Result<Set<PsiField>> compute() {
                final PsiCodeBlock body = constructor.getBody();
                final Map<PsiField, Boolean> map = new HashMap<>();
                final DataFlowRunner dfaRunner = new DataFlowRunner(constructor.getProject()) {
                    PsiElement currentBlock;

                    private boolean isCallExposingNonInitializedFields(Instruction instruction) {
                        if (!(instruction instanceof MethodCallInstruction)) {
                            return false;
                        }

                        PsiCall call = ((MethodCallInstruction) instruction).getCallExpression();
                        if (call == null) {
                            return false;
                        }

                        if (call instanceof PsiNewExpression && canAccessFields((PsiExpression) call)) {
                            return true;
                        }

                        if (call instanceof PsiMethodCallExpression) {
                            PsiExpression qualifier = ((PsiMethodCallExpression) call).getMethodExpression().getQualifierExpression();
                            if (qualifier == null || canAccessFields(qualifier)) {
                                return true;
                            }
                        }

                        PsiExpressionList argumentList = call.getArgumentList();
                        if (argumentList != null) {
                            for (PsiExpression expression : argumentList.getExpressions()) {
                                if (canAccessFields(expression)) {
                                    return true;
                                }
                            }
                        }

                        return false;
                    }

                    private boolean canAccessFields(PsiExpression expression) {
                        PsiClass type = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
                        JBIterable<PsiClass> typeContainers =
                            JBIterable.generate(type, PsiClass::getContainingClass).takeWhile(c -> !c.hasModifierProperty(PsiModifier.STATIC));
                        return typeContainers.contains(containingClass);
                    }

                    @Override
                    protected List<DfaInstructionState> createInitialInstructionStates(PsiElement psiBlock,
                                                                                       Collection<? extends DfaMemoryState> memStates,
                                                                                       ControlFlow flow) {
                        currentBlock = psiBlock;
                        return super.createInitialInstructionStates(psiBlock, memStates, flow);
                    }

                    @Override
                    protected DfaInstructionState[] acceptInstruction(InstructionVisitor visitor,
                                                                      DfaInstructionState instructionState) {
                        Instruction instruction = instructionState.getInstruction();
                        if (currentBlock == body &&
                            (isCallExposingNonInitializedFields(instruction) ||
                                instruction instanceof ReturnInstruction && !((ReturnInstruction) instruction).isViaException())) {
                            for (PsiField field : containingClass.getFields()) {
                                if (!instructionState.getMemoryState().isNotNull(getFactory().getVarFactory().createVariableValue(field))) {
                                    map.put(field, false);
                                }
                                else if (!map.containsKey(field)) {
                                    map.put(field, true);
                                }
                            }
                            return DfaInstructionState.EMPTY_ARRAY;
                        }
                        return super.acceptInstruction(visitor, instructionState);
                    }
                };
                final RunnerResult rc = dfaRunner.analyzeMethod(body, new StandardInstructionVisitor());
                Set<PsiField> notNullFields = new HashSet<>();
                if (rc == RunnerResult.OK) {
                    for (Map.Entry<PsiField, Boolean> entry : map.entrySet()) {
                        if (entry.getValue()) {
                            notNullFields.add(entry.getKey());
                        }
                    }
                }
                return Result.create(notNullFields, constructor, PsiModificationTracker.MODIFICATION_COUNT);
            }
        });
    }

    public static List<PsiExpression> findAllConstructorInitializers(PsiField field) {
        final List<PsiExpression> result = Lists.newLockFreeCopyOnWriteList();
        ContainerUtil.addIfNotNull(result, field.getInitializer());

        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null && !(containingClass instanceof PsiCompiledElement)) {
            result.addAll(getAllConstructorFieldInitializers(containingClass).get(field));
        }
        return result;
    }

    private static MultiMap<PsiField, PsiExpression> getAllConstructorFieldInitializers(final PsiClass psiClass) {
        if (psiClass instanceof PsiCompiledElement) {
            return MultiMap.empty();
        }

        return LanguageCachedValueUtil.getCachedValue(psiClass, new CachedValueProvider<>() {
            @Override
            public Result<MultiMap<PsiField, PsiExpression>> compute() {
                final Set<String> fieldNames = new HashSet<>();
                for (PsiField field : psiClass.getFields()) {
                    ContainerUtil.addIfNotNull(fieldNames, field.getName());
                }

                final MultiMap<PsiField, PsiExpression> result = new MultiMap<>();
                JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
                        super.visitAssignmentExpression(assignment);
                        PsiExpression lExpression = assignment.getLExpression();
                        PsiExpression rExpression = assignment.getRExpression();
                        if (rExpression != null &&
                            lExpression instanceof PsiReferenceExpression &&
                            fieldNames.contains(((PsiReferenceExpression) lExpression).getReferenceName())) {
                            PsiElement target = ((PsiReferenceExpression) lExpression).resolve();
                            if (target instanceof PsiField && ((PsiField) target).getContainingClass() == psiClass) {
                                result.putValue((PsiField) target, rExpression);
                            }
                        }
                    }
                };

                for (PsiMethod constructor : psiClass.getConstructors()) {
                    if (constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
                        constructor.accept(visitor);
                    }
                }

                return Result.create(result, psiClass);
            }
        });
    }

    @Nullable
    public static PsiElement getTopmostBlockInSameClass(PsiElement position) {
        return JBIterable.
            generate(position, PsiElement::getParent).
            takeWhile(e -> !(e instanceof PsiMember || e instanceof PsiFile || e instanceof PsiLambdaExpression)).
            filter(e -> e instanceof PsiCodeBlock || e instanceof PsiExpression && e.getParent() instanceof PsiLambdaExpression).
            last();
    }

    public static Collection<PsiExpression> getVariableAssignmentsInFile(PsiVariable psiVariable,
                                                                         final boolean literalsOnly,
                                                                         final PsiElement place) {
        Ref<Boolean> modificationRef = Ref.create(Boolean.FALSE);
        PsiElement codeBlock = place == null ? null : getTopmostBlockInSameClass(place);
        int placeOffset = codeBlock != null ? place.getTextRange().getStartOffset() : 0;
        PsiFile containingFile = psiVariable.getContainingFile();
        LocalSearchScope scope = new LocalSearchScope(new PsiElement[]{containingFile}, null, true);
        Collection<PsiReference> references = ReferencesSearch.search(psiVariable, scope).findAll();
        List<PsiExpression> list = ContainerUtil.mapNotNull(
            references,
            (Function<PsiReference, PsiExpression>) psiReference -> {
                if (modificationRef.get()) {
                    return null;
                }
                final PsiElement parent = psiReference.getElement().getParent();
                if (parent instanceof PsiAssignmentExpression) {
                    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
                    final IElementType operation = assignmentExpression.getOperationTokenType();
                    if (assignmentExpression.getLExpression() == psiReference) {
                        if (JavaTokenType.EQ.equals(operation)) {
                            final PsiExpression rValue = assignmentExpression.getRExpression();
                            if (!literalsOnly || allOperandsAreLiterals(rValue)) {
                                // if there's a codeBlock omit the values assigned later
                                if (PsiTreeUtil.isAncestor(codeBlock, parent, true)
                                    && placeOffset < parent.getTextRange().getStartOffset()) {
                                    return null;
                                }
                                return rValue;
                            }
                            else {
                                modificationRef.set(Boolean.TRUE);
                            }
                        }
                        else if (JavaTokenType.PLUSEQ.equals(operation)) {
                            modificationRef.set(Boolean.TRUE);
                        }
                    }
                }
                return null;
            });
        if (modificationRef.get()) {
            return Collections.emptyList();
        }
        PsiExpression initializer = psiVariable.getInitializer();
        if (initializer != null && (!literalsOnly || allOperandsAreLiterals(initializer))) {
            list = ContainerUtil.concat(list, Collections.singletonList(initializer));
        }
        return list;
    }

    private static boolean allOperandsAreLiterals(@Nullable final PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof PsiLiteralExpression) {
            return true;
        }
        if (expression instanceof PsiPolyadicExpression) {
            Stack<PsiExpression> stack = new Stack<>();
            stack.add(expression);
            while (!stack.isEmpty()) {
                PsiExpression psiExpression = stack.pop();
                if (psiExpression instanceof PsiPolyadicExpression) {
                    PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression) psiExpression;
                    for (PsiExpression op : binaryExpression.getOperands()) {
                        stack.push(op);
                    }
                }
                else if (!(psiExpression instanceof PsiLiteralExpression)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @param method method to check
     * @return nullability of vararg parameter component; {@link Nullability#UNKNOWN} if not specified or method is not vararg method.
     */
    static Nullability getVarArgComponentNullability(PsiMethod method) {
        if (method != null) {
            if (NON_NULL_VAR_ARG.methodMatches(method)) {
                return Nullability.NOT_NULL;
            }
            PsiParameter varArg = ArrayUtil.getLastElement(method.getParameterList().getParameters());
            if (varArg != null) {
                PsiType type = varArg.getType();
                if (type instanceof PsiEllipsisType) {
                    PsiType componentType = ((PsiEllipsisType) type).getComponentType();
                    return getTypeNullability(componentType);
                }
            }
        }
        return Nullability.UNKNOWN;
    }

    /**
     * Try to restore type parameters based on the expression type
     *
     * @param expression expression which type is a supertype of the type to generify
     * @param type       a type to generify
     * @return a generified type, or original type if generification is not possible
     */
    public static PsiType tryGenerify(PsiExpression expression, PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return type;
        }
        PsiClassType classType = (PsiClassType) type;
        if (!classType.isRaw()) {
            return classType;
        }
        PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
            return classType;
        }
        PsiType expressionType = expression.getType();
        if (!(expressionType instanceof PsiClassType)) {
            return classType;
        }
        PsiClassType result = GenericsUtil.getExpectedGenericType(expression, psiClass, (PsiClassType) expressionType);
        if (result.isRaw()) {
            PsiClass aClass = result.resolve();
            if (aClass != null) {
                int length = aClass.getTypeParameters().length;
                PsiWildcardType wildcard = PsiWildcardType.createUnbounded(aClass.getManager());
                PsiType[] arguments = new PsiType[length];
                Arrays.fill(arguments, wildcard);
                return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, arguments);
            }
        }
        return result;
    }

    /**
     * @param expr literal to create a constant type from
     * @return a DfType that describes given literal
     */
    public static DfType fromLiteral(PsiLiteralExpression expr) {
        PsiType type = expr.getType();
        if (type == null) {
            return DfTypes.TOP;
        }
        if (PsiType.NULL.equals(type)) {
            return DfTypes.NULL;
        }
        Object value = expr.getValue();
        if (value == null) {
            return DfTypes.typedObject(type, Nullability.NOT_NULL);
        }
        return DfTypes.constant(value, type);
    }
}
