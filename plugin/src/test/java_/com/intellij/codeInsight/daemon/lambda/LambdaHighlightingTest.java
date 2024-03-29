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
package com.intellij.codeInsight.daemon.lambda;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;

public abstract class LambdaHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/highlighting";

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
    };
  }

  public void testStaticAccess() { doTest(); }
  public void testEffectiveFinal() { doTest(); }
  public void testReassignUsedVars() { doTest(); }
  public void testLambdaContext() { doTest(); }
  public void testReturnTypeCompatibility() { doTest(); }
  public void testTypeArgsConsistency() { doTest(); }
  public void testTypeArgsConsistencyMisc1() { doTest(); }
  public void testTypeArgsConsistencyMisc2() { doTest(); }
  public void testTypeArgsConsistencyWithoutParams() { doTest(); }
  public void testIncompatibleReturnTypes() { doTest(); }
  public void testWildcardBounds() { doTest(); }
  public void testInferenceOnMethodCallSite() { doTest(); }
  public void testInferFromTypeArgs() { doTest(); }
  public void testAmbiguity1() { doTest(); }
  //public void testAmbiguity2() { doTest(); }
  public void testAmbiguityVarargs() { doTest(); }
  public void testAmbiguityRawGenerics() { doTest(); }
  public void testDefaultMethod() { doTest(); }
  public void testLambdaOnVarargsPlace() { doTest(); }
  public void testLambdaRawOrNot() { doTest(); }
  public void testReturnTypeCompatibility1() { doTest(); }
  public void testNoInferenceResult() { doTest(); }
  public void testInferenceFromArgs() { doTest(); }
  public void testInContexts() { doTest(); }
  public void testCastInContexts() { doTest(); }
  public void testUnhandledException() { doTest(); }
  public void testConditionalExpr() { doTest(); }
  public void testIncompleteSubst() { doTest(); }
  public void testVariableInitialization() { doTest(); }
  public void testUnreachableStatement() { doTest(); }
  public void testUnhandledExceptions() { doTest(); }
  public void testReturnValue() { doTest(); }
  public void testAlreadyUsedParamName() { doTest(); }
  public void testRecursiveAccess() { doTest(); }
  public void testIncompatibleFormalParameterTypes() { doTest(); }
  public void testNestedLambdas() { doTest(); }
  public void testEnumConstants() { doTest(); }
  public void testRawWhenNoParams() { doTest(); }
  public void testUseIncompleteParentSubstitutor() { doTest(); }
  public void testReturnTypeCompatibilityBeforeSpecificsCheck() { doTest(); }
  public void testIntersectionTypeInCast() { doTest(); }
  public void testAmbiguitySpecificReturn() { doTest(true); }
  public void testFunctionalInterfaceAnnotation() { doTest(); }
  public void testAmbiguityReturnValueResolution() { doTest(); }
  public void testAmbiguityReturnValueResolution1() { doTest(); }
  public void testAmbiguityReturnValueResolution2() { doTest(true); }
  public void testAmbiguityReturnValueResolution3() { doTest(); }
  public void testLambdaOnVarargsPlace1() { doTest(); }
  public void testInferenceFromSecondLambda() { doTest(); }
  public void testAcceptRawSubstForLambda() { doTest(); }
  public void testCheckFunctionalInterfaceAccess() { doTest(); }
  public void testVoidCompatibility() { doTest(); }
  public void testConditionalInferenceFromOppositePart() { doTest(); }
  public void testDeclaredTypeParameterBoundsAndUnboundedWildcard() { doTest(); }
  public void testConflictResolution() throws Exception {doTest();}

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }
}
