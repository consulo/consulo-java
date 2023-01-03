/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.guava;

import com.intellij.java.indexing.search.searches.AnnotatedMembersSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.query.FilteredQuery;
import consulo.application.util.query.Query;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.java.language.impl.JavaIcons;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProvider;
import consulo.language.editor.ui.DefaultPsiElementCellRenderer;
import consulo.language.editor.ui.PsiElementListNavigator;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.function.Functions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Collection;

/**
 * @author VISTALL
 * @since 24-Feb-17
 */
@ExtensionImpl
public class GuavaLineMarkerProvider implements LineMarkerProvider {
  private static final GutterIconNavigationHandler<PsiElement> ourClassNavigator = (e, elt) ->
  {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(elt, PsiClass.class);
    if (psiClass == null) {
      return;
    }
    PsiClass anno = findSubscribeAnnotation(psiClass);
    if (anno == null) {
      return;
    }
    PsiElementProcessor.CollectElements<PsiMember> collector = new PsiElementProcessor.CollectElements<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() ->
    {

      createQuery(psiClass, anno).forEach(new PsiElementProcessorAdapter<>(collector));
    }, "Searching event methods", true, psiClass.getProject(), (JComponent) e.getComponent())) {
      return;
    }

    Collection<PsiMember> collection = collector.getCollection();
    PsiElementListNavigator.openTargets(e, collection.toArray(new NavigatablePsiElement[collection.size()]), "Event Methods", "Event Methods", new DefaultPsiElementCellRenderer());
  };

  private static final GutterIconNavigationHandler<PsiElement> ourMethodNavigator = (e, elt) ->
  {
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(elt, PsiMethod.class);
    if (psiMethod == null) {
      return;
    }

    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    if (parameters.length == 1) {
      PsiParameter psiParameter = parameters[0];
      PsiType type = psiParameter.getType();
      if (type instanceof PsiClassType) {
        PsiClass resolvedClas = ((PsiClassType) type).resolve();
        if (resolvedClas != null) {
          resolvedClas.navigate(true);
        }
      }
    }
  };

  @RequiredReadAction
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
    PsiElement parent;
    if (element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiClass) {
      PsiClass annotation = findSubscribeAnnotation(element);
      if (annotation == null) {
        return null;
      }

      if (createQuery((PsiClass) parent, annotation).findFirst() != null) {
        return new LineMarkerInfo<>(element, element.getTextRange(), JavaIcons.Gutter.EventMethod, Pass.LINE_MARKERS, Functions.constant("Event Object"), ourClassNavigator,
            GutterIconRenderer.Alignment.RIGHT);
      }
    } else if (element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiMethod) {
      PsiClass annotation = findSubscribeAnnotation(element);
      if (annotation == null) {
        return null;
      }

      PsiMethod method = (PsiMethod) parent;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length == 1 && parameters[0].getType() instanceof PsiClassType && AnnotationUtil.isAnnotated(method, GuavaLibrary.Subscribe, false)) {
          return new LineMarkerInfo<>(element, element.getTextRange(), JavaIcons.Gutter.EventMethod, Pass.LINE_MARKERS, Functions.constant("Event Method"), ourMethodNavigator,
              GutterIconRenderer.Alignment.RIGHT);
        }
      }
    }
    return null;
  }

  @Nonnull
  private static Query<PsiMember> createQuery(@Nonnull PsiClass target, @Nonnull PsiClass annClass) {
    PsiImmediateClassType type = new PsiImmediateClassType(target, PsiSubstitutor.EMPTY);
    return new FilteredQuery<>(AnnotatedMembersSearch.search(annClass), psiMember -> ReadAction.compute(() ->
    {
      if (psiMember instanceof PsiMethod && !psiMember.hasModifierProperty(PsiModifier.STATIC)) {
        PsiParameterList parameterList = ((PsiMethod) psiMember).getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length == 1 && parameters[0].getType().equals(type)) {
          return true;
        }
      }
      return false;
    }));
  }

  @Nullable
  private static PsiClass findSubscribeAnnotation(@Nonnull PsiElement element) {
    return LanguageCachedValueUtil.getCachedValue(element, () ->
    {
      PsiClass javaClass = JavaPsiFacade.getInstance(element.getProject()).findClass(GuavaLibrary.Subscribe, element.getResolveScope());
      return CachedValueProvider.Result.create(javaClass, ProjectRootManager.getInstance(element.getProject()));
    });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
