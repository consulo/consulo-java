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
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.impl.psi.impl.light.AutomaticJavaModule;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.processor.FilterScopeProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.search.PackageScope;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.content.scope.SearchScope;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.ASTNode;
import consulo.language.ast.FileASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.impl.DebugUtil;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.ResolveScopeManager;
import consulo.language.psi.*;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.intellij.java.language.psi.PsiAnnotation.TargetType;

public class PsiImplUtil {
    private static final Logger LOG = Logger.getInstance(PsiImplUtil.class);

    private PsiImplUtil() {
    }

    @Nonnull
    public static PsiMethod[] getConstructors(@Nonnull PsiClass aClass) {
        List<PsiMethod> result = null;
        for (PsiMethod method : aClass.getMethods()) {
            if (method.isConstructor()) {
                if (result == null) {
                    result = new SmartList<>();
                }
                result.add(method);
            }
        }
        return result == null ? PsiMethod.EMPTY_ARRAY : result.toArray(new PsiMethod[result.size()]);
    }

    @Nullable
    public static PsiAnnotationMemberValue findDeclaredAttributeValue(@Nonnull PsiAnnotation annotation, String attributeName) {
        return findDeclaredAttributeValueImpl(annotation, attributeName, PsiNameValuePair::getValue);
    }

    @Nullable
    public static PsiAnnotationMemberValue findDeclaredAttributeDetachedValue(@Nonnull PsiAnnotation annotation, String attributeName) {
        return findDeclaredAttributeValueImpl(annotation, attributeName, PsiNameValuePair::getDetachedValue);
    }

