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
 * Date: 01.07.2002
 * Time: 15:48:33
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.makeStatic;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import jakarta.annotation.Nullable;

import java.util.HashMap;

import java.util.ArrayList;
import java.util.List;

public final class Settings {
  private final boolean myMakeClassParameter;
  private final String myClassParameterName;
  private final boolean myMakeFieldParameters;
  private final HashMap<PsiField,String> myFieldToNameMapping;
  private final ArrayList<FieldParameter> myFieldToNameList;
  private final boolean myReplaceUsages;


  public static final class FieldParameter {
    public FieldParameter(PsiField field, String name, PsiType type) {
      this.field = field;
      this.name = name;
      this.type = type;
    }

    public final PsiField field;
    public final String name;
    public final PsiType type;
  }


  public Settings(boolean replaceUsages, String classParameterName,
                  VariableData[] variableDatum) {
    myReplaceUsages = replaceUsages;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = variableDatum != null;
    myFieldToNameList = new ArrayList<FieldParameter>();
    if(myMakeFieldParameters) {
      myFieldToNameMapping = new HashMap<PsiField, String>();
      for (VariableData data : variableDatum) {
        if (data.passAsParameter) {
          myFieldToNameMapping.put((PsiField)data.variable, data.name);
          myFieldToNameList.add(new FieldParameter((PsiField)data.variable, data.name, data.type));
        }
      }
    }
    else {
      myFieldToNameMapping = null;
    }
  }

  public Settings(boolean replaceUsages, String classParameterName, 
                  PsiField[] fields, String[] names) {
    myReplaceUsages = replaceUsages;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = fields.length > 0;
    myFieldToNameList = new ArrayList<FieldParameter>();
    if (myMakeFieldParameters) {
      myFieldToNameMapping = new HashMap<PsiField, String>();
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        final String name = names[i];
        myFieldToNameMapping.put(field, name);
        myFieldToNameList.add(new FieldParameter(field, name, field.getType()));
      }
    }
    else {
      myFieldToNameMapping = null;
    }
  }
  
  public boolean isReplaceUsages() {
    return myReplaceUsages;
  }

  public boolean isMakeClassParameter() {
    return myMakeClassParameter;
  }

  public String getClassParameterName() {
    return myClassParameterName;
  }

  public boolean isMakeFieldParameters() {
    return myMakeFieldParameters;
  }

  @Nullable
  public String getNameForField(PsiField field) {
    if (myFieldToNameMapping != null) {
      return myFieldToNameMapping.get(field);
    }
    return null;
  }

  public List<FieldParameter> getParameterOrderList() {
    return myFieldToNameList;
  }

  public boolean isChangeSignature() {
    return isMakeClassParameter() || isMakeFieldParameters();
  }

  public int getNewParametersNumber() {
    final int result = isMakeFieldParameters() ? myFieldToNameList.size() : 0;
    return result + (isMakeClassParameter() ? 1 : 0);
  }
}
