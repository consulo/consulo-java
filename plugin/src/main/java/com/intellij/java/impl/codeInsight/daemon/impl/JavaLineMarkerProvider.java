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
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.progress.ProgressManager;
import consulo.application.util.concurrent.JobLauncher;
import consulo.application.util.function.Computable;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.colorScheme.EditorColorsManager;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.java.impl.JavaBundle;
import consulo.java.language.impl.icon.JavaPsiImplIconGroup;
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
import consulo.ui.ex.action.IdeActions;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;

@ExtensionImpl
public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor {
  public static final Option LAMBDA_OPTION = new Option("java.lambda", JavaBundle.message("title.lambda"), AllIcons.Gutter.ImplementingFunctional) {
    @Override
    public boolean isEnabledByDefault() {
      return false;
    }
  };

  private final Option myOverriddenOption = new Option("java.overridden", JavaBundle.message("gutter.overridden.method"), AllIcons.Gutter.OverridenMethod);
  private final Option myImplementedOption = new Option("java.implemented", JavaBundle.message("gutter.implemented.method"), AllIcons.Gutter.ImplementedMethod);
  private final Option myOverridingOption = new Option("java.overriding", JavaBundle.message("gutter.overriding.method"), AllIcons.Gutter.OverridingMethod);
  private final Option myImplementingOption = new Option("java.implementing", JavaBundle.message("gutter.implementing.method"), AllIcons.Gutter.ImplementingMethod);
  private final Option mySiblingsOption = new Option("java.sibling.inherited", JavaBundle.message("gutter.sibling.inherited.method"), AllIcons.Gutter.SiblingInheritedMethod);
  private final Option myServiceOption = new Option("java.service", JavaBundle.message("gutter.service"), JavaPsiImplIconGroup.gutterJava9service());

  protected final DaemonCodeAnalyzerSettings myDaemonSettings;
  protected final EditorColorsManager myColorsManager;

  @Inject
  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    myDaemonSettings = daemonSettings;
    myColorsManager = colorsManager;
  }

  @RequiredReadAction
  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(final @Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier && parent instanceof PsiMethod) {
      if (!myOverridingOption.isEnabled() && !myImplementingOption.isEnabled()) {
        return null;
      }
      PsiMethod method = (PsiMethod) parent;
      MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superSignature != null) {
        boolean overrides =
            method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        final Image icon;
        if (overrides) {
          if (!myOverridingOption.isEnabled()) {
            return null;
          }
          icon = AllIcons.Gutter.OverridingMethod;
        } else {
          if (!myImplementingOption.isEnabled()) {
            return null;
          }
          icon = AllIcons.Gutter.ImplementingMethod;
        }
        return createSuperMethodLineMarkerInfo(element, icon);
      }
    }
    // in case of ()->{}, anchor to "->"
    // in case of (xxx)->{}, anchor to "->"
    // in case of Type::method, anchor to "method"
    if (LAMBDA_OPTION.isEnabled() &&
        parent instanceof PsiFunctionalExpression &&
        (element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.ARROW && parent instanceof PsiLambdaExpression ||
            element instanceof PsiIdentifier && parent instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression) parent).getReferenceNameElement() == element)
    ) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
      if (interfaceMethod != null) {
        return createSuperMethodLineMarkerInfo(element, AllIcons.Gutter.ImplementingFunctional);
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
  private static LineMarkerInfo createSuperMethodLineMarkerInfo(@Nonnull PsiElement name, @Nonnull Image icon) {
    ArrowUpLineMarkerInfo info = new ArrowUpLineMarkerInfo(name, icon, MarkerType.OVERRIDING_METHOD);
    return NavigateAction.setNavigateAction(info, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
  }

  private static int getCategory(@Nonnull PsiElement element, @Nonnull CharSequence documentChars) {
    if (element instanceof PsiField || element instanceof PsiTypeParameter) {
      return 1;
    }
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) {
      return 2;
    }
    if (element instanceof PsiMethod) {
      if (((PsiMethod) element).hasModifierProperty(PsiModifier.ABSTRACT)) {
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
  public void collectSlowLineMarkers(@Nonnull final List<PsiElement> elements, @Nonnull final Collection<LineMarkerInfo> result) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<Computable<List<LineMarkerInfo<PsiElement>>>> tasks = new ArrayList<>();

    MultiMap<PsiClass, PsiMethod> canBeOverridden = MultiMap.createSet();
    MultiMap<PsiClass, PsiMethod> canHaveSiblings = MultiMap.create();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();
      if (!(element instanceof PsiIdentifier)) {
        continue;
      }
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod) parent;
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
      } else if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        tasks.add(() -> collectInheritingClasses((PsiClass) parent));
        tasks.add(() -> JavaServiceUtil.collectServiceImplementationClass((PsiClass) parent));
      } else if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression grandParent = (PsiMethodCallExpression) parent.getParent();
        if (JavaServiceUtil.SERVICE_LOADER_LOAD.test(grandParent)) {
          tasks.add(() -> JavaServiceUtil.collectServiceLoaderLoadCall((PsiIdentifier) element, grandParent));
        }
      }
    }

    for (Map.Entry<PsiClass, Collection<PsiMethod>> entry : canBeOverridden.entrySet()) {
      PsiClass psiClass = entry.getKey();
      Set<PsiMethod> methods = (Set<PsiMethod>) entry.getValue();
      tasks.add(() -> collectOverridingMethods(methods, psiClass));
    }
    for (PsiClass psiClass : canHaveSiblings.keySet()) {
      Collection<PsiMethod> methods = canHaveSiblings.get(psiClass);
      tasks.add(() -> collectSiblingInheritedMethods(methods));
    }

    Object lock = new Object();
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    List<LineMarkerInfo<PsiElement>> found = new ArrayList<>();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, indicator, computable -> {
      List<LineMarkerInfo<PsiElement>> infos = computable.compute();
      synchronized (lock) {
        found.addAll(infos);
      }
      return true;
    });
    synchronized (lock) {
      result.addAll(found);
    }
  }

  @Nonnull
  private static List<LineMarkerInfo<PsiElement>> collectSiblingInheritedMethods(@Nonnull final Collection<? extends PsiMethod> methods) {
    Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
    return ContainerUtil.map(map.keySet(), method -> {
      PsiElement range = getMethodRange(method);
      ArrowUpLineMarkerInfo upInfo =
          new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.SiblingInheritedMethod, MarkerType.SIBLING_OVERRIDING_METHOD);
      return NavigateAction.setNavigateAction(upInfo, JavaBundle.message("action.go.to.super.method.text"), IdeActions.ACTION_GOTO_SUPER);
    });
  }

  @Nonnull
  private static PsiElement getMethodRange(@Nonnull PsiMethod method) {
    PsiElement range;
    if (method.isPhysical()) {
      range = method.getNameIdentifier();
    } else {
      final PsiElement navigationElement = method.getNavigationElement();
      range = navigationElement instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner) navigationElement).getNameIdentifier() : navigationElement;
    }
    if (range == null) {
      range = method;
    }
    return range;
  }

  @Nonnull
  protected List<LineMarkerInfo<PsiElement>> collectInheritingClasses(@Nonnull PsiClass aClass) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return Collections.emptyList();
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return Collections.emptyList(); // It's useless to have overridden markers for object.
    }

    PsiClass subClass = DirectClassInheritorsSearch.search(aClass).findFirst();
    if (subClass != null || FunctionalExpressionSearch.search(aClass).findFirst() != null) {
      final Image icon;
      if (aClass.isInterface()) {
        if (!myImplementedOption.isEnabled()) {
          return Collections.emptyList();
        }
        icon = AllIcons.Gutter.ImplementedMethod;
      } else {
        if (!myOverriddenOption.isEnabled()) {
          return Collections.emptyList();
        }
        icon = AllIcons.Gutter.OverridenMethod;
      }
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) {
        range = aClass;
      }
      MarkerType type = MarkerType.SUBCLASSED_CLASS;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
          icon, Pass.LINE_MARKERS, type.getTooltip(),
          type.getNavigationHandler(),
          GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, aClass.isInterface() ? JavaBundle.message("action.go.to.implementation.text")
          : JavaBundle.message("action.go.to.subclass.text"), IdeActions.ACTION_GOTO_IMPLEMENTATION);
      return Collections.singletonList(info);
    }
    return Collections.emptyList();
  }

  @Nonnull
  private List<LineMarkerInfo<PsiElement>> collectOverridingMethods(@Nonnull final Set<PsiMethod> methodSet, @Nonnull PsiClass containingClass) {
    if (!myOverriddenOption.isEnabled() && !myImplementedOption.isEnabled()) {
      return Collections.emptyList();
    }
    final Set<PsiMethod> overridden = new HashSet<>();

    AllOverridingMethodsSearch.search(containingClass).forEach(pair -> {
      ProgressManager.checkCanceled();

      final PsiMethod superMethod = pair.getFirst();
      if (methodSet.remove(superMethod)) {
        overridden.add(superMethod);
      }
      return !methodSet.isEmpty();
    });

    if (!methodSet.isEmpty()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(containingClass);
      if (interfaceMethod != null &&
          methodSet.contains(interfaceMethod) &&
          FunctionalExpressionSearch.search(containingClass).findFirst() != null) {
        overridden.add(interfaceMethod);
      }
    }

    List<LineMarkerInfo<PsiElement>> result = new ArrayList<>(overridden.size());
    for (PsiMethod method : overridden) {
      ProgressManager.checkCanceled();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides && !myOverriddenOption.isEnabled()) {
        continue;
      }
      if (!overrides && !myImplementedOption.isEnabled()) {
        continue;
      }
      PsiElement range = getMethodRange(method);
      final MarkerType type = MarkerType.OVERRIDDEN_METHOD;
      final Image icon = overrides ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
          icon, Pass.LINE_MARKERS, type.getTooltip(),
          type.getNavigationHandler(),
          GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, overrides ? JavaBundle.message("action.go.to.overriding.methods.text")
          : JavaBundle.message("action.go.to.implementation.text"), IdeActions.ACTION_GOTO_IMPLEMENTATION);
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
    private ArrowUpLineMarkerInfo(@Nonnull PsiElement element, @Nonnull Image icon, @Nonnull MarkerType markerType) {
      super(element, element.getTextRange(), icon, Pass.LINE_MARKERS, markerType.getTooltip(), markerType.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
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
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiFunctionalExpression) {
        return PsiExpressionTrimRenderer.render((PsiExpression) parent);
      }
      return super.getElementPresentation(element);
    }
  }
}
