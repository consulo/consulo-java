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
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;

import java.util.function.Function;

public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo {
  public static final Logger LOG = Logger.getInstance(ReplaceWithSubtypeUsageInfo.class);
  private final PsiTypeElement myTypeElement;
  private final PsiClassType myTargetClassType;
  private final PsiType myOriginalType;
  private String myConflict;

  public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClassType classType, final PsiClass[] targetClasses) {
    super(typeElement);
    myTypeElement = typeElement;
    myTargetClassType = classType;
    myOriginalType = myTypeElement.getType();
    if (targetClasses.length > 1) {
      myConflict = typeElement.getText() + " can be replaced with any of " + StringUtil.join(targetClasses, new Function<PsiClass, String>() {
        public String apply(final PsiClass psiClass) {
          return psiClass.getQualifiedName();
        }
      }, ", ") ;
    }
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myTypeElement.isValid()) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myTypeElement.getProject()).getElementFactory();
      myTypeElement.replace(elementFactory.createTypeElement(myTargetClassType));
    }
  }

  @Override
  public String getConflictMessage() {
    if (!TypeConversionUtil.isAssignable(myOriginalType, myTargetClassType)) {
      final String conflict = "No consistent substitution found for " +
                              getElement().getText() +
                              ". Expected \'" +
                              myOriginalType.getPresentableText() +
                              "\' but found \'" +
                              myTargetClassType.getPresentableText() +
                              "\'.";
      if (myConflict == null) {
        myConflict = conflict;
      } else {
        myConflict += "\n" + conflict;
      }
    }
    return myConflict;
  }
}
