/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.*;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassInitializerStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.Queryable;
import consulo.content.scope.SearchScope;
import consulo.java.language.impl.psi.augment.JavaEnumAugmentProvider;
import consulo.java.language.impl.psi.stub.PsiClassLevelDeclarationStatementStub;
import consulo.language.ast.ASTNode;
import consulo.language.impl.DebugUtil;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.PsiFileStub;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PsiClassImpl extends JavaStubPsiElement<PsiClassStub<?>> implements PsiExtensibleClass, PsiQualifiedNamedElement, Queryable {
  private static final Logger LOG = Logger.getInstance(PsiClassImpl.class);

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);
  private volatile String myCachedName;

  public PsiClassImpl(final PsiClassStub stub) {
    this(stub, JavaStubElementTypes.CLASS);
  }

  protected PsiClassImpl(final PsiClassStub stub, final IStubElementType type) {
    super(stub, type);
    putUserData(JavaEnumAugmentProvider.FLAG, Boolean.TRUE);
    addTrace(null);
  }

  public PsiClassImpl(final ASTNode node) {
    super(node);
    putUserData(JavaEnumAugmentProvider.FLAG, Boolean.TRUE);
    addTrace(null);
  }

  private void addTrace(@Nullable PsiClassStub stub) {
    if (ourTraceStubAstBinding) {
      String creationTrace = "Creation thread: " + Thread.currentThread() + "\n" + DebugUtil.currentStackTrace();
      if (stub != null) {
        creationTrace += "\nfrom stub " + stub + "@" + System.identityHashCode(stub) + "\n";
        if (stub instanceof UserDataHolder) {
          String stubTrace = ((UserDataHolder) stub).getUserData(CREATION_TRACE);
          if (stubTrace != null) {
            creationTrace += stubTrace;
          }
        }
      }
      putUserData(CREATION_TRACE, creationTrace);
    }
  }

  @Override
  public void subtreeChanged() {
    dropCaches();
    super.subtreeChanged();
  }

  private void dropCaches() {
    myInnersCache.dropCaches();
    myCachedName = null;
  }

  @Override
  protected Object clone() {
    PsiClassImpl clone = (PsiClassImpl) super.clone();
    clone.dropCaches();
    return clone;
  }

  @Override
  public PsiElement getOriginalElement() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<PsiClass>() {
      @Nullable
      @Override
      public Result<PsiClass> compute() {
        final JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(getProject());
        final PsiClass result = helper != null ? helper.getOriginalClass(PsiClassImpl.this) : PsiClassImpl.this;
        return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
  }

  @Override
  @Nonnull
  public CompositeElement getNode() {
    return (CompositeElement) super.getNode();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier) getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  public PsiElement getScope() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    ASTNode treeElement = getNode();
    ASTNode parent = treeElement.getTreeParent();

    while (parent != null) {
      if (parent.getElementType() instanceof IStubElementType) {
        return parent.getPsi();
      }
      parent = parent.getTreeParent();
    }

    return getContainingFile();
  }

  @Override
  public String getName() {
    String name = myCachedName;
    if (name != null) {
      return name;
    }
    final PsiClassStub stub = getGreenStub();
    if (stub == null) {
      PsiIdentifier identifier = getNameIdentifier();
      name = identifier == null ? null : identifier.getText();
    } else {
      name = stub.getName();
    }
    myCachedName = name;
    return name;
  }

  @Override
  public String getQualifiedName() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof PsiJavaFile) {
      return StringUtil.getQualifiedName(((PsiJavaFile) parent).getPackageName(), getName());
    }

    if (parent instanceof PsiClass) {
      String parentQName = ((PsiClass) parent).getQualifiedName();
      if (parentQName == null) {
        return null;
      }
      return StringUtil.getQualifiedName(parentQName, getName());
    }

    if (parent instanceof PsiClassLevelDeclarationStatement) {
      PsiElement context = parent.getContext();
      if (context instanceof PsiClass) {
        String parentQName = ((PsiClass) context).getQualifiedName();
        if (parentQName == null) {
          return null;
        }
        return StringUtil.getQualifiedName(parentQName, getName());
      }
    }

    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    final PsiModifierList modlist = getModifierList();
    return modlist != null && modlist.hasModifierProperty(name);
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return getStubOrPsiChild(JavaStubElementTypes.EXTENDS_LIST);
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return getStubOrPsiChild(JavaStubElementTypes.IMPLEMENTS_LIST);
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
  public @Nullable PsiReferenceList getPermitsList() {
    return getStubOrPsiChild(JavaStubElementTypes.PERMITS_LIST);
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
  @Nonnull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    final PsiClassStub<?> stub = getGreenStub();
    if (stub != null) {
      StubElement<?> parent = stub.getParentStub();
      if (parent instanceof PsiClassLevelDeclarationStatementStub) {
        return parent.getParentStubOfType(PsiSyntheticClass.class);
      } else if (parent instanceof PsiClassStub) {
        return (PsiClass) parent.getPsi();
      }
      return null;
    }

    PsiElement parent = getParent();

    if (parent instanceof PsiClassLevelDeclarationStatement) {
      return PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
    }

    return parent instanceof PsiClass ? (PsiClass) parent : null;
  }

  @Override
  public PsiElement getContext() {
    StubElement contextStub = getContextStub();
    if (contextStub != null) {
      return contextStub.getPsi();
    }

    return super.getContext();
  }

  @Nullable
  private StubElement getContextStub() {
    PsiClassStub<?> stub = getStub();
    if (stub == null) {
      return null;
    }

    // if AST is not loaded, then we only can need context to resolve supertype references
    // this can be done by stubs unless there are local/anonymous classes referencing other local classes
    StubElement parent = stub.getParentStub();
    if (parent instanceof PsiClassInitializerStub || parent instanceof PsiMethodStub) {
      if (parent.getChildrenByType(JavaStubElementTypes.CLASS, PsiElement.ARRAY_FACTORY).length <= 1) {
        parent = parent.getParentStub();
      }
    }
    return parent instanceof PsiClassStub ? parent : null;
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
    return Arrays.asList(getStubOrPsiChildren(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @Nonnull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return Arrays.asList(getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @Nonnull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    return Arrays.asList(getStubOrPsiChildren(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY));
  }

  @Override
  @Nonnull
  public PsiClassInitializer[] getInitializers() {
    return getStubOrPsiChildren(JavaStubElementTypes.CLASS_INITIALIZER, PsiClassInitializer.ARRAY_FACTORY);
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
  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public boolean isDeprecated() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiDocComment getDocComment() {
    return (PsiDocComment) getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public PsiJavaToken getLBrace() {
    return (PsiJavaToken) getNode().findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return (PsiJavaToken) getNode().findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  @Override
  public boolean isInterface() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isInterface();
    }

    ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.INTERFACE_KEYWORD;
  }

  @Override
  public boolean isAnnotationType() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isAnnotationType();
    }

    return getNode().findChildByRole(ChildRole.AT) != null;
  }

  @Override
  public boolean isEnum() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isEnum();
    }

    final ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.ENUM_KEYWORD;
  }

  @Override
  public boolean isRecord() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isRecord();
    }

    final ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.RECORD_KEYWORD;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitClass(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    LanguageLevel level = PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @Override
  public PsiElement setName(@Nonnull String newName) throws IncorrectOperationException {
    String oldName = getName();
    boolean isRenameFile = isRenameFileOnRenaming();

    PsiImplUtil.setName(getNameIdentifier(), newName);

    if (isRenameFile) {
      PsiFile file = (PsiFile) getParent();
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      file.setName(dotIndex >= 0 ? newName + "." + fileName.substring(dotIndex + 1) : newName);
    }

    // rename constructors
    for (PsiMethod method : getConstructors()) {
      if (method.getName().equals(oldName)) {
        method.setName(newName);
      }
    }

    return this;
  }

  private boolean isRenameFileOnRenaming() {
    final PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      PsiFile file = (PsiFile) parent;
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      String name = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
      String oldName = getName();
      return name.equals(oldName);
    } else {
      return false;
    }
  }

  // optimization to not load tree when resolving bases of anonymous and locals
  // if there is no local classes with such name in scope it's possible to use outer scope as context
  @Nullable
  public PsiElement calcBasesResolveContext(String baseClassName, final PsiElement defaultResolveContext) {
    return calcBasesResolveContext(this, baseClassName, true, defaultResolveContext);
  }

  private static boolean isAnonymousOrLocal(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return true;
    }

    final PsiClassStub stub = ((PsiClassImpl) aClass).getGreenStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      return !(parentStub instanceof PsiClassStub || parentStub instanceof PsiFileStub);
    }

    PsiElement parent = aClass.getParent();
    while (parent != null) {
      if (parent instanceof PsiMethod || parent instanceof PsiField || parent instanceof PsiClassInitializer) {
        return true;
      }
      if (parent instanceof PsiClass || parent instanceof PsiFile) {
        return false;
      }
      parent = parent.getParent();
    }

    return false;
  }

  @Nullable
  private static PsiElement calcBasesResolveContext(@Nonnull PsiElement scope, String baseClassName, boolean isInitialClass, final PsiElement defaultResolveContext) {
    final StubElement stub = scope instanceof StubBasedPsiElementBase ? ((StubBasedPsiElementBase<?>) scope).getStub() : null;
    if (stub == null || stub instanceof PsiClassStub && ((PsiClassStub) stub).isAnonymousInQualifiedNew()) {
      return scope.getParent();
    }

    if (scope instanceof PsiClass) {
      if (!isAnonymousOrLocal((PsiClass) scope)) {
        return isInitialClass ? defaultResolveContext : scope;
      }

      if (!isInitialClass) {
        if (((PsiClass) scope).findInnerClassByName(baseClassName, true) != null) {
          return scope;
        }
      }
    }

    final StubElement parentStub = stub.getParentStub();
    PsiElement psi = parentStub.getPsi();
    if (!(psi instanceof StubBasedPsiElementBase)) {
      LOG.error(stub + " parent is " + parentStub);
      return null;
    }

    if (hasChildClassStub(parentStub, baseClassName, scope)) {
      return scope.getParent();
    }

    if (psi instanceof PsiClass || psi instanceof PsiFunctionalExpression) {
      return calcBasesResolveContext(psi, baseClassName, false, defaultResolveContext);
    }
    if (psi instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember) psi).getContainingClass();
      return containingClass != null ? calcBasesResolveContext(containingClass, baseClassName, false, defaultResolveContext) : psi;
    }
    LOG.error(parentStub);
    return psi;
  }

  private static boolean hasChildClassStub(StubElement parentStub, String className, PsiElement place) {
    PsiClass[] classesInScope = (PsiClass[]) parentStub.getChildrenByType(Constants.CLASS_BIT_SET, PsiClass.ARRAY_FACTORY);

    for (PsiClass scopeClass : classesInScope) {
      if (scopeClass == place) {
        continue;
      }
      if (className.equals(scopeClass.getName())) {
        return true;
      }
    }

    if (place instanceof PsiClass) {
      if (classesInScope.length == 0) {
        LOG.error("Parent stub: " + parentStub.getStubType() + "; children: " + parentStub.getChildrenStubs() + "; \ntext:" + parentStub.getPsi().getText());
      }
      LOG.assertTrue(Arrays.asList(classesInScope).contains(place));
    }
    return false;
  }

  @Override
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
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
    putInfo(this, info);
  }

  public static void putInfo(@Nonnull PsiClass psiClass, @Nonnull Map<String, String> info) {
    info.put("className", psiClass.getName());
    info.put("qualifiedClassName", psiClass.getQualifiedName());
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof Queryable) {
      ((Queryable) file).putInfo(info);
    }
  }

  @Override
  @Nonnull
  public PsiRecordComponent[] getRecordComponents() {
    return myInnersCache.getRecordComponents();
  }

  @Override
  @Nullable
  public PsiRecordHeader getRecordHeader() {
    return getStubOrPsiChild(JavaStubElementTypes.RECORD_HEADER);
  }
}