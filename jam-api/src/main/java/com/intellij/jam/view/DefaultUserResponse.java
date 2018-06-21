/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.jam.view;

import com.intellij.jam.JamMessages;
import com.intellij.jam.model.common.CommonModelElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.jam.view.ui.SelectElementsToDeleteDialog;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultUserResponse implements JamUserResponse {
  protected final Project myProject;

  public DefaultUserResponse(Project project) {
    myProject = project;
  }

  public DefaultUserResponse(final CommonModelElement element) {
    myProject = element.getPsiManager().getProject();
  }

  private final List<DeleteError> myErrors = new ArrayList<DeleteError>();

  public Collection<PsiElement> askUserToDeletePsiElements(Collection<PsiElement> ownedReferences, String elementDisplayName) {
    SelectElementsToDeleteDialog dialog = new SelectElementsToDeleteDialog(new ArrayList<PsiElement>(ownedReferences), myProject);
    dialog.show();
    if (dialog.isOK()) {
      return dialog.getSelectedItems();
    }
    else {
      return new ArrayList<PsiElement>();
    }

  }

  public void logErrorWhileDeletingModelElement(IncorrectOperationException e, CommonModelElement object) {
    throw new RuntimeException(e);
  }

  public void logErrorWhileDeletingPsiElement(IncorrectOperationException e, String source, PsiElement current) {
    myErrors.add(new DeleteError(e, source));
  }

  public int showYesNoDialog(String message, String title, Icon icon) {
    return Messages.showYesNoDialog(message, title, icon);
  }

  public void onDeletingHasBeenFinished() {
    if (!myErrors.isEmpty()) {
      StringBuffer errors = new StringBuffer();
      for (final DeleteError deleteError : myErrors) {
        errors.append(deleteError.getLocalizedMessage());
        errors.append("\n");
      }
      Messages.showErrorDialog(errors.toString(),
                               JamMessages.message("message.title.deleting.element", myErrors.get(0).getSourceDisplayString()));
    }
  }

  private static class DeleteError extends RuntimeException {
    private final String mySourceDisplayString;

    public DeleteError(Throwable cause, String sourceDisplayString) {
      super(cause);
      mySourceDisplayString = sourceDisplayString;
    }

    public String getSourceDisplayString() {
      return mySourceDisplayString;
    }
  }
}
