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
package com.intellij.patterns;

import com.intellij.psi.PsiField;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public class PsiFieldPattern extends PsiMemberPattern<PsiField, PsiFieldPattern>{
  public PsiFieldPattern() {
    super(new InitialPatternCondition<PsiField>(PsiField.class) {
      public boolean accepts(@javax.annotation.Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiField;
      }
    });
  }
}
