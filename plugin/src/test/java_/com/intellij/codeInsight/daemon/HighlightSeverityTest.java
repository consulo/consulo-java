/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;

public abstract class HighlightSeverityTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/highlightSeverity";


  public void testErrorLikeUnusedSymbol() throws Exception {
    enableInspectionTool(new LocalInspectionToolWrapper(new UnusedSymbolLocalInspection()) {
      @Nonnull
      @Override
      public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
      }
    });
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
