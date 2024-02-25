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
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.util.Queryable;
import consulo.component.extension.Extensions;
import consulo.content.scope.SearchScope;
import consulo.java.language.impl.psi.augment.JavaEnumAugmentProvider;
import consulo.java.language.module.util.JavaClassNames;
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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

import static consulo.util.lang.ObjectUtil.assertNotNull;
import static java.util.Arrays.asList;

public class ClsClassImpl extends ClsMemberImpl<PsiClassStub<?>> implements PsiExtensibleClass, Queryable {
  public static final Key<PsiClass> DELEGATE_KEY = Key.create("DELEGATE");

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

  public ClsClassImpl(final PsiClassStub stub) {
    super(stub);
    putUserData(JavaEnumAugmentProvider.FLAG, Boolean.TRUE);
  }

  @RequiredReadAction
  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    List<PsiElement> children = ContainerUtil.newArrayList();
    ContainerUtil.addAll(children, getChildren(getDocComment(), getModifierListInternal(), getNameIdentifier(), getExtendsList(), getImplementsList()));
    ContainerUtil.addAll(children, getOwnFields());
    ContainerUtil.addAll(children, getOwnMethods());
    ContainerUtil.addAll(children, getOwnInnerClasses());
    return PsiUtilCore.toPsiElementArray(children);
  }

  @Override
  @Nonnull
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
    return stub instanceof PsiClassStubImpl && ((PsiClassStubImpl) stub).isLocalClassInner();
  }

  private boolean isAnonymousClass() {
    PsiClassStub<?> stub = getStub();
    return stub instanceof PsiClassStubImpl && ((PsiClassStubImpl) stub).isAnonymousInner();
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
  public boolean hasModifierProperty(@Nonnull String name) {
    return getModifierListInternal().hasModifierProperty(name);
  }

  @Override
  @Nonnull
  public PsiReferenceList getExtendsList() {
    return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_LIST)).getPsi();
  }

  @Override
  @Nonnull
  public PsiReferenceList getImplementsList() {
    return assertNotNull(getStub().findChildStubByType(JavaStubElementTypes.IMPLEMENTS_LIST)).getPsi();
  }

  @Override
  public @Nullable PsiReferenceList getPermitsList() {
    PsiClassReferenceListStub type = getStub().findChildStubByType(JavaStubElementTypes.PERMITS_LIST);
    return type == null ? null : type.getPsi();
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Nonnull
  @Override
  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  @Nonnull
  public PsiClass[] getSupers() {
    if (JavaClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
      return PsiClass.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    if (JavaClassNames.JAVA_LANG_OBJECT.equals(getQualifiedName())) {
      return PsiClassType.EMPTY_ARRAY;
    }
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass) parent : null;
  }

  @Override
  @Nonnull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  @Nonnull
  public PsiField[] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  @Nonnull
  public PsiMethod[] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  @Nonnull
  public PsiMethod[] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @Override
  @Nonnull
  public PsiClass[] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @Nonnull
  @Override
  public List<PsiField> getOwnFields() {
    return asList(getStub().getChildrenByType(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @Nonnull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return asList(getStub().getChildrenByType(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @Nonnull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    PsiClass[] classes = getStub().getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
    if (classes.length == 0) {
      return Collections.emptyList();
    }

    int anonymousOrLocalClassesCount = 0;
    for (PsiClass aClass : classes) {
      if (aClass instanceof ClsClassImpl && ((ClsClassImpl) aClass).isAnonymousOrLocalClass()) {
        ++anonymousOrLocalClassesCount;
      }
    }
    if (anonymousOrLocalClassesCount == 0) {
      return asList(classes);
    }

    ArrayList<PsiClass> result = new ArrayList<>(classes.length - anonymousOrLocalClassesCount);
    for (PsiClass aClass : classes) {
      if (!(aClass instanceof ClsClassImpl) || !((ClsClassImpl) aClass).isAnonymousOrLocalClass()) {
        result.add(aClass);
      }
    }
    return result;
  }

  @Override
  @Nonnull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  @Nonnull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  @Nonnull
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
  @Nonnull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
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

  public String getSourceFileName() {
    final String sfn = getStub().getSourceFileName();
    return sfn != null ? sfn : obtainSourceFileNameFromClassFileName();
  }

  @NonNls
  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
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
  public void appendMirrorText(final int indentLevel, @Nonnull @NonNls final StringBuilder buffer) {
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
          } else {
            buffer.append(';');
            if (i < fields.size() - 1) {
              buffer.append('\n');
              goNextLine(newIndentLevel, buffer);
            }
          }
        } else if (i < fields.size() - 1) {
          goNextLine(newIndentLevel, buffer);
        }
      }
    } else if (isEnum() && methods.size() + classes.size() > 0) {
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
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiClass mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirrorIfPresent(getDocComment(), mirror.getDocComment());

    PsiModifierList modifierList = getModifierList();
    if (modifierList != null) {
      setMirror(modifierList, mirror.getModifierList());
    }
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
    setMirror(getTypeParameterList(), mirror.getTypeParameterList());
    setMirror(getExtendsList(), mirror.getExtendsList());
    setMirror(getImplementsList(), mirror.getImplementsList());

    if (mirror instanceof PsiExtensibleClass) {
      PsiExtensibleClass extMirror = (PsiExtensibleClass) mirror;
      setMirrors(getOwnFields(), extMirror.getOwnFields());
      setMirrors(getOwnMethods(), extMirror.getOwnMethods());
      setMirrors(getOwnInnerClasses(), extMirror.getOwnInnerClasses());
    } else {
      setMirrors(getOwnFields(), asList(mirror.getFields()));
      setMirrors(getOwnMethods(), asList(mirror.getMethods()));
      setMirrors(getOwnInnerClasses(), asList(mirror.getInnerClasses()));
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitClass(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    LanguageLevel level = processor instanceof MethodsProcessor ? ((MethodsProcessor) processor).getLanguageLevel() : PsiUtil.getLanguageLevel(place);
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
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Nullable
  public PsiClass getSourceMirrorClass() {
    final PsiClass delegate = getUserData(DELEGATE_KEY);
    if (delegate instanceof ClsClassImpl) {
      return ((ClsClassImpl) delegate).getSourceMirrorClass();
    }

    final String name = getName();
    final PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      if (!(parent instanceof PsiClassOwner)) {
        return null;
      }

      PsiClassOwner fileNavigationElement = (PsiClassOwner) parent.getNavigationElement();
      if (fileNavigationElement == parent) {
        return null;
      }

      for (PsiClass aClass : fileNavigationElement.getClasses()) {
        if (name.equals(aClass.getName())) {
          return aClass;
        }
      }
    } else if (parent != null) {
      ClsClassImpl parentClass = (ClsClassImpl) parent;
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
    } else {
      throw new PsiInvalidElementAccessException(this);
    }

    return null;
  }

  @Override
  @Nonnull
  public PsiElement getNavigationElement() {
    for (ClsCustomNavigationPolicy navigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
      try {
        PsiElement navigationElement = navigationPolicy.getNavigationElement(this);
        if (navigationElement != null) {
          return navigationElement;
        }
      } catch (IndexNotReadyException ignored) {
      }
    }

    try {
      PsiClass aClass = getSourceMirrorClass();
      if (aClass != null) {
        return aClass.getNavigationElement();
      }

      if ("package-info".equals(getName())) {
        PsiElement parent = getParent();
        if (parent instanceof ClsFileImpl) {
          PsiElement sourceFile = parent.getNavigationElement();
          if (sourceFile instanceof PsiJavaFile) {
            return sourceFile;
          }
        }
      }
    } catch (IndexNotReadyException ignore) {
    }

    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public void putInfo(@Nonnull Map<String, String> info) {
    PsiClassImpl.putInfo(this, info);
  }

  @Override
  @Nonnull
  public PsiRecordComponent[] getRecordComponents() {
    PsiRecordHeader header = getRecordHeader();
    return header == null ? PsiRecordComponent.EMPTY_ARRAY : header.getRecordComponents();
  }

  @Override
  public
  @Nullable
  PsiRecordHeader getRecordHeader() {
    PsiRecordHeaderStub headerStub = getStub().findChildStubByType(JavaStubElementTypes.RECORD_HEADER);
    return headerStub == null ? null : headerStub.getPsi();
  }
}