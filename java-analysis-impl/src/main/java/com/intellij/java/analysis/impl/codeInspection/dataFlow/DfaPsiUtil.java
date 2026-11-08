// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.util.collection.Lists;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.DataflowNullabilityContributor;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullabilitySource;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.codeInsight.TypeNullability;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import consulo.language.dataFlow.interpreter.RunnerResult;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import consulo.language.dataFlow.interpreter.DfaListener;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.ReturnInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.analysis.impl.codeInspection.util.OptionalUtil;
import com.intellij.java.language.JavaLanguage;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.GenericsUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.LambdaUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAssertStatement;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiCapturedWildcardType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiForeachStatement;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiRecordComponent;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.PsiWildcardType;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.impl.psi.impl.light.LightCompactConstructorParameter;
import com.intellij.java.language.impl.psi.impl.light.LightRecordField;
import consulo.language.psi.scope.LocalSearchScope;
import com.intellij.java.language.psi.search.searches.DeepestSuperMethodsSearch;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.ast.IElementType;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.collection.ArrayUtil;
import java.util.function.Function;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.collection.MultiMap;
import consulo.util.collection.Stack;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.BoolUtils;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_MAP;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_SET;
import static consulo.util.lang.ObjectUtil.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class DfaPsiUtil {
  private static final Logger LOG = Logger.getInstance(DfaPsiUtil.class);

  private static final CallMatcher NON_NULL_VAR_ARG = CallMatcher.anyOf(
    staticCall(JAVA_UTIL_LIST, "of"),
    staticCall(JAVA_UTIL_SET, "of"),
    staticCall(JAVA_UTIL_MAP, "ofEntries"));

  public static boolean isFinalField(PsiVariable var) {
    return var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && var instanceof PsiField;
  }

  /**
   * Returns nullability of variable or method. This method takes into account various sources of nullability information,
   * like method annotations, type annotations, container annotations, inferred annotations, or external annotations.
   * 
   * @param resultType concrete type of particular variable access or method call (an instantiation of generic method return type, 
   *                   or variable type), if known.
   * @param owner method or variable to get its nullability
   * @return nullability of the owner; {@link Nullability#UNKNOWN} is both parameters are null.
   * @deprecated behaves like {@link #getElementNullabilityForWrite(PsiType, PsiModifierListOwner)}. 
   * Use either {@link #getElementNullabilityForWrite(PsiType, PsiModifierListOwner)} 
   * or {@link #getElementNullabilityForRead(PsiType, PsiModifierListOwner)} instead.
   */
  @Deprecated
  public static Nullability getElementNullability(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, false);
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
   * @param owner method or variable to get its nullability
   * @return nullability of the owner; {@link Nullability#UNKNOWN} is both parameters are null.
   */
  public static Nullability getElementNullabilityForWrite(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, false);
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
   * @param owner method or variable to get its nullability
   * @return nullability of the owner; {@link Nullability#UNKNOWN} is both parameters are null.
   */
  public static Nullability getElementNullabilityForRead(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, true);
  }

  private static Nullability getElementNullability(@Nullable PsiType resultType,
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
    if (forRead && (fromAnnotation == null || fromAnnotation.isContainer() || fromAnnotation.isInferred())) {
      Nullability contributed = contributeNullability(owner);
      if (contributed != null) return contributed;
    }
    if (fromAnnotation != null) {
      if (resultType != null && fromAnnotation.getNullability() != Nullability.NOT_NULL) {
        PsiType type = PsiUtil.getTypeByPsiElement(owner);
        if (type != null) {
          // The nullability comes from the type parameter bound or from the type parameter declaration itself,
          // not from this use site.
          if (PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter tp &&
              fromAnnotation.isExtendedBounds() &&
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

  private static @Nullable Nullability getNullabilityFromType(@Nullable PsiType resultType, PsiModifierListOwner owner) {
    if (resultType == null) return null;
    TypeNullability typeNullability = resultType.getNullability();
    if (typeNullability.equals(TypeNullability.UNKNOWN)) return null;
    Nullability fromType = typeNullability.nullability();
    if (fromType == Nullability.NOT_NULL && hasNullContract(owner)) {
      return Nullability.UNKNOWN;
    }
    return fromType;
  }

  private static boolean hasNullContract(PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
      int index = method.getParameterList().getParameterIndex(parameter);
      List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
      return ContainerUtil.exists(contracts,
                               c -> c.getParameterConstraint(index) == StandardMethodContract.ValueConstraint.NULL_VALUE);
    }
    return false;
  }

  private static @Nullable NullabilityAnnotationInfo getNullabilityFromAnnotation(PsiModifierListOwner owner,
                                                                                  boolean ignoreParameterNullabilityInference) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info != null && shouldIgnoreAnnotation(info.getAnnotation())) {
      return null;
    }
    if (ignoreParameterNullabilityInference && owner instanceof PsiParameter parameter &&
        (info == null || info.isInferred())) {
      // Nullability inferred from the method body is unreliable when the same body is analyzed,
      // so rely on an explicit @Nullable on a super parameter instead (IDEA-228079).
      // Note that the parameter may have no inferred annotation at all (e.g., if the inference bailed out),
      // and the super annotation must be honored in this case as well.
      return getNullableFromSuperParameter(manager, parameter);
    }
    return info;
  }

  private static @Nullable NullabilityAnnotationInfo getNullableFromSuperParameter(NullableNotNullManager manager,
                                                                                   PsiParameter parameter) {
    List<PsiParameter> supers = AnnotationUtil.getSuperAnnotationOwners(parameter);
    return StreamEx.of(supers).map(param -> manager.findEffectiveNullabilityInfo(param))
      .findFirst(i -> i != null && i.getInheritedFrom() == null && i.getNullability() == Nullability.NULLABLE &&
                      !shouldIgnoreAnnotation(i.getAnnotation()))
      .orElse(null);
  }

  private static boolean isMapMethodWithUnknownNullity(PsiMethod method) {
    String name = method.getName();
    if (!"get".equals(name) && !"remove".equals(name)) return false;
    PsiMethod superMethod = DeepestSuperMethodsSearch.search(method).findFirst();
    return ("java.util.Map." + name).equals(PsiUtil.getMemberQualifiedName(superMethod != null ? superMethod : method));
  }

  public static Nullability inferParameterNullability(PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiLambdaExpression) {
        return getFunctionalParameterNullability((PsiLambdaExpression)gParent, ((PsiParameterList)parent).getParameterIndex(parameter));
      } else if (gParent instanceof PsiMethod && OptionalUtil.OPTIONAL_OF_NULLABLE.methodMatches((PsiMethod)gParent)) {
        return Nullability.NULLABLE;
      }
    }
    if (parent instanceof PsiForeachStatement) {
      return getTypeNullability(inferLoopParameterTypeWithNullability((PsiForeachStatement)parent));
    }
    return Nullability.UNKNOWN;
  }

  private static @Nullable PsiType inferLoopParameterTypeWithNullability(PsiForeachStatement loop) {
    PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(loop.getIteratedValue());
    if (iteratedValue == null) return null;

    PsiType iteratedType = iteratedValue.getType();
    if (iteratedValue instanceof PsiReferenceExpression refExpr &&
        refExpr.resolve() instanceof PsiParameter parameter &&
        parameter.getParent() instanceof PsiForeachStatement targetLoop &&
        PsiTreeUtil.isAncestor(targetLoop, loop, true) &&
        !ControlFlowUtil.isReassigned(parameter, new HashMap<>())) {
      iteratedType = inferLoopParameterTypeWithNullability(targetLoop);
    }
    return JavaGenericsUtil.getCollectionItemType(iteratedType, iteratedValue.getResolveScope());
  }

  private static Nullability getTypeNullability(@Nullable PsiType type, boolean forRead) {
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

  /**
   * A contributed nullability is weaker than an annotation that the author wrote, and stronger than a container default or an inference.
   */
  private static @Nullable Nullability contributeNullability(PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter parameter && !(parameter.getDeclarationScope() instanceof PsiMethod)) return null;
    Nullability nullability = DataflowNullabilityContributor.findNullability(owner);
    if (nullability == null || nullability == Nullability.UNKNOWN) return null;
    // NullabilityUtil uses a contributed not-null value of a field only when the code never writes the field
    if (nullability == Nullability.NOT_NULL && owner instanceof PsiField) return null;
    return nullability;
  }

  public static Nullability getTypeNullability(@Nullable PsiType type) {
    if (type == null) return Nullability.UNKNOWN;
    return type.getNullability().nullability();
  }

  public static @Nullable NullabilityAnnotationInfo getTypeNullabilityInfo(@Nullable PsiType type) {
    if (type == null || type instanceof PsiPrimitiveType) return null;
    return type.getNullability().toNullabilityAnnotationInfo();
  }

  private static boolean shouldIgnoreAnnotation(PsiAnnotation annotation) {
    PsiClass containingClass = PsiUtil.getContainingClass(annotation);
    if (containingClass == null || !containingClass.isValid()) return false;
    String qualifiedName = containingClass.getQualifiedName();
    // We deliberately ignore nullability annotations on Guava functional interfaces to avoid noise warnings
    // See IDEA-170548 for details
    return "com.google.common.base.Predicate".equals(qualifiedName) || "com.google.common.base.Function".equals(qualifiedName);
  }

  /**
   * Returns the nullability of functional expression parameter
   *
   * @param function functional expression
   * @param index parameter index
   * @return nullability, defined by SAM parameter annotations or known otherwise
   */
  public static Nullability getFunctionalParameterNullability(PsiFunctionalExpression function, int index) {
    PsiClassType type = tryCast(LambdaUtil.getFunctionalInterfaceType(function, true), PsiClassType.class);
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

  private static boolean isEnumPredefinedMethod(PsiMethod method) {
    return CallMatcher.enumValueOf().methodMatches(method) || CallMatcher.enumValues().methodMatches(method);
  }

  public static boolean isInitializedNotNull(PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return false;

    boolean staticField = field.hasModifierProperty(PsiModifier.STATIC);
    for (PsiClassInitializer initializer : containingClass.getInitializers()) {
      if (initializer.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
          initializer.hasModifierProperty(PsiModifier.STATIC) == staticField &&
          getBlockNotNullFields(initializer.getBody()).contains(field)) {
        return true;
      }
    }
    if (staticField) return false;

    PsiMethod[] constructors = containingClass.getConstructors();
    if (constructors.length == 0) return false;

    for (PsiMethod method : constructors) {
      if (!method.getLanguage().isKindOf(JavaLanguage.INSTANCE) ||
          !getBlockNotNullFields(method.getBody()).contains(field)) {
        return false;
      }
    }
    return true;
  }

  private static Set<PsiField> getBlockNotNullFields(PsiCodeBlock body) {
    if (body == null) return Collections.emptySet();

    return LanguageCachedValueUtil.getCachedValue(body, new CachedValueProvider<>() {
      @Override
      public Result<Set<PsiField>> compute() {
        PsiClass containingClass = PsiTreeUtil.getContextOfType(body, PsiClass.class);
        LOG.assertTrue(containingClass != null);
        DfaValueFactory factory = new DfaValueFactory(body.getProject());
        ControlFlow flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
        if (flow == null) {
          return Result.create(Set.of(), body, PsiModificationTracker.MODIFICATION_COUNT);
        }
        flow.keepVariables(
          desc ->
            desc instanceof PlainDescriptor plainDescriptor && plainDescriptor.getPsiElement() instanceof PsiField field &&
            field.getContainingClass() == containingClass);
        var interpreter = new StandardDataFlowInterpreter(flow, DfaListener.EMPTY) {
          final Map<PsiField, Boolean> map = new HashMap<>();
          private boolean isCallExposingNonInitializedFields(Instruction instruction) {
            if (!(instruction instanceof MethodCallInstruction)) {
              return false;
            }

            PsiCall call = ((MethodCallInstruction)instruction).getCallExpression();
            if (call == null) return false;

            if (call instanceof PsiNewExpression && canAccessFields((PsiExpression)call)) {
              return true;
            }

            if (call instanceof PsiMethodCallExpression) {
              PsiExpression qualifier = ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
              if (qualifier == null || canAccessFields(qualifier)) {
                return true;
              }
            }

            PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList != null) {
              for (PsiExpression expression : argumentList.getExpressions()) {
                if (canAccessFields(expression)) return true;
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
          protected DfaInstructionState [] acceptInstruction(DfaInstructionState instructionState) {
            Instruction instruction = instructionState.getInstruction();
            if ((isCallExposingNonInitializedFields(instruction) || instruction instanceof ReturnInstruction)) {
              for (PsiField field : containingClass.getFields()) {
                DfaVariableValue value = PlainDescriptor.createVariableValue(getFactory(), field);
                DfType dfType = instructionState.getMemoryState().getDfType(value);
                if (dfType.isSuperType(DfTypes.NULL)) {
                  map.put(field, false);
                }
                else if (!map.containsKey(field)) {
                  map.put(field, true);
                }
              }
              return DfaInstructionState.EMPTY_ARRAY;
            }
            return super.acceptInstruction(instructionState);
          }
        };
        DfaMemoryState state = DfaUtil.createStateWithEnabledAssertions(factory);
        final RunnerResult rc = interpreter.interpret(state);
        Set<PsiField> notNullFields = new HashSet<>();
        if (rc == RunnerResult.OK) {
          interpreter.map.forEach((key, value) -> {
            if (value) {
              notNullFields.add(key);
            }
          });
        }
        return Result.create(notNullFields, body, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  /**
   * Finds all initializers of the specified field inside constructors and class/instance initializers.
   * Includes the field's own initializer, if any.
   *
   * @param field  the field find initializers for
   * @return the initializers found.
   */
  public static List<PsiExpression> findAllConstructorInitializers(PsiField field) {
    final List<PsiExpression> result = Lists.newLockFreeCopyOnWriteList();
    ContainerUtil.addIfNotNull(result, field.getInitializer());

    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !(containingClass instanceof PsiCompiledElement)) {
      result.addAll(getAllConstructorFieldInitializers(containingClass).get(field));
    }
    return result;
  }

  /**
   * Finds all initializers of the specified record component.
   * This includes the right-side of assignments in a compact constructor to the implicit parameter
   * corresponding to the specified component.
   * Since a record component is implicitly final, all of its initializers will be inside a constructor (when the code is compilable).
   *
   * @param component  the record component to find initializers for
   * @return the initializers found.
   */
  static List<PsiExpression> findAllConstructorInitializers(PsiRecordComponent component) {
    PsiClass containingClass = component.getContainingClass();
    return containingClass == null || containingClass instanceof PsiCompiledElement
           ? Collections.emptyList()
           : new ArrayList<>(getAllConstructorFieldInitializers(containingClass).get(component));
  }

  private static MultiMap<PsiVariable, PsiExpression> getAllConstructorFieldInitializers(PsiClass psiClass) {
    if (psiClass instanceof PsiCompiledElement) {
      return MultiMap.empty();
    }

    return LanguageCachedValueUtil.getCachedValue(psiClass, new CachedValueProvider<>() {
      @Override
      public Result<MultiMap<PsiVariable, PsiExpression>> compute() {
        final Set<String> fieldNames = new HashSet<>();
        for (PsiField field : psiClass.getFields()) {
          ContainerUtil.addIfNotNull(fieldNames, field.getName());
        }

        final MultiMap<PsiVariable, PsiExpression> result = new MultiMap<>();
        JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            PsiExpression rExpression = assignment.getRExpression();
            if (rExpression != null &&
                assignment.getLExpression() instanceof PsiReferenceExpression ref &&
                fieldNames.contains(ref.getReferenceName())) {
              PsiElement target = ref.resolve();
              if (target instanceof PsiField field && field.getContainingClass() == psiClass) {
                result.putValue(field instanceof LightRecordField f ? f.getRecordComponent() : field, rExpression);
              }
              else if (target instanceof LightCompactConstructorParameter parameter) {
                result.putValue(parameter.getRecordComponent(), rExpression);
              }
            }
          }
        };

        for (PsiMethod constructor : psiClass.getConstructors()) {
          if (constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            constructor.accept(visitor);
          }
        }

        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          if (initializer.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            initializer.accept(visitor);
          }
        }

        return Result.create(result, psiClass);
      }
    });
  }

  public static @Nullable PsiElement getTopmostBlockInSameClass(PsiElement position) {
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
    PsiElement codeBlock = place == null? null : getTopmostBlockInSameClass(place);
    int placeOffset = codeBlock != null? place.getTextRange().getStartOffset() : 0;
    PsiFile containingFile = psiVariable.getContainingFile();
    LocalSearchScope scope = new LocalSearchScope(new PsiElement[]{containingFile}, null, true);
    Collection<PsiReference> references = ReferencesSearch.search(psiVariable, scope).findAll();
    List<PsiExpression> list = ContainerUtil.mapNotNull(
      references,
      (Function<PsiReference, PsiExpression>)psiReference -> {
        if (modificationRef.get()) return null;
        final PsiElement parent = psiReference.getElement().getParent();
        if (parent instanceof PsiAssignmentExpression assignmentExpression) {
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
    if (modificationRef.get()) return Collections.emptyList();
    PsiExpression initializer = psiVariable.getInitializer();
    if (initializer != null && (!literalsOnly || allOperandsAreLiterals(initializer))) {
      list = ContainerUtil.concat(list, Collections.singletonList(initializer));
    }
    return list;
  }

  private static boolean allOperandsAreLiterals(final @Nullable PsiExpression expression) {
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression) return true;
    if (expression instanceof PsiPolyadicExpression) {
      Stack<PsiExpression> stack = new Stack<>();
      stack.add(expression);
      while (!stack.isEmpty()) {
        PsiExpression psiExpression = stack.pop();
        if (psiExpression instanceof PsiPolyadicExpression binaryExpression) {
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
          PsiType componentType = ((PsiEllipsisType)type).getComponentType();
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
   * @param type a type to generify
   * @return a generified type, or original type if generification is not possible
   */
  public static PsiType tryGenerify(PsiExpression expression, PsiType type) {
    if (!(type instanceof PsiClassType classType)) {
      return type;
    }
    if (!classType.isRaw()) {
      return classType;
    }
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return classType;
    if (!(expression.getType() instanceof PsiClassType expressionType)) return classType;
    PsiClassType result = GenericsUtil.getExpectedGenericType(expression, psiClass, expressionType);
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
    return GenericsUtil.getVariableTypeByExpressionType(result);
  }

  /**
   * @param expr literal to create a constant type from
   * @return a DfType that describes given literal
   */
  public static DfType fromLiteral(PsiLiteralExpression expr) {
    PsiType type = expr.getType();
    if (type == null) return DfType.TOP;
    if (PsiTypes.nullType().equals(type)) return DfTypes.NULL;
    Object value = expr.getValue();
    if (value == null) return DfTypes.typedObject(type, Nullability.NOT_NULL);
    return DfTypes.constant(value, type);
  }

  /**
   * Create RelationType from Java token
   * @param type Java token
   * @return RelationType
   */
  public static @Nullable RelationType getRelationByToken(IElementType type) {
    if(JavaTokenType.EQEQ.equals(type)) {
      return RelationType.EQ;
    }
    if(JavaTokenType.NE.equals(type)) {
      return RelationType.NE;
    }
    if(JavaTokenType.LT.equals(type)) {
      return RelationType.LT;
    }
    if(JavaTokenType.GT.equals(type)) {
      return RelationType.GT;
    }
    if(JavaTokenType.LE.equals(type)) {
      return RelationType.LE;
    }
    if(JavaTokenType.GE.equals(type)) {
      return RelationType.GE;
    }
    if(JavaTokenType.INSTANCEOF_KEYWORD.equals(type)) {
      return RelationType.IS;
    }
    return null;
  }

  /**
   * Tries to convert {@link DfType} to {@link PsiType}.
   *
   * @param project project
   * @param dfType DfType to convert
   * @return converted DfType; null if no corresponding PsiType found.
   */
  public static @Nullable PsiType dfTypeToPsiType(Project project, @Nullable DfType dfType) {
    if (dfType instanceof DfPrimitiveType) {
      return ((DfPrimitiveType)dfType).getPsiType();
    }
    if (dfType instanceof DfReferenceType) {
      return ((DfReferenceType)dfType).getConstraint().getPsiType(project);
    }
    return null;
  }

  /**
   * @param value constant value
   * @return human readable representation of the value
   */
  public static String renderValue(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return '"' + StringUtil.escapeStringCharacters((String)value) + '"';
    if (value instanceof Float) return value + "f";
    if (value instanceof Long) return value + "L";
    if (value instanceof PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      return containingClass == null ? field.getName() : containingClass.getName() + "." + field.getName();
    }
    if (value instanceof PsiType type) {
      return type.getPresentableText();
    }
    return value.toString();
  }

  /**
   * @param anchor boolean expression
   * @param expectedValue the expected result of boolean expression
   * @return true if this boolean expression is effectively an assertion (code throws if its value is not equal to expectedValue)
   */
  public static boolean isAssertionEffectively(PsiExpression anchor, boolean expectedValue) {
    PsiElement parent;
    while (true) {
      parent = anchor.getParent();
      if (parent instanceof PsiExpression parentExpr && BoolUtils.isNegation(parentExpr)) {
        expectedValue = !expectedValue;
        anchor = parentExpr;
        continue;
      }
      if (parent instanceof PsiParenthesizedExpression parenthesized) {
        anchor = parenthesized;
        continue;
      }
      if (parent instanceof PsiPolyadicExpression polyadic) {
        IElementType tokenType = polyadic.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          // always true operand makes always true OR-chain and does not affect the result of AND-chain
          // Note that in `assert unknownExpression && trueExpression;` the trueExpression should not be reported
          // because this assertion is essentially the shortened `assert unknownExpression; assert trueExpression;`
          // which is not reported.
          boolean causesShortCircuit = (tokenType.equals(JavaTokenType.OROR) == expectedValue) &&
                                       ArrayUtil.getLastElement(polyadic.getOperands()) != anchor;
          if (!causesShortCircuit) {
            // We still report `assert trueExpression || unknownExpression`, because here `unknownExpression` is never checked
            // which is probably not intended.
            anchor = polyadic;
            continue;
          }
        }
      }
      break;
    }
    if (parent instanceof PsiAssertStatement) {
      return expectedValue;
    }
    if (parent instanceof PsiIfStatement ifStatement && anchor == ifStatement.getCondition()) {
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if (thenBranch instanceof PsiThrowStatement) {
        return !expectedValue;
      }
    }
    return isAssertCallArgument(anchor, ContractValue.booleanValue(expectedValue));
  }

  /**
   * @param anchor expression
   * @param wantedConstraint expected contract value
   * @return true if this expression is an argument for an assertion call that ensures that the expression is equal to the expected value
   */
  public static boolean isAssertCallArgument(PsiElement anchor, ContractValue wantedConstraint) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiExpressionList) {
      int index = ArrayUtil.indexOf(((PsiExpressionList)parent).getExpressions(), anchor);
      if (index >= 0) {
        PsiMethodCallExpression call = tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (call != null) {
          MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(call));
          if (contract != null && contract.getReturnValue().isFail()) {
            ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
            if (condition != null) {
              return condition.getArgumentComparedTo(wantedConstraint, false).orElse(-1) == index;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * @param cond1 first condition
   * @param cond2 second condition
   * @return true if conditions cannot be true at the same time.
   */
  public static boolean mutuallyExclusive(PsiExpression cond1, PsiExpression cond2) {
    Project project = cond1.getProject();
    PsiPolyadicExpression expr =
      (PsiPolyadicExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a && b", cond1);
    PsiExpression[] operands = expr.getOperands();
    operands[0].replace(cond1);
    operands[1].replace(cond2);
    DfaValueFactory factory = new DfaValueFactory(project);
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(expr, factory, true);
    if (flow == null) return false;
    var listener = new JavaDfaListener() {
      final Set<DfType> results = new HashSet<>();

      @Override
      public void beforeExpressionPush(DfaValue value, PsiExpression expression, DfaMemoryState state) {
        if (expression == expr) {
          results.add(value.getDfType());
        }
      }
    };
    StandardDataFlowInterpreter interpreter = new StandardDataFlowInterpreter(flow, listener);
    if (interpreter.interpret(new DfaMemoryStateImpl(factory)) != RunnerResult.OK) return false;
    return DfTypes.FALSE.equals(ContainerUtil.getOnlyItem(listener.results));
  }
}
