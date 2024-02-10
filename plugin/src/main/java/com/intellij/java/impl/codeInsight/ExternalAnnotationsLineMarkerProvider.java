/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.impl.codeInsight.intention.impl.AddAnnotationIntention;
import com.intellij.java.impl.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.java.impl.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.java.impl.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.java.impl.codeInspection.dataFlow.EditContractIntention;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.dataContext.DataContext;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.ide.actions.ApplyIntentionAction;
import consulo.java.impl.codeInsight.JavaCodeInsightSettings;
import consulo.java.language.impl.JavaIcons;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProviderDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionActionDelegate;
import consulo.language.editor.intention.IntentionManager;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.xml.XmlStringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.event.MouseEvent;
import java.util.function.Function;

@ExtensionImpl
public class ExternalAnnotationsLineMarkerProvider extends LineMarkerProviderDescriptor {
  private static final Function<PsiElement, String> ourTooltipProvider = nameIdentifier ->
  {
    PsiModifierListOwner owner = (PsiModifierListOwner) nameIdentifier.getParent();

    return XmlStringUtil.wrapInHtml(NonCodeAnnotationGenerator.getNonCodeHeader(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()) + ". Full signature:<p>\n" +
        JavaDocInfoGenerator.generateSignature(owner));
  };

  @RequiredReadAction
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@Nonnull final PsiElement element) {
    PsiModifierListOwner owner = getAnnotationOwner(element);
    if (owner == null) {
      return null;
    }

    boolean includeSourceInferred = JavaCodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS;
    boolean hasAnnotationsToShow = ContainerUtil.exists(NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values(), a -> includeSourceInferred || !a.isInferredFromSource());
    if (!hasAnnotationsToShow) {
      return null;
    }

    return new LineMarkerInfo<>(element, element.getTextRange(), JavaIcons.Gutter.ExtAnnotation, Pass.LINE_MARKERS, ourTooltipProvider, MyIconGutterHandler.INSTANCE, GutterIconRenderer.Alignment
        .RIGHT);
  }

  @Nullable
  static PsiModifierListOwner getAnnotationOwner(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }

    PsiElement owner = element.getParent();
    if (!(owner instanceof PsiModifierListOwner) || !(owner instanceof PsiNameIdentifierOwner)) {
      return null;
    }
    if (owner instanceof PsiParameter || owner instanceof PsiLocalVariable) {
      return null;
    }

    // support non-Java languages where getNameIdentifier may return non-physical psi with the same range
    PsiElement nameIdentifier = ((PsiNameIdentifierOwner) owner).getNameIdentifier();
    if (nameIdentifier == null || !nameIdentifier.getTextRange().equals(element.getTextRange())) {
      return null;
    }
    return (PsiModifierListOwner) owner;
  }


  @Nonnull
  @Override
  public String getName() {
    return "External annotations";
  }

  @Nullable
  @Override
  public Image getIcon() {
    return JavaIcons.Gutter.ExtAnnotation;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  private static class MyIconGutterHandler implements GutterIconNavigationHandler<PsiElement> {
    static final MyIconGutterHandler INSTANCE = new MyIconGutterHandler();

    @RequiredUIAccess
    @Override
    public void navigate(MouseEvent e, PsiElement nameIdentifier) {
      final PsiElement listOwner = nameIdentifier.getParent();
      final PsiFile containingFile = listOwner.getContainingFile();
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(listOwner);

      if (virtualFile != null && containingFile != null) {
        final Project project = listOwner.getProject();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
          editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

          if (file != null && virtualFile.equals(file.getVirtualFile())) {
            final JBPopup popup = createActionGroupPopup(containingFile, project, editor);
            if (popup != null) {
              popup.show(new RelativePoint(e));
            }
          }
        }
      }
    }

    @Nullable
    protected JBPopup createActionGroupPopup(PsiFile file, Project project, Editor editor) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions()) {
        if (shouldShowInGutterPopup(action) && action.isAvailable(project, editor, file)) {
          group.add(new ApplyIntentionAction(action, action.getText(), editor, file));
        }
      }

      if (group.getChildrenCount() > 0) {
        final DataContext context = DataContext.EMPTY_CONTEXT;
        return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      }

      return null;
    }

    private static boolean shouldShowInGutterPopup(IntentionAction action) {
      return action instanceof AddAnnotationIntention ||
          action instanceof DeannotateIntentionAction ||
          action instanceof EditContractIntention ||
          action instanceof ToggleSourceInferredAnnotations ||
          action instanceof MakeInferredAnnotationExplicit ||
          action instanceof MakeExternalAnnotationExplicit ||
          action instanceof IntentionActionDelegate && shouldShowInGutterPopup(((IntentionActionDelegate) action).getDelegate());
    }
  }
}
