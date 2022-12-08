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
package consulo.java.impl.intelliLang.util;

import javax.annotation.Nonnull;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import consulo.java.impl.intelliLang.validation.InjectionNotApplicable;

public class RemoveAnnotationFix implements LocalQuickFix {
  private final LocalInspectionTool myTool;

  public RemoveAnnotationFix(LocalInspectionTool tool) {
    myTool = tool;
  }

  @Nonnull
  public String getName() {
    return "Remove Annotation";
  }

  @Nonnull
  public String getFamilyName() {
    return myTool.getGroupDisplayName();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    if (FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) {
      try {
        descriptor.getPsiElement().delete();
      }
      catch (IncorrectOperationException e) {
        Logger.getInstance(InjectionNotApplicable.class.getName()).error(e);
      }
    }
  }
}
