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
package com.intellij.java.language.impl.psi.impl.file;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.completion.scope.JavaCompletionHints;
import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.Queryable;
import consulo.application.util.function.CommonProcessors;
import consulo.component.ProcessCanceledException;
import consulo.language.Language;
import consulo.language.impl.psi.PsiPackageBase;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.extension.ModuleExtension;
import consulo.util.collection.ArrayFactory;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import consulo.util.lang.function.Conditions;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PsiPackageImpl extends PsiPackageBase implements PsiJavaPackage, Queryable {
  private static final Logger LOGGER = Logger.getInstance(PsiPackageImpl.class);

  private volatile CachedValue<PsiModifierList> myAnnotationList;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectories;
  private volatile SoftReference<Set<String>> myPublicClassNamesCache;

  public PsiPackageImpl(PsiManager manager,
                        PsiPackageManager packageManager,
                        Class<? extends ModuleExtension> extensionClass,
                        String qualifiedName) {
    super(manager, packageManager, extensionClass, qualifiedName);
  }

  @Override
  protected Collection<PsiDirectory> getAllDirectories(boolean includeLibrarySource) {
    if (myDirectories == null) {
      myDirectories =
        CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new CachedValueProvider<Collection<PsiDirectory>>() {
          @Override
          public Result<Collection<PsiDirectory>> compute() {
            final CommonProcessors.CollectProcessor<PsiDirectory> processor = new CommonProcessors.CollectProcessor<PsiDirectory>();
            getFacade().processPackageDirectories(PsiPackageImpl.this, allScope(), processor);
            return Result.create(processor.getResults(),
                                 PsiPackageImplementationHelper.getInstance().getDirectoryCachedValueDependencies(PsiPackageImpl.this));
          }
        }, false);
    }
    return myDirectories.getValue();
  }

  @Override
  public void handleQualifiedNameChange(@Nonnull final String newQualifiedName) {
    PsiPackageImplementationHelper.getInstance().handleQualifiedNameChange(this, newQualifiedName);
  }

  @Override
  public VirtualFile[] occursInPackagePrefixes() {
    return PsiPackageImplementationHelper.getInstance().occursInPackagePrefixes(this);
  }

  @Override
  public PsiPackageImpl getParentPackage() {
    return (PsiPackageImpl)super.getParentPackage();
  }


  @RequiredReadAction
  @Override
  @Nonnull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean isValid() {
    return PsiPackageImplementationHelper.getInstance().packagePrefixExists(this) || !getAllDirectories(true).isEmpty();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackage(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiJavaPackage:" + getQualifiedName();
  }

  @Override
  @Nonnull
  public PsiClass[] getClasses() {
    return getClasses(allScope());
  }

  protected GlobalSearchScope allScope() {
    return PsiPackageImplementationHelper.getInstance().adjustAllScope(this, GlobalSearchScope.allScope(getProject()));
  }

  @Override
  @Nonnull
  public PsiClass[] getClasses(@Nonnull GlobalSearchScope scope) {
    return getFacade().getClasses(this, scope);
  }

  @Override
  @Nonnull
  public PsiFile[] getFiles(@Nonnull GlobalSearchScope scope) {
    return getFacade().getPackageFiles(this, scope);
  }

  @Override
  @Nullable
  public PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList =
        CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new PackageAnnotationValueProvider(), false);
    }
    return myAnnotationList.getValue();
  }

  @Override
  @Nonnull
  public PsiJavaPackage[] getSubPackages() {
    return getFacade().getSubPackages(this, allScope());
  }

  @Override
  @Nonnull
  public PsiJavaPackage[] getSubPackages(@Nonnull GlobalSearchScope scope) {
    return getFacade().getSubPackages(this, scope);
  }

  @Override
  protected ArrayFactory<? extends PsiPackage> getPackageArrayFactory() {
    return PsiJavaPackage.ARRAY_FACTORY;
  }

  private JavaPsiFacadeImpl getFacade() {
    return (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(myManager.getProject());
  }

  private Set<String> getClassNamesCache() {
    SoftReference<Set<String>> ref = myPublicClassNamesCache;
    Set<String> cache = ref == null ? null : ref.get();
    if (cache == null) {
      GlobalSearchScope scope = allScope();

      if (!scope.isForceSearchingInLibrarySources()) {
        scope = new DelegatingGlobalSearchScope(scope) {
          @Override
          public boolean isForceSearchingInLibrarySources() {
            return true;
          }
        };
      }
      cache = getFacade().getClassNames(this, scope);
      myPublicClassNamesCache = new SoftReference<Set<String>>(cache);
    }

    return cache;
  }

  @Nonnull
  private PsiClass[] findClassesByName(String name, GlobalSearchScope scope) {
    final String qName = getQualifiedName();
    final String classQName = !qName.isEmpty() ? qName + "." + name : name;
    return getFacade().findClasses(classQName, scope);
  }

  @Override
  public boolean containsClassNamed(String name) {
    return getClassNamesCache().contains(name);
  }

  @Nonnull
  @Override
  public PsiClass[] findClassByShortName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
    return getFacade().findClassByShortName(name, this, scope);
  }

  @Nullable
  private PsiJavaPackage findSubPackageByName(String name) {
    final String qName = getQualifiedName();
    final String subpackageQName = qName.isEmpty() ? name : qName + "." + name;
    return getFacade().findPackage(subpackageQName);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    GlobalSearchScope scope = place.getResolveScope();

    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    final Condition<String> nameCondition = processor.getHint(JavaCompletionHints.NAME_FILTER);

    NameHint providedNameHint = processor.getHint(NameHint.KEY);
    final String providedName = providedNameHint == null ? null : providedNameHint.getName(state);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (providedName != null) {
        final PsiClass[] classes = findClassByShortName(providedName, scope);
        if (!processClasses(processor, state, classes, Conditions.<String>alwaysTrue())) {
          return false;
        }
      }
      else {
        PsiClass[] classes = getClasses(scope);
        if (!processClasses(processor, state, classes, nameCondition != null ? nameCondition : Conditions.<String>alwaysTrue())) {
          return false;
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE)) {
      if (providedName != null) {
        PsiPackage aPackage = findSubPackageByName(providedName);
        if (aPackage != null) {
          if (!processor.execute(aPackage, state)) {
            return false;
          }
        }
      }
      else {
        PsiPackage[] packs = getSubPackages(scope);
        for (PsiPackage pack : packs) {
          final String packageName = pack.getName();
          if (packageName == null) {
            continue;
          }
          if (!PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(packageName, PsiUtil.getLanguageLevel(this))) {
            continue;
          }
          if (!processor.execute(pack, state)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean processClasses(@Nonnull PsiScopeProcessor processor,
                                        @Nonnull ResolveState state,
                                        @Nonnull PsiClass[] classes,
                                        @Nonnull Condition<String> nameCondition) {
    for (PsiClass aClass : classes) {
      String name = aClass.getName();
      if (name != null && nameCondition.value(name)) {
        try {
          if (!processor.execute(aClass, state)) {
            return false;
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOGGER.error(e);
        }
      }
    }
    return true;
  }

  @Override
  public void navigate(final boolean requestFocus) {
    PsiPackageImplementationHelper.getInstance().navigate(this, requestFocus);
  }

  private class PackageAnnotationValueProvider implements CachedValueProvider<PsiModifierList> {
    private final Object[] OOCB_DEPENDENCY = {PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT};

    @Override
    public Result<PsiModifierList> compute() {
      List<PsiModifierList> list = new ArrayList<PsiModifierList>();
      for (PsiDirectory directory : getDirectories()) {
        PsiFile file = directory.findFile(PACKAGE_INFO_FILE);
        if (file != null) {
          PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(file, PsiPackageStatement.class);
          if (stmt != null) {
            final PsiModifierList modifierList = stmt.getAnnotationList();
            if (modifierList != null) {
              list.add(modifierList);
            }
          }
        }
      }

      final JavaPsiFacade facade = getFacade();
      final GlobalSearchScope scope = allScope();
      for (PsiClass aClass : facade.findClasses(getQualifiedName() + ".package-info", scope)) {
        ContainerUtil.addIfNotNull(list, aClass.getModifierList());
      }

      return new Result<PsiModifierList>(list.isEmpty() ? null : new PsiCompositeModifierList(getManager(), list), OOCB_DEPENDENCY);
    }
  }

  @Override
  @Nullable
  public PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull final String name) {
    return false;
  }
}
