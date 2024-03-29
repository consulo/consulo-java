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
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
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
import consulo.util.collection.Stack;
import consulo.util.collection.*;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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

  @Nonnull
  public static Nullability getElementNullability(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, false);
  }

  @Nonnull
  public static Nullability getElementNullabilityIgnoringParameterInference(@Nullable PsiType resultType,
                                                                            @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, true);
  }

  @Nonnull
  private static Nullability getElementNullability(@Nullable PsiType resultType,
                                                   @Nullable PsiModifierListOwner owner,
                                                   boolean ignoreParameterNullabilityInference) {
    if (owner == null) {
      return getTypeNullability(resultType);
    }

    if (resultType instanceof PsiPrimitiveType) {
      return Nullability.UNKNOWN;
    }

    if (owner instanceof PsiEnumConstant || PsiUtil.isAnnotationMethod(owner)) {
      return Nullability.NOT_NULL;
    }
    if (owner instanceof PsiMethod && isEnumPredefinedMethod((PsiMethod) owner)) {
      return Nullability.NOT_NULL;
    }

    Nullability fromAnnotation = getNullabilityFromAnnotation(owner, ignoreParameterNullabilityInference);
    if (fromAnnotation != Nullability.UNKNOWN) {
      return fromAnnotation;
    }

    if (owner instanceof PsiMethod && isMapMethodWithUnknownNullity((PsiMethod) owner)) {
      return getTypeNullability(resultType) == Nullability.NULLABLE ? Nullability.NULLABLE : Nullability.UNKNOWN;
    }

    Nullability fromType = getTypeNullability(resultType);
    if (fromType != Nullability.UNKNOWN) {
      return fromType;
    }

    if (owner instanceof PsiParameter) {
      return inferParameterNullability((PsiParameter) owner);
    }

    if (owner instanceof PsiMethod && ((PsiMethod) owner).getParameterList().isEmpty()) {
      PsiField field = PropertyUtil.getFieldOfGetter((PsiMethod) owner);
      if (field != null && getElementNullability(resultType, field) == Nullability.NULLABLE) {
        return Nullability.NULLABLE;
      }
    }

    return Nullability.UNKNOWN;
  }

  @Nonnull
  private static Nullability getNullabilityFromAnnotation(PsiModifierListOwner owner, boolean ignoreParameterNullabilityInference) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info == null || shouldIgnoreAnnotation(info.getAnnotation())) {
      return Nullability.UNKNOWN;
    }
    if (ignoreParameterNullabilityInference && owner instanceof PsiParameter && AnnotationUtil.isInferredAnnotation(info.getAnnotation())) {
      List<PsiParameter> supers = AnnotationUtil.getSuperAnnotationOwners((PsiParameter) owner);
      return ContainerUtil.exists(supers, each -> manager.isNullable(each, false)) ? Nullability.NULLABLE : Nullability.UNKNOWN;
    }
    return info.getNullability();
  }

  private static boolean isMapMethodWithUnknownNullity(@Nonnull PsiMethod method) {
    String name = method.getName();
    if (!"get".equals(name) && !"remove".equals(name)) {
      return false;
    }
    PsiMethod superMethod = DeepestSuperMethodsSearch.search(method).findFirst();
    return ("java.util.Map." + name).equals(PsiUtil.getMemberQualifiedName(superMethod != null ? superMethod : method));
  }

  @Nonnull
  public static Nullability inferParameterNullability(@Nonnull PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiLambdaExpression) {
        return getFunctionalParameterNullability((PsiLambdaExpression) gParent, ((PsiParameterList) parent).getParameterIndex(parameter));
      } else if (gParent instanceof PsiMethod && OptionalUtil.OPTIONAL_OF_NULLABLE.methodMatches((PsiMethod) gParent)) {
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


  @Nonnull
  public static Nullability getTypeNullability(@Nullable PsiType type) {
    NullabilityAnnotationInfo info = getTypeNullabilityInfo(type);
    return info == null ? Nullability.UNKNOWN : info.getNullability();
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
  @Nonnull
  public static Nullability getFunctionalParameterNullability(PsiFunctionalExpression function, int index) {
    Nullability nullability = inferLambdaParameterNullability(function, index);
    if (nullability != Nullability.UNKNOWN) {
      return nullability;
    }
    PsiClassType type = ObjectUtil.tryCast(LambdaUtil.getFunctionalInterfaceType(function, true), PsiClassType.class);
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(type);
    if (sam != null) {
      PsiParameter parameter = sam.getParameterList().getParameter(index);
      if (parameter != null) {
        nullability = getElementNullability(null, parameter);
        if (nullability != Nullability.UNKNOWN) {
          return nullability;
        }
        PsiType parameterType = type.resolveGenerics().getSubstitutor().substitute(parameter.getType());
        return getTypeNullability(GenericsUtil.eliminateWildcards(parameterType, false, true));
      }
    }
    return Nullability.UNKNOWN;
  }

  @Nonnull
  private static Nullability inferLambdaParameterNullability(PsiFunctionalExpression lambda, int parameterIndex) {
    PsiElement expression = lambda;
    PsiElement expressionParent = lambda.getParent();
    while (expressionParent instanceof PsiConditionalExpression || expressionParent instanceof PsiParenthesizedExpression) {
      expression = expressionParent;
      expressionParent = expressionParent.getParent();
    }
    if (expressionParent instanceof PsiExpressionList) {
      PsiExpressionList list = (PsiExpressionList) expressionParent;
      PsiElement listParent = list.getParent();
      if (listParent instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression) listParent).resolveMethod();
        if (method != null) {
          int expressionIndex = ArrayUtil.find(list.getExpressions(), expression);
          return getLambdaParameterNullability(method, expressionIndex, parameterIndex);
        }
      }
    }
    return Nullability.UNKNOWN;
  }

  @Nonnull
  private static Nullability getLambdaParameterNullability(@Nonnull PsiMethod method, int parameterIndex, int lambdaParameterIndex) {
    PsiClass type = method.getContainingClass();
    if (type != null) {
      if (JAVA_UTIL_OPTIONAL.equals(type.getQualifiedName())) {
        String methodName = method.getName();
        if ((methodName.equals("map") || methodName.equals("filter") || methodName.equals("ifPresent") || methodName.equals("flatMap"))
            && parameterIndex == 0 && lambdaParameterIndex == 0) {
          return Nullability.NOT_NULL;
        }
      }
    }
    return Nullability.UNKNOWN;
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
      @Nonnull
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
          protected
          @Nonnull
          List<DfaInstructionState> createInitialInstructionStates(@Nonnull PsiElement psiBlock,
                                                                   @Nonnull Collection<? extends DfaMemoryState> memStates,
                                                                   @Nonnull ControlFlow flow) {
            currentBlock = psiBlock;
            return super.createInitialInstructionStates(psiBlock, memStates, flow);
          }

          @Override
          protected DfaInstructionState[] acceptInstruction(@Nonnull InstructionVisitor visitor,
                                                            @Nonnull DfaInstructionState instructionState) {
            Instruction instruction = instructionState.getInstruction();
            if (currentBlock == body &&
                (isCallExposingNonInitializedFields(instruction) ||
                    instruction instanceof ReturnInstruction && !((ReturnInstruction) instruction).isViaException())) {
              for (PsiField field : containingClass.getFields()) {
                if (!instructionState.getMemoryState().isNotNull(getFactory().getVarFactory().createVariableValue(field))) {
                  map.put(field, false);
                } else if (!map.containsKey(field)) {
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
      @Nonnull
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
  public static PsiElement getTopmostBlockInSameClass(@Nonnull PsiElement position) {
    return JBIterable.
        generate(position, PsiElement::getParent).
        takeWhile(e -> !(e instanceof PsiMember || e instanceof PsiFile || e instanceof PsiLambdaExpression)).
        filter(e -> e instanceof PsiCodeBlock || e instanceof PsiExpression && e.getParent() instanceof PsiLambdaExpression).
        last();
  }

  @Nonnull
  public static Collection<PsiExpression> getVariableAssignmentsInFile(@Nonnull PsiVariable psiVariable,
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
                } else {
                  modificationRef.set(Boolean.TRUE);
                }
              } else if (JavaTokenType.PLUSEQ.equals(operation)) {
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
        } else if (!(psiExpression instanceof PsiLiteralExpression)) {
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
  @Nonnull
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
  @Nonnull
  public static DfType fromLiteral(@Nonnull PsiLiteralExpression expr) {
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
