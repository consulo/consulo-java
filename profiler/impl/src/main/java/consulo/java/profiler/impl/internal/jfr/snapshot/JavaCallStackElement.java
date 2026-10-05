/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.profiler.impl.internal.jfr.snapshot;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.ReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.execution.profiler.BaseCallStackElement;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java method frame of a JFR stack, keyed by its class, method name and descriptor.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JavaCallStackElement extends BaseCallStackElement {
    private static final String LAMBDA_CLASS_MARKER = "$$Lambda";
    private static final String LAMBDA_METHOD_PREFIX = "lambda$";
    private static final String CONSTRUCTOR = "<init>";
    private static final String CLASS_INITIALIZER = "<clinit>";

    private final String myClassName;
    private final String myMethodName;
    private final String myDescriptor;

    public JavaCallStackElement(String className, String methodName, String descriptor) {
        myClassName = className;
        myMethodName = methodName;
        myDescriptor = descriptor;
    }

    /**
     * @return the binary name of the class, such as {@code java.util.HashMap$Node}
     */
    public String getClassName() {
        return myClassName;
    }

    public String getMethodName() {
        return myMethodName;
    }

    /**
     * @return the JVM descriptor of the method, such as {@code (Ljava/lang/String;)V}, or empty when unknown
     */
    public String getDescriptor() {
        return myDescriptor;
    }

    public String getPackageName() {
        int dot = myClassName.lastIndexOf('.');
        return dot < 0 ? "" : myClassName.substring(0, dot);
    }

    public String getShortClassName() {
        int dot = myClassName.lastIndexOf('.');
        return dot < 0 ? myClassName : myClassName.substring(dot + 1);
    }

    @Override
    public String fullName() {
        return myClassName + "." + myMethodName;
    }

    @Override
    public boolean isNavigatable() {
        return true;
    }

    @Override
    public NavigatablePsiElement[] calcNavigatables(Project project) {
        return ReadAction.compute(() -> {
            if (project.isDisposed()) {
                return NavigatablePsiElement.EMPTY_ARRAY;
            }
            try {
                return findNavigatables(project);
            }
            catch (IndexNotReadyException e) {
                return NavigatablePsiElement.EMPTY_ARRAY;
            }
        });
    }

    private NavigatablePsiElement[] findNavigatables(Project project) {
        String className = myClassName;
        int lambda = className.indexOf(LAMBDA_CLASS_MARKER);
        if (lambda > 0) {
            className = className.substring(0, lambda);
        }

        PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className, null, true, GlobalSearchScope.allScope(project));
        if (psiClass == null) {
            return NavigatablePsiElement.EMPTY_ARRAY;
        }

        String methodName = myMethodName;
        if (methodName.startsWith(LAMBDA_METHOD_PREFIX)) {
            int end = methodName.indexOf('$', LAMBDA_METHOD_PREFIX.length());
            methodName = end < 0 ? methodName.substring(LAMBDA_METHOD_PREFIX.length()) : methodName.substring(LAMBDA_METHOD_PREFIX.length(), end);
            return toNavigatables(psiClass, findMethods(psiClass, methodName, null));
        }
        if (CLASS_INITIALIZER.equals(methodName)) {
            return toNavigatables(psiClass, List.of());
        }

        List<String> parameterTypes = parseParameterTypes(myDescriptor);
        if (CONSTRUCTOR.equals(methodName)) {
            return toNavigatables(psiClass, matchParameters(List.of(psiClass.getConstructors()), parameterTypes));
        }
        return toNavigatables(psiClass, findMethods(psiClass, methodName, parameterTypes));
    }

    private static List<PsiMethod> findMethods(PsiClass psiClass, String methodName, @Nullable List<String> parameterTypes) {
        List<PsiMethod> methods = List.of(psiClass.findMethodsByName(methodName, false));
        return parameterTypes == null ? methods : matchParameters(methods, parameterTypes);
    }

    private static List<PsiMethod> matchParameters(List<PsiMethod> methods, @Nullable List<String> parameterTypes) {
        if (parameterTypes == null || methods.size() <= 1) {
            return methods;
        }

        List<PsiMethod> sameCount = new ArrayList<>();
        for (PsiMethod method : methods) {
            if (method.getParameterList().getParametersCount() == parameterTypes.size()) {
                sameCount.add(method);
            }
        }
        if (sameCount.size() <= 1) {
            return sameCount.isEmpty() ? methods : sameCount;
        }

        for (PsiMethod method : sameCount) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            boolean matches = true;
            for (int i = 0; i < parameters.length && matches; i++) {
                PsiType erased = TypeConversionUtil.erasure(parameters[i].getType());
                matches = erased != null && parameterTypes.get(i).equals(erased.getCanonicalText());
            }
            if (matches) {
                return List.of(method);
            }
        }
        return sameCount;
    }

    private static NavigatablePsiElement[] toNavigatables(PsiClass psiClass, List<PsiMethod> methods) {
        List<NavigatablePsiElement> result = new ArrayList<>();
        for (PsiMethod method : methods) {
            if (method instanceof NavigatablePsiElement navigatable) {
                result.add(navigatable);
            }
        }
        if (result.isEmpty() && psiClass instanceof NavigatablePsiElement navigatable) {
            result.add(navigatable);
        }
        return result.toArray(NavigatablePsiElement.EMPTY_ARRAY);
    }

    /**
     * @return the canonical names of the parameter types in a JVM method descriptor, or null when it can't be read
     */
    static @Nullable List<String> parseParameterTypes(String descriptor) {
        if (!descriptor.startsWith("(")) {
            return null;
        }

        List<String> types = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            int dimensions = 0;
            while (descriptor.charAt(index) == '[') {
                dimensions++;
                index++;
            }

            String type;
            char kind = descriptor.charAt(index);
            if (kind == 'L') {
                int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    return null;
                }
                type = descriptor.substring(index + 1, end).replace('/', '.').replace('$', '.');
                index = end + 1;
            }
            else {
                type = primitiveName(kind);
                if (type == null) {
                    return null;
                }
                index++;
            }
            types.add(type + "[]".repeat(dimensions));
        }
        return types;
    }

    private static @Nullable String primitiveName(char kind) {
        return switch (kind) {
            case 'Z' -> "boolean";
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'F' -> "float";
            case 'D' -> "double";
            default -> null;
        };
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaCallStackElement that)) {
            return false;
        }
        return myClassName.equals(that.myClassName) && myMethodName.equals(that.myMethodName) && myDescriptor.equals(that.myDescriptor);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * myClassName.hashCode() + myMethodName.hashCode()) + myDescriptor.hashCode();
    }
}
