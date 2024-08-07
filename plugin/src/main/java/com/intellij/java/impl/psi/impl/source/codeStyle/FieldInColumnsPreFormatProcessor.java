/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.codeStyle;

import com.intellij.java.impl.psi.impl.source.tree.JavaJspElementType;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.psi.PsiField;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.PreFormatProcessor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * There is a possible case that the project is configured to keep fields in columns:
 * <pre>
 *   class Test {
 *     int i                 = 1;
 *     int fieldWithLongName = 2;
 *   }
 * </pre>
 * Suppose that one of the fields is renamed. We want to reformat the whole fields group then in order to keep that 'field columns'.
 * <p/>
 * Current extension checks if given range intersects with a field from a field group and expands its boundaries to contain
 * the whole group.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 5/9/12 4:54 PM
 */
@ExtensionImpl
public class FieldInColumnsPreFormatProcessor implements PreFormatProcessor {

  @Nonnull
  @Override
  public TextRange process(@Nonnull ASTNode element, @Nonnull TextRange range) {
    //region Checking that everything is ready to expand the range for the 'fields in columns'.
    final PsiElement psi = element.getPsi();
    if (psi == null || !psi.isValid()) {
      return range;
    }

    final PsiFile file = psi.getContainingFile();
    if (file == null) {
      return range;
    }

    final Project project = psi.getProject();
    final CommonCodeStyleSettings settings
        = CodeStyleSettingsManager.getInstance(project).getCurrentSettings().getCommonSettings(JavaLanguage.INSTANCE);
    if (!settings.ALIGN_GROUP_FIELD_DECLARATIONS) {
      return range;
    }

    final PsiElement startElement = file.findElementAt(range.getStartOffset());
    if (startElement == null) {
      return range;
    }

    final PsiField parent = PsiTreeUtil.getParentOfType(startElement, PsiField.class);
    if (parent == null) {
      return range;
    }
    //endregion

    //region Calculating start offset to use by the start offset of the first sibling white space or field to the left of the current field.
    int startToUse = range.getStartOffset();
    for (PsiElement f = parent; f != null; f = f.getPrevSibling()) {
      final ASTNode node = f.getNode();
      if (node == null) {
        break;
      }
      if (JavaJspElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType()) || f instanceof PsiField) {
        startToUse = f.getTextRange().getStartOffset();
      } else if (!ElementType.JAVA_COMMENT_BIT_SET.contains(node.getElementType())) {
        break;
      }
    }
    //endregion

    //region Calculating end offset to use by the end offset of the last field in a group located to the right of the current field.
    int endToUse = range.getEndOffset();
    for (PsiElement f = parent; f != null; f = f.getNextSibling()) {
      final ASTNode node = f.getNode();
      if (node == null) {
        break;
      }
      if (f instanceof PsiField) {
        endToUse = f.getTextRange().getEndOffset();
      } else if (!JavaJspElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType()) &&
          !ElementType.JAVA_COMMENT_BIT_SET.contains(node.getElementType())) {
        break;
      }
    }
    //endregion

    return TextRange.from(startToUse, endToUse);
  }
}
