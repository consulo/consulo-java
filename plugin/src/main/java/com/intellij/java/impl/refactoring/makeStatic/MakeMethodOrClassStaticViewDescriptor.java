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
 * Date: 16.04.2002
 * Time: 15:54:37
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.makeStatic;

import com.intellij.java.language.psi.PsiMember;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

public class MakeMethodOrClassStaticViewDescriptor implements UsageViewDescriptor {

  private final PsiMember myMember;
  private final String myProcessedElementsHeader;

  public MakeMethodOrClassStaticViewDescriptor(PsiMember member
  ) {
    myMember = member;
    String who = StringUtil.capitalize(UsageViewUtil.getType(myMember));
    myProcessedElementsHeader = RefactoringLocalize.makeStaticElementsHeader(who).get();
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[]{myMember};
  }


  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringLocalize.referencesToBeChanged(UsageViewBundle.getReferencesString(usagesCount, filesCount)).get();
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
