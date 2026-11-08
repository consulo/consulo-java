// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.component.util.ModificationTracker;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.impl.psi.LightElement;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.file.light.LightVirtualFile;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lattice:
 *     UNKNOWN
 *     |      \
 * MUTABLE   MUST_NOT_MODIFY
 *     |       |
 *     |     UNMODIFIABLE_VIEW
 *     |       |
 *     |     UNMODIFIABLE
 *     \      /
 *     BOTTOM (null)
 */
public enum Mutability {
  /**
   * Mutability is not known; probably value can be mutated; TOP
   */
  UNKNOWN("mutability.unknown", null),
  /**
   * A value is known to be mutable (e.g. elements are sometimes added to the collection)
   */
  MUTABLE("mutability.modifiable", null),
  /**
   * A value that could be mutable, but must not be modified due to contract
   * (e.g. a parameter of pure method)
   */
  MUST_NOT_MODIFY("mutability.must.not.modify", null),
  /**
   * A value is known to be an immutable view over a possibly mutable value: it cannot be mutated directly using this
   * reference; however subsequent reads (e.g. {@link java.util.Collection#size}) may return different results if the
   * underlying value is mutated by somebody else.
   */
  UNMODIFIABLE_VIEW("mutability.unmodifiable.view", "org.jetbrains.annotations.UnmodifiableView"),
  /**
   * A value is known to be immutable. For collection no elements could be added, removed or altered (though if collection
   * contains mutable elements, they still could be mutated).
   */
  UNMODIFIABLE("mutability.unmodifiable", "org.jetbrains.annotations.Unmodifiable");

  public static final String UNMODIFIABLE_ANNOTATION = UNMODIFIABLE.myAnnotation;
  public static final String UNMODIFIABLE_VIEW_ANNOTATION = UNMODIFIABLE_VIEW.myAnnotation;
  private final String myResourceKey;
  private final String myAnnotation;
  private final Key<CachedValue<PsiAnnotation>> myKey;

  Mutability(String resourceKey, String annotation) {
    myResourceKey = resourceKey;
    myAnnotation = annotation;
    myKey = annotation == null ? null : Key.create(annotation);
  }

  public DfReferenceType asDfType() {
    return DfTypes.customObject(TypeConstraints.TOP, DfaNullability.UNKNOWN, this, null, DfType.BOTTOM);
  }

  public String getPresentationName() {
    return JavaAnalysisBundle.message(myResourceKey);
  }

  public boolean isUnmodifiable() {
    return this == UNMODIFIABLE || this == UNMODIFIABLE_VIEW;
  }

  public boolean canBeModified() {
    return this == MUTABLE || this == UNKNOWN;
  }

  public Mutability join(Mutability other) {
    if (this == other) return this;
    if (this == UNKNOWN || other == UNKNOWN) return UNKNOWN;
    if (this == MUTABLE || other == MUTABLE) return UNKNOWN;
    if (this == MUST_NOT_MODIFY || other == MUST_NOT_MODIFY) return MUST_NOT_MODIFY;
    if (this == UNMODIFIABLE_VIEW || other == UNMODIFIABLE_VIEW) return UNMODIFIABLE_VIEW;
    return UNMODIFIABLE;
  }

  /**
   * @param other mutability to meet
   * @return resulting mutability; null if bottom
   */
  public @Nullable Mutability meet(Mutability other) {
    if (this == other) return this;
    if (this == UNKNOWN) return other;
    if (other == UNKNOWN) return this;
    if (this == MUTABLE || other == MUTABLE) return null;
    if (this == UNMODIFIABLE || other == UNMODIFIABLE) return UNMODIFIABLE;
    if (this == UNMODIFIABLE_VIEW || other == UNMODIFIABLE_VIEW) return UNMODIFIABLE_VIEW;
    return MUST_NOT_MODIFY;
  }

