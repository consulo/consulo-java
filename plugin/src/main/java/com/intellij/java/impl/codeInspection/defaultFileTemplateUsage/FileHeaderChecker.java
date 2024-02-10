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
package com.intellij.java.impl.codeInspection.defaultFileTemplateUsage;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.language.impl.JavaFileType;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.fileTemplate.FileTemplateUtil;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class FileHeaderChecker {
  private static final Logger LOG = Logger.getInstance(FileHeaderChecker.class);

  static ProblemDescriptor checkFileHeader(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean onTheFly) {
    IntObjectMap<String> offsetToProperty = IntMaps.newIntObjectHashMap();
    FileTemplate defaultTemplate = FileTemplateManager.getInstance(file.getProject()).getDefaultTemplate(JavaTemplateUtil.FILE_HEADER_TEMPLATE_NAME);
    Pattern pattern = FileTemplateUtil.getTemplatePattern(defaultTemplate, file.getProject(), offsetToProperty);
    Matcher matcher = pattern.matcher(file.getViewProvider().getContents());
    if (!matcher.matches()) {
      return null;
    }

    PsiComment element = PsiTreeUtil.findElementOfClassAtRange(file, matcher.start(1), matcher.end(1), PsiComment.class);
    if (element == null) {
      return null;
    }

    LocalQuickFix[] fixes = createQuickFix(matcher, offsetToProperty, file.getProject(), onTheFly);
    String description = InspectionsBundle.message("default.file.template.description");
    return manager.createProblemDescriptor(element, description, onTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }


  private static Properties computeProperties(final Matcher matcher, final IntObjectMap<String> offsetToProperty, Project project) {
    Properties properties = new Properties(FileTemplateManager.getInstance(project).getDefaultProperties());

    int[] offsets = offsetToProperty.keys();
    Arrays.sort(offsets);
    for (int i = 0; i < offsets.length; i++) {
      final int offset = offsets[i];
      String propName = offsetToProperty.get(offset);
      int groupNum = i + 2; // first group is whole doc comment
      String propValue = matcher.group(groupNum);
      properties.setProperty(propName, propValue);
    }

    return properties;
  }

  private static LocalQuickFix[] createQuickFix(final Matcher matcher, final IntObjectMap<String> offsetToProperty, Project project, boolean onTheFly) {
    final FileTemplate template = FileTemplateManager.getInstance(project).getPattern(JavaTemplateUtil.FILE_HEADER_TEMPLATE_NAME);

    ReplaceWithFileTemplateFix replaceTemplateFix = new ReplaceWithFileTemplateFix() {
      @Override
      public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
          return;
        }

        String newText;
        try {
          newText = template.getText(computeProperties(matcher, offsetToProperty, project)).trim();
        } catch (IOException e) {
          LOG.error(e);
          return;
        }

        if (!newText.isEmpty()) {
          PsiElement parent = element.getParent();
          PsiFile tempFile = PsiFileFactory.getInstance(project).createFileFromText("template.java", JavaFileType.INSTANCE, newText);
          for (PsiElement child : tempFile.getChildren()) {
            if (child.getTextLength() > 0) {
              parent.addBefore(child, element);
            }
          }
        }

        element.delete();
      }
    };

    if (onTheFly) {
      LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceTemplateFix);
      return template.isDefault() ? new LocalQuickFix[]{editFileTemplateFix} : new LocalQuickFix[]{
          replaceTemplateFix,
          editFileTemplateFix
      };
    }
    return template.isDefault() ? null : new LocalQuickFix[]{replaceTemplateFix};
  }
}
