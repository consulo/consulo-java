// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaDfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.CustomMethodHandlers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaCallArguments;
import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MutationSignature;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfAntiConstantType;
import consulo.language.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.impl.psi.impl.light.LightRecordMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PropertyUtilBase;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A descriptor that represents a getter-like method (without arguments)
 */
public final class GetterDescriptor extends PsiVarDescriptor {
  private static final CallMatcher STABLE_METHODS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "trim", "stripLeading", "stripTrailing", "strip").parameterCount(0),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ENUM, "name").parameterCount(0),
    CallMatcher.instanceCall("java.lang.reflect.Member", "getName", "getModifiers", "getDeclaringClass", "isSynthetic"),
    CallMatcher.instanceCall("java.lang.reflect.Executable", "getParameterCount", "isVarArgs"),
    CallMatcher.instanceCall("java.lang.reflect.Field", "getType"),
    CallMatcher.instanceCall("java.lang.reflect.Method", "getReturnType"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS, "getName", "isInterface", "isArray", "isPrimitive", "isSynthetic",
                             "isAnonymousClass", "isLocalClass", "isMemberClass", "getDeclaringClass", "getEnclosingClass",
                             "getSimpleName", "getCanonicalName"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_IO_FILE, "getName", "getParent", "getPath", "getAbsolutePath", 
                             "getParentFile", "getAbsoluteFile", "toPath")
  );
  private static final CallMatcher EXACTLY_STABLE =
    CallMatcher.instanceCall("java.lang.LazyConstant", "get");
  private final PsiMethod myGetter;
  private final boolean myStable;

  public GetterDescriptor(PsiMethod getter) {
    myGetter = getter;
    if (isKnownStableMethod(getter) || getter instanceof LightRecordMethod) {
      myStable = true;
    }
    else {
      PsiField field = PsiUtil.canBeOverridden(getter) ? null : PropertyUtil.getFieldOfGetter(getter);
      myStable = field != null && field.hasModifierProperty(PsiModifier.FINAL);
    }
  }

  public static boolean isKnownStableMethod(PsiMethod getter) {
    return STABLE_METHODS.methodMatches(getter);
  }

  @Override
  public String toString() {
    return myGetter.getName();
  }

  @Nullable
  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    return getSubstitutor(myGetter, qualifier).substitute(myGetter.getReturnType());
  }

  @Override
  public PsiMethod getPsiElement() {
    return myGetter;
  }

  @Override
  public boolean isStable() {
    return myStable;
  }

  @Override
  public boolean isCall() {
    return true;
  }

  @Override
  public DfaValue createValue(DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (myGetter.hasModifierProperty(PsiModifier.STATIC)) {
      return factory.getVarFactory().createVariableValue(this);
    }
    return super.createValue(factory, qualifier);
  }

  @Override
  public DfType restrictFromState(DfaVariableValue qualifier, DfaMemoryState state) {
    CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(myGetter);
    if (handler != null) {
      DfaValue value = handler.getMethodResultValue(
        new DfaCallArguments(qualifier, DfaValue.EMPTY_ARRAY, MutationSignature.pure()),
        state, qualifier.getFactory(), myGetter);
      if (value != null) {
        return state.getDfType(value);
      }
    }
    return super.restrictFromState(qualifier, state);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myGetter.getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || (obj instanceof GetterDescriptor && ((GetterDescriptor)obj).myGetter == myGetter);
  }

  @Override
  DfaNullability calcCanBeNull(DfaVariableValue value, @Nullable PsiElement context) {
    PsiType type = getType(value.getQualifier());
    return JavaDfaNullability.fromNullability(DfaPsiUtil.getElementNullabilityForRead(type, myGetter));
  }

  @Override
  public boolean alwaysEqualsToItself(DfType type) {
    if (!super.alwaysEqualsToItself(type)) return false;
    if (type instanceof DfPrimitiveType || type instanceof DfConstantType) return true;
    if (PropertyUtilBase.isSimplePropertyGetter(myGetter) || myGetter instanceof LightRecordMethod) return true;
    return EXACTLY_STABLE.methodMatches(myGetter);
  }

  @Override
  public DfType getQualifierConstraintFromValue(DfaMemoryState state, DfaValue value) {
    if (!PsiTypesUtil.isGetClass(myGetter)) return DfType.TOP;
    DfType type = state.getDfType(value);
    DfType constraint = DfType.TOP;
    PsiType cls = type.getConstantOfType(PsiType.class);
    if (cls != null) {
      constraint = TypeConstraints.exact(cls).asDfType();
    }
    else if (type instanceof DfAntiConstantType) {
      constraint = StreamEx.of(((DfAntiConstantType<?>)type).getNotValues()).select(PsiType.class)
        .map(t -> TypeConstraints.exact(t).tryNegate())
        .nonNull()
        .reduce(TypeConstraint::meet).orElse(TypeConstraints.TOP).asDfType();
    }
    if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getDescriptor().equals(this)) {
      DfaVariableValue qualifier = ((DfaVariableValue)value).getQualifier();
      if (qualifier != null) {
        constraint = constraint.meet(TypeConstraint.fromDfType(state.getDfType(qualifier)).asDfType());
      }
    }
    return constraint;
  }
}
