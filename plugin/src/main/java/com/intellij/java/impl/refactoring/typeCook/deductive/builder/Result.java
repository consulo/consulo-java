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
package com.intellij.java.impl.refactoring.typeCook.deductive.builder;

import com.intellij.java.impl.refactoring.typeCook.Settings;
import com.intellij.java.impl.refactoring.typeCook.Util;
import com.intellij.java.impl.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.java.language.psi.Bottom;
import com.intellij.java.language.psi.PsiTypeVariable;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author db
 */
public class Result {
  private static final Logger LOG = Logger.getInstance(Result.class);

  private final HashSet<PsiElement> myVictims;
  private final HashMap<PsiElement, PsiType> myTypes;
  private final Settings mySettings;
  private final HashMap<PsiTypeCastExpression, PsiType> myCastToOperandType;

  private int myCookedNumber = -1;
  private int myCastsRemoved = -1;
  private final int myCastsNumber;

  private Binding myBinding;

  public Result(ReductionSystem system) {
    myVictims = system.myElements;
    myTypes = system.myTypes;
    mySettings = system.mySettings;
    myCastToOperandType = system.myCastToOperandType;
    myCastsNumber = myCastToOperandType.size();
  }

  public void incorporateSolution(Binding binding) {
    if (myBinding == null) {
      myBinding = binding;
    }
    else {
      myBinding.merge(binding, mySettings.leaveObjectParameterizedTypesRaw());
    }
  }

  public PsiType getCookedType(PsiElement element) {
    PsiType originalType = Util.getType(element);

    if (myBinding != null) {
      PsiType type = myBinding.substitute(myTypes.get(element));

      String objectFQName = CommonClassNames.JAVA_LANG_OBJECT;
      if (originalType.getCanonicalText().equals(objectFQName)) {
        if (type == null) {
          return originalType;
        }

        if (type instanceof PsiWildcardType){
          PsiType bound = ((PsiWildcardType)type).getBound();

          if (bound != null){
            return bound;
          }

          return originalType;
        }
      }

      return type;
    }

    return originalType;
  }

  public HashSet<PsiElement> getCookedElements() {
    myCookedNumber = 0;

    HashSet<PsiElement> set = new HashSet<PsiElement>();

    for (PsiElement element : myVictims) {
      PsiType originalType = Util.getType(element);

      PsiType cookedType = getCookedType(element);
      if (cookedType != null && !originalType.equals(cookedType)) {
        set.add(element);
        myCookedNumber++;
      }
    }

    if (mySettings.dropObsoleteCasts()) {
      myCastsRemoved = 0;
      if (myBinding != null) {
        for (Map.Entry<PsiTypeCastExpression,PsiType> entry : myCastToOperandType.entrySet()) {
          PsiTypeCastExpression cast = entry.getKey();
          PsiType operandType = myBinding.apply(entry.getValue());
          PsiType castType = cast.getType();
          if (!(operandType instanceof PsiTypeVariable) && castType != null && !isBottomArgument(operandType) && castType.isAssignableFrom(operandType)) {
            set.add(cast);
          }
        }
      }
    }

    return set;
  }

  private static boolean isBottomArgument(PsiType type) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
    PsiClass clazz = resolveResult.getElement();
    if (clazz != null) {
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(clazz)) {
        PsiType t = resolveResult.getSubstitutor().substitute(typeParameter);
        if (t == Bottom.BOTTOM) return true;
      }
    }

    return false;
  }

  public void apply(HashSet<PsiElement> victims) {
    for (PsiElement element : victims) {
      if (element instanceof PsiTypeCastExpression && myCastToOperandType.containsKey(element)) {
        PsiTypeCastExpression cast = ((PsiTypeCastExpression)element);
        try {
          cast.replace(cast.getOperand());
          myCastsRemoved++;
        }
        catch (IncorrectOperationException e1) {
          LOG.error(e1);
        }

      } else {
        Util.changeType(element, getCookedType(element));
      }
    }
  }

  private String getRatio(int x, int y) {
    String ratio = RefactoringBundle.message("type.cook.ratio.generified", x, y);
    return ratio + (y != 0 ? " (" + (x * 100 / y) + "%)" : "");
  }

  public String getReport() {
    return RefactoringLocalize.typeCookReport(getRatio(myCookedNumber, myVictims.size()), getRatio(myCastsRemoved, myCastsNumber)).get();
  }
}
