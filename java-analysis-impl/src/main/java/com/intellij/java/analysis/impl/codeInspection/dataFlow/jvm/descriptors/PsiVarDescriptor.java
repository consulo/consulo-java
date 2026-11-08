// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaDfaNullability;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import consulo.language.dataFlow.types.DfIntegralType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

public abstract class PsiVarDescriptor extends JvmVariableDescriptor {
  abstract @Nullable PsiType getType(@Nullable DfaVariableValue qualifier);

  static PsiSubstitutor getSubstitutor(PsiElement member, @Nullable DfaVariableValue qualifier) {
    if (member instanceof PsiMember && qualifier != null) {
      PsiClass fieldClass = ((PsiMember)member).getContainingClass();
      PsiVarDescriptor qualifierDescriptor = ObjectUtil.tryCast(qualifier.getDescriptor(), PsiVarDescriptor.class);
      PsiClassType classType = qualifierDescriptor == null ? null :
                               ObjectUtil.tryCast(qualifierDescriptor.getType(qualifier.getQualifier()), PsiClassType.class);
      if (classType != null && InheritanceUtil.isInheritorOrSelf(classType.resolve(), fieldClass, true)) {
        return TypeConversionUtil.getSuperClassSubstitutor(fieldClass, classType);
      }
    }
    return PsiSubstitutor.EMPTY;
  }

  @Override
  public DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return DfTypes.typedObject(getType(qualifier), Nullability.UNKNOWN);
  }

  @Override
  public DfaValue createValue(DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier == factory.getSentinel()) return factory.getUnknown();
    if (qualifier instanceof DfaVariableValue) {
      if (!(qualifier.getDfType() instanceof DfReferenceType)) return factory.getUnknown();
      return factory.getVarFactory().createVariableValue(this, (DfaVariableValue)qualifier);
    }
    PsiType type = getType(null);
    PsiModifierListOwner element = ObjectUtil.tryCast(getPsiElement(), PsiModifierListOwner.class);
    LongRangeSet range = JvmPsiRangeSetUtil.fromPsiElement(element);
    DfType dfType = DfTypes.typedObject(type, DfaPsiUtil.getElementNullabilityForRead(type, element));
    if (dfType instanceof DfIntegralType) {
      dfType = ((DfIntegralType)dfType).meetRange(range);
    }
    return factory.fromDfType(dfType);
  }

  @Override
  public DfType getInitialDfType(DfaVariableValue thisValue,
                                          @Nullable PsiElement context) {
    DfType dfType = getDfType(thisValue.getQualifier());
    PsiModifierListOwner psi = ObjectUtil.tryCast(getPsiElement(), PsiModifierListOwner.class);
    if (psi == null) return dfType;
    if (dfType instanceof DfIntegralType integralType) {
      return integralType.meetRange(JvmPsiRangeSetUtil.fromPsiElement(psi));
    }
    if (dfType instanceof DfReferenceType) {
      Mutability mutability = Mutability.getMutability(psi);
      if (mutability == Mutability.MUST_NOT_MODIFY &&
          context != null && !PsiTreeUtil.isAncestor(context.getParent(), psi, false)) {
        // Pure method may return impure lambda, so method parameter may still be modified 
        // in nested lambdas/anonymous classes
        mutability = Mutability.UNKNOWN;
      }
      dfType = dfType.meet(mutability.asDfType());
      dfType = dfType.meet(JavaDfaNullability.asDfType(calcCanBeNull(thisValue, context)));
    }
    return dfType;
  }

  DfaNullability calcCanBeNull(DfaVariableValue value, @Nullable PsiElement context) {
    return DfaNullability.UNKNOWN;
  }
}
