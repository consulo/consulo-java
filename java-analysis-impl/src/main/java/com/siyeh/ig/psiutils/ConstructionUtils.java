/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author Tagir Valeev
 */
public class ConstructionUtils {
    private static final Set<String> GUAVA_UTILITY_CLASSES =
        Set.of("com.google.common.collect.Maps", "com.google.common.collect.Lists", "com.google.common.collect.Sets");
    private static final CallMatcher ENUM_SET_NONE_OF = CallMatcher.staticCall("java.util.EnumSet", "noneOf").parameterCount(1);

    /**
     * Checks that given expression initializes empty StringBuilder or StringBuffer (either with explicit default capacity or not)
     *
     * @param initializer initializer to check
     * @return true if the initializer is empty StringBuilder or StringBuffer initializer
     */
    @Contract("null -> false")
    public static boolean isEmptyStringBuilderInitializer(PsiExpression initializer) {
        return "\"\"".equals(getStringBuilderInitializerText(initializer));
    }

    /**
     * Returns a textual representation of an expression which is equivalent to the initial value of newly created StringBuilder or StringBuffer
     *
     * @param construction StringBuilder/StringBuffer construction expression
     * @return a textual representation of an initial value CharSequence or null if supplied expression is not StringBuilder/StringBuffer
     * construction expression
     */
    @Contract("null -> null")
    public static String getStringBuilderInitializerText(PsiExpression construction) {
        construction = PsiUtil.skipParenthesizedExprDown(construction);
        if (!(construction instanceof PsiNewExpression)) {
            return null;
        }
        final PsiNewExpression newExpression = (PsiNewExpression)construction;
        final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference == null) {
            return null;
        }
        final PsiElement target = classReference.resolve();
        if (!(target instanceof PsiClass)) {
            return null;
        }
        final PsiClass aClass = (PsiClass)target;
        final String qualifiedName = aClass.getQualifiedName();
        if (!JavaClassNames.JAVA_LANG_STRING_BUILDER.equals(qualifiedName) && !JavaClassNames.JAVA_LANG_STRING_BUFFER.equals(qualifiedName)) {
            return null;
        }
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length == 0) {
            return "\"\"";
        }
        if (arguments.length != 1) {
            return null;
        }
        final PsiExpression argument = arguments[0];
        final PsiType argumentType = argument.getType();
        if (PsiType.INT.equals(argumentType)) {
            return "\"\"";
        }
        return argument.getText();
    }

    /**
     * Checks that given expression initializes empty Collection or Map
     *
     * @param expression expression to check
     * @return true if the expression is the empty Collection or Map initializer
     */
    @Contract("null -> false")
    public static boolean isEmptyCollectionInitializer(PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression instanceof PsiNewExpression) {
            PsiExpressionList argumentList = ((PsiNewExpression)expression).getArgumentList();
            if (argumentList != null && argumentList.getExpressions().length == 0) {
                PsiType type = expression.getType();
                return InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_UTIL_COLLECTION)
                    || InheritanceUtil.isInheritor(type, JavaClassNames.JAVA_UTIL_MAP);
            }
        }
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
            String name = call.getMethodExpression().getReferenceName();
            PsiExpressionList argumentList = call.getArgumentList();
            if (name != null && name.startsWith("new") && argumentList.getExpressions().length == 0) {
                PsiMethod method = call.resolveMethod();
                if (method != null && method.getParameterList().getParametersCount() == 0) {
                    PsiClass aClass = method.getContainingClass();
                    if (aClass != null) {
                        String qualifiedName = aClass.getQualifiedName();
                        if (GUAVA_UTILITY_CLASSES.contains(qualifiedName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return isCustomizedEmptyCollectionInitializer(expression);
    }

    /**
     * Checks that given expression initializes empty Collection or Map with custom initial capacity or load factor
     *
     * @param expression expression to check
     * @return true if the expression is the empty Collection or Map initializer with custom initial capacity or load factor
     */
    @Contract("null -> false")
    public static boolean isCustomizedEmptyCollectionInitializer(PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression instanceof PsiNewExpression) {
            PsiExpressionList argumentList = ((PsiNewExpression)expression).getArgumentList();
            if (argumentList == null || argumentList.getExpressions().length == 0) {
                return false;
            }
            PsiMethod constructor = ((PsiNewExpression)expression).resolveConstructor();
            if (constructor == null) {
                return false;
            }
            PsiClass aClass = constructor.getContainingClass();
            if (aClass != null && (aClass.getQualifiedName() == null || !aClass.getQualifiedName().startsWith("java.util."))) {
                return false;
            }
            if (!InheritanceUtil.isInheritor(aClass, JavaClassNames.JAVA_UTIL_COLLECTION)
                && !InheritanceUtil.isInheritor(aClass, JavaClassNames.JAVA_UTIL_MAP)) {
                return false;
            }
            Predicate<PsiType> allowedParameterType =
                t -> t instanceof PsiPrimitiveType || InheritanceUtil.isInheritor(t, JavaClassNames.JAVA_LANG_CLASS);
            return Stream.of(constructor.getParameterList().getParameters()).map(PsiParameter::getType).allMatch(allowedParameterType);
        }
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
            if (ENUM_SET_NONE_OF.test(call)) {
                return true;
            }
            String name = call.getMethodExpression().getReferenceName();
            PsiExpressionList argumentList = call.getArgumentList();
            if (name != null && name.startsWith("new") && argumentList.getExpressions().length > 0) {
                PsiMethod method = call.resolveMethod();
                if (method != null && method.getParameterList().getParametersCount() > 0) {
                    PsiClass aClass = method.getContainingClass();
                    if (aClass != null) {
                        String qualifiedName = aClass.getQualifiedName();
                        if (GUAVA_UTILITY_CLASSES.contains(qualifiedName)) {
                            return Stream.of(method.getParameterList().getParameters())
                                .allMatch(p -> p.getType() instanceof PsiPrimitiveType);
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isPrepopulatedCollectionInitializer(PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression instanceof PsiNewExpression) {
            PsiExpressionList args = ((PsiNewExpression)expression).getArgumentList();
            if (args == null || args.isEmpty()) {
                return false;
            }
            PsiMethod ctor = ((PsiNewExpression)expression).resolveMethod();
            if (ctor == null) {
                return false;
            }
            PsiClass aClass = ctor.getContainingClass();
            if (aClass == null) {
                return false;
            }
            String name = aClass.getQualifiedName();
            if (name == null || !name.startsWith("java.util.")) {
                return false;
            }
            for (PsiParameter parameter : ctor.getParameterList().getParameters()) {
                PsiType type = parameter.getType();
                if (type instanceof PsiClassType) {
                    PsiClassType rawType = ((PsiClassType)type).rawType();
                    if (rawType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION) ||
                        rawType.equalsToText(CommonClassNames.JAVA_UTIL_MAP)) {
                        return true;
                    }
                }
            }
        }
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
            String name = call.getMethodExpression().getReferenceName();
            PsiExpressionList argumentList = call.getArgumentList();
            if (name != null && name.startsWith("new") && !argumentList.isEmpty()) {
                PsiMethod method = call.resolveMethod();
                if (method == null) {
                    return false;
                }
                PsiClass aClass = method.getContainingClass();
                if (aClass == null) {
                    return false;
                }
                String qualifiedName = aClass.getQualifiedName();
                if (!GUAVA_UTILITY_CLASSES.contains(qualifiedName)) {
                    return false;
                }
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    PsiType type = parameter.getType();
                    if (type instanceof PsiEllipsisType) {
                        return true;
                    }
                    if (type instanceof PsiClassType) {
                        PsiClassType rawType = ((PsiClassType)type).rawType();
                        if (rawType.equalsToText(CommonClassNames.JAVA_LANG_ITERABLE) ||
                            rawType.equalsToText(CommonClassNames.JAVA_UTIL_ITERATOR)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if given expression is an empty array initializer
     *
     * @param expression expression to test
     * @return true if supplied expression is an empty array initializer
     */
    public static boolean isEmptyArrayInitializer(@Nullable PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (!(expression instanceof PsiNewExpression)) {
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression)expression;
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if (dimensions.length == 0) {
            final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
            if (arrayInitializer == null) {
                return false;
            }
            final PsiExpression[] initializers = arrayInitializer.getInitializers();
            return initializers.length == 0;
        }
        for (PsiExpression dimension : dimensions) {
            final String dimensionText = dimension.getText();
            if (!"0".equals(dimensionText)) {
                return false;
            }
        }
        return true;
    }
}
