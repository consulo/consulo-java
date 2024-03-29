
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
package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.java.analysis.impl.codeInspection.deprecation.DeprecationInspection;
import consulo.ide.impl.idea.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.java.impl.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.java.impl.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.java.impl.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.java.impl.codeInspection.unusedParameters.UnusedParametersInspection;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;


public abstract class Suppress15InspectionsTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new GlobalInspectionToolWrapper(new UnusedParametersInspection()));
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantThrowsDeclaration(),
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new JavaDocReferenceInspection(),
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppress15Inspections";
  }

}