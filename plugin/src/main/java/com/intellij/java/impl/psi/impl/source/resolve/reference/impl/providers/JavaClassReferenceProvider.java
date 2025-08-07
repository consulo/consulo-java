/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.java.language.impl.psi.JavaClassPsiReferenceProvider;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.util.ClassKind;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.language.psi.*;
import consulo.language.psi.path.CustomizableReferenceProvider;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 */
public class JavaClassReferenceProvider extends JavaClassPsiReferenceProvider implements CustomizableReferenceProvider {

    public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME = new CustomizationKey<Boolean>(PsiBundle.message("qualified.resolve.class.reference.provider.option"));
    public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<String[]>("EXTEND_CLASS_NAMES");
    public static final CustomizationKey<String> CLASS_TEMPLATE = new CustomizationKey<String>("CLASS_TEMPLATE");
    public static final CustomizationKey<ClassKind> CLASS_KIND = new CustomizationKey<ClassKind>("CLASS_KIND");
    public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<Boolean>("INSTANTIATABLE");
    public static final CustomizationKey<Boolean> CONCRETE = new CustomizationKey<Boolean>("CONCRETE");
    public static final CustomizationKey<Boolean> NOT_INTERFACE = new CustomizationKey<Boolean>("NOT_INTERFACE");
    public static final CustomizationKey<Boolean> NOT_ENUM = new CustomizationKey<Boolean>("NOT_ENUM");
    public static final CustomizationKey<Boolean> ADVANCED_RESOLVE = new CustomizationKey<Boolean>("RESOLVE_ONLY_CLASSES");
    public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<Boolean>("JVM_FORMAT");
    public static final CustomizationKey<Boolean> ALLOW_DOLLAR_NAMES = new CustomizationKey<Boolean>("ALLOW_DOLLAR_NAMES");
    public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<String>("DEFAULT_PACKAGE");
    @Nullable
    private Map<CustomizationKey, Object> myOptions;

    private boolean myAllowEmpty;

    private final ParameterizedCachedValueProvider<List<PsiElement>, Project> myProvider = new ParameterizedCachedValueProvider<List<PsiElement>, Project>() {
        @Override
        public CachedValueProvider.Result<List<PsiElement>> compute(Project project) {
            final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
            final String defPackageName = DEFAULT_PACKAGE.getValue(myOptions);
            if (StringUtil.isNotEmpty(defPackageName)) {
                final PsiJavaPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage(defPackageName);
                if (defaultPackage != null) {
                    psiPackages.addAll(getSubPackages(defaultPackage));
                }
            }
            final PsiJavaPackage rootPackage = JavaPsiFacade.getInstance(project).findPackage("");
            if (rootPackage != null) {
                psiPackages.addAll(getSubPackages(rootPackage));
            }
            return CachedValueProvider.Result.createSingleDependency(psiPackages, PsiModificationTracker.MODIFICATION_COUNT);
        }
    };

    private final Key<ParameterizedCachedValue<List<PsiElement>, Project>> myKey = Key.create("default packages");

    public <T> void setOption(CustomizationKey<T> option, T value) {
        if (myOptions == null) {
            myOptions = new HashMap<CustomizationKey, Object>();
        }
        option.putValue(myOptions, value);
    }

    @Nullable
    public <T> T getOption(CustomizationKey<T> option) {
        return myOptions == null ? null : (T) myOptions.get(option);
    }

    @Nullable
    public GlobalSearchScope getScope(Project project) {
        return null;
    }

    @Nonnull
    public PsiFile getContextFile(@Nonnull PsiElement element) {
        return element.getContainingFile();
    }

    @Nullable
    public PsiClass getContextClass(@Nonnull PsiElement element) {
        return null;
    }

    @Override
    @Nonnull
    public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull final ProcessingContext context) {
        return getReferencesByElement(element);
    }

    public PsiReference[] getReferencesByElement(@Nonnull PsiElement element) {
        final int offsetInElement = ElementManipulators.getOffsetInElement(element);
        final String text = ElementManipulators.getValueText(element);
        return getReferencesByString(text, element, offsetInElement);
    }

    @Nonnull
    public PsiReference[] getReferencesByString(String str, @Nonnull PsiElement position, int offsetInPosition) {
        if (myAllowEmpty && StringUtil.isEmpty(str)) {
            return PsiReference.EMPTY_ARRAY;
        }
        boolean allowDollars = Boolean.TRUE.equals(getOption(ALLOW_DOLLAR_NAMES));
        return new JavaClassReferenceSet(str, position, offsetInPosition, allowDollars, this).getAllReferences();
    }

    @Override
    public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
        final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
        if (position == null) {
            return;
        }
        if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE) || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
            final List<PsiElement> cachedPackages = getDefaultPackages(position.getProject());
            for (final PsiElement psiPackage : cachedPackages) {
                if (!processor.execute(psiPackage, ResolveState.initial())) {
                    return;
                }
            }
        }
    }

    protected List<PsiElement> getDefaultPackages(Project project) {
        return CachedValuesManager.getManager(project).getParameterizedCachedValue(project, myKey, myProvider, false, project);
    }

    private static Collection<PsiPackage> getSubPackages(final PsiJavaPackage defaultPackage) {
        return ContainerUtil.mapNotNull(defaultPackage.getSubPackages(), psiPackage -> {
            final String packageName = psiPackage.getName();
            return PsiNameHelper.getInstance(psiPackage.getProject()).isIdentifier(packageName, PsiUtil.getLanguageLevel(psiPackage)) ? psiPackage : null;
        });
    }

    @Override
    public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
        myOptions = options;
    }

    @Override
    @Nullable
    public Map<CustomizationKey, Object> getOptions() {
        return myOptions;
    }

    public void setAllowEmpty(final boolean allowEmpty) {
        myAllowEmpty = allowEmpty;
    }
}
