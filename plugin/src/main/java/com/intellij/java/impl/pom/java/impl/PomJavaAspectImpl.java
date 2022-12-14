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
package com.intellij.java.impl.pom.java.impl;

import com.intellij.java.impl.pom.java.PomJavaAspect;
import com.intellij.java.impl.pom.java.events.PomJavaAspectChangeSet;
import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ServiceImpl;
import consulo.language.pom.PomModel;
import consulo.language.pom.PomModelAspect;
import consulo.language.pom.TreeAspect;
import consulo.language.pom.event.PomModelEvent;
import consulo.language.pom.event.TreeChangeEvent;
import consulo.language.psi.PsiFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;

@Singleton
@ServiceImpl
public class PomJavaAspectImpl extends PomJavaAspect {
  private final PomModel myPomModel;

  @Inject
  public PomJavaAspectImpl(TreeAspect treeAspect, PomModel pomModel) {
    myPomModel = pomModel;
    pomModel.registerAspect(PomJavaAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));
  }

  public void update(PomModelEvent event) {
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myPomModel.getModelAspect(TreeAspect.class));
    if(changeSet == null) return;
    final PsiFile containingFile = changeSet.getRootElement().getPsi().getContainingFile();
    if(!(containingFile.getLanguage() instanceof JavaLanguage)) return;
    final PomJavaAspectChangeSet set = new PomJavaAspectChangeSet(myPomModel);
    event.registerChangeSet(this, set);
  }
}
