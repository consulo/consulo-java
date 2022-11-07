/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.codeEditor.EditorFactory;
import consulo.document.Document;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PsiUtilEx {

  private PsiUtilEx() {
  }

  public static boolean isInSourceContent(PsiElement e) {
    final VirtualFile file = e.getContainingFile().getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex index = ProjectRootManager.getInstance(e.getProject()).getFileIndex();
    return index.isInContent(file);
  }

  @Nullable
  public static PsiParameter getParameterForArgument(PsiElement element) {
    PsiElement p = element.getParent();
    if (!(p instanceof PsiExpressionList)) return null;
    PsiExpressionList list = (PsiExpressionList)p;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiCallExpression)) return null;
    PsiExpression[] arguments = list.getExpressions();
    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      if (argument == element) {
        final PsiCallExpression call = (PsiCallExpression)parent;
        final PsiMethod method = call.resolveMethod();
        if (method != null) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length > i) {
            return parameters[i];
          }
          else if (parameters.length > 0) {
            final PsiParameter lastParam = parameters[parameters.length - 1];
            if (lastParam.getType() instanceof PsiEllipsisType) {
              return lastParam;
            }
          }
        }
        break;
      }
    }
    return null;
  }

  public static boolean isStringOrCharacterLiteral(final PsiElement place) {
    if (place instanceof PsiLiteralExpression) {
      final PsiElement child = place.getFirstChild();
      if (child != null && child instanceof PsiJavaToken) {
        final IElementType tokenType = ((PsiJavaToken)child).getTokenType();
        if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isString(@Nonnull PsiType type) {
    if (type instanceof PsiClassType) {
      // optimization. doesn't require resolve
      final String shortName = ((PsiClassType)type).getClassName();
      if (!Comparing.equal(shortName, JavaClassNames.JAVA_LANG_STRING_SHORT)) return false;
    }
    return JavaClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
  }

  public static boolean isStringOrStringArray(@Nonnull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isString(((PsiArrayType)type).getComponentType());
    }
    else {
      return isString(type);
    }
  }

  @RequiredReadAction
  public static Document createDocument(final String s, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() || project.isDefault()) {
      return EditorFactory.getInstance().createDocument(s);
    }
    else {
      return JavaReferenceEditorUtil.createTypeDocument(s, project);
    }
  }

  public static boolean isLanguageAnnotationTarget(final PsiModifierListOwner owner) {
    if (owner instanceof PsiMethod) {
      final PsiType returnType = ((PsiMethod)owner).getReturnType();
      if (returnType == null || !isStringOrStringArray(returnType)) {
        return false;
      }
    }
    else if (owner instanceof PsiVariable) {
      final PsiType type = ((PsiVariable)owner).getType();
      if (!isStringOrStringArray(type)) {
        return false;
      }
    }
    else {
      return false;
    }
    return true;
  }
}