    @Nullable
    private static PsiAnnotationMemberValue findDeclaredAttributeValueImpl(
        @Nonnull PsiAnnotation annotation,
        String attributeName,
        @Nonnull Function<PsiNameValuePair, PsiAnnotationMemberValue> valueGetter
    ) {
        if ("value".equals(attributeName)) {
            attributeName = null;
        }

        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        for (PsiNameValuePair attribute : attributes) {
            String name = attribute.getName();
            if (Objects.equals(name, attributeName) || attributeName == null && PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
                return valueGetter.apply(attribute);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiAnnotationMemberValue findAttributeValue(@Nonnull PsiAnnotation annotation, @Nullable String attributeName) {
        PsiAnnotationMemberValue value = findDeclaredAttributeValue(annotation, attributeName);
        if (value != null) {
            return value;
        }

        if (attributeName == null) {
            attributeName = "value";
        }
        PsiJavaCodeReferenceElement refElem = annotation.getNameReferenceElement();
        if (refElem != null && refElem.resolve() instanceof PsiClass resolvedClass) {
            PsiMethod[] methods = resolvedClass.findMethodsByName(attributeName, false);
            for (PsiMethod method : methods) {
                if (PsiUtil.isAnnotationMethod(method)) {
                    return ((PsiAnnotationMethod)method).getDefaultValue();
                }
            }
        }
        return null;
    }

    @Nonnull
    public static PsiTypeParameter[] getTypeParameters(@Nonnull PsiTypeParameterListOwner owner) {
        PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
        if (typeParameterList != null) {
            return typeParameterList.getTypeParameters();
        }
        return PsiTypeParameter.EMPTY_ARRAY;
    }

    @Nonnull
    public static PsiJavaCodeReferenceElement[] namesToPackageReferences(@Nonnull PsiManager manager, @Nonnull String[] names) {
        PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[names.length];
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            try {
                refs[i] = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createPackageReferenceElement(name);
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
        return refs;
    }

    public static int getParameterIndex(@Nonnull PsiParameter parameter, @Nonnull PsiParameterList parameterList) {
        PsiElement parameterParent = parameter.getParent();
        assert parameterParent == parameterList : parameterList + "; " + parameterParent;
        PsiParameter[] parameters = parameterList.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter paramInList = parameters[i];
            if (parameter.equals(paramInList)) {
                return i;
            }
        }
        String name = parameter.getName();
        PsiParameter suspect = null;
        int i;
        for (i = parameters.length - 1; i >= 0; i--) {
            PsiParameter paramInList = parameters[i];
            if (Comparing.equal(name, paramInList.getName())) {
                suspect = paramInList;
                break;
            }
        }
        String message = parameter + ":" + parameter.getClass() + " not found among parameters: " + Arrays.asList(parameters) + "." +
            " parameterList' parent: " + parameterList.getParent() + ";" +
            " parameter.isValid()=" + parameter.isValid() + ";" +
            " parameterList.isValid()= " + parameterList.isValid() + ";" +
            " parameterList stub: " + (parameterList instanceof StubBasedPsiElement sbpe ? sbpe.getStub() : "---") + "; " +
            " parameter stub: " + (parameter instanceof StubBasedPsiElement sbpe ? sbpe.getStub() : "---") + ";" +
            " suspect: " + suspect + " (index=" + i + "); " +
            (suspect == null ? null : suspect.getClass()) + " suspect stub: " +
            (suspect instanceof StubBasedPsiElement sbpe ? sbpe.getStub() : suspect == null ? "-null-" : "---" + suspect.getClass()) + ";" +
            " parameter.equals(suspect) = " + parameter.equals(suspect) + "; " +
            " parameter.getNode() == suspect.getNode():  " +
            (parameter.getNode() == (suspect == null ? null : suspect.getNode())) + "; " + ".";
        LOG.error(message);
        return i;
    }

    public static int getTypeParameterIndex(@Nonnull PsiTypeParameter typeParameter, @Nonnull PsiTypeParameterList typeParameterList) {
        PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
        for (int i = 0; i < typeParameters.length; i++) {
            if (typeParameter.equals(typeParameters[i])) {
                return i;
            }
        }
        LOG.assertTrue(false);
        return -1;
    }

    @Nonnull
    public static Object[] getReferenceVariantsByFilter(@Nonnull PsiJavaCodeReferenceElement reference, @Nonnull ElementFilter filter) {
        FilterScopeProcessor processor = new FilterScopeProcessor(filter);
        PsiScopesUtil.resolveAndWalk(processor, reference, null, true);
        return processor.getResults().toArray();
    }

    public static boolean processDeclarationsInMethod(
        @Nonnull PsiMethod method,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        boolean fromBody = lastParent instanceof PsiCodeBlock;
        PsiTypeParameterList typeParameterList = method.getTypeParameterList();
        return processDeclarationsInMethodLike(method, processor, state, place, fromBody, typeParameterList);
    }

    public static boolean processDeclarationsInLambda(
        @Nonnull PsiLambdaExpression lambda,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        boolean fromBody = lastParent != null && lastParent == lambda.getBody();
        return processDeclarationsInMethodLike(lambda, processor, state, place, fromBody, null);
    }

    private static boolean processDeclarationsInMethodLike(
        @Nonnull PsiParameterListOwner element,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place,
        boolean fromBody,
        @Nullable PsiTypeParameterList typeParameterList
    ) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, element);

        if (typeParameterList != null) {
            ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
            if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
                if (!typeParameterList.processDeclarations(processor, state, null, place)) {
                    return false;
                }
            }
        }

        if (fromBody) {
            PsiParameter[] parameters = element.getParameterList().getParameters();
            for (PsiParameter parameter : parameters) {
                if (!processor.execute(parameter, state)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean processDeclarationsInResourceList(
        @Nonnull PsiResourceList resourceList,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent
    ) {
        ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
        if (hint != null && !hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) {
            return true;
        }

        for (PsiResourceListElement resource : resourceList) {
            if (resource == lastParent) {
                break;
            }
            if (resource instanceof PsiResourceVariable && !processor.execute(resource, state)) {
                return false;
            }
        }

        return true;
    }

    public static boolean hasTypeParameters(@Nonnull PsiTypeParameterListOwner owner) {
        PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
        return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
    }

    @Nonnull
    public static PsiType[] typesByReferenceParameterList(@Nonnull PsiReferenceParameterList parameterList) {
        PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

        return typesByTypeElements(typeElements);
    }

    @Nonnull
    public static PsiType[] typesByTypeElements(@Nonnull PsiTypeElement[] typeElements) {
        PsiType[] types = PsiType.createArray(typeElements.length);
        for (int i = 0; i < types.length; i++) {
            types[i] = typeElements[i].getType();
        }
        if (types.length == 1 && types[0] instanceof PsiDiamondType diamondType) {
            return diamondType.resolveInferredTypes().getTypes();
        }
        return types;
    }

    @Nonnull
    @RequiredReadAction
    public static PsiType getType(@Nonnull PsiClassObjectAccessExpression classAccessExpression) {
        GlobalSearchScope resolveScope = classAccessExpression.getResolveScope();
        PsiManager manager = classAccessExpression.getManager();
        PsiClass classClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(JavaClassNames.JAVA_LANG_CLASS, resolveScope);
        if (classClass == null) {
            return new PsiClassReferenceType(new LightClassReference(manager, "Class", JavaClassNames.JAVA_LANG_CLASS, resolveScope), null);
        }
        if (!PsiUtil.isLanguageLevel5OrHigher(classAccessExpression)) {
            //Raw java.lang.Class
            return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(classClass);
        }

        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        PsiType operandType = classAccessExpression.getOperand().getType();
        if (operandType instanceof PsiPrimitiveType primitiveType && !PsiType.NULL.equals(primitiveType)) {
            if (PsiType.VOID.equals(primitiveType)) {
                operandType = JavaPsiFacade.getInstance(manager.getProject())
                    .getElementFactory()
                    .createTypeByFQClassName(JavaClassNames.JAVA_LANG_VOID, classAccessExpression.getResolveScope());
            }
            else {
                operandType = primitiveType.getBoxedType(classAccessExpression);
            }
        }
        PsiTypeParameter[] typeParameters = classClass.getTypeParameters();
        if (typeParameters.length == 1) {
            substitutor = substitutor.put(typeParameters[0], operandType);
        }

        return new PsiImmediateClassType(classClass, substitutor);
    }

    @Nullable
    public static PsiAnnotation findAnnotation(@Nullable PsiAnnotationOwner annotationOwner, @Nonnull String qualifiedName) {
        if (annotationOwner == null) {
            return null;
        }

        PsiAnnotation[] annotations = annotationOwner.getAnnotations();
        if (annotations.length == 0) {
            return null;
        }

        String shortName = StringUtil.getShortName(qualifiedName);
        for (PsiAnnotation annotation : annotations) {
            PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
            if (referenceElement != null && shortName.equals(referenceElement.getReferenceName())) {
                if (qualifiedName.equals(annotation.getQualifiedName())) {
                    return annotation;
                }
            }
        }

        return null;
    }

    /**
     * @deprecated use {@link AnnotationTargetUtil#findAnnotationTarget(PsiAnnotation, TargetType...)} (to be removed ion IDEA 17)
     */
    @SuppressWarnings("unused")
    public static TargetType findApplicableTarget(@Nonnull PsiAnnotation annotation, @Nonnull TargetType... types) {
        return AnnotationTargetUtil.findAnnotationTarget(annotation, types);
    }

    /**
     * @deprecated use {@link AnnotationTargetUtil#findAnnotationTarget(PsiClass, TargetType...)} (to be removed ion IDEA 17)
     */
    @SuppressWarnings("unused")
    public static TargetType findApplicableTarget(@Nonnull PsiClass annotationType, @Nonnull TargetType... types) {
        return AnnotationTargetUtil.findAnnotationTarget(annotationType, types);
    }

    /**
     * @deprecated use {@link AnnotationTargetUtil#getAnnotationTargets(PsiClass)} (to be removed ion IDEA 17)
     */
    @SuppressWarnings("unused")
    public static Set<TargetType> getAnnotationTargets(@Nonnull PsiClass annotationType) {
        return AnnotationTargetUtil.getAnnotationTargets(annotationType);
    }

    /**
     * @deprecated use {@link AnnotationTargetUtil#getTargetsForLocation(PsiAnnotationOwner)} (to be removed ion IDEA 17)
     */
    @SuppressWarnings("unused")
    public static TargetType[] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
        return AnnotationTargetUtil.getTargetsForLocation(owner);
    }

    @Nullable
    public static ASTNode findDocComment(@Nonnull CompositeElement element) {
        TreeElement node = element.getFirstChildNode();
        while (node != null && (isWhitespaceOrComment(node) && !(node.getPsi() instanceof PsiDocComment))) {
            node = node.getTreeNext();
        }

        if (node != null && node.getElementType() == JavaDocElementType.DOC_COMMENT) {
            return node;
        }
        else {
            return null;
        }
    }

    public static PsiType normalizeWildcardTypeByPosition(@Nonnull PsiType type, @Nonnull PsiExpression expression) {
        PsiUtilCore.ensureValid(expression);
        PsiUtil.ensureValidType(type);

        PsiExpression topLevel = expression;
        while (topLevel.getParent() instanceof PsiArrayAccessExpression arrayAccess && arrayAccess.getArrayExpression() == topLevel) {
            topLevel = arrayAccess;
        }

        if (topLevel instanceof PsiArrayAccessExpression && !PsiUtil.isAccessedForWriting(topLevel)) {
            return PsiUtil.captureToplevelWildcards(type, expression);
        }

        PsiType normalized = doNormalizeWildcardByPosition(type, expression, topLevel);
        LOG.assertTrue(normalized.isValid(), type);
        if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(topLevel)) {
            return PsiUtil.captureToplevelWildcards(normalized, expression);
        }

        return normalized;
    }

    private static PsiType doNormalizeWildcardByPosition(PsiType type, @Nonnull PsiExpression expression, PsiExpression topLevel) {
        if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
            PsiWildcardType wildcardType = capturedWildcardType.getWildcard();
            if (expression instanceof PsiReferenceExpression && LambdaUtil.isLambdaReturnExpression(expression)) {
                return capturedWildcardType;
            }

            if (PsiUtil.isAccessedForWriting(topLevel)) {
                return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
            }
            else {
                PsiType upperBound = capturedWildcardType.getUpperBound();
                return upperBound instanceof PsiWildcardType ? doNormalizeWildcardByPosition(upperBound, expression, topLevel) : upperBound;
            }
        }


        if (type instanceof PsiWildcardType wildcardType) {
            if (PsiUtil.isAccessedForWriting(topLevel)) {
                return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
            }
            else if (wildcardType.isExtends()) {
                return wildcardType.getBound();
            }
            else {
                return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
            }
        }
        else if (type instanceof PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            PsiType normalizedComponentType = doNormalizeWildcardByPosition(componentType, expression, topLevel);
            if (normalizedComponentType != componentType) {
                return normalizedComponentType.createArrayType();
            }
        }

        return type;
    }

    @Nonnull
    @RequiredReadAction
    public static SearchScope getMemberUseScope(@Nonnull PsiMember member) {
        PsiFile file = member.getContainingFile();
        PsiElement topElement = file == null ? member : file;
        Project project = topElement.getProject();
        GlobalSearchScope maximalUseScope = ResolveScopeManager.getInstance(project).getUseScope(topElement);
        if (isInServerPage(file)) {
            return maximalUseScope;
        }

        if (member.getContainingClass() instanceof PsiAnonymousClass aClass) {
            //member from anonymous class can be called from outside the class
            PsiElement methodCallExpr = PsiUtil.isLanguageLevel8OrHigher(aClass)
                ? PsiTreeUtil.getTopmostParentOfType(aClass, PsiStatement.class)
                : PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
            return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
        }

        PsiModifierList modifierList = member.getModifierList();
        int accessLevel = modifierList == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList);
        if (accessLevel == PsiUtil.ACCESS_LEVEL_PUBLIC || accessLevel == PsiUtil.ACCESS_LEVEL_PROTECTED) {
            return maximalUseScope; // class use scope doesn't matter, since another very visible class can inherit from aClass
        }
        if (accessLevel == PsiUtil.ACCESS_LEVEL_PRIVATE) {
            PsiClass topClass = PsiUtil.getTopLevelClass(member);
            return topClass != null ? new LocalSearchScope(topClass) : file == null ? maximalUseScope : new LocalSearchScope(file);
        }
        if (file instanceof PsiJavaFile javaFile) {
            PsiJavaPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(javaFile.getPackageName());
            if (aPackage != null) {
                SearchScope scope = PackageScope.packageScope(aPackage, false);
                return scope.intersectWith(maximalUseScope);
            }
        }
        return maximalUseScope;
    }

