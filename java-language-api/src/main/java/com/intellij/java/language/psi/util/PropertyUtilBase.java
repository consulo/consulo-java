// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.util.PropertyKind;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kava.beans.Introspector;
import org.jetbrains.annotations.Contract;

import java.util.*;

public class PropertyUtilBase {
    protected static final String GET_PREFIX = PropertyKind.GETTER.prefix;
    protected static final String IS_PREFIX = PropertyKind.BOOLEAN_GETTER.prefix;
    @Nonnull
    protected static final String SET_PREFIX = PropertyKind.SETTER.prefix;

    @Nullable
    public static String getPropertyName(@Nonnull String methodName) {
        return StringUtil.getPropertyName(methodName);
    }

    @Nonnull
    public static Map<String, PsiMethod> getAllProperties(
        @Nonnull PsiClass psiClass,
        boolean acceptSetters,
        boolean acceptGetters
    ) {
        return getAllProperties(psiClass, acceptSetters, acceptGetters, true);
    }

    @Nonnull
    public static Map<String, PsiMethod> getAllProperties(
        @Nonnull PsiClass psiClass,
        boolean acceptSetters,
        boolean acceptGetters,
        boolean includeSuperClass
    ) {
        return getAllProperties(acceptSetters, acceptGetters, includeSuperClass ? psiClass.getAllMethods() : psiClass.getMethods());
    }

    @Nonnull
    public static Map<String, PsiMethod> getAllProperties(boolean acceptSetters, boolean acceptGetters, PsiMethod[] methods) {
        Map<String, PsiMethod> map = new HashMap<>();

        for (PsiMethod method : methods) {
            if (filterMethods(method)) {
                continue;
            }
            if (acceptSetters && isSimplePropertySetter(method) ||
                acceptGetters && isSimplePropertyGetter(method)) {
                map.put(getPropertyName(method), method);
            }
        }
        return map;
    }


