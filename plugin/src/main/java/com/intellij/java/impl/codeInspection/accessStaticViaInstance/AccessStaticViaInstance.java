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
package com.intellij.java.impl.codeInspection.accessStaticViaInstance;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix;
import com.intellij.java.language.psi.JavaResolveResult;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.annotation.component.ExtensionImpl;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
@ExtensionImpl
public class AccessStaticViaInstance extends AccessStaticViaInstanceBase {
  @Override
  protected AccessStaticViaInstanceFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr,
                                                                        boolean onTheFly,
                                                                        JavaResolveResult result) {
    return new AccessStaticViaInstanceFix(expr, result, onTheFly);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