    public static boolean isInServerPage(@Nullable PsiElement element) {
        return getServerPageFile(element) != null;
    }

    @Nullable
    public static ServerPageFile getServerPageFile(PsiElement element) {
        PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
        return psiFile instanceof ServerPageFile serverPageFile ? serverPageFile : null;
    }

    @RequiredWriteAction
    public static PsiElement setName(@Nonnull PsiElement element, @Nonnull String name) throws IncorrectOperationException {
        PsiManager manager = element.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiIdentifier newNameIdentifier = factory.createIdentifier(name);
        return element.replace(newNameIdentifier);
    }

    public static boolean isDeprecatedByAnnotation(@Nonnull PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        return modifierList != null && modifierList.findAnnotation(JavaClassNames.JAVA_LANG_DEPRECATED) != null;
    }

    public static boolean isDeprecatedByDocTag(@Nonnull PsiJavaDocumentedElement owner) {
        PsiDocComment docComment = owner.getDocComment();
        return docComment != null && docComment.findTagByName("deprecated") != null;
    }

    @Nullable
    public static PsiJavaDocumentedElement findDocCommentOwner(@Nonnull PsiDocComment comment) {
        if (comment.getParent() instanceof PsiJavaDocumentedElement owner && owner.getDocComment() == comment) {
            return owner;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static PsiAnnotationMemberValue setDeclaredAttributeValue(
        @Nonnull PsiAnnotation psiAnnotation,
        @Nullable String attributeName,
        @Nullable PsiAnnotationMemberValue value,
        @Nonnull BiFunction<Project, String, PsiAnnotation> annotationCreator
    ) {
        PsiAnnotationMemberValue existing = psiAnnotation.findDeclaredAttributeValue(attributeName);
        if (value == null) {
            if (existing == null) {
                return null;
            }
            existing.getParent().delete();
        }
        else if (existing != null) {
            ((PsiNameValuePair)existing.getParent()).setValue(value);
        }
        else {
            PsiNameValuePair[] attributes = psiAnnotation.getParameterList().getAttributes();
            if (attributes.length == 1) {
                PsiNameValuePair attribute = attributes[0];
                if (attribute.getName() == null) {
                    PsiAnnotationMemberValue defValue = attribute.getValue();
                    assert defValue != null : attribute;
                    attribute.replace(createNameValuePair(
                        defValue,
                        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + "=",
                        annotationCreator
                    ));
                }
            }

            boolean allowNoName = attributes.length == 0 && ("value".equals(attributeName) || null == attributeName);
            String namePrefix;
            if (allowNoName) {
                namePrefix = "";
            }
            else {
                namePrefix = attributeName + "=";
            }
            psiAnnotation.getParameterList().addBefore(createNameValuePair(value, namePrefix, annotationCreator), null);
        }
        return psiAnnotation.findDeclaredAttributeValue(attributeName);
    }

    @RequiredReadAction
    private static PsiNameValuePair createNameValuePair(
        @Nonnull PsiAnnotationMemberValue value,
        @Nonnull String namePrefix,
        @Nonnull BiFunction<Project, String, PsiAnnotation> annotationCreator
    ) {
        return annotationCreator.apply(value.getProject(), "@A(" + namePrefix + value.getText() + ")")
            .getParameterList().getAttributes()[0];
    }

    @Nullable
    public static ASTNode skipWhitespaceAndComments(ASTNode node) {
        return skipWhitespaceCommentsAndTokens(node, TokenSet.EMPTY);
    }

    @Nullable
    public static ASTNode skipWhitespaceCommentsAndTokens(ASTNode node, TokenSet alsoSkip) {
        ASTNode element = node;
        while (true) {
            if (element == null) {
                return null;
            }
            if (!isWhitespaceOrComment(element) && !alsoSkip.contains(element.getElementType())) {
                break;
            }
            element = element.getTreeNext();
        }
        return element;
    }

    @Nullable
    public static PsiSwitchExpression findEnclosingSwitchExpression(@Nonnull PsiElement start) {
        for (PsiElement e = start; !isCodeBoundary(e); e = e.getParent()) {
            if (e instanceof PsiSwitchExpression switchExpr) {
                return switchExpr;
            }
        }
        return null;
    }

    private static boolean isCodeBoundary(@Nullable PsiElement e) {
        return e == null || e instanceof PsiMethod || e instanceof PsiClassInitializer || e instanceof PsiLambdaExpression;
    }

    public static boolean isWhitespaceOrComment(ASTNode element) {
        return element.getPsi() instanceof PsiWhiteSpace || element.getPsi() instanceof PsiComment;
    }

    @Nullable
    public static ASTNode skipWhitespaceAndCommentsBack(ASTNode node) {
        if (node == null) {
            return null;
        }
        if (!isWhitespaceOrComment(node)) {
            return node;
        }

        ASTNode parent = node.getTreeParent();
        ASTNode prev = node;
        while (prev instanceof CompositeElement) {
            if (!isWhitespaceOrComment(prev)) {
                return prev;
            }
            prev = prev.getTreePrev();
        }
        if (prev == null) {
            return null;
        }
        ASTNode firstChildNode = parent.getFirstChildNode();
        ASTNode lastRelevant = null;
        while (firstChildNode != prev) {
            if (!isWhitespaceOrComment(firstChildNode)) {
                lastRelevant = firstChildNode;
            }
            firstChildNode = firstChildNode.getTreeNext();
        }
        return lastRelevant;
    }

    @Nullable
    @RequiredReadAction
    public static ASTNode findStatementChild(CompositePsiElement statement) {
        if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
            statement.getApplication().assertReadAccessAllowed();
        }
        for (ASTNode element = statement.getFirstChildNode(); element != null; element = element.getTreeNext()) {
            if (element.getPsi() instanceof PsiStatement) {
                return element;
            }
        }
        return null;
    }

    @RequiredReadAction
    public static PsiStatement[] getChildStatements(CompositeElement psiCodeBlock) {
        Application.get().assertReadAccessAllowed();
        // no lock is needed because all chameleons are expanded already
        int count = 0;
        for (ASTNode child1 = psiCodeBlock.getFirstChildNode(); child1 != null; child1 = child1.getTreeNext()) {
            if (child1.getPsi() instanceof PsiStatement) {
                count++;
            }
        }

        PsiStatement[] result = PsiStatement.ARRAY_FACTORY.create(count);
        if (count == 0) {
            return result;
        }
        int idx = 0;
        for (ASTNode child = psiCodeBlock.getFirstChildNode(); child != null && idx < count; child = child.getTreeNext()) {
            PsiElement element = child.getPsi();
            if (element instanceof PsiStatement stmt) {
                result[idx++] = stmt;
            }
        }
        return result;
    }

    public static boolean isVarArgs(@Nonnull PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
    }

    public static PsiElement handleMirror(PsiElement element) {
        return element instanceof PsiMirrorElement mirrorElem ? mirrorElem.getPrototype() : element;
    }

    @Nullable
    public static PsiModifierList findNeighbourModifierList(@Nonnull PsiJavaCodeReferenceElement ref) {
        if (PsiTreeUtil.skipParentsOfType(ref, PsiJavaCodeReferenceElement.class) instanceof PsiTypeElement typeElement
            && typeElement.getParent() instanceof PsiModifierListOwner modifierListOwner) {
            return modifierListOwner.getModifierList();
        }

        return null;
    }

    public static boolean isTypeAnnotation(@Nullable PsiElement element) {
        return element instanceof PsiAnnotation annotation && AnnotationTargetUtil.isTypeAnnotation(annotation);
    }

    public static void collectTypeUseAnnotations(@Nonnull PsiModifierList modifierList, @Nonnull List<PsiAnnotation> annotations) {
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (AnnotationTargetUtil.isTypeAnnotation(annotation)) {
                annotations.add(annotation);
            }
        }
    }

