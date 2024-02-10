// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionState;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.highlight.ReadWriteAccessDetector;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiAnchor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.*;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RefCountHolder {
  private final PsiFile myFile;
  // resolved elements -> list of their references in this file
  private final
  @Nonnull
  MultiMap<PsiElement, PsiReference> myLocalRefsMap;

  private final Set<PsiAnchor> myDclsUsedMap;
  // reference -> import statement the reference has come from
  private final Map<PsiReference, PsiImportStatementBase> myImportStatements;

  private static final Key<Reference<RefCountHolder>> REF_COUNT_HOLDER_IN_FILE_KEY = Key.create("REF_COUNT_HOLDER_IN_FILE_KEY");
  private volatile boolean ready; // true when analysis completed and inner maps can be queried

  static RefCountHolder get(@Nonnull PsiFile file, @Nonnull TextRange dirtyScope) {
    Reference<RefCountHolder> ref = file.getUserData(REF_COUNT_HOLDER_IN_FILE_KEY);
    RefCountHolder storedHolder = consulo.util.lang.ref.SoftReference.dereference(ref);
    boolean wholeFile = dirtyScope.equals(file.getTextRange());
    if (storedHolder == null && !wholeFile) {
      // RefCountHolder was GCed and queried for subrange of the file, can't return anything meaningful
      return null;
    }
    return storedHolder == null || wholeFile ?
        new RefCountHolder(file, MultiMap.createConcurrentSet(), Sets.newConcurrentHashSet(HashingStrategy.canonical()), new ConcurrentHashMap<>())
        : storedHolder.removeInvalidRefs();
  }

  void storeReadyHolder(@Nonnull PsiFile file) {
    ready = true;
    file.putUserData(REF_COUNT_HOLDER_IN_FILE_KEY, new SoftReference<>(this));
  }

  private RefCountHolder(@Nonnull PsiFile file,
                         @Nonnull MultiMap<PsiElement, PsiReference> myLocalRefsMap,
                         @Nonnull Set<PsiAnchor> myDclsUsedMap,
                         @Nonnull Map<PsiReference, PsiImportStatementBase> myImportStatements) {
    myFile = file;
    this.myLocalRefsMap = myLocalRefsMap;
    this.myDclsUsedMap = myDclsUsedMap;
    this.myImportStatements = myImportStatements;
    log("c: created for ", file);
  }

  @Nonnull
  GlobalUsageHelper getGlobalUsageHelper(@Nonnull PsiFile file,
                                         @Nullable UnusedDeclarationInspectionBase deadCodeInspection,
                                         UnusedDeclarationInspectionState deadCodeState) {
    FileViewProvider viewProvider = file.getViewProvider();
    Project project = file.getProject();

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    boolean inLibrary = fileIndex.isInLibrary(virtualFile);

    boolean isDeadCodeEnabled = deadCodeInspection != null && deadCodeInspection.isGlobalEnabledInEditor();
    if (isDeadCodeEnabled && !inLibrary) {
      return new GlobalUsageHelperBase() {
        final Map<PsiMember, Boolean> myEntryPointCache = FactoryMap.create((PsiMember member) -> {
          if (deadCodeInspection.isEntryPoint(member, deadCodeState)) return true;
          if (member instanceof PsiClass) {
            return !JBTreeTraverser
                .<PsiMember>from(m -> m instanceof PsiClass
                    ? JBIterable.from(PsiTreeUtil.getStubChildrenOfTypeAsList(m, PsiMember.class))
                    : JBIterable.empty())
                .withRoot(member)
                .traverse()
                .skip(1)
                .processEach(this::shouldCheckUsages);
          }
          return false;
        });

        @Override
        public boolean shouldCheckUsages(@Nonnull PsiMember member) {
          return !myEntryPointCache.get(member);
        }
      };
    }
    return new GlobalUsageHelperBase();
  }

  void registerLocallyReferenced(@Nonnull PsiNamedElement result) {
    myDclsUsedMap.add(PsiAnchor.create(result));
  }

  void registerReference(@Nonnull PsiReference ref, @Nonnull JavaResolveResult resolveResult) {
    PsiElement refElement = resolveResult.getElement();
    PsiFile psiFile = refElement == null ? null : refElement.getContainingFile();
    if (psiFile != null)
      psiFile = (PsiFile) psiFile.getNavigationElement(); // look at navigation elements because all references resolve into Cls elements when highlighting library source
    if (refElement != null && psiFile != null && myFile.getViewProvider().equals(psiFile.getViewProvider())) {
      registerLocalRef(ref, refElement.getNavigationElement());
    }

    PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStatementBase) {
      registerImportStatement(ref, (PsiImportStatementBase) resolveScope);
    } else if (refElement == null && ref instanceof PsiJavaReference) {
      for (JavaResolveResult result : ((PsiJavaReference) ref).multiResolve(true)) {
        resolveScope = result.getCurrentFileResolveScope();
        if (resolveScope instanceof PsiImportStatementBase) {
          registerImportStatement(ref, (PsiImportStatementBase) resolveScope);
          break;
        }
      }
    }
  }

  private void registerImportStatement(@Nonnull PsiReference ref, @Nonnull PsiImportStatementBase importStatement) {
    myImportStatements.put(ref, importStatement);
  }

  boolean isRedundant(@Nonnull PsiImportStatementBase importStatement) {
    assert ready;
    return !myImportStatements.containsValue(importStatement);
  }

  private void registerLocalRef(@Nonnull PsiReference ref, PsiElement refElement) {
    PsiElement element = ref.getElement();
    if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, element, true))
      return; // filter self-recursive calls
    if (refElement instanceof PsiClass) {
      if (PsiTreeUtil.isAncestor(refElement, element, true)) {
        return; // filter inner use of itself
      }
    }
    myLocalRefsMap.putValue(refElement, ref);
  }

  @Nonnull
  private RefCountHolder removeInvalidRefs() {
    assert ready;
    boolean changed = false;
    MultiMap<PsiElement, PsiReference> newLocalRefsMap = MultiMap.createConcurrentSet();
    for (Map.Entry<PsiElement, Collection<PsiReference>> entry : myLocalRefsMap.entrySet()) {
      PsiElement element = entry.getKey();
      for (PsiReference ref : entry.getValue()) {
        if (ref.getElement().isValid()) {
          newLocalRefsMap.putValue(element, ref);
        } else {
          changed = true;
        }
      }
    }
    Set<PsiAnchor> newDclsUsedMap = Sets.newConcurrentHashSet(HashingStrategy.canonical());
    for (PsiAnchor element : myDclsUsedMap) {
      if (element.retrieve() != null) {
        newDclsUsedMap.add(element);
      } else {
        changed = true;
      }
    }
    Map<PsiReference, PsiImportStatementBase> newImportStatements = new ConcurrentHashMap<>();
    for (Map.Entry<PsiReference, PsiImportStatementBase> entry : myImportStatements.entrySet()) {
      PsiReference key = entry.getKey();
      PsiImportStatementBase value = entry.getValue();
      if (value.isValid() && key.getElement().isValid()) {
        newImportStatements.put(key, value);
      } else {
        changed = true;
      }
    }
    return changed ? new RefCountHolder(myFile, newLocalRefsMap, newDclsUsedMap, newImportStatements) : this;
  }

  @RequiredReadAction
  boolean isReferenced(@Nonnull PsiElement element) {
    assert ready;
    Collection<PsiReference> array = myLocalRefsMap.get(element);
    if (!array.isEmpty() &&
        !isParameterUsedRecursively(element, array) &&
        !isClassUsedForInnerImports(element, array)) {
      for (PsiReference reference : array) {
        if (reference.isReferenceTo(element)) return true;
      }
    }

    return myDclsUsedMap.contains(PsiAnchor.create(element));
  }

  private boolean isClassUsedForInnerImports(@Nonnull PsiElement element, @Nonnull Collection<? extends PsiReference> array) {
    assert ready;
    if (!(element instanceof PsiClass)) return false;

    Set<PsiImportStatementBase> imports = new HashSet<>();
    for (PsiReference classReference : array) {
      PsiImportStatementBase importStmt = PsiTreeUtil.getParentOfType(classReference.getElement(), PsiImportStatementBase.class);
      if (importStmt == null) return false;
      imports.add(importStmt);
    }

    return ContainerUtil.all(imports, importStmt -> {
      PsiElement importedMember = importStmt.resolve();
      if (importedMember != null && PsiTreeUtil.isAncestor(element, importedMember, false)) {
        for (PsiReference memberReference : myLocalRefsMap.get(importedMember)) {
          if (!PsiTreeUtil.isAncestor(element, memberReference.getElement(), false)) {
            return false;
          }
        }
        return true;
      }
      return false;
    });
  }

  @RequiredReadAction
  private static boolean isParameterUsedRecursively(@Nonnull PsiElement element, @Nonnull Collection<? extends PsiReference> array) {
    if (!(element instanceof PsiParameter)) return false;
    PsiParameter parameter = (PsiParameter) element;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod) scope;
    int paramIndex = ArrayUtil.find(method.getParameterList().getParameters(), parameter);

    for (PsiReference reference : array) {
      if (!(reference instanceof PsiElement)) return false;
      PsiElement argument = (PsiElement) reference;

      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) new PsiMatcherImpl(argument)
          .dot(PsiMatchers.hasClass(PsiReferenceExpression.class))
          .parent(PsiMatchers.hasClass(PsiExpressionList.class))
          .parent(PsiMatchers.hasClass(PsiMethodCallExpression.class))
          .getElement();
      if (methodCallExpression == null) return false;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (method != methodExpression.resolve()) return false;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      int argumentIndex = ArrayUtil.find(arguments, argument);
      if (paramIndex != argumentIndex) return false;
    }

    return true;
  }

  @RequiredReadAction
  boolean isReferencedForRead(@Nonnull PsiVariable variable) {
    assert ready;
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement refElement = ref.getElement();
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access != null && access.isReferencedForRead()) {
          if (isJustIncremented(access, refElement)) continue;
          return true;
        }
      }
    }
    return false;
  }

  @RequiredReadAction
  private static ReadWriteAccessDetector.Access getAccess(@Nonnull PsiReference ref, @Nonnull PsiElement resolved) {
    PsiElement start = resolved.getLanguage() == ref.getElement().getLanguage() ? resolved : ref.getElement();
    ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(start);
    if (detector != null) {
      return detector.getReferenceAccess(resolved, ref);
    }
    return null;
  }

  // "var++;"
  private static boolean isJustIncremented(@Nonnull ReadWriteAccessDetector.Access access, @Nonnull PsiElement refElement) {
    return access == ReadWriteAccessDetector.Access.ReadWrite &&
        refElement instanceof PsiExpression &&
        refElement.getParent() instanceof PsiExpression &&
        refElement.getParent().getParent() instanceof PsiExpressionStatement;
  }

  @RequiredReadAction
  boolean isReferencedForWrite(@Nonnull PsiVariable variable) {
    assert ready;
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access != null && access.isReferencedForWrite()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void log(@NonNls @Nonnull Object... info) {
    //FileStatusMap.log(info);
  }

  private class GlobalUsageHelperBase extends GlobalUsageHelper {
    @Override
    public boolean shouldCheckUsages(@Nonnull PsiMember member) {
      return false;
    }

    @Override
    public boolean isCurrentFileAlreadyChecked() {
      return true;
    }

    @Override
    public boolean isLocallyUsed(@Nonnull PsiNamedElement member) {
      return isReferenced(member);
    }
  }
}
