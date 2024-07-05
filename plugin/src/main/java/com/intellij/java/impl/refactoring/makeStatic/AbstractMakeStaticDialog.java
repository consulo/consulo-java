/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 04.07.2002
 * Time: 13:14:49
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.makeStatic;

import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.project.Project;
import consulo.usage.UsageViewUtil;

import javax.swing.*;

public abstract class AbstractMakeStaticDialog extends RefactoringDialog {
  protected final PsiTypeParameterListOwner myMember;
  protected final String myMemberName;

  public AbstractMakeStaticDialog(Project project, PsiTypeParameterListOwner member) {
    super(project, true);
    myMember = member;
    myMemberName = member.getName();
  }

  protected void doAction() {
    if (!validateData())
      return;

    final Settings settings = new Settings(
            isReplaceUsages(),
            isMakeClassParameter() ? getClassParameterName() : null,
            getVariableData()
    );
    if (myMember instanceof PsiMethod) {
      invokeRefactoring(new MakeMethodStaticProcessor(getProject(), (PsiMethod)myMember, settings));
    }
    else {
      invokeRefactoring(new MakeClassStaticProcessor(getProject(), (PsiClass)myMember, settings));
    }
  }

  protected abstract boolean validateData();

  public abstract boolean isMakeClassParameter();

  public abstract String getClassParameterName();

  public abstract VariableData[] getVariableData();

  public abstract boolean isReplaceUsages();

  protected JLabel createDescriptionLabel() {
    String type = UsageViewUtil.getType(myMember);
    return new JLabel(RefactoringLocalize.makeStaticDescriptionLabel(type, myMemberName).get());
  }
}
