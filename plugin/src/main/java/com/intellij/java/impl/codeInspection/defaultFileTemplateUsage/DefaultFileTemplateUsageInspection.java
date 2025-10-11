/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.impl.idea.ide.fileTemplates.impl.FileTemplateConfigurable;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
@ExtensionImpl
public class DefaultFileTemplateUsageInspection extends BaseJavaLocalInspectionTool {
  // Fields are left for the compatibility
  @Deprecated
  public boolean CHECK_FILE_HEADER = true;
  @Deprecated
  public boolean CHECK_TRY_CATCH_SECTION = true;
  @Deprecated
  public boolean CHECK_METHOD_BODY = true;

  @Override
  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.inspectionGeneralToolsGroupName();
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionLocalize.defaultFileTemplateDisplayName();
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return "DefaultFileTemplate";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    ProblemDescriptor descriptor = FileHeaderChecker.checkFileHeader(file, manager, isOnTheFly);
    return descriptor == null ? null : new ProblemDescriptor[]{descriptor};
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static LocalQuickFix createEditFileTemplateFix(FileTemplate templateToEdit, ReplaceWithFileTemplateFix replaceTemplateFix) {
    return new EditFileTemplateFix(templateToEdit, replaceTemplateFix);
  }

  private static class EditFileTemplateFix implements LocalQuickFix {
    private final FileTemplate myTemplateToEdit;
    private final ReplaceWithFileTemplateFix myReplaceTemplateFix;

    public EditFileTemplateFix(FileTemplate templateToEdit, ReplaceWithFileTemplateFix replaceTemplateFix) {
      myTemplateToEdit = templateToEdit;
      myReplaceTemplateFix = replaceTemplateFix;
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionLocalize.defaultFileTemplateEditTemplate();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
      final FileTemplateConfigurable configurable = new FileTemplateConfigurable(project);
      configurable.setTemplate(myTemplateToEdit, null);
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable).doWhenDone(
        () -> WriteCommandAction.runWriteCommandAction(
          project,
          () -> {
            FileTemplateManager.getInstance(project).saveAllTemplates();
            myReplaceTemplateFix.applyFix(project, descriptor);
          }
        )
      );
    }
  }
}