  public @Nullable PsiAnnotation asAnnotation(Project project) {
    if (myAnnotation == null) return null;
    return CachedValuesManager.getManager(project).getCachedValue(project, myKey, () -> {
      PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + myAnnotation, null);
      ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
      return CachedValueProvider.Result.create(annotation, ModificationTracker.NEVER_CHANGED);
    }, false);
  }

  /**
   * Returns a mutability of the supplied element, if known. The element could be a method
   * (in this case the return value mutability is returned), a method parameter
   * (the returned mutability will reflect whether the method can mutate the parameter),
   * or a field (in this case the mutability could be obtained from its initializer).
   *
   * @param owner an element to check the mutability
   * @return a Mutability enum value; {@link #UNKNOWN} if cannot be determined or specified element type is not supported.
   */
  public static Mutability getMutability(PsiModifierListOwner owner) {
    if (owner instanceof LightElement) return UNKNOWN;
    return LanguageCachedValueUtil.getCachedValue(owner, () ->
      CachedValueProvider.Result.create(calcMutability(owner), owner, PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static Mutability calcMutability(PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter parameter
        && owner.getParent() instanceof PsiParameterList list
        && list.getParent() instanceof PsiMethod method) {
      JavaMethodContractUtil.ContractInfo contractInfo = JavaMethodContractUtil.getContractInfo(method);
      if (contractInfo.isExplicit()) {
        int index = list.getParameterIndex(parameter);
        MutationSignature signature = contractInfo.getMutationSignature();
        if (signature.mutatesArg(index)) {
          return MUTABLE;
        }
        else if (signature.preservesArg(index)) {
          return MUST_NOT_MODIFY;
        }
      }
      return UNKNOWN;
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED |
                                   AnnotationUtil.CHECK_TYPE)) {
      return UNMODIFIABLE;
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_VIEW_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED |
                                   AnnotationUtil.CHECK_TYPE)) {
      return UNMODIFIABLE_VIEW;
    }
    if (owner instanceof PsiField field && owner.hasModifierProperty(PsiModifier.FINAL)) {
      List<PsiExpression> initializers = ContainerUtil.createMaybeSingletonList(unwrap(field.getInitializer()));
      if (initializers.isEmpty()) {
        initializers = findFieldInitializers(field);
      }
      return calcMutability(initializers);
    }
    else if (owner instanceof PsiRecordComponent component) {
      return calcMutability(DfaPsiUtil.findAllConstructorInitializers(component));
    }
    return owner instanceof PsiMethodImpl method ? JavaSourceInference.inferMutability(method) : UNKNOWN;
  }

  private static List<PsiExpression> findFieldInitializers(PsiField field) {
    List<PsiExpression> result = null;
    for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(field)) {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(reference, PsiParenthesizedExpression.class);
      if (parent instanceof PsiAssignmentExpression assignment &&
          PsiTreeUtil.isAncestor(assignment.getLExpression(), reference, false)) {
        PsiExpression expression = unwrap(assignment.getRExpression());
        if (expression == null) continue;
        if (result == null) {
          result = List.of(expression);
          continue;
        }
        else if (result.size() == 1) {
          result = new ArrayList<>(result);
        }
        result.add(expression);
      }
    }
    return result == null ? List.of() : result;
  }
  
  private static PsiExpression unwrap(PsiExpression expression) {
    return PsiUtil.deparenthesizeExpression(expression) instanceof PsiAssignmentExpression assignment
           ? unwrap(assignment.getRExpression())
           : PsiUtil.deparenthesizeExpression(expression);
  }

  private static Mutability calcMutability(List<PsiExpression> expressions) {
    expressions = StreamEx.of(expressions).flatMap(ExpressionUtils::nonStructuralChildren).toList();
    if (expressions.isEmpty()) return UNKNOWN;
    Mutability mutability = UNMODIFIABLE;
    for (PsiExpression initializer : expressions) {
      Mutability newMutability = UNKNOWN;
      PsiType type = initializer.getType();
      if (ClassUtils.isImmutable(type) || (type != null && type.hasAnnotation(UNMODIFIABLE_ANNOTATION))) {
        newMutability = UNMODIFIABLE;
      } else if (initializer instanceof PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        newMutability = method == null ? UNKNOWN : getMutability(method);
      }
      mutability = mutability.join(newMutability);
      if (!mutability.isUnmodifiable()) break;
    }
    return mutability;
  }

  public static Mutability fromDfType(DfType dfType) {
    return dfType instanceof DfReferenceType referenceType ? referenceType.getMutability() : UNKNOWN;
  }
}
