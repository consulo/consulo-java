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
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.component.util.Iconable;
import consulo.java.language.module.util.JavaClassNames;
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
    @RequiredReadAction
    public static ReflectiveType getReflectiveType(@Nullable PsiExpression context) {
        context = PsiUtil.skipParenthesizedExprDown(context);
        if (context == null) {
            return null;
        }
        if (context instanceof PsiClassObjectAccessExpression classObjectAccess) {
            PsiTypeElement operand = classObjectAccess.getOperand();
            return ReflectiveType.create(operand.getType(), true);
        }

        if (context instanceof PsiMethodCallExpression methodCall) {
            String methodReferenceName = methodCall.getMethodExpression().getReferenceName();
            if (FOR_NAME.equals(methodReferenceName)) {
                PsiMethod method = methodCall.resolveMethod();
                if (method != null && isJavaLangClass(method.getContainingClass())) {
                    PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
                    if (expressions.length == 1) {
                        PsiExpression argument = findDefinition(PsiUtil.skipParenthesizedExprDown(expressions[0]));
                        String className = computeConstantExpression(argument, String.class);
                        if (className != null) {
                            return ReflectiveType.create(findClass(className, context), true);
                        }
                    }
                }
            }
            else if (GET_CLASS.equals(methodReferenceName) && methodCall.getArgumentList().isEmpty()) {
                PsiMethod method = methodCall.resolveMethod();
                if (method != null && isJavaLangObject(method.getContainingClass())) {
                    PsiExpression qualifier =
                        PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression());
                    if (qualifier instanceof PsiReferenceExpression qRefExpr) {
                        PsiExpression definition = findVariableDefinition(qRefExpr);
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

        if (context instanceof PsiReferenceExpression reference
            && reference.resolve() instanceof PsiVariable variable
            && isJavaLangClass(PsiTypesUtil.getPsiClass(variable.getType()))) {
            PsiExpression definition = findVariableDefinition(reference, variable);
            if (definition != null) {
                ReflectiveType result = ourGuard.doPreventingRecursion(variable, false, () -> getReflectiveType(definition));
                if (result != null) {
                    return result;
                }
            }
        }

        PsiType type = context.getType();
        if (type instanceof PsiClassType classType) {
            PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
            PsiClass resolvedElement = resolveResult.getElement();
            if (!isJavaLangClass(resolvedElement)) {
                return null;
            }

            if (context instanceof PsiReferenceExpression refExpr
                && TYPE.equals(refExpr.getReferenceName())
                && refExpr.resolve() instanceof PsiField field
                && field.isFinal() && field.isStatic()) {
                PsiType[] classTypeArguments = classType.getParameters();
                PsiPrimitiveType unboxedType = classTypeArguments.length == 1
                    ? PsiPrimitiveType.getUnboxedType(classTypeArguments[0]) : null;
                if (unboxedType != null && field.getContainingClass() == PsiUtil.resolveClassInClassTypeOnly(classTypeArguments[0])) {
                    return ReflectiveType.create(unboxedType, true);
                }
            }
            PsiTypeParameter[] parameters = resolvedElement.getTypeParameters();
            if (parameters.length == 1) {
                PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
                PsiType erasure = TypeConversionUtil.erasure(typeArgument);
                PsiClass argumentClass = PsiTypesUtil.getPsiClass(erasure);
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
        if (expression instanceof PsiMethodCallExpression methodCall) {
            String methodReferenceName = methodCall.getMethodExpression().getReferenceName();

            if (NEW_INSTANCE.equals(methodReferenceName)) {
                PsiMethod method = methodCall.resolveMethod();
                if (method != null) {
                    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
                    if (arguments.length == 0 && isClassWithName(method.getContainingClass(), JavaClassNames.JAVA_LANG_CLASS)) {
                        PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
                        if (qualifier != null) {
                            return ourGuard.doPreventingRecursion(qualifier, false, () -> getReflectiveType(qualifier));
                        }
                    }
                    else if (arguments.length > 1
                        && isClassWithName(method.getContainingClass(), JavaClassNames.JAVA_LANG_REFLECT_ARRAY)) {
                        PsiExpression typeExpression = arguments[0];
                        if (typeExpression != null) {
                            ReflectiveType itemType =
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
        Object computed = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
        return ObjectUtil.tryCast(computed, expectedType);
    }

    @Nullable
    @RequiredReadAction
    public static ReflectiveClass getReflectiveClass(PsiExpression context) {
        ReflectiveType reflectiveType = getReflectiveType(context);
        return reflectiveType != null ? reflectiveType.getReflectiveClass() : null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiExpression findDefinition(@Nullable PsiExpression expression) {
        int preventEndlessLoop = 5;
        while (expression instanceof PsiReferenceExpression refExpr) {
            if (--preventEndlessLoop == 0) {
                return null;
            }
            expression = findVariableDefinition(refExpr);
        }
        return expression;
    }

    @Nullable
    @RequiredReadAction
    private static PsiExpression findVariableDefinition(@Nonnull PsiReferenceExpression referenceExpression) {
        return referenceExpression.resolve() instanceof PsiVariable variable
            ? findVariableDefinition(referenceExpression, variable)
            : null;
    }

    @Nullable
    private static PsiExpression findVariableDefinition(
        @Nonnull PsiReferenceExpression referenceExpression,
        @Nonnull PsiVariable variable
    ) {
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer != null) {
                return initializer;
            }
            if (variable instanceof PsiField field) {
                return findFinalFieldDefinition(referenceExpression, field);
            }
        }
        return DeclarationSearchUtils.findDefinition(referenceExpression, variable);
    }

    @Nullable
    private static PsiExpression findFinalFieldDefinition(@Nonnull PsiReferenceExpression referenceExpression, @Nonnull PsiField field) {
        if (!field.isFinal()) {
            return null;
        }
        PsiClass psiClass = ObjectUtil.tryCast(field.getParent(), PsiClass.class);
        if (psiClass != null) {
            boolean isStatic = field.isStatic();
            List<PsiClassInitializer> initializers = ContainerUtil.filter(
                psiClass.getInitializers(),
                initializer -> initializer.isStatic() == isStatic
            );
            for (PsiClassInitializer initializer : initializers) {
                PsiExpression assignedExpression = getAssignedExpression(initializer, field);
                if (assignedExpression != null) {
                    return assignedExpression;
                }
            }
            if (!isStatic) {
                PsiMethod[] constructors = psiClass.getConstructors();
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
        PsiAssignmentExpression assignment = SyntaxTraverser.psiTraverser(maybeContainsAssignment)
            .filter(PsiAssignmentExpression.class)
            .find(expression -> ExpressionUtils.isReferenceTo(expression.getLExpression(), field));
        return assignment != null ? assignment.getRExpression() : null;
    }

    private static PsiClass findClass(@Nonnull String qualifiedName, @Nonnull PsiElement context) {
        Project project = context.getProject();
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
    }

    @Contract("null -> false")
    public static boolean isJavaLangClass(@Nullable PsiClass aClass) {
        return isClassWithName(aClass, JavaClassNames.JAVA_LANG_CLASS);
    }

    @Contract("null -> false")
    public static boolean isJavaLangObject(@Nullable PsiClass aClass) {
        return isClassWithName(aClass, JavaClassNames.JAVA_LANG_OBJECT);
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
        return member.isPublic();
    }

    public static boolean isAtomicallyUpdateable(@Nonnull PsiField field) {
        if (field.isStatic() || !field.hasModifierProperty(PsiModifier.VOLATILE)) {
            return false;
        }
        PsiType type = field.getType();
        return !(type instanceof PsiPrimitiveType) || PsiType.INT.equals(type) || PsiType.LONG.equals(type);
    }

    @Nullable
    public static String getParameterTypesText(@Nonnull PsiMethod method) {
        StringJoiner joiner = new StringJoiner(", ");
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String typeText = getTypeText(parameter.getType());
            joiner.add(typeText + ".class");
        }
        return joiner.toString();
    }

    public static void shortenArgumentsClassReferences(@Nonnull InsertionContext context) {
        PsiElement parameter = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
        PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(parameter, PsiExpressionList.class);
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
        PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
    }

    @Nullable
    @RequiredReadAction
    public static LookupElement lookupMethod(@Nonnull PsiMethod method, @Nullable InsertHandler<LookupElement> insertHandler) {
        ReflectiveSignature signature = getMethodSignature(method);
        return signature != null
            ? LookupElementBuilder.create(signature, method.getName())
                .withIcon(signature.getIcon())
                .withTailText(signature.getShortArgumentTypes())
                .withInsertHandler(insertHandler)
            : null;
    }

    @RequiredReadAction
    public static void replaceText(@Nonnull InsertionContext context, @Nonnull String text) {
        PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
        PsiElement params = newElement.getParent().getParent();
        int end = params.getTextRange().getEndOffset() - 1;
        int start = Math.min(newElement.getTextRange().getEndOffset(), end);

        context.getDocument().replaceString(start, end, text);
        context.commitDocument();
        shortenArgumentsClassReferences(context);
    }

    @Nonnull
    public static String getTypeText(@Nonnull PsiType type) {
        ReflectiveType reflectiveType = ReflectiveType.create(type, false);
        return reflectiveType.getQualifiedName();
    }

    @Nullable
    @RequiredReadAction
    public static String getTypeText(@Nullable PsiExpression argument) {
        ReflectiveType reflectiveType = getReflectiveType(argument);
        return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
    }

    @Contract("null -> null")
    @Nullable
    @RequiredReadAction
    public static ReflectiveSignature getMethodSignature(@Nullable PsiMethod method) {
        if (method != null) {
            List<String> types = new ArrayList<>();
            PsiType returnType = method.getReturnType();
            types.add(getTypeText(returnType != null ? returnType : PsiType.VOID)); // null return type means it's a constructor

            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                types.add(getTypeText(parameter.getType()));
            }
            Image icon = IconDescriptorUpdaters.getIcon(method, Iconable.ICON_FLAG_VISIBILITY);
            return ReflectiveSignature.create(icon, types);
        }
        return null;
    }

    @Nonnull
    public static String getMethodTypeExpressionText(@Nonnull ReflectiveSignature signature) {
        String types = signature.getText(true, type -> type + ".class");
        return JAVA_LANG_INVOKE_METHOD_TYPE + "." + METHOD_TYPE + types;
    }

    public static boolean isCallToMethod(
        @Nonnull PsiMethodCallExpression methodCall,
        @Nonnull String className,
        @Nonnull String methodName
    ) {
        return MethodCallUtils.isCallToMethod(methodCall, className, null, methodName);
    }

    /**
     * Tries to unwrap array and find its components
     *
     * @param maybeArray an array to unwrap
     * @return list of unwrapped array components, some or all of them could be null if unknown (but the length is known);
     * returns null if nothing is known.
     */
    @Nullable
    @RequiredReadAction
    public static List<PsiExpression> getVarargs(@Nullable PsiExpression maybeArray) {
        if (ExpressionUtils.isNullLiteral(maybeArray)) {
            return Collections.emptyList();
        }
        if (isVarargAsArray(maybeArray)) {
            PsiExpression argumentsDefinition = findDefinition(maybeArray);
            if (argumentsDefinition instanceof PsiArrayInitializerExpression arrayInitializer) {
                return Arrays.asList(arrayInitializer.getInitializers());
            }
            if (argumentsDefinition instanceof PsiNewExpression newExpr) {
                PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
                if (arrayInitializer != null) {
                    return Arrays.asList(arrayInitializer.getInitializers());
                }
                PsiExpression[] dimensions = newExpr.getArrayDimensions();
                if (dimensions.length == 1) { // new Object[length] or new Class<?>[length]
                    Integer itemCount = computeConstantExpression(findDefinition(dimensions[0]), Integer.class);
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
        return maybeArray != null
            && maybeArray.getType() instanceof PsiArrayType arrayType
            && arrayType.getArrayDimensions() == 1
            && arrayType.getDeepComponentType() instanceof PsiClassType;
    }

    /**
     * Take method's return type and parameter types
     * from arguments of MethodType.methodType(Class...) and MethodType.genericMethodType(int, boolean?)
     */
    @Nullable
    @RequiredReadAction
    public static ReflectiveSignature composeMethodSignature(@Nullable PsiExpression methodTypeExpression) {
        PsiExpression typeDefinition = findDefinition(methodTypeExpression);
        if (typeDefinition instanceof PsiMethodCallExpression methodCallExpression) {
            String referenceName = methodCallExpression.getMethodExpression().getReferenceName();

            Function<PsiExpression[], ReflectiveSignature> composer = null;
            if (METHOD_TYPE.equals(referenceName)) {
                composer = JavaReflectionReferenceUtil::composeMethodSignatureFromTypes;
            }
            else if (GENERIC_METHOD_TYPE.equals(referenceName)) {
                composer = JavaReflectionReferenceUtil::composeGenericMethodSignature;
            }

            if (composer != null) {
                PsiMethod method = methodCallExpression.resolveMethod();
                if (method != null) {
                    PsiClass psiClass = method.getContainingClass();
                    if (psiClass != null && JAVA_LANG_INVOKE_METHOD_TYPE.equals(psiClass.getQualifiedName())) {
                        PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
                        return composer.apply(arguments);
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static ReflectiveSignature composeMethodSignatureFromTypes(@Nonnull PsiExpression[] returnAndParameterTypes) {
        List<String> typeTexts = ContainerUtil.map(returnAndParameterTypes, JavaReflectionReferenceUtil::getTypeText);
        return ReflectiveSignature.create(typeTexts);
    }

    @Nullable
    public static Pair.NonNull<Integer, Boolean> getGenericSignature(@Nonnull PsiExpression[] genericSignatureShape) {
        if (genericSignatureShape.length == 0 || genericSignatureShape.length > 2) {
            return null;
        }

        Integer objectArgCount = computeConstantExpression(genericSignatureShape[0], Integer.class);
        Boolean finalArray = // there's an additional parameter which is an ellipsis or an array
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
        Pair.NonNull<Integer, Boolean> signature = getGenericSignature(genericSignatureShape);
        if (signature == null) {
            return null;
        }
        int objectArgCount = signature.getFirst();
        boolean finalArray = signature.getSecond();

        List<String> typeNames = new ArrayList<>();
        typeNames.add(JavaClassNames.JAVA_LANG_OBJECT); // return type

        for (int i = 0; i < objectArgCount; i++) {
            typeNames.add(JavaClassNames.JAVA_LANG_OBJECT);
        }
        if (finalArray) {
            typeNames.add(JavaClassNames.JAVA_LANG_OBJECT + "[]");
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
            return myType instanceof PsiArrayType arrayType ? new ReflectiveType(arrayType.getComponentType(), myIsExact) : null;
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
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
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
            PsiType erasure = TypeConversionUtil.erasure(type);
            if (erasure instanceof PsiEllipsisType ellipsisType) {
                return ellipsisType.toArrayType();
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
            return myIsExact || myPsiClass.isFinal();
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
                String[] argumentTypes = ArrayUtil.toStringArray(typeTexts.subList(1, typeTexts.size()));
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
            StringJoiner joiner = new StringJoiner(", ", withParentheses ? "(" : "", withParentheses ? ")" : "");
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
            return o == this
                || o instanceof ReflectiveSignature that
                && Objects.equals(myReturnType, that.myReturnType)
                && Arrays.equals(myArgumentTypes, that.myArgumentTypes);
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
