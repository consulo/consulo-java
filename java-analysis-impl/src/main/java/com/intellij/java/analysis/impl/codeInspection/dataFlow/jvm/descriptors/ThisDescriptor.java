// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.Mutability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MutationSignature;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A variable descriptor that represents 'this' reference
 */
public final class ThisDescriptor extends PsiVarDescriptor {
  private final PsiClass myQualifier;
  private final int myHash;

  /**
   * Creates a descriptor that represents accessible 'this' variable of a specific class type
   * 
   * @param psiClass PSI class designating the corresponding 'this' variable. In case of an inner class, may refer to an outer one.
   */
  public ThisDescriptor(PsiClass psiClass) {
    myQualifier = psiClass;
    myHash = Objects.hashCode(psiClass.getQualifiedName());
  }

  @Override
  public String toString() {
    if (myQualifier instanceof PsiAnonymousClass) {
      return "(anonymous " + ((PsiAnonymousClass)myQualifier).getBaseClassReference().getText() + ").this";
    }
    return myQualifier.getName() + ".this";
  }

  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    return new PsiImmediateClassType(myQualifier, PsiSubstitutor.EMPTY);
  }

  @Override
  public DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return DfTypes.typedObject(getType(qualifier), Nullability.NOT_NULL);
  }

  @Override
  public PsiClass getPsiElement() {
    return myQualifier;
  }

  @Override
  public boolean isStable() {
    return true;
  }

  @Override
  public int hashCode() {
    return myHash;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || obj instanceof ThisDescriptor && ((ThisDescriptor)obj).myQualifier == myQualifier;
  }

  @Override
  public DfType getInitialDfType(DfaVariableValue thisValue,
                                          @Nullable PsiElement context) {
    DfType dfType = getDfType(thisValue.getQualifier());
    // In class initializer this variable is local until escaped
    if (context != null) {
      PsiMethod method = ObjectUtil.tryCast(context.getParent(), PsiMethod.class);
      if (method != null && myQualifier.equals(method.getContainingClass())) {
        if (myQualifier instanceof PsiEnumConstantInitializer) {
          PsiEnumConstant constant = ((PsiEnumConstantInitializer)myQualifier).getEnumConstant();
          return DfTypes.referenceConstant(constant, TypeConstraints.exactClass(myQualifier));
        }
        if (!method.isConstructor() && MutationSignature.fromMethod(method).preservesThis()) {
          return dfType.meet(Mutability.UNMODIFIABLE_VIEW.asDfType());
        }
        if (method.isConstructor()) {
          return dfType.meet(DfTypes.LOCAL_OBJECT);
        }
      }
      if (myQualifier.equals(context)) {
        return dfType.meet(DfTypes.LOCAL_OBJECT);
      }
    }
    return dfType;
  }

  /**
   * Creates a variable representing "this" value with given class as a context
   * @param aClass a class to bind "this" value to
   * @return a DFA variable
   */
  public static DfaVariableValue createThisValue(DfaValueFactory factory, @Nullable PsiClass aClass) {
    if (aClass == null) return null;
    return factory.getVarFactory().createVariableValue(new ThisDescriptor(aClass));
  }

  @Override
  public DfaValue createValue(DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier != null) return factory.getUnknown();
    return factory.getVarFactory().createVariableValue(this);
  }
}
