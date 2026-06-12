// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.InheritanceImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRecordHeaderStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.java.language.impl.psi.impl.source.ClassInnerStuffCache;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.PsiClassImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiExtensibleClass;
import com.intellij.java.language.impl.psi.scope.processor.MethodsProcessor;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.util.Queryable;
import consulo.content.scope.SearchScope;
import consulo.java.language.impl.psi.augment.JavaEnumAugmentProvider;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static consulo.util.lang.ObjectUtil.assertNotNull;
import static java.util.Arrays.asList;

public class ClsClassImpl extends ClsMemberImpl<PsiClassStub<?>> implements PsiExtensibleClass, Queryable {
    public static final Key<PsiClass> DELEGATE_KEY = Key.create("DELEGATE");

    private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

    public ClsClassImpl(PsiClassStub stub) {
        super(stub);
        putUserData(JavaEnumAugmentProvider.FLAG, Boolean.TRUE);
    }

    @Override
    @RequiredReadAction
    public PsiElement[] getChildren() {
        List<PsiElement> children = new ArrayList<>();
        ContainerUtil.addAll(
            children,
            getChildren(getDocComment(), getModifierListInternal(), getNameIdentifier(), getExtendsList(), getImplementsList())
        );
        ContainerUtil.addAll(children, getOwnFields());
        ContainerUtil.addAll(children, getOwnMethods());
        ContainerUtil.addAll(children, getOwnInnerClasses());
        return PsiUtilCore.toPsiElementArray(children);
    }

    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST)).getPsi();
    }

    @Override
    public boolean hasTypeParameters() {
        return PsiImplUtil.hasTypeParameters(this);
    }

    @Override
    @Nullable
    public String getQualifiedName() {
        return getStub().getQualifiedName();
    }

    private boolean isLocalClass() {
        PsiClassStub<?> stub = getStub();
        return stub instanceof PsiClassStubImpl classStub && classStub.isLocalClassInner();
    }

    private boolean isAnonymousClass() {
        PsiClassStub<?> stub = getStub();
        return stub instanceof PsiClassStubImpl classStub && classStub.isAnonymousInner();
    }

    private boolean isAnonymousOrLocalClass() {
        return isAnonymousClass() || isLocalClass();
    }

    @Override
    @Nullable
    public PsiModifierList getModifierList() {
        if (isAnonymousClass()) {
            return null;
        }
        return getModifierListInternal();
    }

    private PsiModifierList getModifierListInternal() {
        return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST)).getPsi();
    }

    @Override
    public boolean hasModifierProperty(String name) {
        return getModifierListInternal().hasModifierProperty(name);
    }

    @Override
    public PsiReferenceList getExtendsList() {
        return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST)).getPsi();
    }

    @Override
    public PsiReferenceList getImplementsList() {
        return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST)).getPsi();
    }

    @Override
    public @Nullable PsiReferenceList getPermitsList() {
        PsiClassReferenceListStub type = getStub().findChildStubByType(JavaStubElementTypes.PERMITS_LIST);
        return type == null ? null : type.getPsi();
    }

    @Override
    public PsiClassType[] getExtendsListTypes() {
        return PsiClassImplUtil.getExtendsListTypes(this);
    }

    @Override
    public PsiClassType[] getImplementsListTypes() {
        return PsiClassImplUtil.getImplementsListTypes(this);
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
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
            return PsiClass.EMPTY_ARRAY;
        }
        return PsiClassImplUtil.getSupers(this);
    }

    @Override
    public PsiClassType[] getSuperTypes() {
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
            return PsiClassType.EMPTY_ARRAY;
        }
        return PsiClassImplUtil.getSuperTypes(this);
    }

    @Override
    public PsiClass getContainingClass() {
        return getParent() instanceof PsiClass containingClass ? containingClass : null;
    }

    @Override
    public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        return PsiSuperMethodImplUtil.getVisibleSignatures(this);
    }

    @Override
    public PsiField[] getFields() {
        return myInnersCache.getFields();
    }

    @Override
    public PsiMethod[] getMethods() {
        return myInnersCache.getMethods();
    }

    @Override
    public PsiMethod[] getConstructors() {
        return myInnersCache.getConstructors();
    }

    @Override
    public PsiClass[] getInnerClasses() {
        return myInnersCache.getInnerClasses();
    }

    @Override
    public List<PsiField> getOwnFields() {
        return asList(getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
    }

    @Override
    public List<PsiMethod> getOwnMethods() {
        return asList(getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
    }

    @Override
    public List<PsiClass> getOwnInnerClasses() {
        PsiClass[] classes = getStub().getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
        if (classes.length == 0) {
            return Collections.emptyList();
        }

        int anonymousOrLocalClassesCount = 0;
        for (PsiClass aClass : classes) {
            if (aClass instanceof ClsClassImpl clsClass && clsClass.isAnonymousOrLocalClass()) {
                ++anonymousOrLocalClassesCount;
            }
        }
        if (anonymousOrLocalClassesCount == 0) {
            return asList(classes);
        }

        ArrayList<PsiClass> result = new ArrayList<>(classes.length - anonymousOrLocalClassesCount);
        for (PsiClass aClass : classes) {
            if (!(aClass instanceof ClsClassImpl clsClass) || !clsClass.isAnonymousOrLocalClass()) {
                result.add(aClass);
            }
        }
        return result;
    }

    @Override
    public PsiClassInitializer[] getInitializers() {
        return PsiClassInitializer.EMPTY_ARRAY;
    }

    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return PsiImplUtil.getTypeParameters(this);
    }

    @Override
    public PsiField[] getAllFields() {
        return PsiClassImplUtil.getAllFields(this);
    }

    @Override
    public PsiMethod[] getAllMethods() {
        return PsiClassImplUtil.getAllMethods(this);
    }

    @Override
    public PsiClass[] getAllInnerClasses() {
        return PsiClassImplUtil.getAllInnerClasses(this);
    }

    @Override
    public PsiField findFieldByName(String name, boolean checkBases) {
        return myInnersCache.findFieldByName(name, checkBases);
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
    public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
        return myInnersCache.findMethodsByName(name, checkBases);
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
        return myInnersCache.findInnerClassByName(name, checkBases);
    }

    @Override
    public boolean isDeprecated() {
        return getStub().isDeprecated() || PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    @RequiredReadAction
    public String getSourceFileName() {
        String sfn = getStub().getSourceFileName();
        return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
    }

    @RequiredReadAction
    private String obtainSourceFileNameFromClassFileName() {
        String name = getContainingFile().getName();
        int i = name.indexOf('$');
        if (i < 0) {
            i = name.indexOf('.');
            if (i < 0) {
                i = name.length();
            }
        }
        return name.substring(0, i) + ".java";
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
    public boolean isInterface() {
        return getStub().isInterface();
    }

    @Override
    public boolean isAnnotationType() {
        return getStub().isAnnotationType();
    }

    @Override
    public boolean isEnum() {
        return getStub().isEnum();
    }

    @Override
    public void appendMirrorText(int indentLevel, StringBuilder buffer) {
        appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);

        appendText(getModifierListInternal(), indentLevel, buffer);
        buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
        appendText(getNameIdentifier(), indentLevel, buffer, " ");
        appendText(getTypeParameterList(), indentLevel, buffer, " ");
        appendText(getExtendsList(), indentLevel, buffer, " ");
        appendText(getImplementsList(), indentLevel, buffer, " ");

        buffer.append('{');

        int newIndentLevel = indentLevel + getIndentSize();
        List<PsiField> fields = getOwnFields();
        List<PsiMethod> methods = getOwnMethods();
        List<PsiClass> classes = getOwnInnerClasses();

        if (!fields.isEmpty()) {
            goNextLine(newIndentLevel, buffer);

            for (int i = 0; i < fields.size(); i++) {
                PsiField field = fields.get(i);
                appendText(field, newIndentLevel, buffer);

                if (field instanceof ClsEnumConstantImpl) {
                    if (i < fields.size() - 1 && fields.get(i + 1) instanceof ClsEnumConstantImpl) {
                        buffer.append(", ");
                    }
                    else {
                        buffer.append(';');
                        if (i < fields.size() - 1) {
                            buffer.append('\n');
                            goNextLine(newIndentLevel, buffer);
                        }
                    }
                }
                else if (i < fields.size() - 1) {
                    goNextLine(newIndentLevel, buffer);
                }
            }
        }
        else if (isEnum() && methods.size() + classes.size() > 0) {
            goNextLine(newIndentLevel, buffer);
            buffer.append(";");
        }

        if (!methods.isEmpty()) {
            if (isEnum() || !fields.isEmpty()) {
                buffer.append('\n');
            }
            goNextLine(newIndentLevel, buffer);

            for (int i = 0; i < methods.size(); i++) {
                appendText(methods.get(i), newIndentLevel, buffer);

                if (i < methods.size() - 1) {
                    buffer.append('\n');
                    goNextLine(newIndentLevel, buffer);
                }
            }
        }

        if (!classes.isEmpty()) {
            if (fields.size() + methods.size() > 0) {
                buffer.append('\n');
            }
            goNextLine(newIndentLevel, buffer);

            for (int i = 0; i < classes.size(); i++) {
                appendText(classes.get(i), newIndentLevel, buffer);

                if (i < classes.size() - 1) {
                    buffer.append('\n');
                    goNextLine(newIndentLevel, buffer);
                }
            }
        }

        goNextLine(indentLevel, buffer);
        buffer.append('}');
    }

    @Override
    public void setMirror(TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, null);

        PsiClass mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

        setMirrorIfPresent(getDocComment(), mirror.getDocComment());

        PsiModifierList modifierList = getModifierList();
        if (modifierList != null && mirror.getModifierList() != null) {
            setMirror(modifierList, mirror.getModifierList());
        }
        if (mirror.getNameIdentifier() != null) {
            setMirrorChecked(getNameIdentifier(), mirror.getNameIdentifier());
        }
        if (mirror.getTypeParameterList() != null) {
            setMirrorChecked(getTypeParameterList(), mirror.getTypeParameterList());
        }
        if (mirror.getExtendsList() != null) {
            setMirrorChecked(getExtendsList(), mirror.getExtendsList());
        }
        if (mirror.getImplementsList() != null) {
            setMirrorChecked(getImplementsList(), mirror.getImplementsList());
        }

        if (mirror instanceof PsiExtensibleClass extMirror) {
            setMirrorsChecked(getOwnFields(), extMirror.getOwnFields());
            setMethodMirrorsChecked(getOwnMethods(), extMirror.getOwnMethods());
            //inner classes are sorted by decompiler by method lines, so it is necessary to resort
            setSortedMirrorsChecked(getOwnInnerClasses(), extMirror.getOwnInnerClasses(), Comparator.comparing(PsiClass::getName));
        }
        else {
            setMirrorsChecked(getOwnFields(), asList(mirror.getFields()));
            setMethodMirrorsChecked(getOwnMethods(), asList(mirror.getMethods()));
            //inner classes are sorted by decompiler by method lines, so it is necessary to resort
            setSortedMirrorsChecked(getOwnInnerClasses(), asList(mirror.getInnerClasses()), Comparator.comparing(PsiClass::getName));
        }
    }

    private static <T extends PsiElement> void setMirrorChecked(T stub, T mirror) {
        setMirror(stub, mirror);
    }

    private static <T extends PsiElement> void setMirrorsChecked(List<T> stubs, List<T> mirrors) {
        if (stubs.size() == mirrors.size()) {
            setMirrors(stubs, mirrors);
        }
    }

    private static void setMethodMirrorsChecked(List<PsiMethod> stubs, List<PsiMethod> mirrors) {
        if (stubs.size() == mirrors.size()) {
            setMirrors(stubs, mirrors);
        }

        // If the count of stubs and mirrors doesn't match,
        // it's probably because the default constructor (present in stubs) isn't present in the decompiled code (mirrors),
        // because it was removed by Fernflower's "high readability" mode.

        // If after removing all constructors from both stubs and mirrors, the count is still different, then we cannot help.
        final long nonConstructorStubCount = stubs.stream().filter(method -> !method.isConstructor()).count();
        final long nonConstructorMirrorCount = mirrors.stream().filter(method -> !method.isConstructor()).count();
        if (nonConstructorStubCount != nonConstructorMirrorCount) {
            return;
        }

        if (stubs.size() - 1 == mirrors.size()) {
            Predicate<PsiMethod> isSyntheticConstructor = (PsiMethod stubMethod) -> {
                final PsiClass containingClass = stubMethod.getContainingClass();
                if (containingClass == null) {
                    return false;
                }

                if (containingClass.isRecord()) {
                    return JavaPsiRecordUtil.isCanonicalConstructor(stubMethod);
                }
                else {
                    return isDefaultConstructor(stubMethod);
                }
            };

            final List<PsiMethod> stubsWithoutSyntheticConstructor = stubs.stream()
                .filter(isSyntheticConstructor.negate())
                .collect(Collectors.toList());

            setMirrors(stubsWithoutSyntheticConstructor, mirrors);
        }
    }

    private static boolean isDefaultConstructor(PsiMethod stubMethod) {
        if (!stubMethod.isConstructor()) {
            return false;
        }
        if (!stubMethod.getParameterList().isEmpty()) {
            return false;
        }
        return true;
    }

    private static <T extends PsiElement> void setSortedMirrorsChecked(List<T> stub,
                                                                       List<T> mirror,
                                                                       Comparator<? super T> comparator) {
        setMirrorsChecked(stub.stream().sorted(comparator).collect(Collectors.toList()),
                          mirror.stream().sorted(comparator).collect(Collectors.toList()));
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor javaElementVisitor) {
            javaElementVisitor.visitClass(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public String toString() {
        return "PsiClass:" + getName();
    }

    @Override
    @RequiredReadAction
    public boolean processDeclarations(
        PsiScopeProcessor processor,
        ResolveState state,
        PsiElement lastParent,
        PsiElement place
    ) {
        LanguageLevel level =
            processor instanceof MethodsProcessor methodsProcessor ? methodsProcessor.getLanguageLevel() : PsiUtil.getLanguageLevel(place);
        return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
    }

    @Override
    public PsiElement getScope() {
        return getParent();
    }

    @Override
    public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
        return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
    }

    @Override
    public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
        return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
    }

    @Nullable
    @RequiredReadAction
    public PsiClass getSourceMirrorClass() {
        PsiClass delegate = getUserData(DELEGATE_KEY);
        if (delegate instanceof ClsClassImpl clsClass) {
            return clsClass.getSourceMirrorClass();
        }

        String name = getName();
        PsiElement parent = getParent();
        if (parent instanceof PsiFile) {
            if (!(parent instanceof PsiClassOwner classOwner)) {
                return null;
            }

            PsiClassOwner fileNavigationElement = (PsiClassOwner)classOwner.getNavigationElement();
            if (fileNavigationElement == parent) {
                return null;
            }

            for (PsiClass aClass : fileNavigationElement.getClasses()) {
                if (name.equals(aClass.getName())) {
                    return aClass;
                }
            }
        }
        else if (parent != null) {
            ClsClassImpl parentClass = (ClsClassImpl)parent;
            PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
            if (parentSourceMirror == null) {
                return null;
            }
            PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
            for (PsiClass innerClass : innerClasses) {
                if (name.equals(innerClass.getName())) {
                    return innerClass;
                }
            }
        }
        else {
            throw new PsiInvalidElementAccessException(this);
        }

        return null;
    }

    @Override
    @RequiredReadAction
    public PsiElement getNavigationElement() {
        for (ClsCustomNavigationPolicy navigationPolicy : ClsCustomNavigationPolicy.EP_NAME.getExtensions()) {
            try {
                PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
                if (navigationElement != null) {
                    return navigationElement;
                }
            }
            catch (IndexNotReadyException ignored) {
            }
        }

        try {
            PsiClass aClass = getSourceMirrorClass();
            if (aClass != null) {
                return aClass.getNavigationElement();
            }

            if ("package-info".equals(getName())) {
                if (getParent() instanceof ClsFileImpl clsFile && clsFile.getNavigationElement() instanceof PsiJavaFile javaFile) {
                    return javaFile;
                }
            }
        }
        catch (IndexNotReadyException ignore) {
        }

        return this;
    }

    @Override
    public ItemPresentation getPresentation() {
        return ItemPresentationProvider.getItemPresentation(this);
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
        return PsiClassImplUtil.isClassEquivalentTo(this, another);
    }

    @Override
    public SearchScope getUseScope() {
        return PsiClassImplUtil.getClassUseScope(this);
    }

    @Override
    public void putInfo(Map<String, String> info) {
        PsiClassImpl.putInfo(this, info);
    }

    @Override
    public PsiRecordComponent[] getRecordComponents() {
        PsiRecordHeader header = getRecordHeader();
        return header == null ? PsiRecordComponent.EMPTY_ARRAY : header.getRecordComponents();
    }

    @Nullable
    @Override
    public PsiRecordHeader getRecordHeader() {
        PsiRecordHeaderStub headerStub = getStub().findChildStubByType(JavaStubElementTypes.RECORD_HEADER);
        return headerStub == null ? null : headerStub.getPsi();
    }
}