    private static boolean filterMethods(PsiMethod method) {
        if (method.isStatic() || !method.isPublic()) {
            return true;
        }

        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null) {
            return false;
        }
        String className = psiClass.getQualifiedName();
        return JavaClassNames.JAVA_LANG_OBJECT.equals(className);
    }

    @Nonnull
    public static List<PsiMethod> getSetters(@Nonnull PsiClass psiClass, String propertyName) {
        String setterName = suggestSetterName(propertyName);
        PsiMethod[] psiMethods = psiClass.findMethodsByName(setterName, true);
        ArrayList<PsiMethod> list = new ArrayList<>(psiMethods.length);
        for (PsiMethod method : psiMethods) {
            if (filterMethods(method)) {
                continue;
            }
            if (isSimplePropertySetter(method)) {
                list.add(method);
            }
        }
        return list;
    }

    @Nonnull
    public static List<PsiMethod> getGetters(@Nonnull PsiClass psiClass, String propertyName) {
        ArrayList<PsiMethod> list = new ArrayList<>();
        for (String name : suggestGetterNames(propertyName)) {
            PsiMethod[] psiMethods = psiClass.findMethodsByName(name, true);
            for (PsiMethod method : psiMethods) {
                if (filterMethods(method)) {
                    continue;
                }
                if (isSimplePropertyGetter(method)) {
                    list.add(method);
                }
            }
        }
        return list;
    }

    @Nonnull
    public static List<PsiMethod> getAccessors(@Nonnull PsiClass psiClass, String propertyName) {
        return ContainerUtil.concat(getGetters(psiClass, propertyName), getSetters(psiClass, propertyName));
    }

    @Nonnull
    public static String[] getReadableProperties(@Nonnull PsiClass aClass, boolean includeSuperClass) {
        List<String> result = new ArrayList<>();

        PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

        for (PsiMethod method : methods) {
            if (JavaClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) {
                continue;
            }

            if (isSimplePropertyGetter(method)) {
                result.add(getPropertyName(method));
            }
        }

        return ArrayUtil.toStringArray(result);
    }

    @Nonnull
    public static String[] getWritableProperties(@Nonnull PsiClass aClass, boolean includeSuperClass) {
        List<String> result = new ArrayList<>();

        PsiMethod[] methods = includeSuperClass ? aClass.getAllMethods() : aClass.getMethods();

        for (PsiMethod method : methods) {
            if (JavaClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) {
                continue;
            }

            if (isSimplePropertySetter(method)) {
                result.add(getPropertyName(method));
            }
        }

        return ArrayUtil.toStringArray(result);
    }

    @Nullable
    public static PsiType getPropertyType(PsiMember member) {
        if (member instanceof PsiField field) {
            return field.getType();
        }
        if (member instanceof PsiMethod method) {
            if (isSimplePropertyGetter(method)) {
                return method.getReturnType();
            }
            else if (isSimplePropertySetter(method)) {
                return method.getParameterList().getParameters()[0].getType();
            }
        }
        return null;
    }


    @Nullable
    public static PsiMethod findPropertySetter(
        PsiClass aClass,
        @Nonnull String propertyName,
        boolean isStatic,
        boolean checkSuperClasses
    ) {
        if (aClass == null) {
            return null;
        }
        String setterName = suggestSetterName(propertyName);
        PsiMethod[] methods = aClass.findMethodsByName(setterName, checkSuperClasses);

        for (PsiMethod method : methods) {
            if (method.isStatic() != isStatic) {
                continue;
            }

            if (isSimplePropertySetter(method)) {
                if (getPropertyNameBySetter(method).equals(propertyName)) {
                    return method;
                }
            }
        }

        return null;
    }

    @Nullable
    public static PsiField findPropertyField(PsiClass aClass, String propertyName, boolean isStatic) {
        PsiField[] fields = aClass.getAllFields();

        for (PsiField field : fields) {
            if (field.isStatic() != isStatic) {
                continue;
            }
            if (propertyName.equals(suggestPropertyName(field))) {
                return field;
            }
        }

        return null;
    }

    @Nullable
    public static PsiMethod findPropertyGetter(
        PsiClass aClass,
        @Nonnull String propertyName,
        boolean isStatic,
        boolean checkSuperClasses
    ) {
        if (aClass == null) {
            return null;
        }
        String[] getterCandidateNames = suggestGetterNames(propertyName);

        for (String getterCandidateName : getterCandidateNames) {
            PsiMethod[] getterCandidates = aClass.findMethodsByName(getterCandidateName, checkSuperClasses);
            for (PsiMethod method : getterCandidates) {
                if (method.isStatic() != isStatic) {
                    continue;
                }

                if (isSimplePropertyGetter(method)) {
                    if (getPropertyNameByGetter(method).equals(propertyName)) {
                        return method;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public static PsiMethod findPropertyGetterWithType(
        String propertyName,
        boolean isStatic,
        PsiType type,
        Iterator<? extends PsiMethod> methods
    ) {
        while (methods.hasNext()) {
            PsiMethod method = methods.next();
            if (method.isStatic() != isStatic) {
                continue;
            }
            if (isSimplePropertyGetter(method)
                && getPropertyNameByGetter(method).equals(propertyName)
                && type.equals(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    public static boolean isSimplePropertyAccessor(PsiMethod method) {
        return isSimplePropertyGetter(method) || isSimplePropertySetter(method);
    }

    @Nullable
    public static PsiMethod findPropertySetterWithType(
        String propertyName,
        boolean isStatic,
        PsiType type,
        Iterator<? extends PsiMethod> methods
    ) {
        while (methods.hasNext()) {
            PsiMethod method = methods.next();
            if (method.isStatic() != isStatic) {
                continue;
            }

            if (isSimplePropertySetter(method)) {
                if (getPropertyNameBySetter(method).equals(propertyName)) {
                    PsiType methodType = method.getParameterList().getParameters()[0].getType();
                    if (type.equals(methodType)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    public enum GetterFlavour {
        BOOLEAN,
        GENERIC,
        NOT_A_GETTER
    }

    @Nonnull
    public static GetterFlavour getMethodNameGetterFlavour(@Nonnull String methodName) {
        if (checkPrefix(methodName, GET_PREFIX)) {
            return GetterFlavour.GENERIC;
        }
        else if (checkPrefix(methodName, IS_PREFIX)) {
            return GetterFlavour.BOOLEAN;
        }
        return GetterFlavour.NOT_A_GETTER;
    }


    @Contract("null -> false")
    public static boolean isSimplePropertyGetter(@Nullable PsiMethod method) {
        return hasGetterName(method) && method.getParameterList().isEmpty();
    }


    public static boolean hasGetterName(PsiMethod method) {
        if (method == null) {
            return false;
        }

        if (method.isConstructor()) {
            return false;
        }

        String methodName = method.getName();
        GetterFlavour flavour = getMethodNameGetterFlavour(methodName);
        switch (flavour) {
            case GENERIC:
                PsiType returnType = method.getReturnType();
                return returnType == null || !PsiType.VOID.equals(returnType);
            case BOOLEAN:
                return isBoolean(method.getReturnType());
            case NOT_A_GETTER:
            default:
                return false;
        }
    }


    private static boolean isBoolean(@Nullable PsiType propertyType) {
        return PsiType.BOOLEAN.equals(propertyType);
    }


    public static String suggestPropertyName(@Nonnull PsiField field) {
        return suggestPropertyName(field, field.getName());
    }

    @Nonnull
    public static String suggestPropertyName(@Nonnull PsiField field, @Nonnull String fieldName) {
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(field.getProject());
        VariableKind kind = codeStyleManager.getVariableKind(field);
        String name = codeStyleManager.variableNameToPropertyName(fieldName, kind);
        if (!field.isStatic() && isBoolean(field.getType())) {
            if (name.startsWith(IS_PREFIX) && name.length() > IS_PREFIX.length() && Character.isUpperCase(name.charAt(IS_PREFIX.length()))) {
                name = Introspector.decapitalize(name.substring(IS_PREFIX.length()));
            }
        }
        return name;
    }

    public static String suggestGetterName(PsiField field) {
        String propertyName = suggestPropertyName(field);
        return suggestGetterName(propertyName, field.getType());
    }

    public static String suggestSetterName(PsiField field) {
        String propertyName = suggestPropertyName(field);
        return suggestSetterName(propertyName);
    }

    @Nullable
    public static String getPropertyName(PsiMember member) {
        if (member instanceof PsiMethod) {
            return getPropertyName((PsiMethod)member);
        }
        if (member instanceof PsiField) {
            return member.getName();
        }
        return null;
    }


    public static boolean isSimplePropertySetter(@Nullable PsiMethod method) {
        if (method == null) {
            return false;
        }

        if (method.isConstructor()) {
            return false;
        }

        String methodName = method.getName();

        if (!isSetterName(methodName)) {
            return false;
        }

        if (method.getParameterList().getParametersCount() != 1) {
            return false;
        }

        PsiType returnType = method.getReturnType();

        //noinspection SimplifiableIfStatement
        if (returnType == null || PsiType.VOID.equals(returnType)) {
            return true;
        }

        return Comparing.equal(PsiUtil.resolveClassInType(TypeConversionUtil.erasure(returnType)), method.getContainingClass());
    }

    public static boolean isSetterName(@Nonnull String methodName) {
        return checkPrefix(methodName, SET_PREFIX);
    }

    @Nullable
    public static String getPropertyName(@Nonnull PsiMethod method) {
        if (isSimplePropertyGetter(method)) {
            return getPropertyNameByGetter(method);
        }
        if (isSimplePropertySetter(method)) {
            return getPropertyNameBySetter(method);
        }
        return null;
    }

    @Nonnull
    public static String getPropertyNameByGetter(PsiMethod getterMethod) {
        String methodName = getterMethod.getName();
        if (methodName.startsWith(GET_PREFIX)) {
            return StringUtil.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith(IS_PREFIX)) {
            return StringUtil.decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    @Nonnull
    public static String getPropertyNameBySetter(@Nonnull PsiMethod setterMethod) {
        String methodName = setterMethod.getName();
        return Introspector.decapitalize(methodName.substring(3));
    }

    private static boolean checkPrefix(@Nonnull String methodName, @Nonnull String prefix) {
        boolean hasPrefix = methodName.startsWith(prefix) && methodName.length() > prefix.length();
        return hasPrefix && !(Character.isLowerCase(methodName.charAt(prefix.length())) &&
            (methodName.length() == prefix.length() + 1 || Character.isLowerCase(methodName.charAt(prefix.length() + 1))));
    }

    @Nonnull
    public static String[] suggestGetterNames(@Nonnull String propertyName) {
        String str = StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName));
        return new String[]{
            IS_PREFIX + str,
            GET_PREFIX + str
        };
    }

    public static String suggestGetterName(@Nonnull String propertyName, @Nullable PsiType propertyType) {
        return suggestGetterName(propertyName, propertyType, null);
    }

    public static String suggestGetterName(
        @Nonnull String propertyName,
        @Nullable PsiType propertyType,
        String existingGetterName
    ) {
        StringBuilder name =
            new StringBuilder(StringUtil.capitalizeWithJavaBeanConvention(StringUtil.sanitizeJavaIdentifier(propertyName)));
        if (isBoolean(propertyType)) {
            if (existingGetterName == null || !existingGetterName.startsWith(GET_PREFIX)) {
                name.insert(0, IS_PREFIX);
            }
            else {
                name.insert(0, GET_PREFIX);
            }
        }
        else {
            name.insert(0, GET_PREFIX);
        }

        return name.toString();
    }


    public static String suggestSetterName(@Nonnull String propertyName) {
        return suggestSetterName(propertyName, SET_PREFIX);
    }

    public static String suggestSetterName(@Nonnull String propertyName, String setterPrefix) {
        String sanitizeJavaIdentifier = StringUtil.sanitizeJavaIdentifier(propertyName);
        if (StringUtil.isEmpty(setterPrefix)) {
            return sanitizeJavaIdentifier;
        }
        StringBuilder name = new StringBuilder(StringUtil.capitalizeWithJavaBeanConvention(sanitizeJavaIdentifier));
        name.insert(0, setterPrefix);
        return name.toString();
    }

    /**
     * Consider using {@link GenerateMembersUtil#generateGetterPrototype(PsiField)} or
     * {@link GenerateMembersUtil#generateSimpleGetterPrototype(PsiField)}
     * to add @Override annotation
     */
    @Nonnull
    public static PsiMethod generateGetterPrototype(@Nonnull PsiField field) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
        Project project = field.getProject();
        String name = field.getName();
        String getName = suggestGetterName(field);
        PsiMethod getMethod = factory.createMethod(getName, field.getType());
        PsiUtil.setModifierProperty(getMethod, PsiModifier.PUBLIC, true);
        if (field.isStatic()) {
            PsiUtil.setModifierProperty(getMethod, PsiModifier.STATIC, true);
        }

        NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, getMethod);

        PsiCodeBlock body = factory.createCodeBlockFromText("{\nreturn " + name + ";\n}", null);
        Objects.requireNonNull(getMethod.getBody()).replace(body);
        getMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(getMethod);
        return getMethod;
    }

    /**
     * Consider using {@link GenerateMembersUtil#generateSetterPrototype(PsiField)}
     * or {@link GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
     * to add @Override annotation
     */
    @Nonnull
    public static PsiMethod generateSetterPrototype(@Nonnull PsiField field) {
        return generateSetterPrototype(field, field.getContainingClass());
    }

    /**
     * Consider using {@link GenerateMembersUtil#generateSetterPrototype(PsiField)}
     * or {@link GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
     * to add @Override annotation
     */
    @Nonnull
    public static PsiMethod generateSetterPrototype(@Nonnull PsiField field, @Nonnull PsiClass containingClass) {
        return generateSetterPrototype(field, containingClass, false);
    }

    /**
     * Consider using {@link GenerateMembersUtil#generateSetterPrototype(PsiField)}
     * or {@link GenerateMembersUtil#generateSimpleSetterPrototype(PsiField)}
     * to add @Override annotation
     */
    @Nonnull
    @RequiredReadAction
    public static PsiMethod generateSetterPrototype(@Nonnull PsiField field, @Nonnull PsiClass containingClass, boolean returnSelf) {
        Project project = field.getProject();
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());

        String name = field.getName();
        boolean isStatic = field.isStatic();
        VariableKind kind = codeStyleManager.getVariableKind(field);
        String propertyName = codeStyleManager.variableNameToPropertyName(name, kind);
        String setName = suggestSetterName(field);
        PsiMethod setMethod = factory.createMethodFromText(
            factory.createMethod(setName, returnSelf ? factory.createType(containingClass) : PsiType.VOID).getText(),
            field
        );
        String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
        PsiParameter param = factory.createParameter(parameterName, field.getType());

        NullableNotNullManager.getInstance(project).copyNullableOrNotNullAnnotation(field, param);

        setMethod.getParameterList().add(param);
        PsiUtil.setModifierProperty(setMethod, PsiModifier.PUBLIC, true);
        PsiUtil.setModifierProperty(setMethod, PsiModifier.STATIC, isStatic);

        StringBuilder buffer = new StringBuilder();
        buffer.append("{\n");
        if (name.equals(parameterName)) {
            if (!isStatic) {
                buffer.append("this.");
            }
            else {
                String className = containingClass.getName();
                if (className != null) {
                    buffer.append(className);
                    buffer.append(".");
                }
            }
        }
        buffer.append(name);
        buffer.append("=");
        buffer.append(parameterName);
        buffer.append(";\n");
        if (returnSelf) {
            buffer.append("return this;\n");
        }
        buffer.append("}");
        PsiCodeBlock body = factory.createCodeBlockFromText(buffer.toString(), null);
        Objects.requireNonNull(setMethod.getBody()).replace(body);
        setMethod = (PsiMethod)CodeStyleManager.getInstance(project).reformat(setMethod);
        return setMethod;
    }

    @Nullable
    public static PsiTypeElement getPropertyTypeElement(PsiMember member) {
        if (member instanceof PsiField field) {
            return field.getTypeElement();
        }
        if (member instanceof PsiMethod method) {
            if (isSimplePropertyGetter(method)) {
                return method.getReturnTypeElement();
            }
            else if (isSimplePropertySetter(method)) {
                return method.getParameterList().getParameters()[0].getTypeElement();
            }
        }
        return null;
    }

    @Nullable
    public static PsiIdentifier getPropertyNameIdentifier(PsiMember member) {
        if (member instanceof PsiField field) {
            return field.getNameIdentifier();
        }
        if (member instanceof PsiMethod method) {
            return method.getNameIdentifier();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiField findPropertyFieldByMember(PsiMember member) {
        if (member instanceof PsiField field) {
            return field;
        }
        if (member instanceof PsiMethod method) {
            PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return null;
            }
            PsiCodeBlock body = method.getBody();
            PsiStatement[] statements = body == null ? null : body.getStatements();
            PsiStatement statement = statements == null || statements.length != 1 ? null : statements[0];
            PsiElement target;
            if (PsiType.VOID.equals(returnType)) {
                PsiExpression expression =
                    statement instanceof PsiExpressionStatement exprStmt ? exprStmt.getExpression() : null;
                target = expression instanceof PsiAssignmentExpression assignment ? assignment.getLExpression() : null;
            }
            else {
                target = statement instanceof PsiReturnStatement returnStmt ? returnStmt.getReturnValue() : null;
            }
            if (target instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiField field) {
                PsiClass memberClass = member.getContainingClass();
                PsiClass fieldClass = field.getContainingClass();
                if (memberClass != null && fieldClass != null
                    && (memberClass == fieldClass || memberClass.isInheritor(fieldClass, true))) {
                    return field;
                }
            }
        }
        return null;
    }

    public static PsiMethod findSetterForField(PsiField field) {
        PsiClass containingClass = field.getContainingClass();
        String propertyName = suggestPropertyName(field);
        boolean isStatic = field.isStatic();
        return findPropertySetter(containingClass, propertyName, isStatic, true);
    }

    public static PsiMethod findGetterForField(PsiField field) {
        PsiClass containingClass = field.getContainingClass();
        String propertyName = suggestPropertyName(field);
        boolean isStatic = field.isStatic();
        return findPropertyGetter(containingClass, propertyName, isStatic, true);
    }

    /**
     * If the name of the method looks like a getter and the body consists of a single return statement,
     * returns the returned expression. Otherwise, returns null.
     *
     * @param method the method to check
     * @return the return value, or null if it doesn't match the conditions.
     */
    @Nullable
    public static PsiExpression getGetterReturnExpression(@Nullable PsiMethod method) {
        return method != null && hasGetterSignature(method) ? getSingleReturnValue(method) : null;
    }

    private static boolean hasGetterSignature(@Nonnull PsiMethod method) {
        return isSimplePropertyGetter(method);
    }

    @Nullable
    public static PsiExpression getSingleReturnValue(@Nonnull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }
        PsiStatement[] statements = body.getStatements();
        PsiStatement statement = statements.length != 1 ? null : statements[0];
        return statement instanceof PsiReturnStatement returnStmt? returnStmt.getReturnValue() : null;
    }

    @Contract(pure = true)
    @Nullable
    public static PropertyKind getPropertyKind(@Nonnull String accessorName) {
        for (PropertyKind kind : PropertyKind.values()) {
            String prefix = kind.prefix;
            int prefixLength = prefix.length();
            if (accessorName.startsWith(prefix) && accessorName.length() > prefixLength) {
                return kind;
            }
        }
        return null;
    }

    /**
     * @see StringUtil#getPropertyName(String)
     * @see Introspector
     */
    @Contract(pure = true)
    @Nullable
    public static Pair<String, PropertyKind> getPropertyNameAndKind(@Nonnull String accessorName) {
        PropertyKind kind = getPropertyKind(accessorName);
        if (kind == null) {
            return null;
        }
        String baseName = accessorName.substring(kind.prefix.length());
        String propertyName = Introspector.decapitalize(baseName);
        return Pair.create(propertyName, kind);
    }

    @Contract(pure = true)
    @Nonnull
    public static String getAccessorName(@Nonnull String propertyName, @Nonnull PropertyKind kind) {
        return kind.prefix + StringUtil.capitalizeWithJavaBeanConvention(propertyName);
    }
}
