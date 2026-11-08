// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.java.language.psi.util.ExpressionUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.FieldChecker;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaTypeValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.VariableDescriptor;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiForeachStatement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import consulo.language.psi.PsiManager;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiThisExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A descriptor that represents a PsiVariable (either local, or field -- may have a qualifier)
 */
public final class PlainDescriptor extends PsiVarDescriptor {
  private final PsiVariable myVariable;
  private final int myHash;

  public PlainDescriptor(PsiVariable variable) {
    myVariable = variable;
    myHash = System.identityHashCode(variable);
  }

  @Override
  public String toString() {
    return PsiFormatUtil.formatVariable(myVariable, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    PsiType type = myVariable.getType();
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return getSubstitutor(myVariable, qualifier).substitute(type);
  }

  @Override
  public PsiVariable getPsiElement() {
    return myVariable;
  }

  @Override
  public boolean isStable() {
    return PsiUtil.isJvmLocalVariable(myVariable) ||
           (myVariable.hasModifierProperty(PsiModifier.FINAL) && !hasInitializationHacks(myVariable));
  }

  @Override
  public DfaValue createValue(DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (myVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
      PsiType type = getType(ObjectUtil.tryCast(qualifier, DfaVariableValue.class));
      return factory.fromDfType(DfTypes.typedObject(type, DfaPsiUtil.getElementNullabilityForRead(type, myVariable)));
    }
    if (PsiUtil.isJvmLocalVariable(myVariable) ||
        (myVariable instanceof PsiField && myVariable.hasModifierProperty(PsiModifier.STATIC))) {
      if (qualifier != null && qualifier != factory.getUnknown()) return factory.getUnknown();
      return factory.getVarFactory().createVariableValue(this);
    }
    if (qualifier instanceof DfaTypeValue typeValue) {
      PsiField field = typeValue.getDfType().getConstantOfType(PsiField.class);
      if (field != null) {
        qualifier = factory.getVarFactory().createVariableValue(new PlainDescriptor(field));
      }
    }
    return super.createValue(factory, qualifier);
  }

  @Override
  public int hashCode() {
    return myHash;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PlainDescriptor && ((PlainDescriptor)obj).myVariable == myVariable;
  }

  public static DfaVariableValue createVariableValue(DfaValueFactory factory, PsiVariable variable) {
    DfaVariableValue qualifier = null;
    if (variable instanceof PsiField && !(variable.hasModifierProperty(PsiModifier.STATIC))) {
      qualifier = ThisDescriptor.createThisValue(factory, ((PsiField)variable).getContainingClass());
    }
    return factory.getVarFactory().createVariableValue(new PlainDescriptor(variable), qualifier);
  }

  @Override
  DfaNullability calcCanBeNull(DfaVariableValue value, @Nullable PsiElement context) {
    PsiField field = ObjectUtil.tryCast(myVariable, PsiField.class);
    if (field != null && hasInitializationHacks(field)) {
      return DfaNullability.FLUSHED;
    }

    DfaVariableValue qualifier = value.getQualifier();
    if (field != null && context != null) {
      PsiMember member = ObjectUtil.tryCast(context.getParent(), PsiMember.class);
      if (member != null) {
        PsiClass methodClass = member.getContainingClass();
        if (methodClass != null && methodClass.equals(field.getContainingClass())) {
          PsiMethod method = ObjectUtil.tryCast(member, PsiMethod.class);
          VariableDescriptor qualifierDescriptor = qualifier == null ? null : qualifier.getDescriptor();
          if (qualifierDescriptor instanceof ThisDescriptor && ((ThisDescriptor)qualifierDescriptor).getPsiElement().equals(methodClass)) {
            if (member instanceof PsiClassInitializer) {
              return DfaNullability.UNKNOWN;
            }
            if (method != null) {
              if (!method.isConstructor() && isPossiblyNonInitialized(field, method)) {
                return DfaNullability.NULLABLE;
              }
              else if (method.isConstructor()) {
                return DfaNullability.UNKNOWN;
              }
            }
          }
          if (method != null && field.hasModifierProperty(PsiModifier.STATIC) && isPossiblyNonInitialized(field, method)) {
            return DfaNullability.NULLABLE;
          }
        }
      }
    }

    PsiType type = getType(qualifier);
    Nullability nullability = DfaPsiUtil.getElementNullabilityForRead(type, myVariable);
    if (nullability != Nullability.UNKNOWN) {
      return DfaNullability.fromNullability(nullability);
    }

    if (myVariable instanceof PsiParameter && myVariable.getParent() instanceof PsiForeachStatement forEach) {
      PsiExpression iteratedValue = forEach.getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaNullability.fromNullability(DfaPsiUtil.getElementNullabilityForRead(itemType, myVariable));
        }
      }
    }

