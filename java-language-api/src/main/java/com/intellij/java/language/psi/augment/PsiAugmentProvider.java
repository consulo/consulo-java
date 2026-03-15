// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.augment;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.dumb.DumbAware;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.component.ProcessCanceledException;
import consulo.component.extension.ExtensionPointName;
import consulo.component.util.PluginExceptionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Some code is not what it seems to be!
 * This extension allows plugins <strike>augment a reality</strike> alter a behavior of Java PSI elements.
 * To get an insight of how the extension may be used see {@code PsiAugmentProviderTest}.
 * <p>
 * N.B. during indexing, only {@link DumbAware} providers are run.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PsiAugmentProvider {
    private static final Logger LOG = Logger.getInstance(PsiAugmentProvider.class);
    public static final ExtensionPointName<PsiAugmentProvider> EP_NAME = ExtensionPointName.create(PsiAugmentProvider.class);

    @SuppressWarnings("rawtypes")
    private /* non-static */ final Key<CachedValue<Map<Class, List>>> myCacheKey = Key.create(getClass().getName());

    //<editor-fold desc="Methods to override in implementations.">

    /**
     * An extension that enables one to add children to some PSI elements, e.g. methods to Java classes.
     * The class code remains the same, but its method accessors also include the results returned from {@link PsiAugmentProvider}s.
     * An augmenter can be called several times with the same parameters in the same state of the code model,
     * and the PSI returned from these invocations must be equal and implement {@link #equals}/{@link #hashCode()} accordingly.
     *
     * @param nameHint the expected name of the requested augmented members, or null if all members of the specified class are to be returned.
     *                 Implementations can ignore this parameter or use it for optimizations.
     */
    @SuppressWarnings({
        "unchecked",
        "rawtypes"
    })
    protected <Psi extends PsiElement> List<Psi> getAugments(
        PsiElement element,
        Class<Psi> type,
        @Nullable String nameHint
    ) {
        if (nameHint == null) {
            return getAugments(element, type);
        }

        // cache to emulate previous behavior where augmenters were called just once, not for each name hint separately
        Map<Class, List> cache = LanguageCachedValueUtil.getCachedValue(
            element,
            myCacheKey,
            () -> {
                Map<Class, List> map = ConcurrentFactoryMap.createMap(c -> getAugments(element, c));
                return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
            }
        );
        return (List<Psi>)cache.get(type);
    }

    /**
     * An extension which enables one to inject extension methods with name {@code nameHint} in class {@code aClass} in context `{@code context}`
     *
     * @param aClass   where extension methods would be injected
     * @param nameHint name of the method which is requested.
     *                 Implementations are supposed to use this parameter as no additional name check would be performed
     * @param context  context where extension methods should be applicable
     */
    protected List<PsiExtensionMethod> getExtensionMethods(
        PsiClass aClass,
        String nameHint,
        PsiElement context
    ) {
        return Collections.emptyList();
    }

    /**
     * @deprecated invoke and override {@link #getAugments(PsiElement, Class, String)}.
     */
    @SuppressWarnings("unused")
    @Deprecated
    protected <Psi extends PsiElement> List<Psi> getAugments(PsiElement element, Class<Psi> type) {
        return Collections.emptyList();
    }

    /**
     * Extends {@link PsiTypeElement#getType()} so that a type could be retrieved from external place
     * (e.g. inferred from a variable initializer).
     */
    @Nullable
    protected PsiType inferType(PsiTypeElement typeElement) {
        return null;
    }

    /**
     * @return whether this extension might infer the type for the given PSI,
     * preferably checked in a lightweight way without actually inferring the type.
     */
    protected boolean canInferType(PsiTypeElement typeElement) {
        return inferType(typeElement) != null;
    }

    /**
     * Intercepts {@link PsiModifierList#hasModifierProperty(String)}, so that plugins can add imaginary modifiers or hide existing ones.
     */
    protected Set<String> transformModifiers(PsiModifierList modifierList, Set<String> modifiers) {
        return modifiers;
    }

    /**
     * @param field field to check
     * @return true if this field initializer can be changed due to extra-linguistic extensions
     * (e.g., it's annotated via some annotation and annotation processor will transform the field to be non-constant)
     */
    protected boolean fieldInitializerMightBeChanged(PsiField field) {
        return false;
    }

    //</editor-fold>

    //<editor-fold desc="API and the inner kitchen.">

    /**
     * @deprecated use {@link #collectAugments(PsiElement, Class, String)}
     */
    @Deprecated
    public static <Psi extends PsiElement> List<Psi> collectAugments(PsiElement element, Class<? extends Psi> type) {
        return collectAugments(element, type, null);
    }

    public static <Psi extends PsiElement> List<Psi> collectAugments(
        PsiElement element, Class<? extends Psi> type,
        @Nullable String nameHint
    ) {
        List<Psi> result = new SmartList<>();

        forEach(
            element.getProject(),
            provider -> {
                List<? extends Psi> augments = provider.getAugments(element, type, nameHint);
                for (Psi augment : augments) {
                    if (nameHint == null || !(augment instanceof PsiNamedElement namedElem) || nameHint.equals(namedElem.getName())) {
                        try {
                            PsiUtilCore.ensureValid(augment);
                            result.add(augment);
                        }
                        catch (ProcessCanceledException e) {
                            throw e;
                        }
                        catch (Throwable e) {
                            PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, provider.getClass());
                        }
                    }
                }
                return true;
            }
        );

        return result;
    }

    public static List<PsiExtensionMethod> collectExtensionMethods(PsiClass aClass, String nameHint, PsiElement context) {
        List<PsiExtensionMethod> extensionMethods = new SmartList<>();
        forEach(aClass.getProject(), provider -> {
            List<PsiExtensionMethod> methods = provider.getExtensionMethods(aClass, nameHint, context);
            for (PsiExtensionMethod method : methods) {
                try {
                    PsiUtilCore.ensureValid(method);
                    extensionMethods.add(method);
                }
                catch (ProcessCanceledException e) {
                    throw e;
                }
                catch (Throwable e) {
                    PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, provider.getClass());
                }
            }
            return true;
        });
        return extensionMethods;
    }

    @Nullable
    public static PsiType getInferredType(PsiTypeElement typeElement) {
        SimpleReference<PsiType> result = SimpleReference.create();

        forEach(
            typeElement.getProject(),
            provider -> {
                PsiType type = provider.inferType(typeElement);
                if (type != null) {
                    try {
                        PsiUtil.ensureValidType(type);
                    }
                    catch (ProcessCanceledException e) {
                        throw e;
                    }
                    catch (Throwable e) {
                        PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, provider.getClass());
                    }
                    result.set(type);
                    return false;
                }
                else {
                    return true;
                }
            }
        );

        return result.get();
    }

    public static boolean isInferredType(PsiTypeElement typeElement) {
        AtomicBoolean result = new AtomicBoolean();

        forEach(typeElement.getProject(), provider -> {
            boolean canInfer = provider.canInferType(typeElement);
            if (canInfer) {
                result.set(true);
            }
            return !canInfer;
        });

        return result.get();
    }

    /**
     * @param field field to check
     * @return true if we can trust the field initializer;
     * false if any of providers reported that the initializer might be changed
     */
    public static boolean canTrustFieldInitializer(PsiField field) {
        AtomicBoolean result = new AtomicBoolean(true);

        forEach(field.getProject(), provider -> {
            boolean mightBeReplaced = provider.fieldInitializerMightBeChanged(field);
            if (mightBeReplaced) {
                result.set(false);
            }
            return !mightBeReplaced;
        });

        return result.get();
    }

    public static Set<String> transformModifierProperties(
        PsiModifierList modifierList,
        Project project,
        Set<String> modifiers
    ) {
        SimpleReference<Set<String>> result = SimpleReference.create(modifiers);

        forEach(
            project,
            provider -> {
                result.set(provider.transformModifiers(modifierList, Collections.unmodifiableSet(result.get())));
                return true;
            }
        );

        return result.get();
    }

    private static void forEach(Project project, Predicate<? super PsiAugmentProvider> processor) {
        boolean dumb = DumbService.isDumb(project);
        for (PsiAugmentProvider provider : EP_NAME.getExtensionList()) {
            if (!dumb || DumbService.isDumbAware(provider)) {
                try {
                    boolean goOn = processor.test(provider);
                    if (!goOn) {
                        break;
                    }
                }
                catch (ProcessCanceledException e) {
                    throw e;
                }
                catch (Exception e) {
                    Logger.getInstance(PsiAugmentProvider.class).error("provider: " + provider, e);
                }
            }
        }
    }

    //</editor-fold>
}