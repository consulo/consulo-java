/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.language.psi.util;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageFeatureProvider;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo.ApplicabilityLevel;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.*;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.util.lang.TimeoutUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.function.Predicate;

import static consulo.java.language.module.util.JavaClassNames.JAVA_LANG_STRING;

public final class PsiUtil extends PsiUtilCore {
    private static final Logger LOG = Logger.getInstance(PsiUtil.class);

    public static final int ACCESS_LEVEL_PUBLIC = 4;
    public static final int ACCESS_LEVEL_PROTECTED = 3;
    public static final int ACCESS_LEVEL_PACKAGE_LOCAL = 2;
    public static final int ACCESS_LEVEL_PRIVATE = 1;
    public static final Key<Boolean> VALID_VOID_TYPE_IN_CODE_FRAGMENT = Key.create("VALID_VOID_TYPE_IN_CODE_FRAGMENT");

    private static final Set<String> IGNORED_NAMES = Set.of(
        "ignore",
        "ignore1",
        "ignore2",
        "ignore3",
        "ignore4",
        "ignore5",
        "ignored",
        "ignored1",
        "ignored2",
        "ignored3",
        "ignored4",
        "ignored5"
    );

    private PsiUtil() {
    }

    public static boolean isOnAssignmentLeftHand(@Nonnull PsiExpression expr) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
        return parent instanceof PsiAssignmentExpression assignment
            && PsiTreeUtil.isAncestor(assignment.getLExpression(), expr, false);
    }

    public static boolean isAccessibleFromPackage(@Nonnull PsiModifierListOwner element, @Nonnull PsiJavaPackage aPackage) {
        //noinspection SimplifiableIfStatement
        if (element.hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
        }
        return !element.hasModifierProperty(PsiModifier.PRIVATE)
            && JavaPsiFacade.getInstance(element.getProject()).isInPackage(element, aPackage);
    }

    public static boolean isAccessedForWriting(@Nonnull PsiExpression expr) {
        if (isOnAssignmentLeftHand(expr)) {
            return true;
        }
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
        if (parent instanceof PsiPrefixExpression prefixExpr) {
            IElementType tokenType = prefixExpr.getOperationTokenType();
            return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
        }
        else if (parent instanceof PsiPostfixExpression postfixExpr) {
            IElementType tokenType = postfixExpr.getOperationTokenType();
            return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
        }
        else {
            return false;
        }
    }

    public static boolean isAccessedForReading(@Nonnull PsiExpression expr) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
        return !(parent instanceof PsiAssignmentExpression assignment)
            || !PsiTreeUtil.isAncestor(assignment.getLExpression(), expr, false)
            || assignment.getOperationTokenType() != JavaTokenType.EQ;
    }

    public static boolean isAccessible(@Nonnull PsiMember member, @Nonnull PsiElement place, @Nullable PsiClass accessObjectClass) {
        return isAccessible(place.getProject(), member, place, accessObjectClass);
    }

    public static boolean isAccessible(
        @Nonnull Project project,
        @Nonnull PsiMember member,
        @Nonnull PsiElement place,
        @Nullable PsiClass accessObjectClass
    ) {
        return JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, place, accessObjectClass);
    }

    @Nonnull
    public static JavaResolveResult getAccessObjectClass(@Nonnull PsiExpression expression) {
        if (expression instanceof PsiSuperExpression) {
            return JavaResolveResult.EMPTY;
        }
        PsiType type = expression.getType();
        if (type instanceof PsiClassType classType) {
            return classType.resolveGenerics();
        }
        if (type instanceof PsiDisjunctionType disjunctionType
            && disjunctionType.getLeastUpperBound() instanceof PsiClassType lub) {
            return lub.resolveGenerics();
        }
        if (type == null && expression instanceof PsiReferenceExpression refExpr) {
            JavaResolveResult resolveResult = refExpr.advancedResolve(false);
            if (resolveResult.getElement() instanceof PsiClass) {
                return resolveResult;
            }
        }
        return JavaResolveResult.EMPTY;
    }

    public static boolean isConstantExpression(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        IsConstantExpressionVisitor visitor = new IsConstantExpressionVisitor();
        expression.accept(visitor);
        return visitor.myIsConstant;
    }

    // todo: move to PsiThrowsList?
    @RequiredWriteAction
    public static void addException(@Nonnull PsiMethod method, @Nonnull String exceptionFQName) throws IncorrectOperationException {
        PsiClass exceptionClass = JavaPsiFacade.getInstance(method.getProject()).findClass(exceptionFQName, method.getResolveScope());
        addException(method, exceptionClass, exceptionFQName);
    }

    @RequiredWriteAction
    public static void addException(@Nonnull PsiMethod method, @Nonnull PsiClass exceptionClass) throws IncorrectOperationException {
        addException(method, exceptionClass, exceptionClass.getQualifiedName());
    }

    @RequiredWriteAction
    private static void addException(@Nonnull PsiMethod method, @Nullable PsiClass exceptionClass, @Nullable String exceptionName)
        throws IncorrectOperationException {
        assert exceptionClass != null || exceptionName != null : "One of exceptionName, exceptionClass must be not null";
        PsiReferenceList throwsList = method.getThrowsList();
        PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
        boolean replaced = false;
        for (PsiJavaCodeReferenceElement ref : refs) {
            if (ref.isReferenceTo(exceptionClass)) {
                return;
            }
            PsiClass aClass = (PsiClass)ref.resolve();
            if (exceptionClass == null || aClass == null) {
                continue;
            }
            if (aClass.isInheritor(exceptionClass, true)) {
                if (replaced) {
                    ref.delete();
                }
                else {
                    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
                    PsiJavaCodeReferenceElement ref1;
                    if (exceptionName != null) {
                        ref1 = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
                    }
                    else {
                        PsiClassType type = factory.createType(exceptionClass);
                        ref1 = factory.createReferenceElementByType(type);
                    }
                    ref.replace(ref1);
                    replaced = true;
                }
            }
            else if (exceptionClass.isInheritor(aClass, true)) {
                return;
            }
        }
        if (replaced) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
        PsiJavaCodeReferenceElement ref;
        if (exceptionName != null) {
            ref = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
        }
        else {
            PsiClassType type = factory.createType(exceptionClass);
            ref = factory.createReferenceElementByType(type);
        }
        throwsList.add(ref);
    }

    // todo: move to PsiThrowsList?
    @RequiredWriteAction
    public static void removeException(@Nonnull PsiMethod method, String exceptionClass) throws IncorrectOperationException {
        PsiJavaCodeReferenceElement[] refs = method.getThrowsList().getReferenceElements();
        for (PsiJavaCodeReferenceElement ref : refs) {
            if (ref.getCanonicalText().equals(exceptionClass)) {
                ref.delete();
            }
        }
    }

    public static boolean isVariableNameUnique(@Nonnull String name, @Nonnull PsiElement place) {
        PsiResolveHelper helper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
        return helper.resolveAccessibleReferencedVariable(name, place) == null;
    }

    /**
     * @return enclosing outermost (method or class initializer) body but not higher than scope
     */
    @Nullable
    public static PsiElement getTopLevelEnclosingCodeBlock(@Nullable PsiElement element, PsiElement scope) {
        PsiElement blockSoFar = null;
        while (element != null) {
            // variable can be defined in for loop initializer
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiExpression) || parent instanceof PsiLambdaExpression) {
                if (element instanceof PsiCodeBlock || element instanceof PsiForStatement || element instanceof PsiForeachStatement) {
                    blockSoFar = element;
                }

                if (parent instanceof PsiMethod && parent.getParent() instanceof PsiClass psiClass && !isLocalOrAnonymousClass(psiClass)) {
                    break;
                }
                if (parent instanceof PsiClassInitializer && !(parent.getParent() instanceof PsiAnonymousClass)) {
                    break;
                }
                if (parent instanceof PsiField field && field.getInitializer() == element) {
                    blockSoFar = element;
                }
                if (parent instanceof PsiClassLevelDeclarationStatement) {
                    parent = parent.getParent();
                }
                if (element instanceof PsiClass psiClass && !isLocalOrAnonymousClass(psiClass)) {
                    break;
                }
                if (element instanceof PsiFile && PsiUtilCore.getTemplateLanguageFile(element) != null) {
                    return element;
                }
            }
            if (element == scope) {
                break;
            }
            element = parent;
        }
        return blockSoFar;
    }

    public static boolean isLocalOrAnonymousClass(@Nonnull PsiClass psiClass) {
        return psiClass instanceof PsiAnonymousClass || isLocalClass(psiClass);
    }

    public static boolean isLocalClass(@Nonnull PsiClass psiClass) {
        PsiElement parent = psiClass.getParent();
        return parent instanceof PsiDeclarationStatement && parent.getParent() instanceof PsiCodeBlock;
    }

    public static boolean isAbstractClass(@Nonnull PsiClass clazz) {
        PsiModifierList modifierList = clazz.getModifierList();
        return modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    /**
     * @return topmost code block where variable makes sense
     */
    @Nullable
    public static PsiElement getVariableCodeBlock(@Nonnull PsiVariable variable, @Nullable PsiElement context) {
        PsiElement codeBlock = null;
        if (variable instanceof PsiParameter param) {
            PsiElement declarationScope = param.getDeclarationScope();
            if (declarationScope instanceof PsiCatchSection catchSection) {
                codeBlock = catchSection.getCatchBlock();
            }
            else if (declarationScope instanceof PsiForeachStatement foreachStmt) {
                codeBlock = foreachStmt.getBody();
            }
            else if (declarationScope instanceof PsiMethod method) {
                codeBlock = method.getBody();
            }
            else if (declarationScope instanceof PsiLambdaExpression lambda) {
                codeBlock = lambda.getBody();
            }
        }
        else if (variable instanceof PsiResourceVariable) {
            PsiElement resourceList = variable.getParent();
            return resourceList != null ? resourceList.getParent() : null;  // use try statement as topmost
        }
        else if (variable instanceof PsiLocalVariable && variable.getParent() instanceof PsiForStatement) {
            return variable.getParent();
        }
        else if (variable instanceof PsiField field && context != null) {
            PsiClass aClass = field.getContainingClass();
            while (context != null && context.getParent() != aClass) {
                context = context.getParent();
                if (context instanceof PsiClassLevelDeclarationStatement) {
                    return null;
                }
            }
            return context instanceof PsiMethod method ? method.getBody()
                : context instanceof PsiClassInitializer classInitializer ? classInitializer.getBody() : null;
        }
        else {
            PsiElement scope = variable.getParent() == null ? null : variable.getParent().getParent();
            codeBlock = getTopLevelEnclosingCodeBlock(variable, scope);
            if (codeBlock != null && codeBlock.getParent() instanceof PsiSwitchStatement switchStmt) {
                codeBlock = switchStmt.getParent();
            }
        }
        return codeBlock;
    }

    public static boolean isIncrementDecrementOperation(@Nonnull PsiElement element) {
        if (element instanceof PsiPostfixExpression postfixExpr) {
            IElementType sign = postfixExpr.getOperationTokenType();
            if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS) {
                return true;
            }
        }
        else if (element instanceof PsiPrefixExpression prefixExpr) {
            IElementType sign = prefixExpr.getOperationTokenType();
            if (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    public static List<PsiExpression> getSwitchResultExpressions(PsiSwitchExpression switchExpression) {
        PsiCodeBlock body = switchExpression.getBody();
        if (body != null) {
            List<PsiExpression> result = new ArrayList<>();
            PsiStatement[] statements = body.getStatements();
            for (PsiStatement statement : statements) {
                if (statement instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStmt) {
                    PsiStatement ruleBody = switchLabeledRuleStmt.getBody();
                    if (ruleBody instanceof PsiExpressionStatement ruleBodyStmt) {
                        result.add(ruleBodyStmt.getExpression());
                    }
                    else if (ruleBody instanceof PsiBlockStatement blockStmt) {
                        collectSwitchResultExpressions(result, blockStmt);
                    }
                }
                else {
                    collectSwitchResultExpressions(result, statement);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    @RequiredReadAction
    private static void collectSwitchResultExpressions(@Nonnull List<? super PsiExpression> result, @Nonnull PsiElement container) {
        List<PsiYieldStatement> yields = new ArrayList<>();
        addStatements(yields, container, PsiYieldStatement.class, element -> element instanceof PsiSwitchExpression);
        for (PsiYieldStatement statement : yields) {
            ContainerUtil.addIfNotNull(result, statement.getExpression());
        }
    }

    @MagicConstant(intValues = {
        ACCESS_LEVEL_PUBLIC,
        ACCESS_LEVEL_PROTECTED,
        ACCESS_LEVEL_PACKAGE_LOCAL,
        ACCESS_LEVEL_PRIVATE
    })
    public @interface AccessLevel {
    }

    @AccessLevel
    public static int getAccessLevel(@Nonnull PsiModifierList modifierList) {
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            return ACCESS_LEVEL_PRIVATE;
        }
        if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            return ACCESS_LEVEL_PACKAGE_LOCAL;
        }
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            return ACCESS_LEVEL_PROTECTED;
        }
        return ACCESS_LEVEL_PUBLIC;
    }

    @PsiModifier.ModifierConstant
    @Nullable
    public static String getAccessModifier(@AccessLevel int accessLevel) {
        @SuppressWarnings("UnnecessaryLocalVariable") @PsiModifier.ModifierConstant String modifier =
            accessLevel > accessModifiers.length ? null : accessModifiers[accessLevel - 1];
        return modifier;
    }

    private static final String[] accessModifiers = {
        PsiModifier.PRIVATE,
        PsiModifier.PACKAGE_LOCAL,
        PsiModifier.PROTECTED,
        PsiModifier.PUBLIC
    };

    /**
     * @return true if element specified is statement or expression statement. see JLS 14.5-14.8
     */
    public static boolean isStatement(@Nonnull PsiElement element) {
        PsiElement parent = element.getParent();

        if (element instanceof PsiExpressionListStatement exprListStmt) {
            // statement list allowed in for() init or update only
            if (!(parent instanceof PsiForStatement)) {
                return false;
            }
            PsiForStatement forStatement = (PsiForStatement)parent;
            if (!(exprListStmt == forStatement.getInitialization() || exprListStmt == forStatement.getUpdate())) {
                return false;
            }
            PsiExpressionList expressionList = exprListStmt.getExpressionList();
            for (PsiExpression expression : expressionList.getExpressions()) {
                if (!isStatement(expression)) {
                    return false;
                }
            }
            return true;
        }

        if (element instanceof PsiExpressionStatement exprStmt) {
            return parent instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStmt
                && switchLabeledRuleStmt.getEnclosingSwitchBlock() instanceof PsiSwitchExpression
                || isStatement(exprStmt.getExpression());
        }

        if (element instanceof PsiDeclarationStatement) {
            if (parent instanceof PsiCodeBlock) {
                return true;
            }
            if (parent instanceof PsiCodeFragment) {
                return true;
            }
            if (!(parent instanceof PsiForStatement forStmt && forStmt.getBody() != element)) {
                return false;
            }
        }

        if (element instanceof PsiStatement) {
            return true;
        }
        if (element instanceof PsiAssignmentExpression) {
            return true;
        }
        if (isIncrementDecrementOperation(element)) {
            return true;
        }
        if (element instanceof PsiMethodCallExpression) {
            return true;
        }
        if (element instanceof PsiNewExpression newExpr) {
            return !(newExpr.getType() instanceof PsiArrayType);
        }

        return element instanceof PsiCodeBlock;
    }

    @Nullable
    public static PsiElement getEnclosingStatement(PsiElement element) {
        while (element != null) {
            if (element.getParent() instanceof PsiCodeBlock) {
                return element;
            }
            element = element.getParent();
        }
        return null;
    }


    @Nullable
    @RequiredReadAction
    public static PsiElement getElementInclusiveRange(@Nonnull PsiElement scope, @Nonnull TextRange range) {
        PsiElement psiElement = scope.findElementAt(range.getStartOffset());
        while (psiElement != null && !psiElement.getTextRange().contains(range)) {
            if (psiElement == scope) {
                return null;
            }
            psiElement = psiElement.getParent();
        }
        return psiElement;
    }

    @Nullable
    public static PsiClass resolveClassInType(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        if (type instanceof PsiArrayType arrayType) {
            return resolveClassInType(arrayType.getComponentType());
        }
        if (type instanceof PsiDisjunctionType disjunctionType && disjunctionType.getLeastUpperBound() instanceof PsiClassType lub) {
            return lub.resolve();
        }
        return null;
    }

    @Nullable
    public static PsiClass resolveClassInClassTypeOnly(@Nullable PsiType type) {
        return type instanceof PsiClassType classType ? classType.resolve() : null;
    }

    public static PsiClassType.ClassResolveResult resolveGenericsClassInType(@Nullable PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolveGenerics();
        }
        if (type instanceof PsiArrayType arrayType) {
            return resolveGenericsClassInType(arrayType.getComponentType());
        }
        if (type instanceof PsiDisjunctionType disjunctionType && disjunctionType.getLeastUpperBound() instanceof PsiClassType lub) {
            return lub.resolveGenerics();
        }
        return PsiClassType.ClassResolveResult.EMPTY;
    }

    @Nonnull
    public static PsiType convertAnonymousToBaseType(@Nonnull PsiType type) {
        if (resolveClassInType(type) instanceof PsiAnonymousClass aClass) {
            int dims = type.getArrayDimensions();
            type = aClass.getBaseClassType();
            while (dims != 0) {
                type = type.createArrayType();
                dims--;
            }
        }
        return type;
    }

    @RequiredReadAction
    public static boolean isApplicable(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiExpressionList argList
    ) {
        return getApplicabilityLevel(method, substitutorForMethod, argList) != ApplicabilityLevel.NOT_APPLICABLE;
    }

    @RequiredReadAction
    public static boolean isApplicable(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiExpression[] argList
    ) {
        PsiType[] types = ContainerUtil.map2Array(argList, PsiType.class, PsiExpression.EXPRESSION_TO_TYPE);
        return getApplicabilityLevel(method, substitutorForMethod, types, getLanguageLevel(method)) != ApplicabilityLevel.NOT_APPLICABLE;
    }

    @MethodCandidateInfo.ApplicabilityLevelConstant
    @RequiredReadAction
    public static int getApplicabilityLevel(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiExpressionList argList
    ) {
        return getApplicabilityLevel(method, substitutorForMethod, argList.getExpressionTypes(), getLanguageLevel(argList));
    }

    @MethodCandidateInfo.ApplicabilityLevelConstant
    @RequiredReadAction
    public static int getApplicabilityLevel(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiType[] args,
        @Nonnull LanguageLevel languageLevel
    ) {
        return getApplicabilityLevel(method, substitutorForMethod, args, languageLevel, true, true);
    }

    public interface ApplicabilityChecker {
        ApplicabilityChecker ASSIGNABILITY_CHECKER =
            (left, right, allowUncheckedConversion, argId) -> TypeConversionUtil.isAssignable(left, right, allowUncheckedConversion);

        boolean isApplicable(PsiType left, PsiType right, boolean allowUncheckedConversion, int argId);
    }

    @MethodCandidateInfo.ApplicabilityLevelConstant
    @RequiredReadAction
    public static int getApplicabilityLevel(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiType[] args,
        @Nonnull LanguageLevel languageLevel,
        boolean allowUncheckedConversion,
        boolean checkVarargs
    ) {
        return getApplicabilityLevel(
            method,
            substitutorForMethod,
            args,
            languageLevel,
            allowUncheckedConversion,
            checkVarargs,
            ApplicabilityChecker.ASSIGNABILITY_CHECKER
        );
    }

    @MethodCandidateInfo.ApplicabilityLevelConstant
    @RequiredReadAction
    public static int getApplicabilityLevel(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutorForMethod,
        @Nonnull PsiType[] args,
        @Nonnull LanguageLevel languageLevel,
        boolean allowUncheckedConversion,
        boolean checkVarargs,
        @Nonnull ApplicabilityChecker function
    ) {
        PsiParameter[] parms = method.getParameterList().getParameters();
        if (args.length < parms.length - 1) {
            return ApplicabilityLevel.NOT_APPLICABLE;
        }

        PsiClass containingClass = method.getContainingClass();
        boolean isRaw =
            containingClass != null && isRawSubstitutor(method, substitutorForMethod) && isRawSubstitutor(
                containingClass,
                substitutorForMethod
            );
        if (!areFirstArgumentsApplicable(args, parms, languageLevel, substitutorForMethod, isRaw, allowUncheckedConversion, function)) {
            return ApplicabilityLevel.NOT_APPLICABLE;
        }
        if (args.length == parms.length) {
            if (parms.length == 0) {
                return ApplicabilityLevel.FIXED_ARITY;
            }
            PsiType parmType = getParameterType(parms[parms.length - 1], languageLevel, substitutorForMethod);
            PsiType argType = args[args.length - 1];
            if (argType == null) {
                return ApplicabilityLevel.NOT_APPLICABLE;
            }
            if (function.isApplicable(parmType, argType, allowUncheckedConversion, parms.length - 1)) {
                return ApplicabilityLevel.FIXED_ARITY;
            }

            if (isRaw) {
                PsiType erasedParamType = TypeConversionUtil.erasure(parmType);
                if (erasedParamType != null
                    && function.isApplicable(erasedParamType, argType, allowUncheckedConversion, parms.length - 1)) {
                    return ApplicabilityLevel.FIXED_ARITY;
                }
            }
        }

        if (checkVarargs && method.isVarArgs() && languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0) {
            if (args.length < parms.length) {
                return ApplicabilityLevel.VARARGS;
            }
            PsiParameter lastParameter = parms.length == 0 ? null : parms[parms.length - 1];
            if (lastParameter == null || !lastParameter.isVarArgs()) {
                return ApplicabilityLevel.NOT_APPLICABLE;
            }
            PsiType lastParamType = getParameterType(lastParameter, languageLevel, substitutorForMethod);
            if (!(lastParamType instanceof PsiArrayType arrayType)) {
                return ApplicabilityLevel.NOT_APPLICABLE;
            }
            lastParamType = arrayType.getComponentType();
            if (lastParamType instanceof PsiCapturedWildcardType capturedWildcardType
                && !JavaVersionService.getInstance().isAtLeast(capturedWildcardType.getContext(), JavaSdkVersion.JDK_1_8)) {
                lastParamType = capturedWildcardType.getWildcard();
            }
            for (int i = parms.length - 1; i < args.length; i++) {
                PsiType argType = args[i];
                if (argType == null || !function.isApplicable(lastParamType, argType, allowUncheckedConversion, i)) {
                    return ApplicabilityLevel.NOT_APPLICABLE;
                }
            }
            return ApplicabilityLevel.VARARGS;
        }

        return ApplicabilityLevel.NOT_APPLICABLE;
    }

    private static boolean areFirstArgumentsApplicable(
        @Nonnull PsiType[] args,
        @Nonnull PsiParameter[] parms,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiSubstitutor substitutorForMethod,
        boolean isRaw,
        boolean allowUncheckedConversion,
        ApplicabilityChecker function
    ) {
        for (int i = 0; i < parms.length - 1; i++) {
            PsiType type = args[i];
            if (type == null) {
                return false;
            }
            PsiParameter parameter = parms[i];
            PsiType substitutedParmType = getParameterType(parameter, languageLevel, substitutorForMethod);
            if (isRaw) {
                PsiType substErasure = TypeConversionUtil.erasure(substitutedParmType);
                if (substErasure != null && !function.isApplicable(substErasure, type, allowUncheckedConversion, i)) {
                    return false;
                }
            }
            else if (!function.isApplicable(substitutedParmType, type, allowUncheckedConversion, i)) {
                return false;
            }
        }
        return true;
    }

    private static PsiType getParameterType(
        @Nonnull PsiParameter parameter,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiSubstitutor substitutor
    ) {
        PsiType paramType = parameter.getType();
        if (paramType instanceof PsiClassType classType) {
            paramType = classType.setLanguageLevel(languageLevel);
        }
        return substitutor.substitute(paramType);
    }

    /**
     * Compares types with respect to type parameter bounds: e.g. for
     * <code>class Foo&lt;T extends Number&gt;{}</code> types Foo&lt;?&gt; and Foo&lt;? extends Number&gt;
     * would be equivalent
     */
    public static boolean equalOnEquivalentClasses(
        PsiClassType thisClassType,
        @Nonnull PsiClass aClass,
        PsiClassType otherClassType,
        @Nonnull PsiClass bClass
    ) {
        PsiClassType capture1 = !PsiCapturedWildcardType.isCapture()
            ? thisClassType : (PsiClassType)captureToplevelWildcards(thisClassType, aClass);
        PsiClassType capture2 = !PsiCapturedWildcardType.isCapture()
            ? otherClassType : (PsiClassType)captureToplevelWildcards(otherClassType, bClass);

        PsiClassType.ClassResolveResult result1 = capture1.resolveGenerics();
        PsiClassType.ClassResolveResult result2 = capture2.resolveGenerics();

        return equalOnEquivalentClasses(result1.getSubstitutor(), aClass, result2.getSubstitutor(), bClass);
    }

    private static boolean equalOnEquivalentClasses(
        @Nonnull PsiSubstitutor s1,
        @Nonnull PsiClass aClass,
        @Nonnull PsiSubstitutor s2,
        @Nonnull PsiClass bClass
    ) {
        if (s1 == s2 && aClass == bClass) {
            return true;
        }
        // assume generic class equals to non-generic
        if (aClass.hasTypeParameters() != bClass.hasTypeParameters()) {
            return true;
        }
        PsiTypeParameter[] typeParameters1 = aClass.getTypeParameters();
        PsiTypeParameter[] typeParameters2 = bClass.getTypeParameters();
        if (typeParameters1.length != typeParameters2.length) {
            return false;
        }
        for (int i = 0; i < typeParameters1.length; i++) {
            PsiType substituted2 = s2.substitute(typeParameters2[i]);
            PsiType substituted1 = s1.substitute(typeParameters1[i]);
            if (!Comparing.equal(substituted1, substituted2)) {
                return false;
            }
        }
        if (aClass.isStatic()) {
            return true;
        }
        PsiClass containingClass1 = aClass.getContainingClass();
        PsiClass containingClass2 = bClass.getContainingClass();

        if (containingClass1 != null && containingClass2 != null) {
            return equalOnEquivalentClasses(s1, containingClass1, s2, containingClass2);
        }

        if (containingClass1 == null && containingClass2 == null) {
            if (aClass == bClass && isLocalClass(aClass)) {
                PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
                return containingClass != null && equalOnEquivalentClasses(s1, containingClass, s2, containingClass);
            }
            return true;
        }

        return false;

    }

    /**
     * @deprecated use more generic {@link #isCompileTimeConstant(PsiVariable)} instead
     */
    public static boolean isCompileTimeConstant(@Nonnull PsiField field) {
        return isCompileTimeConstant((PsiVariable)field);
    }

    /**
     * JLS 15.28
     */
    public static boolean isCompileTimeConstant(@Nonnull PsiVariable field) {
        return field.hasModifierProperty(PsiModifier.FINAL) && (TypeConversionUtil.isPrimitiveAndNotNull(field.getType()) || field.getType()
            .equalsToText(
                JAVA_LANG_STRING)) && field.hasInitializer()
            && isConstantExpression(field.getInitializer());
    }

    public static boolean allMethodsHaveSameSignature(@Nonnull PsiMethod[] methods) {
        if (methods.length == 0) {
            return true;
        }
        MethodSignature methodSignature = methods[0].getSignature(PsiSubstitutor.EMPTY);
        for (int i = 1; i < methods.length; i++) {
            PsiMethod method = methods[i];
            if (!methodSignature.equals(method.getSignature(PsiSubstitutor.EMPTY))) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static PsiExpression deparenthesizeExpression(PsiExpression expression) {
        while (true) {
            if (expression instanceof PsiParenthesizedExpression parenExpr) {
                expression = parenExpr.getExpression();
                continue;
            }
            if (expression instanceof PsiTypeCastExpression typeCast) {
                expression = typeCast.getOperand();
                continue;
            }
            return expression;
        }
    }

    /**
     * Checks whether given class is inner (as opposed to nested)
     */
    public static boolean isInnerClass(@Nonnull PsiClass aClass) {
        return !aClass.isStatic() && aClass.getContainingClass() != null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiElement findModifierInList(@Nonnull PsiModifierList modifierList, String modifier) {
        PsiElement[] children = modifierList.getChildren();
        for (PsiElement child : children) {
            if (child.getText().equals(modifier)) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    public static PsiClass getTopLevelClass(@Nonnull PsiElement element) {
        if (element.getContainingFile() instanceof PsiClassOwner classOwner) {
            PsiClass[] classes = classOwner.getClasses();
            for (PsiClass aClass : classes) {
                if (PsiTreeUtil.isAncestor(aClass, element, false)) {
                    return aClass;
                }
            }
        }
        return null;
    }

    /**
     * @param place  place to start traversal
     * @param aClass level to stop traversal
     * @return element with static modifier enclosing place and enclosed by aClass (if not null)
     */
    @Nullable
    public static PsiModifierListOwner getEnclosingStaticElement(@Nonnull PsiElement place, @Nullable PsiClass aClass) {
        LOG.assertTrue(aClass == null || !place.isPhysical() || PsiTreeUtil.isContextAncestor(aClass, place, false));
        PsiElement parent = place;
        while (parent != aClass) {
            if (parent instanceof PsiFile) {
                break;
            }
            if (parent instanceof PsiModifierListOwner modifierListOwner && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
                return (PsiModifierListOwner)parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    public static PsiType getTypeByPsiElement(@Nonnull PsiElement element) {
        if (element instanceof PsiVariable variable) {
            return variable.getType();
        }
        else if (element instanceof PsiMethod method) {
            return method.getReturnType();
        }
        return null;
    }

    @Nonnull
    public static PsiType captureToplevelWildcards(@Nonnull PsiType type, @Nonnull PsiElement context) {
        if (type instanceof PsiClassType classType) {
            PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            PsiClass aClass = result.getElement();
            if (aClass != null) {
                PsiSubstitutor substitutor = result.getSubstitutor();

                PsiSubstitutor captureSubstitutor = substitutor;
                for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
                    if (substitutor.substitute(typeParameter) instanceof PsiWildcardType wildcardType) {
                        captureSubstitutor = captureSubstitutor.put(
                            typeParameter,
                            PsiCapturedWildcardType.create(wildcardType, context, typeParameter)
                        );
                    }
                }

                if (captureSubstitutor != substitutor) {
                    Map<PsiTypeParameter, PsiType> substitutionMap = null;
                    for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
                        if (substitutor.substitute(typeParameter) instanceof PsiWildcardType wildcardType) {
                            if (substitutionMap == null) {
                                substitutionMap = new HashMap<>(substitutor.getSubstitutionMap());
                            }
                            PsiCapturedWildcardType capturedWildcard =
                                (PsiCapturedWildcardType)captureSubstitutor.substitute(typeParameter);
                            LOG.assertTrue(capturedWildcard != null);
                            PsiType upperBound = PsiCapturedWildcardType.captureUpperBound(typeParameter, wildcardType, captureSubstitutor);
                            if (upperBound != null) {
                                capturedWildcard.setUpperBound(upperBound);
                            }
                            substitutionMap.put(typeParameter, capturedWildcard);
                        }
                    }

                    if (substitutionMap != null) {
                        PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
                        PsiSubstitutor newSubstitutor = factory.createSubstitutor(substitutionMap);
                        return factory.createType(aClass, newSubstitutor);
                    }
                }
            }
        }
        else if (type instanceof PsiArrayType arrayType) {
            return captureToplevelWildcards(arrayType.getComponentType(), context).createArrayType();
        }

        return type;
    }

    public static PsiType captureTypeParameterBounds(
        @Nonnull PsiTypeParameter typeParameter,
        PsiType substituted,
        PsiElement context,
        PsiSubstitutor captureSubstitutor
    ) {
        PsiType oldSubstituted = substituted;
        PsiElement captureContext = context;
        if (substituted instanceof PsiCapturedWildcardType captured) {
            substituted = captured.getWildcard();
            captureContext = captured.getContext();
        }
        PsiType glb = null;
        if (substituted instanceof PsiWildcardType wildcardType) {
            PsiType[] boundTypes = typeParameter.getExtendsListTypes();
            PsiManager manager = typeParameter.getManager();
            PsiType originalBound = !wildcardType.isSuper() ? wildcardType.getBound() : null;
            glb = originalBound;
            for (PsiType boundType : boundTypes) {
                PsiType substitutedBoundType = captureSubstitutor.substitute(boundType);
                if (substitutedBoundType != null && !(substitutedBoundType instanceof PsiWildcardType)
                    && !substitutedBoundType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)) {
                    if (originalBound instanceof PsiArrayType && substitutedBoundType instanceof PsiArrayType
                        && !originalBound.isAssignableFrom(substitutedBoundType)
                        && !substitutedBoundType.isAssignableFrom(originalBound)) {
                        continue;
                    }

                    if (originalBound == null
                        || !TypeConversionUtil.erasure(substitutedBoundType).isAssignableFrom(TypeConversionUtil.erasure(originalBound))
                        && !TypeConversionUtil.erasure(substitutedBoundType).isAssignableFrom(originalBound)) {
                        //erasure is essential to avoid infinite recursion

                        if (glb == null) {
                            glb = substitutedBoundType;
                        }
                        else {
                            glb = GenericsUtil.getGreatestLowerBound(glb, substitutedBoundType);
                        }
                    }
                }
            }

            if (glb != null && !((PsiWildcardType)substituted).isSuper()) {
                substituted = glb instanceof PsiCapturedWildcardType capturedWildcardType
                    ? capturedWildcardType.getWildcard()
                    : PsiWildcardType.createExtends(manager, glb);
            }
        }

        if (captureContext != null) {
            substituted = oldSubstituted instanceof PsiCapturedWildcardType capturedWildcardType
                && substituted.equals(capturedWildcardType.getWildcard())
                ? oldSubstituted
                : captureSubstitutor.substitute(typeParameter);
            LOG.assertTrue(substituted instanceof PsiCapturedWildcardType);
            if (glb != null) {
                ((PsiCapturedWildcardType)substituted).setUpperBound(glb);
            }
        }
        return substituted;
    }

    public static boolean isInsideJavadocComment(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiDocComment.class, true) != null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<PsiTypeElement> getParameterTypeElements(@Nonnull PsiParameter parameter) {
        PsiTypeElement typeElement = parameter.getTypeElement();
        return typeElement != null && typeElement.getType() instanceof PsiDisjunctionType
            ? PsiTreeUtil.getChildrenOfTypeAsList(typeElement, PsiTypeElement.class)
            : Collections.singletonList(typeElement);
    }

    public static void checkIsIdentifier(@Nonnull PsiManager manager, String text) throws IncorrectOperationException {
        if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(text)) {
            throw new IncorrectOperationException(PsiBundle.message("0.is.not.an.identifier", text));
        }
    }

    @Nullable
    public static VirtualFile getJarFile(@Nonnull PsiElement candidate) {
        VirtualFile file = candidate.getContainingFile().getVirtualFile();
        if (file != null && file.getFileSystem() instanceof ArchiveFileSystem) {
            return ArchiveVfsUtil.getVirtualFileForJar(file);
        }
        return file;
    }

    public static boolean isAnnotationMethod(PsiElement element) {
        if (!(element instanceof PsiAnnotationMethod annotationMethod)) {
            return false;
        }
        PsiClass psiClass = annotationMethod.getContainingClass();
        return psiClass != null && psiClass.isAnnotationType();
    }

    @PsiModifier.ModifierConstant
    public static String getMaximumModifierForMember(PsiClass aClass) {
        return getMaximumModifierForMember(aClass, true);
    }

    @PsiModifier.ModifierConstant
    public static String getMaximumModifierForMember(PsiClass aClass, boolean allowPublicAbstract) {
        String modifier = PsiModifier.PUBLIC;

        if (!allowPublicAbstract && aClass.isAbstract() && !aClass.isEnum()) {
            modifier = PsiModifier.PROTECTED;
        }
        else if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || aClass.isEnum()) {
            modifier = PsiModifier.PACKAGE_LOCAL;
        }
        else if (aClass.isPrivate()) {
            modifier = PsiModifier.PRIVATE;
        }

        return modifier;
    }

    /*
     * Returns iterator of type parameters visible in owner. Type parameters are iterated in
     * inner-to-outer, right-to-left order.
     */
    @Nonnull
    public static Iterator<PsiTypeParameter> typeParametersIterator(@Nonnull PsiTypeParameterListOwner owner) {
        return typeParametersIterable(owner).iterator();
    }

    @Nonnull
    public static List<PsiTypeParameter> typeParametersIterable(@Nonnull PsiTypeParameterListOwner owner) {
        List<PsiTypeParameter> result = null;

        PsiTypeParameterListOwner currentOwner = owner;
        while (currentOwner != null) {
            PsiTypeParameter[] typeParameters = currentOwner.getTypeParameters();
            if (typeParameters.length > 0) {
                if (result == null) {
                    result = new ArrayList<>(typeParameters.length);
                }
                for (int i = typeParameters.length - 1; i >= 0; i--) {
                    result.add(typeParameters[i]);
                }
            }

            if (currentOwner.isStatic()) {
                break;
            }
            currentOwner = currentOwner.getContainingClass();
        }

        if (result == null) {
            return List.of();
        }
        return result;
    }

    public static boolean canBeOverriden(@Nonnull PsiMethod method) {
        PsiClass parentClass = method.getContainingClass();
        return parentClass != null && !method.isConstructor() && !method.isStatic() && !method.isFinal() && !method.isPrivate()
            && !(parentClass instanceof PsiAnonymousClass) && !parentClass.isFinal();
    }

    @Nonnull
    public static PsiElement[] mapElements(@Nonnull ResolveResult[] candidates) {
        PsiElement[] result = new PsiElement[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            result[i] = candidates[i].getElement();
        }
        return result;
    }

    @Nullable
    public static PsiMember findEnclosingConstructorOrInitializer(PsiElement expression) {
        PsiMember parent = PsiTreeUtil.getParentOfType(
            expression,
            PsiClassInitializer.class,
            PsiEnumConstantInitializer.class,
            PsiMethod.class,
            PsiField.class
        );
        if (parent instanceof PsiMethod method && !method.isConstructor()) {
            return null;
        }
        if (parent instanceof PsiField && parent.isStatic()) {
            return null;
        }
        return parent;
    }

    @RequiredReadAction
    public static boolean checkName(@Nonnull PsiElement element, @Nonnull String name, PsiElement context) {
        if (element instanceof PsiMetaOwner metaOwner) {
            PsiMetaData data = metaOwner.getMetaData();
            if (data != null) {
                return name.equals(data.getName(context));
            }
        }
        return element instanceof PsiNamedElement namedElem && name.equals(namedElem.getName());
    }

    public static boolean isRawSubstitutor(@Nonnull PsiTypeParameterListOwner owner, @Nonnull PsiSubstitutor substitutor) {
        if (substitutor == PsiSubstitutor.EMPTY) {
            return false;
        }

        for (PsiTypeParameter parameter : typeParametersIterable(owner)) {
            if (substitutor.substitute(parameter) == null) {
                return true;
            }
        }
        return false;
    }

    public static final Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = Key.create("FORCE_LANGUAGE_LEVEL");

    @RequiredReadAction
    public static boolean isLanguageLevel5OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_5);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel6OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_6);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel7OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel8OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_8);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel9OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_9);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel10OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_10);
    }

    @RequiredReadAction
    public static boolean isLanguageLevel11OrHigher(@Nonnull PsiElement element) {
        return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_11);
    }

    /**
     * @param feature feature to check
     * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
     * @return true if the feature is available in the PsiFile the supplied element belongs to
     */
    @RequiredReadAction
    public static boolean isAvailable(@Nonnull JavaFeature feature, @Nonnull PsiElement element) {
        if (!feature.isSufficient(getLanguageLevel(element))) {
            return false;
        }
        if (!feature.canBeCustomized()) {
            return true;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return true;
        }
        for (LanguageFeatureProvider extension : file.getProject().getExtensionList(LanguageFeatureProvider.class)) {
            ThreeState threeState = extension.isFeatureSupported(feature, file);
            if (threeState != ThreeState.UNSURE) {
                return threeState.toBoolean();
            }
        }
        return true;
    }

    @Nonnull
    @RequiredReadAction
    public static LanguageLevel getLanguageLevel(@Nonnull PsiElement element) {
        if (element instanceof PsiDirectory directory) {
            return JavaDirectoryService.getInstance().getLanguageLevel(directory);
        }

        PsiFile file = element.getContainingFile();
        if (file instanceof PsiJavaFile javaFile) {
            return javaFile.getLanguageLevel();
        }

        if (file != null) {
            PsiElement context = file.getContext();
            if (context != null) {
                return getLanguageLevel(context);
            }
        }

        JavaModuleExtension extension = ModuleUtilCore.getExtension(element, JavaModuleExtension.class);

        return extension == null ? LanguageLevel.HIGHEST : extension.getLanguageLevel();
    }

    public static boolean isInstantiatable(@Nonnull PsiClass clazz) {
        return !clazz.isAbstract() && clazz.isPublic() && hasDefaultConstructor(clazz);
    }

    public static boolean hasDefaultConstructor(@Nonnull PsiClass clazz) {
        return hasDefaultConstructor(clazz, false);
    }

    public static boolean hasDefaultConstructor(@Nonnull PsiClass clazz, boolean allowProtected) {
        return hasDefaultConstructor(clazz, allowProtected, true);
    }

    public static boolean hasDefaultConstructor(@Nonnull PsiClass clazz, boolean allowProtected, boolean checkModifiers) {
        return hasDefaultCtrInHierarchy(clazz, allowProtected, checkModifiers, null);
    }

    private static boolean hasDefaultCtrInHierarchy(
        @Nonnull PsiClass clazz,
        boolean allowProtected,
        boolean checkModifiers,
        @Nullable Set<PsiClass> visited
    ) {
        PsiMethod[] constructors = clazz.getConstructors();
        if (constructors.length > 0) {
            for (PsiMethod cls : constructors) {
                if ((!checkModifiers || cls.isPublic() || allowProtected && cls.isProtected())
                    && cls.getParameterList().getParametersCount() == 0) {
                    return true;
                }
            }
        }
        else {
            PsiClass superClass = clazz.getSuperClass();
            if (superClass == null) {
                return true;
            }
            if (visited == null) {
                visited = new HashSet<>();
            }
            //noinspection SimplifiableIfStatement
            if (!visited.add(clazz)) {
                return false;
            }
            return hasDefaultCtrInHierarchy(superClass, true, true, visited);
        }
        return false;
    }

    @Nullable
    public static PsiType extractIterableTypeParameter(@Nullable PsiType psiType, boolean eraseTypeParameter) {
        PsiType type = substituteTypeParameter(psiType, JavaClassNames.JAVA_LANG_ITERABLE, 0, eraseTypeParameter);
        return type != null ? type : substituteTypeParameter(psiType, JavaClassNames.JAVA_UTIL_COLLECTION, 0, eraseTypeParameter);
    }

    @Nullable
    public static PsiType substituteTypeParameter(
        @Nullable PsiType psiType,
        @Nonnull String superClass,
        int typeParamIndex,
        boolean eraseTypeParameter
    ) {
        if (psiType == null) {
            return null;
        }

        if (!(psiType instanceof PsiClassType classType)) {
            return null;
        }

        PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        PsiClass psiClass = classResolveResult.getElement();
        if (psiClass == null) {
            return null;
        }

        PsiClass baseClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(superClass, psiClass.getResolveScope());
        if (baseClass == null) {
            return null;
        }

        if (!psiClass.isEquivalentTo(baseClass) && !psiClass.isInheritor(baseClass, true)) {
            return null;
        }

        PsiTypeParameter[] parameters = baseClass.getTypeParameters();
        if (parameters.length <= typeParamIndex) {
            return PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
        }

        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, classResolveResult.getSubstitutor());
        PsiType type = substitutor.substitute(parameters[typeParamIndex]);
        if (type == null && eraseTypeParameter) {
            return TypeConversionUtil.typeParameterErasure(parameters[typeParamIndex]);
        }
        return type;
    }

    public static final Comparator<PsiElement> BY_POSITION = PsiUtilCore::compareElementsByPosition;

    public static void setModifierProperty(
        @Nonnull PsiModifierListOwner owner,
        @Nonnull @PsiModifier.ModifierConstant String property,
        boolean value
    ) {
        PsiModifierList modifierList = owner.getModifierList();
        assert modifierList != null : owner;
        modifierList.setModifierProperty(property, value);
    }

    public static boolean isTryBlock(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }
        PsiElement parent = element.getParent();
        return parent instanceof PsiTryStatement tryStmt && element == tryStmt.getTryBlock();
    }

    public static boolean isElseBlock(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }
        PsiElement parent = element.getParent();
        return parent instanceof PsiIfStatement ifStmt && element == ifStmt.getElseBranch();
    }

    public static boolean isJavaToken(@Nullable PsiElement element, IElementType type) {
        return element instanceof PsiJavaToken javaToken && javaToken.getTokenType() == type;
    }

    public static boolean isJavaToken(@Nullable PsiElement element, @Nonnull TokenSet types) {
        return element instanceof PsiJavaToken javaToken && types.contains(javaToken.getTokenType());
    }

    public static boolean isCatchParameter(@Nullable PsiElement element) {
        return element instanceof PsiParameter && element.getParent() instanceof PsiCatchSection;
    }

    public static boolean isIgnoredName(@Nullable String name) {
        return name != null && IGNORED_NAMES.contains(name);
    }

    @Nullable
    public static PsiMethod[] getResourceCloserMethodsForType(@Nonnull PsiClassType resourceType) {
        PsiClass resourceClass = resourceType.resolve();
        if (resourceClass == null) {
            return null;
        }

        Project project = resourceClass.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass autoCloseable =
            facade.findClass(JavaClassNames.JAVA_LANG_AUTO_CLOSEABLE, (GlobalSearchScope)ProjectScopes.getLibrariesScope(project));
        if (autoCloseable == null) {
            return null;
        }

        if (JavaClassSupers.getInstance()
            .getSuperClassSubstitutor(
                autoCloseable,
                resourceClass,
                resourceType.getResolveScope(),
                PsiSubstitutor.EMPTY
            ) == null) {
            return null;
        }

        PsiMethod[] closes = autoCloseable.findMethodsByName("close", false);
        if (closes.length == 1) {
            return resourceClass.findMethodsBySignature(closes[0], true);
        }
        return null;
    }

    @Nullable
    public static PsiMethod getResourceCloserMethod(@Nonnull PsiResourceListElement resource) {
        return resource.getType() instanceof PsiClassType resourceType ? getResourceCloserMethodForType(resourceType) : null;
    }

    /**
     * @deprecated use {@link #getResourceCloserMethod(PsiResourceListElement)} (to be removed in IDEA 17)
     */
    @SuppressWarnings("unused")
    public static PsiMethod getResourceCloserMethod(@Nonnull PsiResourceVariable resource) {
        return getResourceCloserMethod((PsiResourceListElement)resource);
    }

    @Nullable
    public static PsiMethod getResourceCloserMethodForType(@Nonnull PsiClassType resourceType) {
        PsiClass resourceClass = resourceType.resolve();
        if (resourceClass == null) {
            return null;
        }

        Project project = resourceClass.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass autoCloseable =
            facade.findClass(JavaClassNames.JAVA_LANG_AUTO_CLOSEABLE, (GlobalSearchScope)ProjectScopes.getLibrariesScope(project));
        if (autoCloseable == null) {
            return null;
        }

        if (JavaClassSupers.getInstance()
            .getSuperClassSubstitutor(
                autoCloseable,
                resourceClass,
                resourceType.getResolveScope(),
                PsiSubstitutor.EMPTY
            ) == null) {
            return null;
        }

        PsiMethod[] closes = autoCloseable.findMethodsByName("close", false);
        return closes.length == 1 ? resourceClass.findMethodBySignature(closes[0], true) : null;
    }

    @Nullable
    public static PsiExpression skipParenthesizedExprDown(PsiExpression initializer) {
        while (initializer instanceof PsiParenthesizedExpression parenExpr) {
            initializer = parenExpr.getExpression();
        }
        return initializer;
    }

    public static PsiElement skipParenthesizedExprUp(PsiElement parent) {
        while (parent instanceof PsiParenthesizedExpression) {
            parent = parent.getParent();
        }
        return parent;
    }

    public static void ensureValidType(@Nonnull PsiType type) {
        ensureValidType(type, null);
    }

    public static void ensureValidType(@Nonnull PsiType type, @Nullable String customMessage) {
        if (!type.isValid()) {
            TimeoutUtil.sleep(1); // to see if processing in another thread suddenly makes the type valid again (which is a bug)
            if (type.isValid()) {
                LOG.error("PsiType resurrected: " + type + " of " + type.getClass() + " " + customMessage);
                return;
            }
            if (type instanceof PsiClassType classType) {
                try {
                    PsiClass psiClass = classType.resolve(); // should throw exception
                    if (psiClass != null) {
                        ensureValid(psiClass);
                    }
                }
                catch (PsiInvalidElementAccessException e) {
                    throw customMessage == null ? e : new RuntimeException(customMessage, e);
                }
            }
            throw new AssertionError("Invalid type: " + type + " of class " + type.getClass() + " " + customMessage);
        }
    }

    @Nullable
    public static String getMemberQualifiedName(@Nonnull PsiMember member) {
        if (member instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }

        PsiClass containingClass = member.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        String className = containingClass.getQualifiedName();
        if (className == null) {
            return null;
        }
        return className + "." + member.getName();
    }

    @RequiredReadAction
    public static boolean isFromDefaultPackage(PsiClass aClass) {
        return isFromDefaultPackage((PsiElement)aClass);
    }

    @RequiredReadAction
    public static boolean isFromDefaultPackage(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile instanceof PsiClassOwner classOwner) {
            return StringUtil.isEmpty(classOwner.getPackageName());
        }

        if (containingFile instanceof JavaCodeFragment) {
            PsiElement context = containingFile.getContext();
            if (context instanceof PsiPackage psiPackage) {
                return StringUtil.isEmpty(psiPackage.getName());
            }
            if (context != null && context != containingFile) {
                return isFromDefaultPackage(context);
            }
        }

        return false;
    }

    static boolean checkSameExpression(PsiElement templateExpr, PsiExpression expression) {
        return templateExpr.equals(skipParenthesizedExprDown(expression));
    }

    public static boolean isCondition(PsiElement expr, PsiElement parent) {
        if (parent instanceof PsiIfStatement ifStmt) {
            if (checkSameExpression(expr, ifStmt.getCondition())) {
                return true;
            }
        }
        else if (parent instanceof PsiWhileStatement whileStmt) {
            if (checkSameExpression(expr, whileStmt.getCondition())) {
                return true;
            }
        }
        else if (parent instanceof PsiForStatement forStmt) {
            if (checkSameExpression(expr, forStmt.getCondition())) {
                return true;
            }
        }
        else if (parent instanceof PsiDoWhileStatement doWhileStmt) {
            if (checkSameExpression(expr, doWhileStmt.getCondition())) {
                return true;
            }
        }
        else if (parent instanceof PsiConditionalExpression condExpr) {
            if (checkSameExpression(expr, condExpr.getCondition())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @RequiredReadAction
    public static PsiReturnStatement[] findReturnStatements(@Nonnull PsiMethod method) {
        return findReturnStatements(method.getBody());
    }

    @Nonnull
    @RequiredReadAction
    public static PsiReturnStatement[] findReturnStatements(@Nullable PsiCodeBlock body) {
        List<PsiReturnStatement> list = new ArrayList<>();
        if (body != null) {
            addStatements(list, body, PsiReturnStatement.class, statement -> false);
        }
        return list.toArray(PsiReturnStatement.EMPTY_ARRAY);
    }

    @RequiredReadAction
    private static <T extends PsiElement> void addStatements(
        @Nonnull List<? super T> list,
        @Nonnull PsiElement element,
        @Nonnull Class<? extends T> clazz,
        @Nonnull Predicate<? super PsiElement> stopAt
    ) {
        if (PsiTreeUtil.instanceOf(element, clazz)) {
            //noinspection unchecked
            list.add((T)element);
        }
        else if (!(element instanceof PsiClass) && !(element instanceof PsiLambdaExpression) && !stopAt.test(element)) {
            PsiElement[] children = element.getChildren();
            for (PsiElement child : children) {
                addStatements(list, child, clazz, stopAt);
            }
        }
    }

    @RequiredReadAction
    public static boolean isPackageEmpty(@Nonnull PsiDirectory[] directories, @Nonnull String packageName) {
        for (PsiDirectory directory : directories) {
            for (PsiFile file : directory.getFiles()) {
                if (file instanceof PsiClassOwner classOwner
                    && packageName.equals(classOwner.getPackageName())
                    && classOwner.getClasses().length > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Nonnull
    public static PsiModifierListOwner preferCompiledElement(@Nonnull PsiModifierListOwner element) {
        return element.getOriginalElement() instanceof PsiModifierListOwner modifierListOwner ? modifierListOwner : element;
    }

    @RequiredReadAction
    public static boolean isModuleFile(@Nonnull PsiFile file) {
        return file instanceof PsiJavaFile javaFile && javaFile.getModuleDeclaration() != null;
    }

    public static boolean canBeOverridden(@Nonnull PsiMethod method) {
        PsiClass parentClass = method.getContainingClass();
        return parentClass != null && !method.isConstructor() && !method.isStatic() && !method.isFinal() && !method.isPrivate()
            && !(parentClass instanceof PsiAnonymousClass) && !parentClass.isFinal();
    }

    @RequiredReadAction
    public static boolean isArrayClass(@Nullable PsiElement psiClass) {
        return psiClass != null && psiClass.getManager().areElementsEquivalent(
            psiClass, JavaPsiFacade.getElementFactory(psiClass.getProject()).getArrayClass(getLanguageLevel(psiClass)));
    }

    /**
     * @param variable variable to test
     * @return true if variable corresponds to JVM local variable defined inside the method
     */
    @Contract("null -> false")
    public static boolean isJvmLocalVariable(PsiElement variable) {
        return variable instanceof PsiLocalVariable || variable instanceof PsiParameter;
    }
}
