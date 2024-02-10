// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionManager;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;

/**
 * A facade for all inference algorithms which work on Java source code (Light AST) and cache results in the index.
 */
public class JavaSourceInference {
  public static final int MAX_CONTRACT_COUNT = 10;
  private static final Logger LOG = Logger.getInstance(JavaSourceInference.class);

  enum InferenceMode {
    DISABLED,
    ENABLED,
    PARAMETERS
  }

  private static class MethodInferenceData {
    static final MethodInferenceData UNKNOWN =
        new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(), false, new BitSet());

    final
    @Nonnull
    Mutability myMutability;
    final
    @Nonnull
    Nullability myNullability;
    final
    @Nonnull
    List<StandardMethodContract> myContracts;
    final boolean myPure;
    final
    @Nonnull
    BitSet myNotNullParameters;

    MethodInferenceData(@Nonnull Mutability mutability,
                        @Nonnull Nullability nullability,
                        @Nonnull List<StandardMethodContract> contracts,
                        boolean pure,
                        @Nonnull BitSet parameters) {
      myMutability = mutability;
      myNullability = nullability;
      myContracts = contracts;
      myPure = pure;
      myNotNullParameters = parameters;
    }
  }

  @Nonnull
  private static MethodInferenceData infer(PsiMethodImpl method) {
    InferenceMode mode = getInferenceMode(method);
    if (mode == InferenceMode.DISABLED ||
        mode == InferenceMode.PARAMETERS && method.getParameterList().isEmpty()) {
      return MethodInferenceData.UNKNOWN;
    }

    MethodData data = ContractInferenceIndexKt.getIndexedData(method);
    if (data == null) {
      return MethodInferenceData.UNKNOWN;
    }
    BitSet notNullParameters = data.getNotNullParameters();
    if (mode == InferenceMode.PARAMETERS) {
      // Infer parameters nullability only (for unstable methods)
      return notNullParameters.isEmpty() ? MethodInferenceData.UNKNOWN :
          new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(), false, notNullParameters);
    }

    Nullability nullability = findNullability(method, data);
    Mutability mutability = findMutability(method, data);
    boolean pure = findPurity(method, data);

    IntPredicate isNotNullParameter = i -> {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
      NullabilityAnnotationInfo parameterInfo = manager.findExplicitNullability(parameters[i]);
      return parameterInfo != null ? parameterInfo.getNullability() == Nullability.NOT_NULL : notNullParameters.get(i);
    };
    List<StandardMethodContract> contracts = findContracts(method, data, nullability, isNotNullParameter);
    if (nullability == Nullability.NULLABLE && ContainerUtil.find(contracts, c -> c.getReturnValue().isNull()) != null) {
      nullability = Nullability.UNKNOWN;
    }

    return new MethodInferenceData(mutability, nullability, contracts, pure, notNullParameters);
  }

  @Nonnull
  private static Nullability findNullability(PsiMethodImpl method, MethodData data) {
    PsiType type = method.getReturnType();
    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findExplicitNullability(method);
    if (info != null) {
      return info.getNullability();
    }
    if (type == null || type instanceof PsiPrimitiveType) {
      return Nullability.UNKNOWN;
    }
    MethodReturnInferenceResult result = data.getMethodReturn();
    if (result == null) {
      return Nullability.UNKNOWN;
    }
    Nullability nullability = RecursionManager.doPreventingRecursion(
        method, true, () -> result.getNullability(method, data.methodBody(method)));
    return nullability == null ? Nullability.UNKNOWN : nullability;
  }

  @Nonnull
  private static Mutability findMutability(@Nonnull PsiMethodImpl method, @Nonnull MethodData data) {
    PsiType type = method.getReturnType();
    if (type == null || ClassUtils.isImmutable(type, false)) {
      return Mutability.UNKNOWN;
    }
    MethodReturnInferenceResult result = data.getMethodReturn();
    if (result == null) {
      return Mutability.UNKNOWN;
    }
    Mutability mutability = RecursionManager.doPreventingRecursion(
        method, true, () -> result.getMutability(method, data.methodBody(method)));
    return mutability == null ? Mutability.UNKNOWN : mutability;
  }

  private static boolean findPurity(@Nonnull PsiMethodImpl method, @Nonnull MethodData data) {
    PurityInferenceResult result = data.getPurity();
    if (result == null) {
      return false;
    }
    return Boolean.TRUE.equals(RecursionManager.doPreventingRecursion(method, true, () -> result.isPure(method, data.methodBody(method))));
  }

  @Nonnull
  private static List<StandardMethodContract> findContracts(@Nonnull PsiMethodImpl method,
                                                            @Nonnull MethodData data,
                                                            @Nonnull Nullability nullability,
                                                            @Nonnull IntPredicate notNullParameter) {
    PsiAnnotation explicitContract = AnnotationUtil.findAnnotationInHierarchy(
        method, Collections.singleton(JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT), true);
    if (explicitContract != null) {
      // Explicit contract may suppress inferred nullability, so parse them
      return JavaMethodContractUtil.parseContracts(method, explicitContract);
    }
    List<PreContract> preContracts = data.getContracts();
    List<StandardMethodContract> contracts = RecursionManager.doPreventingRecursion(
        method, true, () -> ContainerUtil.concat(preContracts, c -> c.toContracts(method, data.methodBody(method))));
    if (contracts == null || contracts.isEmpty()) {
      return Collections.emptyList();
    }
    if (contracts.size() == 2) {
      StandardMethodContract collapsed = contracts.get(0).tryCollapse(contracts.get(1));
      if (collapsed != null) {
        contracts = Collections.singletonList(collapsed);
      }
    }

    return postProcessContracts(contracts, method, nullability, notNullParameter);
  }

  @Nonnull
  private static List<StandardMethodContract> postProcessContracts(List<StandardMethodContract> contracts, @Nonnull PsiMethod method,
                                                                   @Nonnull Nullability nullability,
                                                                   @Nonnull IntPredicate notNullParameter) {
    final PsiType returnType = method.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<StandardMethodContract> compatible = ContainerUtil.filter(contracts, contract -> {
      for (int i = 0; i < contract.getParameterCount(); i++) {
        if (contract.getParameterConstraint(i) == NULL_VALUE && notNullParameter.test(i)) {
          return false;
        }
      }
      ContractReturnValue retValue = contract.getReturnValue();
      if (nullability == Nullability.NOT_NULL && (retValue.equals(ContractReturnValue.returnNotNull()) || retValue.isNull())) {
        return false;
      }
      if (nullability == Nullability.NULLABLE && retValue.isNotNull() && contract.isTrivial()) {
        return false;
      }
      return retValue.isMethodCompatible(method);
    });
    if (compatible.size() > MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
      return compatible.subList(0, MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  @Nonnull
  private static MethodInferenceData getInferenceData(PsiMethod method) {
    if (!(method instanceof PsiMethodImpl)) {
      return MethodInferenceData.UNKNOWN;
    }
    return LanguageCachedValueUtil.getCachedValue(
        method, () -> CachedValueProvider.Result.create(infer((PsiMethodImpl) method), method, PsiModificationTracker.MODIFICATION_COUNT));
  }

  /**
   * Infer method return type nullability
   *
   * @param method method to analyze
   * @return inferred return type nullability; {@link Nullability#UNKNOWN} if cannot be inferred or non-applicable
   */
  @Nonnull
  public static Nullability inferNullability(PsiMethodImpl method) {
    return getInferenceData(method).myNullability;
  }

  /**
   * Infer method parameter nullability
   *
   * @param parameter parameter to analyze
   * @return inferred parameter nullability; {@link Nullability#UNKNOWN} if cannot be inferred or non-applicable
   */
  public static Nullability inferNullability(@Nonnull PsiParameter parameter) {
    if (!parameter.isPhysical() || parameter.getType() instanceof PsiPrimitiveType) {
      return Nullability.UNKNOWN;
    }
    PsiParameterList parent = ObjectUtil.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parent == null) {
      return Nullability.UNKNOWN;
    }
    PsiMethodImpl method = ObjectUtil.tryCast(parent.getParent(), PsiMethodImpl.class);
    if (method == null) {
      return Nullability.UNKNOWN;
    }

    BitSet notNullParameters = getInferenceData(method).myNotNullParameters;
    if (!notNullParameters.isEmpty()) {
      int index = parent.getParameterIndex(parameter);
      if (notNullParameters.get(index)) {
        return Nullability.NOT_NULL;
      }
    }
    return Nullability.UNKNOWN;
  }

  /**
   * Infer method return type mutability
   *
   * @param method method to analyze
   * @return inferred return type mutability; {@link Mutability#UNKNOWN} if cannot be inferred or non-applicable
   */
  @Nonnull
  public static Mutability inferMutability(PsiMethodImpl method) {
    return getInferenceData(method).myMutability;
  }

  /**
   * Infer method contracts
   *
   * @param method method to analyze
   * @return inferred contracts; empty list of cannot be inferred or non-applicable
   */
  @Nonnull
  public static List<StandardMethodContract> inferContracts(@Nonnull PsiMethodImpl method) {
    return getInferenceData(method).myContracts;
  }

  /**
   * Infer method purity
   *
   * @param method method to analyze
   * @return true if method was inferred to be pure; false if method is not pure or cannot be analyzed
   */
  public static boolean inferPurity(@Nonnull PsiMethodImpl method) {
    return getInferenceData(method).myPure;
  }

  @Nonnull
  private static List<StandardMethodContract> boxReturnValues(List<StandardMethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, contract -> {
      if (contract.getReturnValue().isBoolean()) {
        return contract.withReturnValue(ContractReturnValue.returnNotNull());
      }
      return contract;
    });
  }

  private static InferenceMode getInferenceMode(@Nonnull PsiMethodImpl method) {
    if (isLibraryCode(method) ||
        ((PsiMethod) method).hasModifierProperty(PsiModifier.ABSTRACT) ||
        ((PsiMethod) method).hasModifierProperty(PsiModifier.NATIVE)) {
      return InferenceMode.DISABLED;
    }

    if (((PsiMethod) method).hasModifierProperty(PsiModifier.STATIC)) {
      return InferenceMode.ENABLED;
    }
    if (PsiUtil.canBeOverridden(method)) {
      return InferenceMode.PARAMETERS;
    }
    if (isUnusedInAnonymousClass(method)) {
      return InferenceMode.DISABLED;
    }

    return InferenceMode.ENABLED;
  }

  private static boolean isUnusedInAnonymousClass(@Nonnull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (!(containingClass instanceof PsiAnonymousClass)) {
      return false;
    }

    if (containingClass.getParent() instanceof PsiNewExpression &&
        containingClass.getParent().getParent() instanceof PsiVariable &&
        !method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) {
      // references outside anonymous class can still resolve to this method, see com.intellij.psi.scope.util.PsiScopesUtil.setupAndRunProcessor()
      return false;
    }

    return MethodReferencesSearch.search(method, new LocalSearchScope(containingClass), false).findFirst() == null;
  }

  private static boolean isLibraryCode(@Nonnull PsiMethod method) {
    if (method instanceof PsiCompiledElement) {
      return true;
    }
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
    return virtualFile != null && FileIndexFacade.getInstance(method.getProject()).isInLibrarySource(virtualFile);
  }
}