    /**
     * @deprecated use {@link #collectTypeUseAnnotations(PsiModifierList, List)} (to be removed in IDEA 16)
     */
    @SuppressWarnings("unused")
    public static List<PsiAnnotation> getTypeUseAnnotations(@Nonnull PsiModifierList modifierList) {
        SmartList<PsiAnnotation> result = null;

        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (isTypeAnnotation(annotation)) {
                if (result == null) {
                    result = new SmartList<>();
                }
                result.add(annotation);
            }
        }

        return result;
    }

    private static final Key<Boolean> TYPE_ANNO_MARK = Key.create("type.annotation.mark");

    @RequiredReadAction
    public static void markTypeAnnotations(@Nonnull PsiTypeElement typeElement) {
        PsiElement left = PsiTreeUtil.skipSiblingsBackward(typeElement, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
        if (left instanceof PsiModifierList modifierList) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                if (AnnotationTargetUtil.isTypeAnnotation(annotation)) {
                    annotation.putUserData(TYPE_ANNO_MARK, Boolean.TRUE);
                }
            }
        }
    }

    @RequiredReadAction
    public static void deleteTypeAnnotations(@Nonnull PsiTypeElement typeElement) {
        PsiElement left = PsiTreeUtil.skipSiblingsBackward(typeElement, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
        if (left instanceof PsiModifierList modifierList) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                if (TYPE_ANNO_MARK.get(annotation) == Boolean.TRUE) {
                    annotation.delete();
                }
            }
        }
    }

    public static boolean isLeafElementOfType(@Nullable PsiElement element, IElementType type) {
        return element instanceof LeafElement leafElement && leafElement.getElementType() == type;
    }

    public static boolean isLeafElementOfType(PsiElement element, TokenSet tokenSet) {
        return element instanceof LeafElement leafElement && tokenSet.contains(leafElement.getElementType());
    }

    public static PsiType buildTypeFromTypeString(@Nonnull String typeName, @Nonnull PsiElement context, @Nonnull PsiFile psiFile) {
        PsiType resultType;
        PsiManager psiManager = psiFile.getManager();

        if (typeName.indexOf('<') != -1 || typeName.indexOf('[') != -1 || typeName.indexOf('.') == -1) {
            try {
                return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createTypeFromText(typeName, context);
            }
            catch (Exception ignored) {
            } // invalid syntax will produce unresolved class type
        }

        PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(typeName, context.getResolveScope());

        if (aClass == null) {
            LightClassReference ref =
                new LightClassReference(psiManager, PsiNameHelper.getShortClassName(typeName), typeName, PsiSubstitutor.EMPTY, psiFile);
            resultType = new PsiClassReferenceType(ref, null);
        }
        else {
            PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
            PsiSubstitutor substitutor = factory.createRawSubstitutor(aClass);
            resultType = factory.createType(aClass, substitutor);
        }

        return resultType;
    }

    @Nonnull
    public static <T extends PsiJavaCodeReferenceElement> JavaResolveResult[] multiResolveImpl(
        @Nonnull T element,
        boolean incompleteCode,
        @Nonnull ResolveCache.PolyVariantContextResolver<? super T> resolver
    ) {
        FileASTNode fileElement = SharedImplUtil.findFileElement(element.getNode());
        if (fileElement == null) {
            PsiUtilCore.ensureValid(element);
            LOG.error("fileElement == null!");
            return JavaResolveResult.EMPTY_ARRAY;
        }
        PsiFile psiFile = SharedImplUtil.getContainingFile(fileElement);
        PsiManager manager = psiFile == null ? null : psiFile.getManager();
        if (manager == null) {
            PsiUtilCore.ensureValid(element);
            LOG.error("getManager() == null!");
            return JavaResolveResult.EMPTY_ARRAY;
        }
        boolean valid = psiFile.isValid();
        if (!valid) {
            PsiUtilCore.ensureValid(element);
            LOG.error("psiFile.isValid() == false!");
            return JavaResolveResult.EMPTY_ARRAY;
        }
        if (element instanceof PsiMethodReferenceExpression) {
            // method refs: do not cache results during parent conflict resolving, acceptable checks, etc
            Map<PsiElement, PsiType> map = LambdaUtil.ourFunctionTypes.get();
            if (map != null && map.containsKey(element)) {
                return (JavaResolveResult[])resolver.resolve(element, psiFile, incompleteCode);
            }
        }

        return multiResolveImpl(manager.getProject(), psiFile, element, incompleteCode, resolver);
    }

    @Nonnull
    public static <T extends PsiJavaCodeReferenceElement> JavaResolveResult[] multiResolveImpl(
        @Nonnull Project project,
        @Nonnull PsiFile psiFile,
        @Nonnull T element,
        boolean incompleteCode,
        @Nonnull ResolveCache.PolyVariantContextResolver<? super T> resolver
    ) {
        ResolveResult[] results = ResolveCache.getInstance(project).resolveWithCaching(element, resolver, true, incompleteCode, psiFile);
        return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
    }

    /**
     * Returns enclosing label statement for given label expression
     *
     * @param expression switch label expression
     * @return enclosing label statement or null if given expression is not a label statement expression
     */
    @Nullable
    public static PsiSwitchLabelStatementBase getSwitchLabel(@Nonnull PsiExpression expression) {
        if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiExpressionList exprList
            && exprList.getParent() instanceof PsiSwitchLabelStatementBase switchLabelStmt) {
            return switchLabelStmt;
        }
        return null;
    }

    public static VirtualFile getModuleVirtualFile(@Nonnull PsiJavaModule module) {
        if (module instanceof AutomaticJavaModule automaticJavaModule) {
            return automaticJavaModule.getRootVirtualFile();
        }
        else {
            return module.getContainingFile().getVirtualFile();
        }
    }
}
