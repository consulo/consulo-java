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
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.refactoring.changeSignature.inplace.LanguageChangeSignatureDetector;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;
import java.util.List;

@ExtensionImpl
public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector<DetectedJavaChangeInfo> {
  private static final Logger LOG = Logger.getInstance(JavaChangeSignatureDetector.class);

  @Nonnull
  @Override
  public DetectedJavaChangeInfo createInitialChangeInfo(final @Nonnull PsiElement element) {
    return DetectedJavaChangeInfo.createFromMethod(PsiTreeUtil.getParentOfType(element, PsiMethod.class), false);
  }

  @Override
  public void performChange(final DetectedJavaChangeInfo changeInfo, Editor editor, @Nonnull final String oldText) {
    changeInfo.perform(oldText, editor, true);
  }

  @Override
  public boolean isChangeSignatureAvailableOnElement(@Nonnull PsiElement element, DetectedJavaChangeInfo currentInfo) {
    final PsiMethod method = currentInfo.getMethod();
    TextRange range = method.getTextRange();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      range = new TextRange(range.getStartOffset(), body.getTextOffset());
    }
    return element.getContainingFile() == method.getContainingFile() && range.contains(element.getTextRange());
  }

  @Override
  public boolean ignoreChanges(PsiElement element) {
    if (element instanceof PsiMethod) {
      return true;
    }
    return PsiTreeUtil.getParentOfType(element, PsiImportList.class) != null;
  }

  @Override
  public TextRange getHighlightingRange(@Nonnull DetectedJavaChangeInfo changeInfo) {
    PsiElement method = changeInfo.getMethod();
    return method != null ? getSignatureRange((PsiMethod) method) : null;
  }

  @Override
  public String getMethodSignaturePreview(DetectedJavaChangeInfo initialChangeInfo, final List<TextRange> deleteRanges, final List<TextRange> newRanges) {
    StringBuilder buf = new StringBuilder();
    String visibility = VisibilityUtil.getVisibilityString(initialChangeInfo.getNewVisibility());
    buf.append(visibility);
    if (!StringUtil.isEmptyOrSpaces(visibility)) {
      buf.append(" ");
    }
    CanonicalTypes.Type returnType = initialChangeInfo.getNewReturnType();
    if (returnType != null) {
      buf.append(returnType.getTypeText()).append(" ");
    }
    buf.append(initialChangeInfo.getNewName()).append("(");

    JavaParameterInfo[] newParameters = initialChangeInfo.getNewParameters();
    boolean first = true;
    boolean[] toRemove = initialChangeInfo.toRemoveParm();
    for (int i = 0; i < toRemove.length; i++) {
      if (first) {
        first = false;
      } else {
        buf.append(", ");
      }

      String oldParamName = initialChangeInfo.getOldParameterNames()[i];
      String oldParamType = initialChangeInfo.getOldParameterTypes()[i];
      if (toRemove[i]) {
        String deletedParam = oldParamType + " " + oldParamName;
        deleteRanges.add(new TextRange(buf.length(), buf.length() + deletedParam.length()));
        buf.append(deletedParam);
      } else {
        for (JavaParameterInfo parameter : newParameters) {
          if (parameter.getOldIndex() == i) {
            buf.append(parameter.getTypeText());
            buf.append(" ");
            if (!oldParamName.equals(parameter.getName())) {
              deleteRanges.add(new TextRange(buf.length(), buf.length() + oldParamName.length()));
              buf.append(oldParamName);
            }
            buf.append(parameter.getName());
            break;
          }
        }
      }
    }

    for (JavaParameterInfo param : newParameters) {
      if (param.getOldIndex() == -1) {
        if (first) {
          first = false;
        } else {
          buf.append(", ");
        }
        String paramPresentation = param.getTypeText() + " " + ObjectUtil.notNull(param.getName(), "");
        newRanges.add(new TextRange(buf.length(), buf.length() + paramPresentation.length()));
        buf.append(paramPresentation);
      }
    }
    buf.append(")");
    return buf.toString();
  }

  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public DetectedJavaChangeInfo createNextChangeInfo(String signature, @Nonnull final DetectedJavaChangeInfo currentInfo, boolean delegate) {
    final PsiElement currentInfoMethod = currentInfo.getMethod();
    if (currentInfoMethod == null) {
      return null;
    }
    final Project project = currentInfoMethod.getProject();

    final PsiMethod oldMethod = currentInfo.getMethod();
    String visibility = "";
    PsiClass containingClass = oldMethod.getContainingClass();
    if (containingClass != null && containingClass.isInterface()) {
      visibility = PsiModifier.PUBLIC + " ";
    }
    PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText((visibility + signature).trim(), oldMethod);
    return currentInfo.createNextInfo(method, delegate);
  }

  public static TextRange getSignatureRange(PsiMethod method) {
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    int startOffset = method.getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
