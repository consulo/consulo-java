// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.impl.ide.util.MethodOrFunctionalExpressionCellRenderer;
import com.intellij.java.impl.ide.util.PsiClassOrFunctionalExpressionListCellRenderer;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.impl.psi.impl.FindSuperElementsHelper;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.CommonProcessors;
import consulo.java.language.module.util.JavaClassNames;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.DaemonBundle;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.localize.DaemonLocalize;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.editor.ui.PsiElementListNavigator;
import consulo.language.editor.ui.navigation.BackgroundUpdaterTask;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.DumbService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MarkerType {
    private final GutterIconNavigationHandler<PsiElement> handler;
    private final Function<PsiElement, String> myTooltip;
    @Nonnull
    private final String myDebugName;

    /**
     * @deprecated use {@link #MarkerType(String, Function, LineMarkerNavigator)} instead
     */
    @Deprecated
    public MarkerType(@Nonnull Function<PsiElement, String> tooltip, @Nonnull LineMarkerNavigator navigator) {
        this("Unknown", tooltip, navigator);
    }

    public MarkerType(
        @Nonnull String debugName,
        @Nonnull Function<PsiElement, String> tooltip,
        @Nonnull LineMarkerNavigator navigator
    ) {
        myTooltip = tooltip;
        myDebugName = debugName;
        handler = (e, elt) -> DumbService.getInstance(elt.getProject()).withAlternativeResolveEnabled(() -> navigator.browse(e, elt));
    }

    @Override
    public String toString() {
        return myDebugName;
    }

    @Nonnull
    public GutterIconNavigationHandler<PsiElement> getNavigationHandler() {
        return handler;
    }

    @Nonnull
    public Function<PsiElement, String> getTooltip() {
        return myTooltip;
    }

    public static final MarkerType OVERRIDING_METHOD = new MarkerType(
        "OVERRIDING_METHOD",
        element -> getParentMethod(element) instanceof PsiMethod method
            ? calculateOverridingMethodTooltip(method, method != element.getParent()) : null,
        new LineMarkerNavigator() {
            @Override
            public void browse(MouseEvent e, PsiElement element) {
                PsiElement parent = getParentMethod(element);
                if (!(parent instanceof PsiMethod method)) {
                    return;
                }
                navigateToOverridingMethod(e, method, method != element.getParent());
            }
        }
    );
    public static final MarkerType SIBLING_OVERRIDING_METHOD = new MarkerType(
        "SIBLING_OVERRIDING_METHOD",
        element -> getParentMethod(element) instanceof PsiMethod method ? calculateOverridingSiblingMethodTooltip(method) : null,
        new LineMarkerNavigator() {
            @Override
            public void browse(MouseEvent e, PsiElement element) {
                if (getParentMethod(element) instanceof PsiMethod method) {
                    navigateToSiblingOverridingMethod(e, method);
                }
            }
        }
    );

    @Nullable
    private static String calculateOverridingMethodTooltip(@Nonnull PsiMethod method, boolean acceptSelf) {
        PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
        if (superMethods.length == 0) {
            return null;
        }

        String divider = GutterTooltipHelper.getElementDivider(false, false, superMethods.length);
        AtomicReference<String> reference = new AtomicReference<>(""); // optimization: calculate next divider only once
        return GutterTooltipHelper.getTooltipText(
            Arrays.asList(superMethods),
            superMethod -> getTooltipPrefix(method, superMethod, reference.getAndSet(divider)),
            superMethod -> isSameSignature(method, superMethod),
            IdeActions.ACTION_GOTO_SUPER
        );
    }

    @Nullable
    private static String calculateOverridingSiblingMethodTooltip(@Nonnull PsiMethod method) {
        FindSuperElementsHelper.SiblingInfo pair = FindSuperElementsHelper.getSiblingInfoInheritedViaSubClass(method);
        if (pair == null) {
            return null;
        }

        return GutterTooltipHelper.getTooltipText(
            Arrays.asList(pair.superMethod, pair.subClass),
            element -> element instanceof PsiMethod psiMethod ? getTooltipPrefix(method, psiMethod, "") : " via subclass ",
            element -> element instanceof PsiMethod psiMethod && isSameSignature(method, psiMethod),
            IdeActions.ACTION_GOTO_SUPER
        );
    }

    @Nonnull
    private static String getTooltipPrefix(@Nonnull PsiMethod method, @Nonnull PsiMethod superMethod, @Nonnull String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        boolean isAbstract = method.isAbstract();
        boolean isSuperAbstract = superMethod.isAbstract();
        sb.append(isSuperAbstract && !isAbstract ? "Implements method " : "Overrides method ");
        if (isSameSignature(method, superMethod)) {
            sb.append("in ");
        }
        return sb.toString();
    }

    private static boolean isSameSignature(@Nonnull PsiMethod method, @Nonnull PsiMethod superMethod) {
        return method.getSignature(PsiSubstitutor.EMPTY).equals(superMethod.getSignature(PsiSubstitutor.EMPTY));
    }

    @Nonnull
    private static <E extends PsiElement> PsiElementProcessor.CollectElementsWithLimit<E> getProcessor(int limit, boolean set) {
        return set
            ? new PsiElementProcessor.CollectElementsWithLimit<>(limit, new HashSet<>())
            : new PsiElementProcessor.CollectElementsWithLimit<>(limit);
    }

    private static String getFunctionalImplementationTooltip(@Nonnull PsiClass psiClass) {
        PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> processor = getProcessor(5, true);
        FunctionalExpressionSearch.search(psiClass).forEach(new PsiElementProcessorAdapter<>(processor));
        if (processor.isOverflow()) {
            return getImplementationTooltip("Has several functional implementations");
        }
        if (processor.getCollection().isEmpty()) {
            return null;
        }
        return getImplementationTooltip(processor.getCollection(), "Is functionally implemented in");
    }

    @Nonnull
    private static String getImplementationTooltip(@Nonnull String prefix, @Nonnull PsiElement... elements) {
        return getImplementationTooltip(Arrays.asList(elements), prefix);
    }

    @Nonnull
    private static String getImplementationTooltip(@Nonnull Collection<? extends PsiElement> elements, @Nonnull String prefix) {
        return GutterTooltipHelper.getTooltipText(elements, prefix, true, IdeActions.ACTION_GOTO_IMPLEMENTATION);
    }

    private static void navigateToOverridingMethod(MouseEvent e, @Nonnull PsiMethod method, boolean acceptSelf) {
        PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
        if (superMethods.length == 0) {
            return;
        }
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
        PsiElementListNavigator.openTargets(
            e,
            superMethods,
            DaemonLocalize.navigationTitleSuperMethod(method.getName()).get(),
            DaemonLocalize.navigationFindusagesTitleSuperMethod(method.getName()).get(),
            new MethodCellRenderer(showMethodNames)
        );
    }

    private static void navigateToSiblingOverridingMethod(MouseEvent e, @Nonnull PsiMethod method) {
        PsiMethod superMethod = FindSuperElementsHelper.getSiblingInheritedViaSubClass(method);
        if (superMethod == null) {
            return;
        }
        PsiElementListNavigator.openTargets(
            e,
            new NavigatablePsiElement[]{superMethod},
            DaemonLocalize.navigationTitleSuperMethod(method.getName()).get(),
            DaemonLocalize.navigationFindusagesTitleSuperMethod(method.getName()).get(),
            new MethodCellRenderer(false)
        );
    }

    @Nonnull
    private static PsiMethod[] composeSuperMethods(@Nonnull PsiMethod method, boolean acceptSelf) {
        PsiElement[] superElements = FindSuperElementsHelper.findSuperElements(method);

        PsiMethod[] superMethods = ContainerUtil.map(superElements, element -> (PsiMethod)element, PsiMethod.EMPTY_ARRAY);
        if (acceptSelf) {
            superMethods = ArrayUtil.prepend(method, superMethods);
        }
        return superMethods;
    }

    private static PsiElement getParentMethod(@Nonnull PsiElement element) {
        PsiElement parent = element.getParent();
        PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
        return interfaceMethod != null ? interfaceMethod : parent;
    }

    public static final String SEARCHING_FOR_OVERRIDING_METHODS = "Searching for Overriding Methods";
    public static final MarkerType OVERRIDDEN_METHOD = new MarkerType(
        "OVERRIDDEN_METHOD",
        element -> element.getParent() instanceof PsiMethod method ? getOverriddenMethodTooltip(method) : null,
        new LineMarkerNavigator() {
            @Override
            public void browse(MouseEvent e, PsiElement element) {
                if (element.getParent() instanceof PsiMethod method) {
                    navigateToOverriddenMethod(e, method);
                }
            }
        }
    );

    private static String getOverriddenMethodTooltip(@Nonnull PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass != null && JavaClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
            return getImplementationTooltip("Is implemented in several subclasses");
        }

        PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = getProcessor(5, false);
        GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
        OverridingMethodsSearch.search(method, scope, true).forEach(new PsiElementProcessorAdapter<>(processor));

        boolean isAbstract = method.isAbstract();

        if (processor.isOverflow()) {
            return getImplementationTooltip(isAbstract ? "Is implemented in several subclasses" : "Is overridden in several subclasses");
        }

        PsiMethod[] overridings = processor.toArray(PsiMethod.EMPTY_ARRAY);
        if (overridings.length == 0) {
            return !isAbstract || aClass == null ? null : getFunctionalImplementationTooltip(aClass);
        }

        Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
        Arrays.sort(overridings, comparator);

        return getImplementationTooltip(isAbstract ? "Is implemented in" : "Is overridden in", overridings);
    }

    private static void navigateToOverriddenMethod(MouseEvent e, @Nonnull PsiMethod method) {
        if (DumbService.isDumb(method.getProject())) {
            DumbService.getInstance(method.getProject())
                .showDumbModeNotification(JavaLocalize.notificationNavigationToOverridingClasses());
            return;
        }

        PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor = getProcessor(2, true);
        PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> collectExprProcessor = getProcessor(2, true);
        boolean isAbstract = method.isAbstract();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
                OverridingMethodsSearch.search(method, scope, true).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
                if (isAbstract && collectProcessor.getCollection().size() < 2) {
                    PsiClass aClass = ReadAction.compute(method::getContainingClass);
                    if (aClass != null) {
                        FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
                    }
                }
            },
            SEARCHING_FOR_OVERRIDING_METHODS,
            true,
            method.getProject(),
            (JComponent)e.getComponent()
        )) {
            return;
        }

        PsiMethod[] methodOverriders = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
        List<NavigatablePsiElement> overridings = new ArrayList<>();
        overridings.addAll(collectProcessor.getCollection());
        overridings.addAll(collectExprProcessor.getCollection());
        if (overridings.isEmpty()) {
            return;
        }
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(methodOverriders);
        MethodOrFunctionalExpressionCellRenderer renderer = new MethodOrFunctionalExpressionCellRenderer(showMethodNames);
        Collections.sort(overridings, renderer.getComparator());
        OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
        PsiElementListNavigator.openTargets(
            e,
            overridings.toArray(NavigatablePsiElement.EMPTY_ARRAY),
            methodsUpdater.getCaption(overridings.size()),
            "Overriding methods of " + method.getName(),
            renderer,
            methodsUpdater
        );
    }

    private static final String SEARCHING_FOR_OVERRIDDEN_METHODS = "Searching for Overridden Methods";
    public static final MarkerType SUBCLASSED_CLASS = new MarkerType(
        "SUBCLASSED_CLASS",
        element -> element.getParent() instanceof PsiClass aClass ? getSubclassedClassTooltip(aClass) : null,
        new LineMarkerNavigator() {
            @Override
            public void browse(MouseEvent e, PsiElement element) {
                if (element.getParent() instanceof PsiClass aClass) {
                    navigateToSubclassedClass(e, aClass);
                }
            }
        }
    );

    // Used in Kotlin, please don't make private
    public static String getSubclassedClassTooltip(@Nonnull PsiClass aClass) {
        PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = getProcessor(5, true);
        ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(processor));

        if (processor.isOverflow()) {
            return getImplementationTooltip(aClass.isInterface() ? "Is implemented by several subclasses" : "Is overridden by several subclasses");
        }

        PsiClass[] subclasses = processor.toArray(PsiClass.EMPTY_ARRAY);
        if (subclasses.length == 0) {
            return getFunctionalImplementationTooltip(aClass);
        }

        Comparator<PsiClass> comparator = new PsiClassListCellRenderer().getComparator();
        Arrays.sort(subclasses, comparator);

        return getImplementationTooltip(aClass.isInterface() ? "Is implemented by" : "Is subclassed by", subclasses);
    }

    // Used in Kotlin, please don't make private
    public static void navigateToSubclassedClass(MouseEvent e, @Nonnull PsiClass aClass) {
        navigateToSubclassedClass(e, aClass, new PsiClassOrFunctionalExpressionListCellRenderer());
    }

    // Used in Kotlin, please don't make private
    public static void navigateToSubclassedClass(
        MouseEvent e,
        @Nonnull PsiClass aClass,
        PsiElementListCellRenderer<NavigatablePsiElement> renderer
    ) {
        if (DumbService.isDumb(aClass.getProject())) {
            DumbService.getInstance(aClass.getProject())
                .showDumbModeNotification("Navigation to overriding methods is not possible during index update");
            return;
        }

        PsiElementProcessor.FindElement<PsiClass> collectProcessor = new PsiElementProcessor.FindElement<>();
        PsiElementProcessor.FindElement<PsiFunctionalExpression> collectExprProcessor = new PsiElementProcessor.FindElement<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
                if (collectProcessor.getFoundElement() == null) {
                    FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
                }
            },
            SEARCHING_FOR_OVERRIDDEN_METHODS,
            true,
            aClass.getProject(),
            (JComponent)e.getComponent()
        )) {
            return;
        }

        List<NavigatablePsiElement> inheritors = new ArrayList<>();
        ContainerUtil.addIfNotNull(inheritors, collectProcessor.getFoundElement());
        ContainerUtil.addIfNotNull(inheritors, collectExprProcessor.getFoundElement());
        if (inheritors.isEmpty()) {
            return;
        }
        SubclassUpdater subclassUpdater = new SubclassUpdater(aClass, renderer);
        Collections.sort(inheritors, renderer.getComparator());
        PsiElementListNavigator.openTargets(
            e,
            inheritors.toArray(NavigatablePsiElement.EMPTY_ARRAY),
            subclassUpdater.getCaption(inheritors.size()),
            CodeInsightLocalize.gotoImplementationFindusagesTitle(aClass.getName()).get(),
            renderer,
            subclassUpdater
        );
    }

    private static class SubclassUpdater extends BackgroundUpdaterTask {
        private final PsiClass myClass;

        private SubclassUpdater(@Nonnull PsiClass aClass, @Nonnull PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
            super(aClass.getProject(), SEARCHING_FOR_OVERRIDDEN_METHODS, createComparatorWrapper((Comparator)renderer.getComparator()));
            myClass = aClass;
        }

        @Override
        public String getCaption(int size) {
            String suffix = isFinished() ? "" : " so far";
            return myClass.isInterface()
                ? CodeInsightBundle.message("goto.implementation.chooserTitle", myClass.getName(), size, suffix)
                : DaemonBundle.message("navigation.title.subclass", myClass.getName(), size, suffix);
        }

        @Override
        @RequiredUIAccess
        public void onSuccess() {
            super.onSuccess();
            PsiElement oneElement = getTheOnlyOneElement();
            if (oneElement instanceof NavigatablePsiElement navigatablePsiElement) {
                navigatablePsiElement.navigate(true);
                myPopup.cancel();
            }
        }

        @Override
        public void run(@Nonnull final ProgressIndicator indicator) {
            super.run(indicator);
            ClassInheritorsSearch.search(myClass, ReadAction.compute(myClass::getUseScope), true)
                .forEach(new CommonProcessors.CollectProcessor<>() {
                    @Override
                    public boolean process(PsiClass o) {
                        if (!updateComponent(o)) {
                            indicator.cancel();
                        }
                        ProgressManager.checkCanceled();
                        return super.process(o);
                    }
                });

            FunctionalExpressionSearch.search(myClass).forEach(new CommonProcessors.CollectProcessor<>() {
                @Override
                public boolean process(PsiFunctionalExpression expr) {
                    if (!updateComponent(expr)) {
                        indicator.cancel();
                    }
                    ProgressManager.checkCanceled();
                    return super.process(expr);
                }
            });
        }
    }

    private static class OverridingMethodsUpdater extends BackgroundUpdaterTask {
        private final PsiMethod myMethod;

        private OverridingMethodsUpdater(@Nonnull PsiMethod method, @Nonnull PsiElementListCellRenderer renderer) {
            super(method.getProject(), SEARCHING_FOR_OVERRIDING_METHODS, createComparatorWrapper(renderer.getComparator()));
            myMethod = method;
        }

        @Override
        public String getCaption(int size) {
            return myMethod.isAbstract()
                ? DaemonLocalize.navigationTitleImplementationMethod(myMethod.getName(), size).get()
                : DaemonLocalize.navigationTitleOverriderMethod(myMethod.getName(), size).get();
        }

        @Override
        @RequiredUIAccess
        public void onSuccess() {
            super.onSuccess();
            PsiElement oneElement = getTheOnlyOneElement();
            if (oneElement instanceof NavigatablePsiElement navigatableElement) {
                navigatableElement.navigate(true);
                myPopup.cancel();
            }
        }

        @Override
        public void run(@Nonnull final ProgressIndicator indicator) {
            super.run(indicator);
            GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(myMethod));
            OverridingMethodsSearch.search(myMethod, scope, true).forEach(
                new CommonProcessors.CollectProcessor<>() {
                    @Override
                    public boolean process(PsiMethod psiMethod) {
                        if (!updateComponent(psiMethod)) {
                            indicator.cancel();
                        }
                        ProgressManager.checkCanceled();
                        return super.process(psiMethod);
                    }
                });
            if (ReadAction.compute(myMethod::isAbstract)) {
                PsiClass psiClass = ReadAction.compute(myMethod::getContainingClass);
                FunctionalExpressionSearch.search(psiClass).forEach(new CommonProcessors.CollectProcessor<>() {
                    @Override
                    public boolean process(PsiFunctionalExpression expr) {
                        if (!updateComponent(expr)) {
                            indicator.cancel();
                        }
                        ProgressManager.checkCanceled();
                        return super.process(expr);
                    }
                });
            }
        }
    }
}
