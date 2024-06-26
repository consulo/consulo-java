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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.psi.statistics.JavaStatisticsManager;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.psi.statistics.StatisticsInfo;
import consulo.ide.impl.psi.util.ProximityLocation;
import consulo.ide.impl.psi.util.proximity.ProximityStatistician;
import consulo.language.psi.PsiElement;

/**
 * @author peter
 */
@ExtensionImpl
public class JavaProximityStatistician extends ProximityStatistician {
  @Override
  public StatisticsInfo serialize(final PsiElement element, final ProximityLocation location) {
    return element instanceof PsiMember ? JavaStatisticsManager.createInfo(null, (PsiMember)element) : null;
  }
}
