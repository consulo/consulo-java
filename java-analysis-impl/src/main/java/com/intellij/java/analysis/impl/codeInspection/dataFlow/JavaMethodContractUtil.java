// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInsight.DefaultInferredAnnotationProvider;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.light.LightRecordMethod;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Methods to operate on Java contracts
 */
public class JavaMethodContractUtil {
  private JavaMethodContractUtil() {
  }

  /**
   * JetBrains contract annotation fully-qualified name
   */
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  /**
   * Returns a list of contracts defined for given method call (including hardcoded contracts if any)
   *
   * @param call a method call site.
   * @return list of contracts (empty list if no contracts found)
   */
  @Nonnull
  public static List<? extends MethodContract> getMethodCallContracts(@Nonnull PsiCallExpression call) {
    PsiMethod method = call.resolveMethod();
    return method == null ? Collections.emptyList() : getMethodCallContracts(method, call);
  }

  /**
   * Returns a list of contracts defined for given method call (including hardcoded contracts if any)
   *
   * @param method a method to check the contracts for
   * @param call   an optional call site. If specified, could be taken into account to derive contracts for some
   *               testing methods like assertThat(x, is(null))
   * @return list of contracts (empty list if no contracts found)
   */
  @Nonnull
  public static List<? extends MethodContract> getMethodCallContracts(@Nonnull final PsiMethod method,
                                                                      @Nullable PsiCallExpression call) {
    List<MethodContract> contracts =
      HardcodedContracts.getHardcodedContracts(method, ObjectUtil.tryCast(call, PsiMethodCallExpression.class));
    return !contracts.isEmpty() ? contracts : getMethodContracts(method);
  }

  /**
   * Returns a list of contracts defined for given method call (excluding hardcoded contracts)
   *
   * @param method a method to check the contracts for
   * @return list of contracts (empty list if no contracts found)
   */
  @Nonnull
  public static List<StandardMethodContract> getMethodContracts(@Nonnull final PsiMethod method) {
    return getContractInfo(method).getContracts();
  }

  /**
   * Checks whether method has an explicit contract annotation (either in source code or as external annotation)
   *
   * @param method method to check
   * @return true if method has explicit (non-inferred) contract annotation.
   */
  public static boolean hasExplicitContractAnnotation(@Nonnull PsiMethod method) {
    return getContractInfo(method).isExplicit();
  }

