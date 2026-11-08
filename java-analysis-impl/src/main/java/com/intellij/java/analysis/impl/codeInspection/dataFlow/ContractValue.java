// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaTypeValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import consulo.language.dataFlow.value.VariableDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.impl.psi.impl.compiled.ClsParameterImpl;
import com.intellij.java.language.psi.util.JavaElementKind;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.Function;

public abstract class ContractValue {
  // package private to avoid uncontrolled implementations
  ContractValue() {

  }

  abstract DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments);

  public DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
    return DfaCondition.getUnknown();
  }

  public DfaCondition fromCall(DfaValueFactory factory, PsiCallExpression call) {
    DfaCallArguments arguments = DfaCallArguments.fromCall(factory, call);
    if (arguments == null) return DfaCondition.getUnknown();
    return makeCondition(factory, arguments);
  }

  /**
   * @param other other contract condition
   * @return true if this contract condition and other condition cannot be fulfilled at the same time
   */
  public boolean isExclusive(ContractValue other) {
    return false;
  }

  public ContractValue invert() {
    return null;
  }

  /**
   * @return true if this contract value represents a bounds-checking condition
   */
  public boolean isBoundCheckingCondition() {
    return false;
  }

  public OptionalInt getNullCheckedArgument(boolean equalToNull) {
    return getArgumentComparedTo(nullValue(), equalToNull);
  }

  public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
    return OptionalInt.empty();
  }

  public String getPresentationText(PsiCallExpression call) {
    return toString();
  }

  /**
   * @param call call to find the place in
   * @return the expression in the call that is the most relevant to the current value
   */
  public PsiExpression findPlace(PsiCallExpression call) {
    return null;
  }

  public DfaCallState updateState(DfaCallState state) {
    return state;
  }

  public static ContractValue qualifier() {
    return Qualifier.INSTANCE;
  }

  public static ContractValue argument(int index) {
    return new Argument(index);
  }

  public ContractValue specialField(SpecialField field) {
    return new Spec(this, field);
  }

  public static ContractValue constant(Object value, PsiType type) {
    return new IndependentValue(String.valueOf(value),
                                factory -> factory.fromDfType(DfTypes.constant(TypeConversionUtil.computeCastTo(value, type), type))
    );
  }

  public static ContractValue booleanValue(boolean value) {
    return value ? IndependentValue.TRUE : IndependentValue.FALSE;
  }

  public static ContractValue nullValue() {
    return IndependentValue.NULL;
  }

  public static ContractValue zero() {
    return IndependentValue.ZERO;
  }

  public static ContractValue condition(ContractValue left, RelationType relation, ContractValue right) {
    return new Condition(left, relation, right);
  }

  private static class Qualifier extends ContractValue {
    static final Qualifier INSTANCE = new Qualifier();

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return arguments.myQualifier;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      if (call instanceof PsiMethodCallExpression) {
        return ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
      }
      return null;
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      PsiExpression place = findPlace(call);
      if (place != null) {
        return PsiExpressionTrimRenderer.render(place);
      }
      return super.getPresentationText(call);
    }

    @Override
    public DfaCallState updateState(DfaCallState state) {
      DfaValueFactory factory = state.getReturnValue().getFactory();
      DfaCallArguments callArguments = state.getCallArguments();
      DfaValue value = callArguments.myQualifier;
      if (!(value instanceof DfaVariableValue) && !DfaTypeValue.isUnknown(value) && !(value.getDfType() instanceof DfConstantType)) {
        DfaVariableValue var = makeVariable(state, factory, value);
        return state.withArguments(new DfaCallArguments(var, callArguments.myArguments, callArguments.myMutation));
      }
      return state;
    }

    @Override
    public String toString() {
      return "this";
    }
  }

  private static final class Argument extends ContractValue {
    private final int myIndex;

    Argument(int index) {
      myIndex = index;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      if (arguments.myArguments.length <= myIndex) {
        return factory.getUnknown();
      }
      return arguments.myArguments[myIndex];
    }

    @Override
    public DfaCallState updateState(DfaCallState state) {
      DfaValueFactory factory = state.getReturnValue().getFactory();
      DfaCallArguments callArguments = state.getCallArguments();
      DfaValue value = makeDfaValue(factory, callArguments);
      if (!(value instanceof DfaVariableValue) && !DfaTypeValue.isUnknown(value) && !(value.getDfType() instanceof DfConstantType)) {
        DfaVariableValue var = makeVariable(state, factory, value);
        DfaValue[] newArgs = callArguments.getArguments().clone();
        newArgs[myIndex] = var;
        return state.withArguments(new DfaCallArguments(callArguments.myQualifier, newArgs, callArguments.myMutation));
      }
      return state;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      PsiExpressionList list = call.getArgumentList();
      if (list != null) {
        PsiExpression[] args = list.getExpressions();
        if (myIndex < args.length - 1 || (myIndex == args.length - 1 && !MethodCallUtils.isVarArgCall(call))) {
          return args[myIndex];
        }
      }
      return null;
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      PsiExpression place = findPlace(call);
      if (place != null && !ExpressionUtils.isNullLiteral(place)) {
        return PsiExpressionTrimRenderer.render(place);
      }
      PsiMethod method = call.resolveMethod();
      if (method == null) return toString();
      PsiParameter[] params = method.getParameterList().getParameters();
      if (myIndex == 0 && params.length == 1) {
        return JavaElementKind.PARAMETER.subject().get();
      }
      if (myIndex < params.length) {
        PsiParameter param = params[myIndex];
        if (param instanceof ClsParameterImpl && ((ClsParameterImpl)param).isAutoGeneratedName()) {
          return "param" + (myIndex + 1);
        }
        return param.getName();
      }
      return toString();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || (obj instanceof Argument && myIndex == ((Argument)obj).myIndex);
    }

    @Override
    public String toString() {
      return "param" + (myIndex + 1);
    }
  }

  private static class IndependentValue extends ContractValue {
    static final IndependentValue NULL = new IndependentValue("null", factory -> factory.fromDfType(DfTypes.NULL));
    static final IndependentValue TRUE = new IndependentValue("true", factory -> factory.fromDfType(DfTypes.TRUE)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == FALSE;
      }
    };
    static final IndependentValue FALSE = new IndependentValue("false", factory -> factory.fromDfType(DfTypes.FALSE)) {
      @Override
      public boolean isExclusive(ContractValue other) {
        return other == TRUE;
      }
    };
    static final IndependentValue ZERO = new IndependentValue("0", factory -> factory.fromDfType(DfTypes.intValue(0)));

    private final Function<? super DfaValueFactory, ? extends DfaValue> mySupplier;
    private final String myPresentation;

    IndependentValue(String presentation, Function<? super DfaValueFactory, ? extends DfaValue> supplier) {
      mySupplier = supplier;
      myPresentation = presentation;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return mySupplier.apply(factory);
    }

    @Override
    public String toString() {
      return myPresentation;
    }
  }

  private static final class Spec extends ContractValue {
    private final ContractValue myQualifier;
    private final SpecialField myField;

    Spec(ContractValue qualifier, SpecialField field) {
      myQualifier = qualifier;
      myField = field;
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return myField.createValue(factory, myQualifier.makeDfaValue(factory, arguments));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      return obj instanceof Spec that && myQualifier.equals(that.myQualifier) && myField == that.myField;
    }

    @Override
    public PsiExpression findPlace(PsiCallExpression call) {
      return myQualifier.findPlace(call);
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      return JavaAnalysisBundle.message("dfa.find.cause.special.field.of.something", myField, myQualifier.getPresentationText(call));
    }

    @Override
    public String toString() {
      return myQualifier + "." + myField + "()";
    }
  }

  /**
   * A contract value that represents a relation between two other values
   */
  public static class Condition extends ContractValue {
    private final ContractValue myLeft, myRight;
    private final RelationType myRelationType;

    Condition(ContractValue left, RelationType type, ContractValue right) {
      myLeft = left;
      myRight = right;
      myRelationType = type;
    }

    @Override
    public boolean isBoundCheckingCondition() {
      return switch (myRelationType) {
        case LE, LT, GE, GT -> true;
        default -> false;
      };
    }

    @Override
    public boolean isExclusive(ContractValue other) {
      if (!(other instanceof Condition that)) return false;
      if (that.myLeft.equals(myLeft) && that.myRight.equals(myRight) && that.myRelationType.getNegated() == myRelationType) {
        return true;
      }
      if (that.myLeft.equals(myRight) && that.myRight.equals(myLeft) && that.myRelationType.getNegated() == myRelationType.getFlipped()) {
        return true;
      }
      if (that.myRelationType == myRelationType) {
        if (that.myLeft.equals(myLeft) && that.myRight.isExclusive(myRight)) return true;
        if (that.myLeft.equals(myRight) && that.myRight.isExclusive(myLeft)) return true;
      }
      return false;
    }

    @Override
    public DfaCallState updateState(DfaCallState state) {
      return myRight.updateState(myLeft.updateState(state));
    }

    private @Nullable ContractValue getValueComparedTo(ContractValue value, boolean equal) {
      if (myRelationType == RelationType.equivalence(equal)) {
        ContractValue other;
        if (myLeft == value) {
          other = myRight;
        }
        else if (myRight == value) {
          other = myLeft;
        }
        else {
          return null;
        }
        return other;
      }
      if (value == IndependentValue.FALSE) {
        return getValueComparedTo(IndependentValue.TRUE, !equal);
      }
      return null;
    }

    @Override
    public OptionalInt getArgumentComparedTo(ContractValue value, boolean equal) {
      ContractValue other = getValueComparedTo(value, equal);
      return other instanceof Argument ? OptionalInt.of(((Argument)other).myIndex) : OptionalInt.empty();
    }

    @Override
    DfaValue makeDfaValue(DfaValueFactory factory, DfaCallArguments arguments) {
      return factory.getUnknown();
    }

    @Override
    public DfaCondition makeCondition(DfaValueFactory factory, DfaCallArguments arguments) {
      DfaValue left = myLeft.makeDfaValue(factory, arguments);
      DfaValue right = myRight.makeDfaValue(factory, arguments);
      if (left.getDfType() instanceof DfPrimitiveType primitiveType) {
        right = DfaUtil.boxUnbox(right, DfTypes.typedObject(primitiveType.getPsiType(), Nullability.UNKNOWN));
      }
      if (right.getDfType() instanceof DfPrimitiveType primitiveType) {
        left = DfaUtil.boxUnbox(left, DfTypes.typedObject(primitiveType.getPsiType(), Nullability.UNKNOWN));
      }
      return left.cond(myRelationType, right);
    }

    @Override
    public String getPresentationText(PsiCallExpression call) {
      if (myLeft instanceof IndependentValue) {
        return myRight.getPresentationText(call) + " " + myRelationType.getFlipped() + " " + myLeft.getPresentationText(call);
      }
      return myLeft.getPresentationText(call) + " " + myRelationType + " " + myRight.getPresentationText(call);
    }

    /**
     * @return condition relation type
     */
    public RelationType getRelationType() {
      return myRelationType;
    }

    /**
     * @return condition left operand
     */
    public ContractValue getLeft() {
      return myLeft;
    }

    /**
     * @return condition right operand
     */
    public ContractValue getRight() {
      return myRight;
    }

    @Override
    public ContractValue invert() {
      return new Condition(myLeft, myRelationType.getNegated(), myRight);
    }

    @Override
    public String toString() {
      return myLeft + " " + myRelationType + " " + myRight;
    }
  }

  private static class ContractTempDescriptor implements VariableDescriptor {
    private final ContractValue myValue;
    private final DfType myType;

    private ContractTempDescriptor(ContractValue value, DfType type) {
      myValue = value;
      myType = type;
    }

    @Override
    public boolean isStable() {
      return true;
    }

    @Override
    public DfType getDfType(@Nullable DfaVariableValue qualifier) {
      return myType;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof ContractTempDescriptor &&
                            ((ContractTempDescriptor)obj).myValue == myValue &&
                            ((ContractTempDescriptor)obj).myType.equals(myType);
    }

    @Override
    public int hashCode() {
      return myValue.hashCode();
    }

    @Override
    public String toString() {
      return "contract$" + myValue;
    }
  }

  DfaVariableValue makeVariable(DfaCallState state, DfaValueFactory factory, DfaValue value) {
    DfType type = value.getDfType();
    if (type instanceof DfReferenceType) {
      if (type.isLocal()) {
        type = ((DfReferenceType)type).dropLocality();
        value = factory.fromDfType(type);
      }
      if (((DfReferenceType)type).getNullability() == DfaNullability.NULLABLE) {
        type = ((DfReferenceType)type).dropNullability();
      }
    }
    DfaVariableValue var = factory.getVarFactory().createVariableValue(new ContractTempDescriptor(this, type));
    state.getMemoryState().setVarValue(var, value);
    return var;
  }

  public static void flushContractTempVariables(DfaMemoryState state) {
    state.flushVariables(var -> var.getDescriptor() instanceof ContractTempDescriptor);
  }
}