    if (field != null && FieldChecker.getChecker(context).canTrustFieldInitializer(field)) {
      return DfaNullability.fromNullability(NullabilityUtil.getNullabilityFromFieldInitializers(field).second);
    }
    return DfaNullability.UNKNOWN;
  }

  private static boolean isPossiblyNonInitialized(PsiField target, PsiMethod placeMethod) {
    if (target.getType() instanceof PsiPrimitiveType) return false;
    PsiClass placeClass = placeMethod.getContainingClass();
    if (placeClass == null || placeClass != target.getContainingClass()) return false;
    if (!placeMethod.hasModifierProperty(PsiModifier.STATIC) && target.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!target.hasModifierProperty(PsiModifier.STATIC) &&
        !placeMethod.hasModifierProperty(PsiModifier.STATIC) &&
        (isReadResolveMethodWithoutReadObject(placeMethod, placeClass) ||
         methodCanBeCalledFromConstructorBeforeFieldInitializing(target, placeMethod, placeClass))) {
      return true;
    }
    return getAccessOffset(placeMethod) < getWriteOffset(target);
  }

  private static final CallMatcher READ_OBJECT = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "readObject")
    .parameterTypes("java.io.ObjectInputStream");  

  private static boolean isReadResolveMethodWithoutReadObject(PsiMethod method, PsiClass aClass) {
    return "readResolve".equals(method.getName()) && method.getParameterList().isEmpty() &&
           !aClass.isRecord() && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_SERIALIZABLE) &&
           !ContainerUtil.exists(aClass.findMethodsByName("readObject", false), READ_OBJECT::methodMatches);
   }

  private record TargetCallInfo(PsiMethod constructor,
                                PsiMethodCallExpression call){}
  private static boolean methodCanBeCalledFromConstructorBeforeFieldInitializing(PsiField target,
                                                                                 PsiMethod method,
                                                                                 PsiClass placeClass) {

    if (target.hasInitializer() || method.isConstructor()) {
      return false;
    }
    TargetCallInfo callInfo = findTargetCallIn(target, placeClass, method);
    if (callInfo == null) {
      return false;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(callInfo.constructor())) {
      return true;
    }
    if (!VariableAccessUtils.variableIsAssignedAtPoint(target, callInfo.constructor, callInfo.call)) {
      return true;
    }
    return false;
  }

  /**
   * @param methods - grouped calls and corresponded methods by their name
   * @param constructor - constructor where these calls are placed
   * @param hasCallOutside - there are any calls, except instance calls of the same class on `this` qualifier
   */
  private record ConstructorMethodInfo(Map<String, Map<PsiMethod, PsiMethodCallExpression>> methods,
                                       @Nullable PsiMethod constructor,
                                       boolean hasCallOutside) {
  }

  /**
   * Find a method call expression in the given field's context class.
   * It is supposed that context class contains only one constructor for simplification
   *
   * @param field        The field to search for method calls.
   * @param contextClass The context class. The first constructor of this class is used to search for method calls.
   * @param method       The method to look for within the context class.
   * @return The found method call expression, or null if not found.
   */
  private static @Nullable TargetCallInfo findTargetCallIn(PsiField field,
                                                 PsiClass contextClass,
                                                 PsiMethod method) {
    ConstructorMethodInfo constructorMethodInfo = getConstructorMethodInfo(contextClass);
    PsiManager psiManager = contextClass.getManager();
    if (field.hasModifierProperty(PsiModifier.FINAL) ||
        //simplification, that there is no other way to initialize fields
        hasOnlyOneInsideCall(constructorMethodInfo)) {
      Map<PsiMethod, PsiMethodCallExpression> methodsByCall = constructorMethodInfo.methods().get(method.getName());
      if (methodsByCall == null) {
        return null;
      }
      for (PsiMethod methodByCall : methodsByCall.keySet()) {
        if (psiManager.areElementsEquivalent(methodByCall, method)) {
          PsiMethodCallExpression call = methodsByCall.get(methodByCall);
          PsiMethod constructor = constructorMethodInfo.constructor();
          if (call != null && constructor != null) {
            return new TargetCallInfo(constructor, call) ;
          }
        }
      }
    }
    return null;
  }

  private static ConstructorMethodInfo getConstructorMethodInfo(PsiClass contextClass) {
    return LanguageCachedValueUtil.getCachedValue(contextClass, () -> {
      CachedValueProvider.Result<ConstructorMethodInfo> emptyResult =
        CachedValueProvider.Result.create(new ConstructorMethodInfo(Map.of(), null, false), PsiModificationTracker.MODIFICATION_COUNT);
      PsiMethod context = findOnlyOneNotChainConstructor(contextClass);
      if(context == null) return emptyResult;
      PsiManager psiManager = context.getManager();
      var visitor = new JavaRecursiveElementWalkingVisitor() {
        final Map<String, Map<PsiMethod, PsiMethodCallExpression>> collectedMethods = new HashMap<>();
        boolean callsOutside = false;

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) { }

        @Override
        public void visitClass(PsiClass aClass) { }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) { }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
          PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
          if (qualifier == null || qualifier instanceof PsiThisExpression) {
            PsiMethod resolvedMethod = methodCallExpression.resolveMethod();
            if (resolvedMethod != null &&
                resolvedMethod.getContainingClass() != null &&
                psiManager.areElementsEquivalent(resolvedMethod.getContainingClass(), contextClass)) {
              collectedMethods.computeIfAbsent(resolvedMethod.getName(), k -> new HashMap<>())
                .putIfAbsent(resolvedMethod, methodCallExpression);
            }
            else {
              callsOutside = true;
            }
          }
          else {
            callsOutside = true;
          }
          super.visitMethodCallExpression(methodCallExpression);
        }
      };
      context.accept(visitor);
      ConstructorMethodInfo info = new ConstructorMethodInfo(visitor.collectedMethods, context, visitor.callsOutside);
      return CachedValueProvider.Result.create(info, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Finds the only constructor in the given context class that is not a chain constructor and doesn't contain super call
   *
   * @param contextClass The context class to search for constructors.
   * @return The found constructor, or null if not found.
   */
  private static @Nullable PsiMethod findOnlyOneNotChainConstructor(PsiClass contextClass) {
    List<PsiMethod> notChainConstructors = new ArrayList<>();
    for (PsiMethod constructor : contextClass.getConstructors()) {
      PsiMethodCallExpression callInConstructor = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (JavaPsiConstructorUtil.isChainedConstructorCall(callInConstructor)) {
        continue;
      }
      notChainConstructors.add(constructor);
    }
    if (notChainConstructors.size() != 1) {
      return null;
    }
    PsiMethod targetConstructor = notChainConstructors.getFirst();
    PsiCodeBlock constructorBody = targetConstructor.getBody();

    if (constructorBody == null || constructorBody.getStatements().length == 0) {
      return null;
    }
    if (JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(targetConstructor) != null) {
      return null;
    }
    return targetConstructor;
  }

  private static boolean hasOnlyOneInsideCall(ConstructorMethodInfo constructorMethodInfo) {
    Map<String, Map<PsiMethod, PsiMethodCallExpression>> methods = constructorMethodInfo.methods();
    if (constructorMethodInfo.hasCallOutside() || methods.size() != 1) {
      return false;
    }
    Map<PsiMethod, PsiMethodCallExpression> callExpressionMap = methods.entrySet().iterator().next().getValue();
    return callExpressionMap.size() == 1;
  }

  private static int getWriteOffset(PsiField target) {
    // Final field: written either in field initializer or in class initializer block which directly writes this field
    // Non-final field: written either in field initializer, in class initializer which directly writes this field or calls any method,
    //    or in other field initializer which directly writes this field or calls any method
    boolean isFinal = target.hasModifierProperty(PsiModifier.FINAL);
    int offset = Integer.MAX_VALUE;
    PsiExpression fieldInitializer = target.getInitializer();
    if (fieldInitializer != null) {
      offset = fieldInitializer.getTextOffset();
      if (isFinal) return offset;
    }
    PsiClass aClass = Objects.requireNonNull(target.getContainingClass());
    PsiClassInitializer[] initializers = aClass.getInitializers();
    Predicate<PsiElement> writesToTarget = element ->
      !PsiTreeUtil.processElements(element, e -> !(e instanceof PsiExpression) ||
                                                 !PsiUtil.isAccessedForWriting((PsiExpression)e) ||
                                                 !ExpressionUtils.isReferenceTo((PsiExpression)e, target));
    Predicate<PsiElement> hasSideEffectCall = element -> !PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class).stream()
      .map(PsiMethodCallExpression::resolveMethod).allMatch(method -> method != null && JavaMethodContractUtil.isPure(method));
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (!isFinal && hasSideEffectCall.test(initializer)) {
        // non-final field could be written indirectly (via method call), so assume it's written in the first applicable initializer
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        break;
      }
      if (writesToTarget.test(initializer)) {
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        if (isFinal) return offset;
        break;
      }
    }
    if (!isFinal) {
      for (PsiField field : aClass.getFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (hasSideEffectCall.test(field.getInitializer()) || writesToTarget.test(field)) {
          offset = Math.min(offset, field.getTextRange().getStartOffset());
          break;
        }
      }
    }
    return offset;
  }

  private static int getAccessOffset(PsiMethod referrer) {
    PsiClass aClass = Objects.requireNonNull(referrer.getContainingClass());
    boolean isStatic = referrer.hasModifierProperty(PsiModifier.STATIC);
    for (PsiField field : aClass.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      PsiExpression initializer = field.getInitializer();
      Predicate<PsiExpression> callToMethod = (PsiExpression e) -> {
        if (!(e instanceof PsiMethodCallExpression call)) return false;
        return call.getMethodExpression().isReferenceTo(referrer) &&
               (isStatic || ExpressionUtil.isEffectivelyUnqualified(call.getMethodExpression()));
      };
      if (ExpressionUtils.isMatchingChildAlwaysExecuted(initializer, callToMethod)) {
        // current method is definitely called from some field initialization
        return field.getTextRange().getStartOffset();
      }
    }
    return Integer.MAX_VALUE; // accessed after initialization or at unknown moment
  }

  /**
   * @param var variable to check
   * @return true if variable is known to be initialized in a weird way and actual initializer should be taken into account.
   * Currently, reports fields declared inside java.lang.System class (System.out, System.in, System.err)
   */
  public static boolean hasInitializationHacks(PsiVariable var) {
    if (!(var instanceof PsiField)) return false;
    PsiClass containingClass = ((PsiField)var).getContainingClass();
    return containingClass != null && System.class.getName().equals(containingClass.getQualifiedName());
  }
}
