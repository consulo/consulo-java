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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.impl.psi.util.ProjectIconsAccessor;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.fileEditor.FileEditorManager;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.GutterIconNavigationHandler;
import consulo.language.editor.gutter.LineMarkerInfo;
import consulo.language.editor.gutter.LineMarkerProviderDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Shows small (16x16 or less) icons as gutters.
 * <p>
 * Works in places where it's possible to resolve from literal expression
 * to an icon image.
 *
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class IconLineMarkerProvider extends LineMarkerProviderDescriptor {
  @RequiredReadAction
  @Override
  public LineMarkerInfo getLineMarkerInfo(@Nonnull PsiElement element) {
    if (element instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression) element).getLExpression();
      PsiExpression expr = ((PsiAssignmentExpression) element).getRExpression();
      if (lExpression instanceof PsiReferenceExpression) {
        PsiElement var = ((PsiReferenceExpression) lExpression).resolve();
        if (var instanceof PsiVariable) {
          return createIconLineMarker(((PsiVariable) var).getType(), expr);
        }
      }
    } else if (element instanceof PsiReturnStatement) {
      PsiReturnStatement psiReturnStatement = (PsiReturnStatement) element;
      PsiExpression value = psiReturnStatement.getReturnValue();
      PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method != null) {
        PsiType returnType = method.getReturnType();
        LineMarkerInfo<PsiElement> result = createIconLineMarker(returnType, value);

        if (result != null || !ProjectIconsAccessor.isIconClassType(returnType) || value == null) {
          return result;
        }

        if (methodContainsReturnStatementOnly(method)) {
          for (PsiReference ref : value.getReferences()) {
            PsiElement field = ref.resolve();
            if (field instanceof PsiField) {
              return createIconLineMarker(returnType, ((PsiField) field).getInitializer(), psiReturnStatement);
            }
          }
        }
      }
    } else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable) element;

      PsiUtilCore.ensureValid(var);
      PsiType type = var.getType();
      if (!type.isValid()) {
        PsiUtil.ensureValidType(type, "in variable: " + var + " of " + var.getClass());
      }

      return createIconLineMarker(type, var.getInitializer());
    }
    return null;
  }

  private static boolean methodContainsReturnStatementOnly(@Nonnull PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null || body.getStatements().length != 1) {
      return false;
    }

    return body.getStatements()[0] instanceof PsiReturnStatement;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(PsiType type, @Nullable PsiExpression initializer) {
    return createIconLineMarker(type, initializer, initializer);
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> createIconLineMarker(PsiType type, @Nullable PsiExpression initializer, PsiElement bindingElement) {
    if (initializer == null) {
      return null;
    }

    Project project = initializer.getProject();

    VirtualFile file = ProjectIconsAccessor.getInstance(project).resolveIconFile(type, initializer);
    if (file == null) {
      return null;
    }

    Image icon = ProjectIconsAccessor.getInstance(project).getIcon(file, initializer);
    if (icon == null) {
      return null;
    }

    GutterIconNavigationHandler<PsiElement> navHandler = (e, elt) -> FileEditorManager.getInstance(project).openFile(file, true);

    return new LineMarkerInfo<>(bindingElement, bindingElement.getTextRange(), icon, Pass.UPDATE_ALL, null, navHandler, GutterIconRenderer.Alignment.LEFT);
  }

  @Override
  public Image getIcon() {
    return AllIcons.Gutter.Colors;
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Icon preview");
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
