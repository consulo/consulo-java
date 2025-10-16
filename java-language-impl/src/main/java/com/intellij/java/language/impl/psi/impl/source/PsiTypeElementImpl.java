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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.psi.PsiDiamondTypeImpl;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.JavaPsiPatternUtil;
import com.intellij.java.language.psi.util.JavaVarTypeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.attachment.AttachmentFactory;
import consulo.logging.attachment.RuntimeExceptionWithAttachments;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.collection.SmartList;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
    @SuppressWarnings("UnusedDeclaration")
    public PsiTypeElementImpl() {
        this(JavaElementType.TYPE);
    }

    PsiTypeElementImpl(@Nonnull IElementType type) {
        super(type);
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitTypeElement(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiType getType() {
        return LanguageCachedValueUtil.getCachedValue(
            this,
            () -> CachedValueProvider.Result.create(
                calculateType(),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        );
    }

    @Nonnull
    @RequiredReadAction
    private PsiType calculateType() {
        PsiType inferredType = PsiAugmentProvider.getInferredType(this);
        if (inferredType != null) {
            return inferredType;
        }

        PsiType type = null;
        boolean ellipsis = false;
        List<PsiAnnotation> annotations = new SmartList<>();
        List<TypeAnnotationProvider> arrayComponentAnnotations = new SmartList<>();

        PsiElement parent = getParent();
        PsiElement firstChild = getFirstChild();
        if (firstChild == null && parent instanceof PsiUnnamedPattern unnamedPattern) {
            type = JavaPsiPatternUtil.getDeconstructedImplicitPatternType(unnamedPattern);
        }
        for (PsiElement child = firstChild; child != null; child = child.getNextSibling()) {
            if (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
                continue;
            }

            if (child instanceof PsiAnnotation annotation) {
                annotations.add(annotation);
            }
            else if (child instanceof PsiTypeElement typeElem) {
                assert type == null : this;
                if (child instanceof PsiDiamondTypeElementImpl) {
                    type = new PsiDiamondTypeImpl(getManager(), this);
                    break;
                }
                else {
                    type = typeElem.getType();
                }
            }
            else if (PsiUtil.isJavaToken(child, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
                assert type == null : this;
                String text = child.getText();
                type = annotations.isEmpty()
                    ? PsiJavaParserFacadeImpl.getPrimitiveType(text)
                    : new PsiPrimitiveType(text, createProvider(annotations));
            }
            else if (PsiUtil.isJavaToken(child, JavaTokenType.VAR_KEYWORD)) {
                assert type == null : this;
                type = inferVarType(parent);
            }
            else if (child instanceof PsiJavaCodeReferenceElement codeRefElem) {
                assert type == null : this;
                type = new PsiClassReferenceType(getReferenceComputable(codeRefElem), null, createProvider(annotations));
            }
            else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
                assert type != null : this;
                arrayComponentAnnotations.add(createProvider(annotations));
                annotations = new SmartList<>();
            }
            else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
                assert type != null : this;
                arrayComponentAnnotations.add(createProvider(annotations));
                annotations = new SmartList<>();
                ellipsis = true;
            }

            if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST)) {
                assert type == null : this;
                PsiElement boundKind = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
                PsiElement boundType = PsiTreeUtil.skipWhitespacesAndCommentsForward(boundKind);
                if (PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement typeElem) {
                    type = PsiWildcardType.createExtends(getManager(), typeElem.getType());
                }
                else if (PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement typeElem) {
                    type = PsiWildcardType.createSuper(getManager(), typeElem.getType());
                }
                else {
                    type = PsiWildcardType.createUnbounded(getManager());
                }
                type = type.annotate(createProvider(annotations));
                break;
            }
            else if (child instanceof ASTNode childNode) {
                childNode.getElementType();
            }

            if (PsiUtil.isJavaToken(child, JavaTokenType.AND)) {
                List<PsiType> types = collectTypes();
                assert !types.isEmpty() : this;
                type = PsiIntersectionType.createIntersection(false, types.toArray(PsiType.createArray(types.size())));
                break;
            }

            if (PsiUtil.isJavaToken(child, JavaTokenType.OR)) {
                List<PsiType> types = collectTypes();
                assert !types.isEmpty() : this;
                type = PsiDisjunctionType.createDisjunction(types, getManager());
                break;
            }
        }

        if (type == null) {
            return PsiTypes.nullType();
        }

        if (!arrayComponentAnnotations.isEmpty()) {
            type = createArray(type, arrayComponentAnnotations, ellipsis);
        }

        if (parent instanceof PsiModifierListOwner modifierListOwner) {
            type = JavaSharedImplUtil.applyAnnotations(type, modifierListOwner.getModifierList());
        }

        return type;
    }

    private static PsiType createArray(PsiType elementType, List<TypeAnnotationProvider> providers, boolean ellipsis) {
        PsiType result = elementType;
        for (int i = providers.size() - 1; i >= 0; i--) {
            TypeAnnotationProvider provider = providers.get(i);
            result = ellipsis && i == 0 ? new PsiEllipsisType(result, provider) : new PsiArrayType(result, provider);
        }
        providers.clear();
        return result;
    }

    @RequiredReadAction
    private PsiType inferVarType(PsiElement parent) {
        if (parent instanceof PsiParameter parameter) {
            if (parameter instanceof PsiPatternVariable patternVar) {
                return JavaPsiPatternUtil.getDeconstructedImplicitPatternVariableType(patternVar);
            }
            PsiElement declarationScope = parameter.getDeclarationScope();
            if (declarationScope instanceof PsiForeachStatement forEach) {
                PsiExpression iteratedValue = forEach.getIteratedValue();
                if (iteratedValue != null) {
                    PsiType type = JavaGenericsUtil.getCollectionItemType(iteratedValue);
                    //Upward projection is applied to the type of the initializer when determining the type of the
                    //variable
                    return type != null ? JavaVarTypeUtil.getUpwardProjection(type) : null;
                }
                return null;
            }

            if (declarationScope instanceof PsiLambdaExpression) {
                return parameter.getType();
            }
        }
        else {
            for (PsiElement e = this; e != null; e = e.getNextSibling()) {
                if (e instanceof PsiExpression expression) {
                    if (!(e instanceof PsiArrayInitializerExpression)) {
                        RecursionGuard.StackStamp stamp = RecursionManager.markStack();
                        PsiType type = RecursionManager.doPreventingRecursion(expression, true, expression::getType);
                        if (stamp.mayCacheNow()) {
                            return type == null ? null : JavaVarTypeUtil.getUpwardProjection(type);
                        }
                        return null;
                    }
                    return null;
                }
            }
        }
        return null;
    }


    @Override
    public boolean isInferredType() {
        PsiElement firstChild = getFirstChild();
        return PsiUtil.isJavaToken(firstChild, JavaTokenType.VAR_KEYWORD) || PsiAugmentProvider.isInferredType(this);
    }

    @Nonnull
    private static ClassReferencePointer getReferenceComputable(@Nonnull PsiJavaCodeReferenceElement ref) {
        PsiTypeElement rootType = getRootTypeElement(ref);
        if (rootType != null) {
            PsiElement parent = rootType.getParent();
            if (parent instanceof PsiMethod || parent instanceof PsiVariable) {
                int index = allReferencesInside(rootType).indexOf(ref::equals);
                if (index < 0) {
                    throw new AssertionError(rootType.getClass());
                }
                return computeFromTypeOwner(parent, index, new WeakReference<>(ref));
            }
        }
        return ClassReferencePointer.constant(ref);
    }

    @Nullable
    private static PsiTypeElement getRootTypeElement(@Nonnull PsiJavaCodeReferenceElement ref) {
        PsiElement root = SyntaxTraverser.psiApi()
            .parents(ref.getParent())
            .takeWhile(
                it -> it instanceof PsiTypeElement || it instanceof PsiReferenceParameterList || it instanceof PsiJavaCodeReferenceElement
            )
            .last();
        return ObjectUtil.tryCast(root, PsiTypeElement.class);
    }

    @Nonnull
    private static ClassReferencePointer computeFromTypeOwner(
        PsiElement parent,
        int index,
        @Nonnull WeakReference<PsiJavaCodeReferenceElement> ref
    ) {
        return new ClassReferencePointer() {
            volatile WeakReference<PsiJavaCodeReferenceElement> myCache = ref;

            @Override
            public
            @Nullable
            PsiJavaCodeReferenceElement retrieveReference() {
                PsiJavaCodeReferenceElement result = myCache.get();
                if (result == null) {
                    PsiType type = calcTypeByParent();
                    if (type instanceof PsiClassReferenceType classRefType) {
                        result = findReferenceByIndex(classRefType);
                    }
                    myCache = new WeakReference<>(result);
                }
                return result;
            }

            @Nullable
            private PsiJavaCodeReferenceElement findReferenceByIndex(PsiClassReferenceType type) {
                PsiTypeElement root = getRootTypeElement(type.getReference());
                return root == null ? null : allReferencesInside(root).get(index);
            }

            @Nullable
            private PsiType calcTypeByParent() {
                if (!parent.isValid()) {
                    return null;
                }

                PsiType type = parent instanceof PsiMethod method ? method.getReturnType() : ((PsiVariable)parent).getType();
                //also, for c-style array, e.g. String args[]
                return type instanceof PsiArrayType arrayType ? arrayType.getDeepComponentType() : type;
            }

            @Override
            public
            @Nonnull
            PsiJavaCodeReferenceElement retrieveNonNullReference() {
                PsiJavaCodeReferenceElement result = retrieveReference();
                if (result == null) {
                    PsiType type = calcTypeByParent();
                    if (!(type instanceof PsiClassReferenceType classRefType)) {
                        PsiUtilCore.ensureValid(parent);
                        throw new IllegalStateException(
                            "No reference type for " + parent.getClass() + "; type: " + (type != null ? type.getClass() : "null")
                        );
                    }
                    result = findReferenceByIndex(classRefType);
                    if (result == null) {
                        PsiUtilCore.ensureValid(parent);
                        throw new RuntimeExceptionWithAttachments(
                            "Can't retrieve reference by index " + index + " for " + parent.getClass() + "; type: " + type.getClass(),
                            AttachmentFactory.get().create("memberType.txt", type.getCanonicalText())
                        );
                    }
                }
                return result;
            }

            @Override
            @RequiredReadAction
            public String toString() {
                String msg =
                    "Type element reference of " + parent.getClass() + " #" + parent.getClass().getSimpleName() + ", index=" + index;
                return parent.isValid() ? msg + " #" + parent.getLanguage() : msg + ", invalid";
            }
        };
    }

    @Nonnull
    private static JBIterable<PsiJavaCodeReferenceElement> allReferencesInside(@Nonnull PsiTypeElement rootType) {
        return SyntaxTraverser.psiTraverser(rootType).filter(PsiJavaCodeReferenceElement.class);
    }

    @Nonnull
    private static TypeAnnotationProvider createProvider(@Nonnull List<PsiAnnotation> annotations) {
        return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
    }

    @Nonnull
    @RequiredReadAction
    private List<PsiType> collectTypes() {
        List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
        return ContainerUtil.map(typeElements, PsiTypeElement::getType);
    }

    @Override
    public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
        TreeElement firstChildNode = getFirstChildNode();
        if (firstChildNode == null) {
            return null;
        }
        if (firstChildNode.getElementType() == JavaElementType.TYPE) {
            return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
        }
        return getReferenceElement();
    }

    @Nullable
    private PsiJavaCodeReferenceElement getReferenceElement() {
        ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
        if (ref == null) {
            return null;
        }
        return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
    }

    @Override
    public boolean processDeclarations(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
        return true;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiAnnotation[] getAnnotations() {
        PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(this, PsiAnnotation.class);
        return annotations != null ? annotations : PsiAnnotation.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiAnnotation[] getApplicableAnnotations() {
        return getType().getAnnotations();
    }

    @Override
    public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
        return PsiImplUtil.findAnnotation(this, qualifiedName);
    }

    @Nonnull
    @Override
    @RequiredWriteAction
    public PsiAnnotation addAnnotation(@Nonnull String qualifiedName) {
        PsiAnnotation annotation = JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText('@' + qualifiedName, this);
        PsiElement firstChild = getFirstChild();
        for (PsiElement child = getLastChild(); child != firstChild; child = child.getPrevSibling()) {
            if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET) || PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
                return (PsiAnnotation)addBefore(annotation, child);
            }
        }
        if (firstChild instanceof PsiJavaCodeReferenceElement) {
            PsiIdentifier identifier = PsiTreeUtil.getChildOfType(firstChild, PsiIdentifier.class);
            if (identifier != null && identifier != firstChild.getFirstChild()) {
                // qualified reference
                return (PsiAnnotation)firstChild.addBefore(annotation, identifier);
            }
        }
        PsiElement parent = getParent();
        while (parent instanceof PsiTypeElement typeElem && typeElem.getType() instanceof PsiArrayType) {
            parent = typeElem.getParent();
        }
        if (parent instanceof PsiModifierListOwner modifierListOwner) {
            PsiModifierList modifierList = modifierListOwner.getModifierList();
            if (modifierList != null) {
                PsiTypeParameterList list =
                    parent instanceof PsiTypeParameterListOwner typeParamListOwner ? typeParamListOwner.getTypeParameterList() : null;
                if (list == null || list.textMatches("")) {
                    return (PsiAnnotation)modifierList.add(annotation);
                }
            }
        }
        return (PsiAnnotation)addBefore(annotation, firstChild);
    }

    @Override
    @RequiredWriteAction
    public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
        // neighbouring type annotations are logical part of this type element and should be dropped
        //if replacement is `var`, annotations should be left as they are not inferred from the right side of the assignment
        if (!(newElement instanceof PsiTypeElement typeElem) || !typeElem.isInferredType()) {
            PsiImplUtil.markTypeAnnotations(this);
        }
        PsiElement result = super.replace(newElement);
        if (result instanceof PsiTypeElement typeElem) {
            PsiImplUtil.deleteTypeAnnotations(typeElem);
        }
        return result;
    }

    @Override
    @RequiredReadAction
    public boolean acceptsAnnotations() {
        if (isInferredType()) {
            return false;
        }
        PsiType type = getType();
        return !PsiTypes.voidType().equals(type) && !PsiTypes.nullType().equals(type);
    }

    @Override
    public String toString() {
        return "PsiTypeElement:" + getText();
    }
}