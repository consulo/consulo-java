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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.PsiElementRenameHandler;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.function.Consumer;

@ExtensionImpl(id = "javamethod")
public class RenameJavaMethodProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaMethodProcessor.class);

  public boolean canProcessElement(@Nonnull final PsiElement element) {
    return element instanceof PsiMethod;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiMethod method = (PsiMethod) psiElement;
    Set<PsiMethod> methodAndOverriders = new HashSet<PsiMethod>();
    Set<PsiClass> containingClasses = new HashSet<PsiClass>();
    LinkedHashSet<PsiElement> renamedReferences = new LinkedHashSet<PsiElement>();
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<MemberHidesOuterMemberUsageInfo>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<MemberHidesStaticImportUsageInfo>();

    methodAndOverriders.add(method);
    containingClasses.add(method.getContainingClass());

    // do actual rename of overriding/implementing methods and of references to all them
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof MemberHidesStaticImportUsageInfo) {
        staticImportHides.add((MemberHidesStaticImportUsageInfo) usage);
      } else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement) element;
        PsiMethod resolved = (PsiMethod) collidingRef.resolve();
        outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
      } else if (!(element instanceof PsiMethod)) {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        } else {
          ref = element.getReference();
        }
        if (ref instanceof PsiImportStaticReferenceElement && ((PsiImportStaticReferenceElement) ref).multiResolve(false).length > 1) {
          continue;
        }
        if (ref != null) {
          PsiElement e = processRef(ref, newName);
          if (e != null) {
            renamedReferences.add(e);
          }
        }
      } else {
        PsiMethod overrider = (PsiMethod) element;
        methodAndOverriders.add(overrider);
        containingClasses.add(overrider.getContainingClass());
      }
    }

    // do actual rename of method
    method.setName(newName);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethod) {
        ((PsiMethod) element).setName(newName);
      }
    }
    if (listener != null) {
      listener.elementRenamed(method);
    }

    for (PsiElement element : renamedReferences) {
      fixNameCollisionsWithInnerClassMethod(element, newName, methodAndOverriders, containingClasses,
          method.hasModifierProperty(PsiModifier.STATIC));
    }
    qualifyOuterMemberReferences(outerHides);
    qualifyStaticImportReferences(staticImportHides);
  }

  /**
   * handles rename of refs
   *
   * @param ref
   * @param newName
   * @return
   */
  @Nullable
  protected PsiElement processRef(PsiReference ref, String newName) {
    return ref.handleElementRename(newName);
  }

  private static void fixNameCollisionsWithInnerClassMethod(final PsiElement element, final String newName,
                                                            final Set<PsiMethod> methodAndOverriders, final Set<PsiClass> containingClasses,
                                                            final boolean isStatic) throws IncorrectOperationException {
    if (!(element instanceof PsiReferenceExpression)) return;
    PsiElement elem = ((PsiReferenceExpression) element).resolve();

    if (elem instanceof PsiMethod) {
      PsiMethod actualMethod = (PsiMethod) elem;
      if (!methodAndOverriders.contains(actualMethod)) {
        PsiClass outerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        while (outerClass != null) {
          if (containingClasses.contains(outerClass)) {
            qualifyMember(element, newName, outerClass, isStatic);
            break;
          }
          outerClass = PsiTreeUtil.getParentOfType(outerClass, PsiClass.class);
        }
      }
    }
  }

  @Nonnull
  public Collection<PsiReference> findReferences(final PsiElement element) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
    return MethodReferencesSearch.search((PsiMethod) element, projectScope, true).findAll();
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
    final PsiMethod methodToRename = (PsiMethod) element;
    findSubmemberHidesMemberCollisions(methodToRename, newName, result);
    findMemberHidesOuterMemberCollisions((PsiMethod) element, newName, result);
    findCollisionsAgainstNewName(methodToRename, newName, result);
    findHidingMethodWithOtherSignature(methodToRename, newName, result);
    final PsiClass containingClass = methodToRename.getContainingClass();
    if (containingClass != null) {
      final PsiMethod patternMethod = (PsiMethod) methodToRename.copy();
      try {
        patternMethod.setName(newName);
        final PsiMethod methodInBaseClass = containingClass.findMethodBySignature(patternMethod, true);
        if (methodInBaseClass != null && methodInBaseClass.getContainingClass() != containingClass) {
          if (methodInBaseClass.hasModifierProperty(PsiModifier.FINAL)) {
            result.add(new UnresolvableCollisionUsageInfo(methodInBaseClass, methodToRename) {
              @Override
              public String getDescription() {
                return "Renaming method will override final \"" + RefactoringUIUtil.getDescription(methodInBaseClass, true) + "\"";
              }
            });
          }
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void findHidingMethodWithOtherSignature(final PsiMethod methodToRename, final String newName, final List<UsageInfo> result) {
    final PsiClass containingClass = methodToRename.getContainingClass();
    if (containingClass != null) {
      final PsiMethod prototype = getPrototypeWithNewName(methodToRename, newName);
      if (prototype == null || containingClass.findMethodBySignature(prototype, true) != null) return;

      final PsiMethod[] methodsByName = containingClass.findMethodsByName(newName, true);
      if (methodsByName.length > 0) {

        for (UsageInfo info : result) {
          final PsiElement element = info.getElement();
          if (element instanceof PsiReferenceExpression) {
            if (((PsiReferenceExpression) element).resolve() == methodToRename) {
              final PsiMethodCallExpression copy = (PsiMethodCallExpression) JavaPsiFacade.getElementFactory(element.getProject())
                  .createExpressionFromText(element.getParent().getText(), element);
              final PsiReferenceExpression expression = (PsiReferenceExpression) processRef(copy.getMethodExpression(), newName);
              if (expression == null) continue;
              final JavaResolveResult resolveResult = expression.advancedResolve(true);
              final PsiMember resolveResultElement = (PsiMember) resolveResult.getElement();
              if (resolveResult.isValidResult() && resolveResultElement != null) {
                result.add(new UnresolvableCollisionUsageInfo(element, methodToRename) {
                  @Override
                  public String getDescription() {
                    return "Method call would be linked to \"" + RefactoringUIUtil.getDescription(resolveResultElement, true) +
                        "\" after rename";
                  }
                });
                break;
              }
            }
          }
        }
      }
    }
  }

  private static PsiMethod getPrototypeWithNewName(PsiMethod methodToRename, String newName) {
    final PsiMethod prototype = (PsiMethod) methodToRename.copy();
    try {
      prototype.setName(newName);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
    return prototype;
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final MultiMap<PsiElement, String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiMethod refactoredMethod = (PsiMethod) element;
    if (newName.equals(refactoredMethod.getName())) return;
    final PsiMethod prototype = getPrototypeWithNewName(refactoredMethod, newName);
    if (prototype == null) return;

    ConflictsUtil.checkMethodConflicts(
        refactoredMethod.getContainingClass(),
        refactoredMethod,
        prototype,
        conflicts);
  }

  @Override
  public void prepareRenaming(PsiElement element, final String newName, final Map<PsiElement, String> allRenames, SearchScope scope) {
    final PsiMethod method = (PsiMethod) element;
    OverridingMethodsSearch.search(method, scope, true).forEach(new Processor<PsiMethod>() {
      public boolean process(PsiMethod overrider) {
        if (overrider instanceof PsiMirrorElement) {
          final PsiElement prototype = ((PsiMirrorElement) overrider).getPrototype();
          if (prototype instanceof PsiMethod) {
            overrider = (PsiMethod) prototype;
          }
        }

        if (overrider instanceof SyntheticElement) return true;

        final String overriderName = overrider.getName();
        final String baseName = method.getName();
        final String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
        if (newOverriderName != null) {
          RenameProcessor.assertNonCompileElement(overrider);
          allRenames.put(overrider, newOverriderName);
        }
        return true;
      }
    });
  }

  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_METHOD;
  }

  public boolean isToSearchInComments(final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
  }

  @Nullable
  public PsiElement substituteElementToRename(PsiElement element, Editor editor) {
    PsiMethod psiMethod = (PsiMethod) element;
    if (psiMethod.isConstructor()) {
      PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return null;
      if (Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        element = containingClass;
        if (!PsiElementRenameHandler.canRename(element.getProject(), editor, element)) {
          return null;
        }
        return element;
      }
    }
    return SuperMethodWarningUtil.checkSuperMethod(psiMethod, RefactoringLocalize.toRename().get());
  }

  @Override
  public void substituteElementToRename(@Nonnull PsiElement element,
                                        @Nonnull final Editor editor,
                                        @Nonnull final Consumer<PsiElement> renameCallback) {
    final PsiMethod psiMethod = (PsiMethod) element;
    if (psiMethod.isConstructor()) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return;
      if (!Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        renameCallback.accept(psiMethod);
        return;
      }
      super.substituteElementToRename(element, editor, renameCallback);
    } else {
      SuperMethodWarningUtil.checkSuperMethod(psiMethod, "Rename", new PsiElementProcessor<PsiMethod>() {
        @Override
        public boolean execute(@Nonnull PsiMethod method) {
          if (!PsiElementRenameHandler.canRename(method.getProject(), editor, method)) return false;
          renameCallback.accept(method);
          return false;
        }
      }, editor);
    }
  }

  private static void findSubmemberHidesMemberCollisions(final PsiMethod method, final String newName, final List<UsageInfo> result) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, true).findAll();

    MethodSignature oldSignature = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature newSignature = MethodSignatureUtil.createMethodSignature(newName, oldSignature.getParameterTypes(),
        oldSignature.getTypeParameters(),
        oldSignature.getSubstitutor(),
        method.isConstructor());
    for (PsiClass inheritor : inheritors) {
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
      final PsiMethod[] methodsByName = inheritor.findMethodsByName(newName, false);
      for (PsiMethod conflictingMethod : methodsByName) {
        if (newSignature.equals(conflictingMethod.getSignature(superSubstitutor))) {
          result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
          break;
        }
      }
    }
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
  }
}
