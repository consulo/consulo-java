/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

import javax.annotation.Nonnull;

import java.util.EventListener;

/**
 * @author Evgeny Gerashchenko
 * @since  27 Aug 2012
 */
@TopicAPI(ComponentScope.PROJECT)
public interface ExternalAnnotationsListener extends EventListener {
  /**
   * Invoked at the end of annotateExternally/editExternalAnnotation/deannotate work.
   * It's invoked in both cases: either it was completed successfully or not.
   *
   * @param owner annotation owner
   * @param annotationFQName annotation class FQ name
   * @param successful if annotation modification was successful
   */
  void afterExternalAnnotationChanging(@Nonnull PsiModifierListOwner owner, @Nonnull String annotationFQName, boolean successful);

  /**
   * Invoked when external annotations files were modified
   */
  void externalAnnotationsChangedExternally();

  abstract class Adapter implements ExternalAnnotationsListener {
    @Override
    public void afterExternalAnnotationChanging(@Nonnull PsiModifierListOwner owner, @Nonnull String annotationFQName, boolean successful) {
    }

    @Override
    public void externalAnnotationsChangedExternally() {
    }
  }
}
