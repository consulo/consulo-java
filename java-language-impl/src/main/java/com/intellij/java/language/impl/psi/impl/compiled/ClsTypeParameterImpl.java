/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.InheritanceImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.java.language.impl.psi.impl.light.LightEmptyImplementsList;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.content.scope.SearchScope;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.meta.MetaDataService;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.Pair;

import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class ClsTypeParameterImpl extends ClsRepositoryPsiElement<PsiTypeParameterStub> implements PsiTypeParameter, PsiMetaOwner {
    private final LightEmptyImplementsList myLightEmptyImplementsList;

    public ClsTypeParameterImpl(PsiTypeParameterStub stub) {
        super(stub);
        myLightEmptyImplementsList = new LightEmptyImplementsList(getManager());
    }

    @Override
    public String getQualifiedName() {
        return null;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isAnnotationType() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public PsiField[] getFields() {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    public PsiMethod[] getMethods() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
        return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
    }

    @Override
    public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
        return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
    }

    @Override
    public PsiField findFieldByName(String name, boolean checkBases) {
        return PsiClassImplUtil.findFieldByName(this, name, checkBases);
    }

    @Override
    public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
        return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
    }

    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
        return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
    }

    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
        return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
    }

    @Override
    public PsiClass findInnerClassByName(String name, boolean checkBases) {
        return PsiClassImplUtil.findInnerByName(this, name, checkBases);
    }

    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return null;
    }

    @Override
    public boolean hasTypeParameters() {
        return false;
    }

    // very special method!
    @Override
    public PsiElement getScope() {
        return getParent().getParent();
    }

    @Override
    public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
        return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
    }

    @Override
    public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
        return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Override
    @RequiredReadAction
    public boolean processDeclarations(
        PsiScopeProcessor processor,
        ResolveState state,
        PsiElement lastParent,
        PsiElement place
    ) {
        return PsiClassImplUtil.processDeclarationsInClass(
            this,
            processor,
            state,
            null,
            lastParent,
            place,
            PsiUtil.getLanguageLevel(place),
            false
        );
    }

    @Override
    @RequiredReadAction
    public String getName() {
        return getStub().getName();
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Cannot change compiled classes");
    }

    @Override
    public PsiMethod[] getConstructors() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return false;
    }

    @Override
    public PsiReferenceList getExtendsList() {
        return getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_BOUND_LIST).getPsi();
    }

    @Override
    public PsiReferenceList getImplementsList() {
        return myLightEmptyImplementsList;
    }

    @Override
    public PsiClassType[] getExtendsListTypes() {
        return getExtendsList().getReferencedTypes();
    }

    @Override
    public PsiClassType[] getImplementsListTypes() {
        return PsiClassType.EMPTY_ARRAY;
    }

    @Override
    public PsiClass[] getInnerClasses() {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    public PsiField[] getAllFields() {
        return PsiField.EMPTY_ARRAY;
    }

    @Override
    public PsiMethod[] getAllMethods() {
        return PsiMethod.EMPTY_ARRAY;
    }

    @Override
    public PsiClass[] getAllInnerClasses() {
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    public PsiClassInitializer[] getInitializers() {
        return PsiClassInitializer.EMPTY_ARRAY;
    }

    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return PsiTypeParameter.EMPTY_ARRAY;
    }

    @Override
    public PsiClass getSuperClass() {
        return PsiClassImplUtil.getSuperClass(this);
    }

    @Override
    public PsiClass[] getInterfaces() {
        return PsiClassImplUtil.getInterfaces(this);
    }

    @Override
    public PsiClass[] getSupers() {
        return PsiClassImplUtil.getSupers(this);
    }

    @Override
    public PsiClassType[] getSuperTypes() {
        return PsiClassImplUtil.getSuperTypes(this);
    }

    @Override
    public PsiClass getContainingClass() {
        return null;
    }

    @Override
    public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        return PsiSuperMethodImplUtil.getVisibleSignatures(this);
    }

    @Override
    public PsiModifierList getModifierList() {
        return null;
    }

    @Override
    public boolean hasModifierProperty(String name) {
        return false;
    }

    @Override
    public PsiJavaToken getLBrace() {
        return null;
    }

    @Override
    public PsiJavaToken getRBrace() {
        return null;
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitTypeParameter(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public String toString() {
        return "PsiTypeParameter";
    }

    @Override
    @RequiredReadAction
    public void appendMirrorText(int indentLevel, StringBuilder buffer) {
        buffer.append(getName());

        PsiJavaCodeReferenceElement[] bounds = getExtendsList().getReferenceElements();
        if (bounds.length > 0) {
            buffer.append(" extends ");
            for (int i = 0; i < bounds.length; i++) {
                if (i > 0) {
                    buffer.append(" & ");
                }
                buffer.append(bounds[i].getCanonicalText());
            }
        }
    }

    @Override
    public void setMirror(TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, null);
        setMirror(getExtendsList(), SourceTreeToPsiMap.<PsiTypeParameter>treeToPsiNotNull(element).getExtendsList());
    }

    @Override
    @RequiredReadAction
    public PsiElement[] getChildren() {
        return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public PsiTypeParameterListOwner getOwner() {
        return (PsiTypeParameterListOwner)getParent().getParent();
    }

    @Override
    public int getIndex() {
        PsiTypeParameterStub stub = getStub();
        return stub.getParentStub().getChildrenStubs().indexOf(stub);
    }

    @Override
    public PsiMetaData getMetaData() {
        return MetaDataService.getInstance().getMeta(this);
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
        return PsiClassImplUtil.isClassEquivalentTo(this, another);
    }

    @Override
    public SearchScope getUseScope() {
        return PsiClassImplUtil.getClassUseScope(this);
    }

    //todo parse annotataions
    @Override
    public PsiAnnotation[] getAnnotations() {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    public PsiAnnotation findAnnotation(String qualifiedName) {
        return null;
    }

    @Override
    public PsiAnnotation addAnnotation(String qualifiedName) {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return getAnnotations();
    }
}
