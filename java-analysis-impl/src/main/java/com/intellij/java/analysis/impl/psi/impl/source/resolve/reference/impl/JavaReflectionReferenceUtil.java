// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.component.util.Iconable;
import consulo.language.editor.completion.lookup.*;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * @author Pavel.Dolgov
 */
public final class JavaReflectionReferenceUtil {
  // MethodHandle (Java 7) and VarHandle (Java 9) infrastructure
  public static final String JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP = "java.lang.invoke.MethodHandles.Lookup";
  public static final String JAVA_LANG_INVOKE_METHOD_TYPE = "java.lang.invoke.MethodType";

  public static final String METHOD_TYPE = "methodType";
  public static final String GENERIC_METHOD_TYPE = "genericMethodType";

  public static final String FIND_VIRTUAL = "findVirtual";
  public static final String FIND_STATIC = "findStatic";
  public static final String FIND_SPECIAL = "findSpecial";

  public static final String FIND_GETTER = "findGetter";
  public static final String FIND_SETTER = "findSetter";
  public static final String FIND_STATIC_GETTER = "findStaticGetter";
  public static final String FIND_STATIC_SETTER = "findStaticSetter";

  public static final String FIND_VAR_HANDLE = "findVarHandle";
  public static final String FIND_STATIC_VAR_HANDLE = "findStaticVarHandle";

  public static final String FIND_CONSTRUCTOR = "findConstructor";
  public static final String FIND_CLASS = "findClass";

  public static final String[] HANDLE_FACTORY_METHOD_NAMES = {
      FIND_VIRTUAL,
      FIND_STATIC,
      FIND_SPECIAL,
      FIND_GETTER,
      FIND_SETTER,
      FIND_STATIC_GETTER,
      FIND_STATIC_SETTER,
      FIND_VAR_HANDLE,
      FIND_STATIC_VAR_HANDLE
  };

  // Classic reflection infrastructure
  public static final String GET_FIELD = "getField";
  public static final String GET_DECLARED_FIELD = "getDeclaredField";
  public static final String GET_METHOD = "getMethod";
  public static final String GET_DECLARED_METHOD = "getDeclaredMethod";
  public static final String GET_CONSTRUCTOR = "getConstructor";
  public static final String GET_DECLARED_CONSTRUCTOR = "getDeclaredConstructor";

  public static final String JAVA_LANG_CLASS_LOADER = "java.lang.ClassLoader";
  public static final String FOR_NAME = "forName";
  public static final String LOAD_CLASS = "loadClass";
  public static final String GET_CLASS = "getClass";
  public static final String NEW_INSTANCE = "newInstance";
  public static final String TYPE = "TYPE";

  // Atomic field updaters
  public static final String NEW_UPDATER = "newUpdater";
  public static final String ATOMIC_LONG_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicLongFieldUpdater";
  public static final String ATOMIC_INTEGER_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater";
  public static final String ATOMIC_REFERENCE_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater";

  private static final RecursionGuard<PsiElement> ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");