  /**
   * Creates a new {@link PsiAnnotation} describing the updated contract. Only contract clauses are updated;
   * purity and mutation signature (if exist) are left as is.
   *
   * @param annotation original annotation to update
   * @param contracts  new contracts
   * @return new {@link PsiAnnotation} object which describes updated contracts or null if no annotation is required to represent
   * the target contracts (i.e. contracts is empty, method has no mutation signature and is not marked as pure).
   */
  @Nullable
  public static PsiAnnotation updateContract(PsiAnnotation annotation, List<StandardMethodContract> contracts) {
    boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "pure"));
    String mutates = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(annotation, MutationSignature.ATTR_MUTATES));
    String resultValue = StreamEx.of(contracts).joining("; ");
    Project project = annotation.getProject();
    return DefaultInferredAnnotationProvider.createContractAnnotation(project, pure, resultValue, mutates);
  }

  static class ContractInfo {
    static final ContractInfo EMPTY = new ContractInfo(Collections.emptyList(), false, false, MutationSignature.UNKNOWN);
    static final ContractInfo PURE = new ContractInfo(Collections.emptyList(), true, false, MutationSignature.PURE);

    @Nonnull
    private final List<StandardMethodContract> myContracts;
    private final boolean myPure;
    private final boolean myExplicit;
    @Nonnull
    private final MutationSignature myMutationSignature;

    ContractInfo(@Nonnull List<StandardMethodContract> contracts, boolean pure, boolean explicit, @Nonnull MutationSignature signature) {
      myContracts = contracts;
      myPure = pure;
      myExplicit = explicit;
      myMutationSignature = signature;
    }

    @Nonnull
    List<StandardMethodContract> getContracts() {
      return myContracts;
    }

    boolean isPure() {
      return myPure;
    }

    boolean isExplicit() {
      return myExplicit;
    }

    @Nonnull
    MutationSignature getMutationSignature() {
      return myMutationSignature;
    }
  }

  @Nonnull
  static ContractInfo getContractInfo(@Nonnull PsiMethod method) {
    if (PsiUtil.isAnnotationMethod(method) || method instanceof LightRecordMethod) {
      return ContractInfo.PURE;
    }
    return LanguageCachedValueUtil.getCachedValue(method, () -> {
      final PsiAnnotation contractAnno = findContractAnnotation(method);
      ContractInfo info = ContractInfo.EMPTY;
      if (contractAnno != null) {
        List<StandardMethodContract> contracts = parseContracts(method, contractAnno);
        boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(contractAnno, "pure"));
        MutationSignature mutationSignature = MutationSignature.UNKNOWN;
        if (pure) {
          mutationSignature = MutationSignature.PURE;
        }
        else {
          String mutationText = AnnotationUtil.getStringAttributeValue(contractAnno, MutationSignature.ATTR_MUTATES);
          if (mutationText != null) {
            try {
              mutationSignature = MutationSignature.parse(mutationText);
            }
            catch (IllegalArgumentException ignored) {
            }
          }
        }
        boolean explicit = !AnnotationUtil.isInferredAnnotation(contractAnno);
        info = new ContractInfo(contracts, pure, explicit, mutationSignature);
      }
      return CachedValueProvider.Result.create(info, method, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Parse contracts for given method. Calling this method is rarely necessary in client code; it exists mainly to
   * aid the inference procedure. Use {@link #getMethodContracts(PsiMethod)} instead.
   *
   * @param method       method to parse contracts for
   * @param contractAnno a contract annotation
   * @return a list of parsed contracts
   */
  @Nonnull
  public static List<StandardMethodContract> parseContracts(@Nonnull PsiMethod method, @Nullable PsiAnnotation contractAnno) {
    if (contractAnno == null)
      return Collections.emptyList();
    String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
    if (text != null) {
      try {
        final int paramCount = method.getParameterList().getParametersCount();
        List<StandardMethodContract> parsed = StandardMethodContract.parseContract(text);
        if (parsed.stream().allMatch(c -> c.getParameterCount() == paramCount)) {
          return parsed;
        }
      }
      catch (StandardMethodContract.ParseException ignored) {
      }
    }
    return Collections.emptyList();
  }

  /**
   * Returns a contract annotation for given method, checking the hierarchy
   *
   * @param method a method
   * @return a found annotation (null if not found)
   */
  @Nullable
  public static PsiAnnotation findContractAnnotation(@Nonnull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
  }

  /**
   * Checks the method purity based on its contract
   *
   * @param method method to check
   * @return true if the method known to be pure (see {@link Contract#pure()} for details).
   */
  public static boolean isPure(@Nonnull PsiMethod method) {
    return getContractInfo(method).myPure;
  }

  /**
   * Returns the common return value of the method assuming that it does not fail
   *
   * @param contracts method contracts
   * @return common return value or null if there's no common return value
   */
  @Nullable
  public static ContractReturnValue getNonFailingReturnValue(List<? extends MethodContract> contracts) {
    List<ContractValue> failConditions = new ArrayList<>();
    for (MethodContract contract : contracts) {
      List<ContractValue> conditions = contract.getConditions();
      if (conditions.isEmpty() || conditions.stream().allMatch(c -> failConditions.stream().anyMatch(c::isExclusive))) {
        return contract.getReturnValue();
      }
      if (contract.getReturnValue().isFail()) {
        // support "null, _ -> fail; !null, _ -> this", but do not support more complex cases like "null, true -> fail; !null, false -> this"
        if (conditions.size() == 1) {
          failConditions.add(conditions.get(0));
        }
      }
      else {
        break;
      }
    }
    return null;
  }

  /**
   * For given method call find the returned expression if the method is known to return always the same parameter or its qualifier
   * (unless fail).
   *
   * @param call call to analyze
   * @return the expression which is always returned by this method if it completes successfully,
   * null if method may return something less trivial or its contract is unknown.
   */
  @Nullable
  @Contract("null -> null")
  public static PsiExpression findReturnedValue(@Nullable PsiMethodCallExpression call) {
    if (call == null)
      return null;
    List<? extends MethodContract> contracts = getMethodCallContracts(call);
    ContractReturnValue returnValue = getNonFailingReturnValue(contracts);
    if (returnValue == null)
      return null;
    if (returnValue.equals(ContractReturnValue.returnThis())) {
      return ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
    }
    if (returnValue instanceof ContractReturnValue.ParameterReturnValue) {
      int number = ((ContractReturnValue.ParameterReturnValue)returnValue).getParameterNumber();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length <= number)
        return null;
      if (args.length == number + 1 && MethodCallUtils.isVarArgCall(call))
        return null;
      return args[number];
    }
    return null;
  }
}
