/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.JavaServiceUtil;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.FindSuperElementsHelper;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.progress.ProgressManager;
import consulo.application.util.concurrent.JobLauncher;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.colorScheme.EditorColorsManager;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
import consulo.java.language.module.util.JavaClassNames;
import consulo.java.localize.JavaLocalize;
import consulo.language.Language;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProviderDescriptor;
import consulo.language.editor.gutter.MergeableLineMarkerInfo;
import consulo.language.editor.gutter.NavigateAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@ExtensionImpl
public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor {
    public static final Option LAMBDA_OPTION =
        new Option("java.lambda", JavaLocalize.titleLambda().get(), PlatformIconGroup.gutterImplementingfunctionalinterface()) {
            @Override
            public boolean isEnabledByDefault() {
                return false;
            }
        };

    private final Option myOverriddenOption =
        new Option("java.overridden", JavaLocalize.gutterOverriddenMethod().get(), PlatformIconGroup.gutterOverridenmethod());
    private final Option myImplementedOption =
        new Option("java.implemented", JavaLocalize.gutterImplementedMethod().get(), PlatformIconGroup.gutterImplementedmethod());
    private final Option myOverridingOption =
        new Option("java.overriding", JavaLocalize.gutterOverridingMethod().get(), PlatformIconGroup.gutterOverridingmethod());
    private final Option myImplementingOption =
        new Option("java.implementing", JavaLocalize.gutterImplementingMethod().get(), PlatformIconGroup.gutterImplementingmethod());
    private final Option mySiblingsOption =
        new Option(
            "java.sibling.inherited",
            JavaLocalize.gutterSiblingInheritedMethod().get(),
            PlatformIconGroup.gutterSiblinginheritedmethod()
        );
    private final Option myServiceOption =
        new Option("java.service", JavaLocalize.gutterService().get(), JavaPsiImplIconGroup.gutterJava9service());

    protected final Application myApplication;
    protected final DaemonCodeAnalyzerSettings myDaemonSettings;
    protected final EditorColorsManager myColorsManager;

    @Inject
    public JavaLineMarkerProvider(Application application, DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
        myApplication = application;
        myDaemonSettings = daemonSettings;
        myColorsManager = colorsManager;
    }

    @RequiredReadAction
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@Nonnull PsiElement element) {
        PsiElement parent = element.getParent();
        if (element instanceof PsiIdentifier && parent instanceof PsiMethod) {
            if (!myOverridingOption.isEnabled() && !myImplementingOption.isEnabled()) {
                return null;
            }
            PsiMethod method = (PsiMethod)parent;
            MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
            if (superSignature != null) {
                boolean overrides = method.isAbstract() ==
                    superSignature.getMethod().isAbstract();

                Image icon;
                if (overrides) {
                    if (!myOverridingOption.isEnabled()) {
                        return null;
                    }
                    icon = PlatformIconGroup.gutterOverridingmethod();
                }
                else {
                    if (!myImplementingOption.isEnabled()) {
                        return null;
                    }
                    icon = PlatformIconGroup.gutterImplementingmethod();
                }
                return createSuperMethodLineMarkerInfo(element, icon);
            }
        }
        // in case of ()->{}, anchor to "->"
        // in case of (xxx)->{}, anchor to "->"
        // in case of Type::method, anchor to "method"
        if (LAMBDA_OPTION.isEnabled()
            && parent instanceof PsiFunctionalExpression
            && (element instanceof PsiJavaToken javaToken
            && javaToken.getTokenType() == JavaTokenType.ARROW
            && parent instanceof PsiLambdaExpression
            || element instanceof PsiIdentifier
            && parent instanceof PsiMethodReferenceExpression methodRefExpr
            && methodRefExpr.getReferenceNameElement() == element)
        ) {
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
            if (interfaceMethod != null) {
                return createSuperMethodLineMarkerInfo(element, PlatformIconGroup.gutterImplementingfunctionalinterface());
            }
        }

        if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
            PsiElement element1 = element;
            boolean isMember = false;
            while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
                element1 = element1.getParent();
                if (element1 instanceof PsiMember) {
                    isMember = true;
                    break;
                }
            }
            if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
                PsiFile file = element1.getContainingFile();
                Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getLastCommittedDocument(file);
                boolean drawSeparator = false;

                if (document != null) {
                    CharSequence documentChars = document.getCharsSequence();
                    int category = getCategory(element1, documentChars);
                    for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
                        int category1 = getCategory(child, documentChars);
                        if (category1 == 0) {
                            continue;
                        }
                        drawSeparator = category != 1 || category1 != 1;
                        break;
                    }
                }

                if (drawSeparator) {
                    return LineMarkerInfo.createMethodSeparatorLineMarker(element, myColorsManager);
                }
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    private static LineMarkerInfo createSuperMethodLineMarkerInfo(@Nonnull PsiElement name, @Nonnull Image icon) {
        ArrowUpLineMarkerInfo info = new ArrowUpLineMarkerInfo(name, icon, MarkerType.OVERRIDING_METHOD);
        return NavigateAction.setNavigateAction(info, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
    }

    @RequiredReadAction
    private static int getCategory(@Nonnull PsiElement element, @Nonnull CharSequence documentChars) {
        if (element instanceof PsiField || element instanceof PsiTypeParameter) {
            return 1;
        }
        if (element instanceof PsiClass || element instanceof PsiClassInitializer) {
            return 2;
        }
        if (element instanceof PsiMethod method) {
            if (method.isAbstract()) {
                return 1;
            }
            TextRange textRange = element.getTextRange();
            int start = textRange.getStartOffset();
            int end = Math.min(documentChars.length(), textRange.getEndOffset());
            int crlf = StringUtil.getLineBreakCount(documentChars.subSequence(start, end));
            return crlf == 0 ? 1 : 2;
        }
        return 0;
    }

    @Override
    @RequiredReadAction
    public void collectSlowLineMarkers(@Nonnull List<PsiElement> elements, @Nonnull Collection<LineMarkerInfo> result) {
        myApplication.assertReadAccessAllowed();

        List<Supplier<List<LineMarkerInfo<PsiElement>>>> tasks = new ArrayList<>();

        MultiMap<PsiClass, PsiMethod> canBeOverridden = MultiMap.createSet();
        MultiMap<PsiClass, PsiMethod> canHaveSiblings = MultiMap.create();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < elements.size(); i++) {
            ProgressManager.checkCanceled();
            if (!(elements.get(i) instanceof PsiIdentifier identifier)) {
                continue;
            }
            PsiElement parent = identifier.getParent();
            if (parent instanceof PsiMethod method) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && PsiUtil.canBeOverridden(method)) {
                    canBeOverridden.putValue(containingClass, method);
                }
                if (mySiblingsOption.isEnabled() && FindSuperElementsHelper.canHaveSiblingSuper(method, containingClass)) {
                    canHaveSiblings.putValue(containingClass, method);
                }
                if (JavaServiceUtil.isServiceProviderMethod(method)) {
                    tasks.add(() -> JavaServiceUtil.collectServiceProviderMethod(method));
                }
            }
            else if (parent instanceof PsiClass psiClass && !(parent instanceof PsiTypeParameter)) {
                tasks.add(() -> collectInheritingClasses(psiClass));
                tasks.add(() -> JavaServiceUtil.collectServiceImplementationClass(psiClass));
            }
            else if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiMethodCallExpression methodCall) {
                if (JavaServiceUtil.SERVICE_LOADER_LOAD.test(methodCall)) {
                    tasks.add(() -> JavaServiceUtil.collectServiceLoaderLoadCall(identifier, methodCall));
                }
            }
        }

        for (Map.Entry<PsiClass, Collection<PsiMethod>> entry : canBeOverridden.entrySet()) {
            PsiClass psiClass = entry.getKey();
            Set<PsiMethod> methods = (Set<PsiMethod>)entry.getValue();
            tasks.add(() -> collectOverridingMethods(methods, psiClass));
        }
        for (PsiClass psiClass : canHaveSiblings.keySet()) {
            Collection<PsiMethod> methods = canHaveSiblings.get(psiClass);
            tasks.add(() -> collectSiblingInheritedMethods(methods));
        }

        Object lock = new Object();
        ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        List<LineMarkerInfo<PsiElement>> found = new ArrayList<>();
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
            tasks,
            indicator,
            computable -> {
                List<LineMarkerInfo<PsiElement>> infos = computable.get();
                synchronized (lock) {
                    found.addAll(infos);
                }
                return true;
            }
        );
        synchronized (lock) {
            result.addAll(found);
        }
    }

    @Nonnull
    private static List<LineMarkerInfo<PsiElement>> collectSiblingInheritedMethods(@Nonnull Collection<? extends PsiMethod> methods) {
        Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
        return ContainerUtil.map(
            map.keySet(),
            method -> {
                PsiElement range = getMethodRange(method);
                ArrowUpLineMarkerInfo upInfo = new ArrowUpLineMarkerInfo(
                    range,
                    PlatformIconGroup.gutterSiblinginheritedmethod(),
                    MarkerType.SIBLING_OVERRIDING_METHOD
                );
                return NavigateAction.setNavigateAction(
                    upInfo,
                    JavaLocalize.actionGoToSuperMethodText().get(),
                    IdeActions.ACTION_GOTO_SUPER
                );
            }
        );
    }

    @Nonnull
    @RequiredReadAction
    private static PsiElement getMethodRange(@Nonnull PsiMethod method) {
        PsiElement range;
        if (method.isPhysical()) {
            range = method.getNameIdentifier();
        }
        else {
            PsiElement navigationElement = method.getNavigationElement();
            range = navigationElement instanceof PsiNameIdentifierOwner nameIdentifierOwner
                ? nameIdentifierOwner.getNameIdentifier()
                : navigationElement;
        }
        if (range == null) {
            range = method;
        }
        return range;
    }

    @Nonnull
    @RequiredReadAction
    protected List<LineMarkerInfo<PsiElement>> collectInheritingClasses(@Nonnull PsiClass aClass) {
        if (aClass.isFinal()) {
            return Collections.emptyList();
        }
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
            return Collections.emptyList(); // It's useless to have overridden markers for object.
        }

        PsiClass subClass = DirectClassInheritorsSearch.search(aClass).findFirst();
        if (subClass != null || FunctionalExpressionSearch.search(aClass).findFirst() != null) {
            Image icon;
            if (aClass.isInterface()) {
                if (!myImplementedOption.isEnabled()) {
                    return Collections.emptyList();
                }
                icon = PlatformIconGroup.gutterImplementedmethod();
            }
            else {
                if (!myOverriddenOption.isEnabled()) {
                    return Collections.emptyList();
                }
                icon = PlatformIconGroup.gutterOverridenmethod();
            }
            PsiElement range = aClass.getNameIdentifier();
            if (range == null) {
                range = aClass;
            }
            MarkerType type = MarkerType.SUBCLASSED_CLASS;
            LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(
                range,
                range.getTextRange(),
                icon,
                Pass.LINE_MARKERS,
                type.getTooltip(),
                type.getNavigationHandler(),
                GutterIconRenderer.Alignment.RIGHT
            );
            LocalizeValue text = aClass.isInterface()
                ? JavaLocalize.actionGoToImplementationText()
                : JavaLocalize.actionGoToSubclassText();
            NavigateAction.setNavigateAction(info, text.get(), IdeActions.ACTION_GOTO_IMPLEMENTATION);
            return Collections.singletonList(info);
        }
        return Collections.emptyList();
    }

    @Nonnull
    @RequiredReadAction
    private List<LineMarkerInfo<PsiElement>> collectOverridingMethods(
        @Nonnull Set<PsiMethod> methodSet,
        @Nonnull PsiClass containingClass
    ) {
        if (!myOverriddenOption.isEnabled() && !myImplementedOption.isEnabled()) {
            return Collections.emptyList();
        }
        Set<PsiMethod> overridden = new HashSet<>();

        AllOverridingMethodsSearch.search(containingClass).forEach(pair -> {
            ProgressManager.checkCanceled();

            PsiMethod superMethod = pair.getFirst();
            if (methodSet.remove(superMethod)) {
                overridden.add(superMethod);
            }
            return !methodSet.isEmpty();
        });

        if (!methodSet.isEmpty()) {
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(containingClass);
            if (interfaceMethod != null &&
                methodSet.contains(interfaceMethod) &&
                FunctionalExpressionSearch.search(containingClass).findFirst() != null) {
                overridden.add(interfaceMethod);
            }
        }

        List<LineMarkerInfo<PsiElement>> result = new ArrayList<>(overridden.size());
        for (PsiMethod method : overridden) {
            ProgressManager.checkCanceled();
            boolean overrides = !method.isAbstract();
            if (overrides && !myOverriddenOption.isEnabled()) {
                continue;
            }
            if (!overrides && !myImplementedOption.isEnabled()) {
                continue;
            }
            PsiElement range = getMethodRange(method);
            MarkerType type = MarkerType.OVERRIDDEN_METHOD;
            Image icon = overrides ? PlatformIconGroup.gutterOverridenmethod() : PlatformIconGroup.gutterImplementedmethod();
            LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(
                range,
                range.getTextRange(),
                icon,
                Pass.LINE_MARKERS,
                type.getTooltip(),
                type.getNavigationHandler(),
                GutterIconRenderer.Alignment.RIGHT
            );
            LocalizeValue text = overrides
                ? JavaLocalize.actionGoToOverridingMethodsText()
                : JavaLocalize.actionGoToImplementationText();
            NavigateAction.setNavigateAction(info, text.get(), IdeActions.ACTION_GOTO_IMPLEMENTATION);
            result.add(info);
        }
        return result;
    }

    @Override
    public String getName() {
        return "Java line markers";
    }

    @Override
    public Option[] getOptions() {
        return new Option[]{
            LAMBDA_OPTION,
            myOverriddenOption,
            myImplementedOption,
            myOverridingOption,
            myImplementingOption,
            mySiblingsOption,
            myServiceOption
        };
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    private static class ArrowUpLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement> {
        @RequiredReadAction
        private ArrowUpLineMarkerInfo(@Nonnull PsiElement element, @Nonnull Image icon, @Nonnull MarkerType markerType) {
            super(
                element,
                element.getTextRange(),
                icon,
                Pass.LINE_MARKERS,
                markerType.getTooltip(),
                markerType.getNavigationHandler(),
                GutterIconRenderer.Alignment.LEFT
            );
        }

        @Override
        public boolean canMergeWith(@Nonnull MergeableLineMarkerInfo<?> info) {
            if (!(info instanceof ArrowUpLineMarkerInfo)) {
                return false;
            }
            PsiElement otherElement = info.getElement();
            PsiElement myElement = getElement();
            return otherElement != null && myElement != null;
        }

        @Nonnull
        @Override
        public Image getCommonIcon(@Nonnull List<MergeableLineMarkerInfo> infos) {
            return myIcon;
        }

        @Nonnull
        @Override
        public Function<? super PsiElement, String> getCommonTooltip(@Nonnull List<MergeableLineMarkerInfo> infos) {
            return element -> "Multiple method overrides";
        }

        @Override
        public String getElementPresentation(PsiElement element) {
            PsiElement parent = element.getParent();
            if (parent instanceof PsiFunctionalExpression functionalExpr) {
                return PsiExpressionTrimRenderer.render(functionalExpr);
            }
            return super.getElementPresentation(element);
        }
    }
}