  @Contract("null -> null")
  public static ReflectiveType getReflectiveType(@Nullable PsiExpression context) {
    context = PsiUtil.skipParenthesizedExprDown(context);
    if (context == null) {
      return null;
    }
    if (context instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression) context).getOperand();
      return ReflectiveType.create(operand.getType(), true);
    }

    if (context instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) context;
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();
      if (FOR_NAME.equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangClass(method.getContainingClass())) {
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length == 1) {
            final PsiExpression argument = findDefinition(PsiUtil.skipParenthesizedExprDown(expressions[0]));
            final String className = computeConstantExpression(argument, String.class);
            if (className != null) {
              return ReflectiveType.create(findClass(className, context), true);
            }
          }
        }
      } else if (GET_CLASS.equals(methodReferenceName) && methodCall.getArgumentList().isEmpty()) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangObject(method.getContainingClass())) {
          final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiReferenceExpression) {
            final PsiExpression definition = findVariableDefinition((PsiReferenceExpression) qualifier);
            if (definition != null) {
              return getClassInstanceType(definition);
            }
          }
          //TODO type of the qualifier may be a supertype of the actual value - need to compute the type of the actual value
          // otherwise getDeclaredField and getDeclaredMethod may work not reliably
          if (qualifier != null) {
            return getClassInstanceType(qualifier);
          }
        }
      }
    }

    if (context instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression) context;
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable) resolved;
        if (isJavaLangClass(PsiTypesUtil.getPsiClass(variable.getType()))) {
          final PsiExpression definition = findVariableDefinition(reference, variable);
          if (definition != null) {
            ReflectiveType result = ourGuard.doPreventingRecursion(variable, false, () -> getReflectiveType(definition));
            if (result != null) {
              return result;
            }
          }
        }
      }
    }

    final PsiType type = context.getType();
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType) type).resolveGenerics();
      final PsiClass resolvedElement = resolveResult.getElement();
      if (!isJavaLangClass(resolvedElement)) {
        return null;
      }

      if (context instanceof PsiReferenceExpression && TYPE.equals(((PsiReferenceExpression) context).getReferenceName())) {
        final PsiElement resolved = ((PsiReferenceExpression) context).resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField) resolved;
          if (field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiType[] classTypeArguments = ((PsiClassType) type).getParameters();
            final PsiPrimitiveType unboxedType = classTypeArguments.length == 1
                ? PsiPrimitiveType.getUnboxedType(classTypeArguments[0]) : null;
            if (unboxedType != null && field.getContainingClass() == PsiUtil.resolveClassInClassTypeOnly(classTypeArguments[0])) {
              return ReflectiveType.create(unboxedType, true);
            }
          }
        }
      }
      final PsiTypeParameter[] parameters = resolvedElement.getTypeParameters();
      if (parameters.length == 1) {
        final PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
        final PsiType erasure = TypeConversionUtil.erasure(typeArgument);
        final PsiClass argumentClass = PsiTypesUtil.getPsiClass(erasure);
        if (argumentClass != null && !isJavaLangObject(argumentClass)) {
          return ReflectiveType.create(argumentClass, false);
        }
      }
    }
    return null;
  }

  @Nullable
  private static ReflectiveType getClassInstanceType(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return null;
    }
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression;
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();

      if (NEW_INSTANCE.equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null) {
          final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
          if (arguments.length == 0 && isClassWithName(method.getContainingClass(), CommonClassNames.JAVA_LANG_CLASS)) {
            final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            if (qualifier != null) {
              return ourGuard.doPreventingRecursion(qualifier, false, () -> getReflectiveType(qualifier));
            }
          } else if (arguments.length > 1 && isClassWithName(method.getContainingClass(), CommonClassNames.JAVA_LANG_REFLECT_ARRAY)) {
            final PsiExpression typeExpression = arguments[0];
            if (typeExpression != null) {
              final ReflectiveType itemType =
                  ourGuard.doPreventingRecursion(typeExpression, false, () -> getReflectiveType(typeExpression));
              return ReflectiveType.arrayOf(itemType);
            }
          }
        }
      }
    }
    return ReflectiveType.create(expression.getType(), false);
  }

  @Contract("null,_->null")
  @Nullable
  public static <T> T computeConstantExpression(@Nullable PsiExpression expression, @Nonnull Class<T> expectedType) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    final Object computed = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return ObjectUtil.tryCast(computed, expectedType);
  }

  @Nullable
  public static ReflectiveClass getReflectiveClass(PsiExpression context) {
    final ReflectiveType reflectiveType = getReflectiveType(context);
    return reflectiveType != null ? reflectiveType.getReflectiveClass() : null;
  }

  @Nullable
  public static PsiExpression findDefinition(@Nullable PsiExpression expression) {
    int preventEndlessLoop = 5;
    while (expression instanceof PsiReferenceExpression) {
      if (--preventEndlessLoop == 0) {
        return null;
      }
      expression = findVariableDefinition((PsiReferenceExpression) expression);
    }
    return expression;
  }

  @Nullable
  private static PsiExpression findVariableDefinition(@Nonnull PsiReferenceExpression referenceExpression) {
    final PsiElement resolved = referenceExpression.resolve();
    return resolved instanceof PsiVariable ? findVariableDefinition(referenceExpression, (PsiVariable) resolved) : null;
  }

  @Nullable
  private static PsiExpression findVariableDefinition(@Nonnull PsiReferenceExpression referenceExpression, @Nonnull PsiVariable variable) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        return initializer;
      }
      if (variable instanceof PsiField) {
        return findFinalFieldDefinition(referenceExpression, (PsiField) variable);
      }
    }
    return DeclarationSearchUtils.findDefinition(referenceExpression, variable);
  }

  @Nullable
  private static PsiExpression findFinalFieldDefinition(@Nonnull PsiReferenceExpression referenceExpression, @Nonnull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    final PsiClass psiClass = ObjectUtil.tryCast(field.getParent(), PsiClass.class);
    if (psiClass != null) {
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      final List<PsiClassInitializer> initializers =
          ContainerUtil.filter(psiClass.getInitializers(), initializer -> initializer.hasModifierProperty(PsiModifier.STATIC) == isStatic);
      for (PsiClassInitializer initializer : initializers) {
        final PsiExpression assignedExpression = getAssignedExpression(initializer, field);
        if (assignedExpression != null) {
          return assignedExpression;
        }
      }
      if (!isStatic) {
        final PsiMethod[] constructors = psiClass.getConstructors();
        if (constructors.length == 1) {
          return getAssignedExpression(constructors[0], field);
        }
        for (PsiMethod constructor : constructors) {
          if (PsiTreeUtil.isAncestor(constructor, referenceExpression, true)) {
            return getAssignedExpression(constructor, field);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiExpression getAssignedExpression(@Nonnull PsiMember maybeContainsAssignment, @Nonnull PsiField field) {
    final PsiAssignmentExpression assignment = SyntaxTraverser.psiTraverser(maybeContainsAssignment)
        .filter(PsiAssignmentExpression.class)
        .find(expression -> ExpressionUtils.isReferenceTo(expression.getLExpression(), field));
    return assignment != null ? assignment.getRExpression() : null;
  }

  private static PsiClass findClass(@Nonnull String qualifiedName, @Nonnull PsiElement context) {
    final Project project = context.getProject();
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
  }

  @Contract("null -> false")
  public static boolean isJavaLangClass(@Nullable PsiClass aClass) {
    return isClassWithName(aClass, CommonClassNames.JAVA_LANG_CLASS);
  }

  @Contract("null -> false")
  public static boolean isJavaLangObject(@Nullable PsiClass aClass) {
    return isClassWithName(aClass, CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Contract("null, _ -> false")
  public static boolean isClassWithName(@Nullable PsiClass aClass, @Nonnull String name) {
    return aClass != null && name.equals(aClass.getQualifiedName());
  }

  @Contract("null -> false")
  public static boolean isRegularMethod(@Nullable PsiMethod method) {
    return method != null && !method.isConstructor();
  }

  public static boolean isPublic(@Nonnull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.PUBLIC);
  }

  public static boolean isAtomicallyUpdateable(@Nonnull PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.VOLATILE)) {
      return false;
    }
    final PsiType type = field.getType();
    return !(type instanceof PsiPrimitiveType) || PsiType.INT.equals(type) || PsiType.LONG.equals(type);
  }

  @Nullable
  public static String getParameterTypesText(@Nonnull PsiMethod method) {
    final StringJoiner joiner = new StringJoiner(", ");
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final String typeText = getTypeText(parameter.getType());
      joiner.add(typeText + ".class");
    }
    return joiner.toString();
  }

  public static void shortenArgumentsClassReferences(@Nonnull InsertionContext context) {
    final PsiElement parameter = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(parameter, PsiExpressionList.class);
    if (parameterList != null && parameterList.getParent() instanceof PsiMethodCallExpression) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(parameterList);
    }
  }

  @Nonnull
  public static LookupElement withPriority(@Nonnull LookupElement lookupElement, boolean hasPriority) {
    return hasPriority ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, -1);
  }

  @Nullable
  public static LookupElement withPriority(@Nullable LookupElement lookupElement, int priority) {
    return priority == 0 || lookupElement == null ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, priority);
  }

  public static int getMethodSortOrder(@Nonnull PsiMethod method) {
    return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
  }

  @Nullable
  public static String getMemberType(@Nullable PsiElement element) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  @Nullable
  public static LookupElement lookupMethod(@Nonnull PsiMethod method, @Nullable InsertHandler<LookupElement> insertHandler) {
    final ReflectiveSignature signature = getMethodSignature(method);
    return signature != null
        ? LookupElementBuilder.create(signature, method.getName())
        .withIcon(signature.getIcon())
        .withTailText(signature.getShortArgumentTypes())
        .withInsertHandler(insertHandler)
        : null;
  }

  public static void replaceText(@Nonnull InsertionContext context, @Nonnull String text) {
    final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final PsiElement params = newElement.getParent().getParent();
    final int end = params.getTextRange().getEndOffset() - 1;
    final int start = Math.min(newElement.getTextRange().getEndOffset(), end);

    context.getDocument().replaceString(start, end, text);
    context.commitDocument();
    shortenArgumentsClassReferences(context);
  }

  @Nonnull
  public static String getTypeText(@Nonnull PsiType type) {
    final ReflectiveType reflectiveType = ReflectiveType.create(type, false);
    return reflectiveType.getQualifiedName();
  }

  @Nullable
  public static String getTypeText(@Nullable PsiExpression argument) {
    final ReflectiveType reflectiveType = getReflectiveType(argument);
    return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
  }

  @Contract("null -> null")
  @Nullable
  public static ReflectiveSignature getMethodSignature(@Nullable PsiMethod method) {
    if (method != null) {
      final List<String> types = new ArrayList<>();
      final PsiType returnType = method.getReturnType();
      types.add(getTypeText(returnType != null ? returnType : PsiType.VOID)); // null return type means it's a constructor

      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        types.add(getTypeText(parameter.getType()));
      }
      final Image icon = IconDescriptorUpdaters.getIcon(method, Iconable.ICON_FLAG_VISIBILITY);
      return ReflectiveSignature.create(icon, types);
    }
    return null;
  }

  @Nonnull
  public static String getMethodTypeExpressionText(@Nonnull ReflectiveSignature signature) {
    final String types = signature.getText(true, type -> type + ".class");
    return JAVA_LANG_INVOKE_METHOD_TYPE + "." + METHOD_TYPE + types;
  }

  public static boolean isCallToMethod(@Nonnull PsiMethodCallExpression methodCall, @Nonnull String className, @Nonnull String methodName) {
    return MethodCallUtils.isCallToMethod(methodCall, className, null, methodName, (PsiType[]) null);
  }

  /**
   * Tries to unwrap array and find its components
   *
   * @param maybeArray an array to unwrap
   * @return list of unwrapped array components, some or all of them could be null if unknown (but the length is known);
   * returns null if nothing is known.
   */
  @Nullable
  public static List<PsiExpression> getVarargs(@Nullable PsiExpression maybeArray) {
    if (ExpressionUtils.isNullLiteral(maybeArray)) {
      return Collections.emptyList();
    }
    if (isVarargAsArray(maybeArray)) {
      final PsiExpression argumentsDefinition = findDefinition(maybeArray);
      if (argumentsDefinition instanceof PsiArrayInitializerExpression) {
        return Arrays.asList(((PsiArrayInitializerExpression) argumentsDefinition).getInitializers());
      }
      if (argumentsDefinition instanceof PsiNewExpression) {
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression) argumentsDefinition).getArrayInitializer();
        if (arrayInitializer != null) {
          return Arrays.asList(arrayInitializer.getInitializers());
        }
        final PsiExpression[] dimensions = ((PsiNewExpression) argumentsDefinition).getArrayDimensions();
        if (dimensions.length == 1) { // new Object[length] or new Class<?>[length]
          final Integer itemCount = computeConstantExpression(findDefinition(dimensions[0]), Integer.class);
          if (itemCount != null && itemCount >= 0 && itemCount < 256) {
            return Collections.nCopies(itemCount, null);
          }
        }
      }
    }
    return null;
  }

  @Contract("null -> false")
  public static boolean isVarargAsArray(@Nullable PsiExpression maybeArray) {
    final PsiType type = maybeArray != null ? maybeArray.getType() : null;
    return type instanceof PsiArrayType &&
        type.getArrayDimensions() == 1 &&
        type.getDeepComponentType() instanceof PsiClassType;
  }

  /**
   * Take method's return type and parameter types
   * from arguments of MethodType.methodType(Class...) and MethodType.genericMethodType(int, boolean?)
   */
  @Nullable
  public static ReflectiveSignature composeMethodSignature(@Nullable PsiExpression methodTypeExpression) {
    final PsiExpression typeDefinition = findDefinition(methodTypeExpression);
    if (typeDefinition instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) typeDefinition;
      final String referenceName = methodCallExpression.getMethodExpression().getReferenceName();

      Function<PsiExpression[], ReflectiveSignature> composer = null;
      if (METHOD_TYPE.equals(referenceName)) {
        composer = JavaReflectionReferenceUtil::composeMethodSignatureFromTypes;
      } else if (GENERIC_METHOD_TYPE.equals(referenceName)) {
        composer = JavaReflectionReferenceUtil::composeGenericMethodSignature;
      }

      if (composer != null) {
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method != null) {
          final PsiClass psiClass = method.getContainingClass();
          if (psiClass != null && JAVA_LANG_INVOKE_METHOD_TYPE.equals(psiClass.getQualifiedName())) {
            final PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
            return composer.apply(arguments);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static ReflectiveSignature composeMethodSignatureFromTypes(@Nonnull PsiExpression[] returnAndParameterTypes) {
    final List<String> typeTexts = ContainerUtil.map(returnAndParameterTypes, JavaReflectionReferenceUtil::getTypeText);
    return ReflectiveSignature.create(typeTexts);
  }

  @Nullable
  public static Pair.NonNull<Integer, Boolean> getGenericSignature(@Nonnull PsiExpression[] genericSignatureShape) {
    if (genericSignatureShape.length == 0 || genericSignatureShape.length > 2) {
      return null;
    }

    final Integer objectArgCount = computeConstantExpression(genericSignatureShape[0], Integer.class);
    final Boolean finalArray = // there's an additional parameter which is an ellipsis or an array
        genericSignatureShape.length > 1 ? computeConstantExpression(genericSignatureShape[1], Boolean.class) : false;

    if (objectArgCount == null || objectArgCount < 0 || objectArgCount > 255) {
      return null;
    }
    if (finalArray == null || finalArray && objectArgCount > 254) {
      return null;
    }
    return Pair.createNonNull(objectArgCount, finalArray);
  }

  /**
   * All the types in the method signature are either unbounded type parameters or java.lang.Object (with possible vararg)
   */
  @Nullable
  private static ReflectiveSignature composeGenericMethodSignature(@Nonnull PsiExpression[] genericSignatureShape) {
    final Pair.NonNull<Integer, Boolean> signature = getGenericSignature(genericSignatureShape);
    if (signature == null) {
      return null;
    }
    final int objectArgCount = signature.getFirst();
    final boolean finalArray = signature.getSecond();

    final List<String> typeNames = new ArrayList<>();
    typeNames.add(CommonClassNames.JAVA_LANG_OBJECT); // return type

    for (int i = 0; i < objectArgCount; i++) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT);
    }
    if (finalArray) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT + "[]");
    }
    return ReflectiveSignature.create(typeNames);
  }


  public static final class ReflectiveType {
    final PsiType myType;
    final boolean myIsExact;

    private ReflectiveType(@Nonnull PsiType erasedType, boolean isExact) {
      myType = erasedType;
      myIsExact = isExact;
    }

    @Nonnull
    public String getQualifiedName() {
      return myType.getCanonicalText();
    }

    @Override
    public String toString() {
      return myType.getCanonicalText();
    }

    public boolean isEqualTo(@Nullable PsiType otherType) {
      return otherType != null && myType.equals(erasure(otherType));
    }

    public boolean isAssignableFrom(@Nonnull PsiType type) {
      return myType.isAssignableFrom(type);
    }

    public boolean isPrimitive() {
      return myType instanceof PsiPrimitiveType;
    }

    @Nonnull
    public PsiType getType() {
      return myType;
    }

    public boolean isExact() {
      return myIsExact;
    }

    @Nullable
    public ReflectiveClass getReflectiveClass() {
      PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        return new ReflectiveClass(psiClass, myIsExact);
      }
      return null;
    }

    @Nullable
    public ReflectiveType getArrayComponentType() {
      if (myType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType) myType).getComponentType();
        return new ReflectiveType(componentType, myIsExact);
      }
      return null;
    }

    @Nullable
    public PsiClass getPsiClass() {
      return PsiTypesUtil.getPsiClass(myType);
    }

    @Contract("!null,_ -> !null; null,_ -> null")
    @Nullable
    public static ReflectiveType create(@Nullable PsiType originalType, boolean isExact) {
      if (originalType != null) {
        return new ReflectiveType(erasure(originalType), isExact);
      }
      return null;
    }

    @Contract("!null,_ -> !null; null,_ -> null")
    @Nullable
    public static ReflectiveType create(@Nullable PsiClass psiClass, boolean isExact) {
      if (psiClass != null) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        return new ReflectiveType(factory.createType(psiClass), isExact);
      }
      return null;
    }

    @Contract("!null -> !null; null -> null")
    @Nullable
    public static ReflectiveType arrayOf(@Nullable ReflectiveType itemType) {
      if (itemType != null) {
        return new ReflectiveType(itemType.myType.createArrayType(), itemType.myIsExact);
      }
      return null;
    }

    @Nonnull
    private static PsiType erasure(@Nonnull PsiType type) {
      final PsiType erasure = TypeConversionUtil.erasure(type);
      if (erasure instanceof PsiEllipsisType) {
        return ((PsiEllipsisType) erasure).toArrayType();
      }
      return erasure;
    }
  }

  public static class ReflectiveClass {
    final PsiClass myPsiClass;
    final boolean myIsExact;

    public ReflectiveClass(@Nonnull PsiClass psiClass, boolean isExact) {
      myPsiClass = psiClass;
      myIsExact = isExact;
    }

    @Nonnull
    public PsiClass getPsiClass() {
      return myPsiClass;
    }

    public boolean isExact() {
      return myIsExact || myPsiClass.hasModifierProperty(PsiModifier.FINAL);
    }
  }

  public static final class ReflectiveSignature implements Comparable<ReflectiveSignature> {
    public static final ReflectiveSignature NO_ARGUMENT_CONSTRUCTOR_SIGNATURE =
        new ReflectiveSignature(null, PsiKeyword.VOID, ArrayUtil.EMPTY_STRING_ARRAY);

    private final Image myIcon;
    @Nonnull
    private final String myReturnType;
    @Nonnull
    private final String[] myArgumentTypes;

    @Nullable
    public static ReflectiveSignature create(@Nonnull List<String> typeTexts) {
      return create(null, typeTexts);
    }

    @Nullable
    public static ReflectiveSignature create(@Nullable Image icon, @Nonnull List<String> typeTexts) {
      if (!typeTexts.isEmpty() && !typeTexts.contains(null)) {
        final String[] argumentTypes = ArrayUtil.toStringArray(typeTexts.subList(1, typeTexts.size()));
        return new ReflectiveSignature(icon, typeTexts.get(0), argumentTypes);
      }
      return null;
    }

    private ReflectiveSignature(@Nullable Image icon, @Nonnull String returnType, @Nonnull String[] argumentTypes) {
      myIcon = icon;
      myReturnType = returnType;
      myArgumentTypes = argumentTypes;
    }

    public String getText(boolean withReturnType, @Nonnull Function<? super String, String> transformation) {
      return getText(withReturnType, true, transformation);
    }

    public String getText(boolean withReturnType, boolean withParentheses, @Nonnull Function<? super String, String> transformation) {
      final StringJoiner joiner = new StringJoiner(", ", withParentheses ? "(" : "", withParentheses ? ")" : "");
      if (withReturnType) {
        joiner.add(transformation.apply(myReturnType));
      }
      for (String argumentType : myArgumentTypes) {
        joiner.add(transformation.apply(argumentType));
      }
      return joiner.toString();
    }

    @Nonnull
    public String getShortReturnType() {
      return PsiNameHelper.getShortClassName(myReturnType);
    }

    @Nonnull
    public String getShortArgumentTypes() {
      return getText(false, PsiNameHelper::getShortClassName);
    }

    @Nonnull
    public Image getIcon() {
      return myIcon != null ? myIcon : PlatformIconGroup.nodesMethod();
    }

    @Override
    public int compareTo(@Nonnull ReflectiveSignature other) {
      int c = myArgumentTypes.length - other.myArgumentTypes.length;
      if (c != 0) {
        return c;
      }
      c = ArrayUtil.lexicographicCompare(myArgumentTypes, other.myArgumentTypes);
      if (c != 0) {
        return c;
      }
      return myReturnType.compareTo(other.myReturnType);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ReflectiveSignature)) {
        return false;
      }
      final ReflectiveSignature other = (ReflectiveSignature) o;
      return Objects.equals(myReturnType, other.myReturnType) &&
          Arrays.equals(myArgumentTypes, other.myArgumentTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myReturnType, myArgumentTypes);
    }

    @Override
    public String toString() {
      return myReturnType + " " + Arrays.toString(myArgumentTypes);
    }
  }
}
