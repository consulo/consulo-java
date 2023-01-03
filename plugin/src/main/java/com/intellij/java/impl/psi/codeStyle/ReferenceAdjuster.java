/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.codeStyle;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ReferenceAdjuster extends LanguageExtension {
  ExtensionPointCacheKey<ReferenceAdjuster, ByLanguageValue<ReferenceAdjuster>> KEY = ExtensionPointCacheKey.create("ReferenceAdjuster", LanguageOneToOne.build());

  @Nullable
  static ReferenceAdjuster forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(ReferenceAdjuster.class).getOrBuildCache(KEY).get(language);
  }

  ASTNode process(ASTNode element, boolean addImports, boolean incompleteCode, boolean useFqInJavadoc, boolean useFqInCode);

  ASTNode process(ASTNode element, boolean addImports, boolean incompleteCode, Project project);

  void processRange(ASTNode element, int startOffset, int endOffset, boolean useFqInJavadoc, boolean useFqInCode);

  void processRange(ASTNode element, int startOffset, int endOffset, Project project);
}
