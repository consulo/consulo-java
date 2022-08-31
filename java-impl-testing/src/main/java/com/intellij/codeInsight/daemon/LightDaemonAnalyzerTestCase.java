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
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(LightPlatformTestCase.getProject())).prepareForTest();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
  }

  @Override
  protected void tearDown() throws Exception {
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(LightPlatformTestCase.getProject())).cleanupAfterTest();
    super.tearDown();
  }

  @Override
  protected void runTest() throws Throwable {
    final Throwable[] throwable = {null};
    CommandProcessor.getInstance().executeCommand(LightPlatformTestCase.getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          doRunTest();
        }
        catch (Throwable t) {
          throwable[0] = t;
        }
      }
    }, "", null);
    if (throwable[0] != null) {
      throw throwable[0];
    }
  }

  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkInfos) {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkInfos, filePath);
  }

  protected void doTestNewInference(@NonNls String filePath, boolean checkWarnings, boolean checkInfos) {
    final PsiResolveHelperImpl helper = (PsiResolveHelperImpl)JavaPsiFacade.getInstance(LightPlatformTestCase.getProject()).getResolveHelper();
    //helper.setTestHelper(new PsiGraphInferenceHelper(getPsiManager()));
    try {
      configureByFile(filePath);
      doTestConfiguredFile(checkWarnings, checkInfos, filePath);
    }
    finally {
   //   helper.setTestHelper(null);
    }
  }

  protected void doTest(@NonNls String filePath, boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    configureByFile(filePath);
    doTestConfiguredFile(checkWarnings, checkWeakWarnings, checkInfos, filePath);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkInfos, @Nullable String filePath) {
    doTestConfiguredFile(checkWarnings, false, checkInfos, filePath);
  }

  protected void doTestConfiguredFile(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos, @Nullable String filePath) {
    LightCodeInsightTestCase.getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, null);

    ExpectedHighlightingData data = new ExpectedHighlightingData(LightPlatformCodeInsightTestCase.getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
    checkHighlighting(data, composeLocalPath(filePath));
  }

  @Nullable
  private String composeLocalPath(@Nullable String filePath) {
    return filePath != null ? getTestDataPath() + "/" + filePath : null;
  }

  private void checkHighlighting(ExpectedHighlightingData data, String filePath) {
    data.init();

    PsiDocumentManager.getInstance(LightPlatformTestCase.getProject()).commitAllDocuments();
    LightPlatformCodeInsightTestCase.getFile().getText(); //to load text
    myJavaFilesFilter.allowTreeAccessForFile(LightPlatformCodeInsightTestCase.getVFile());
    LightCodeInsightTestCase.getJavaFacade().setAssertOnFileLoadingFilter(myJavaFilesFilter, null); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    LightCodeInsightTestCase.getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, null);

    data.checkResult(infos, LightPlatformCodeInsightTestCase.getEditor().getDocument().getText(), filePath);
  }

  protected HighlightTestInfo doTestFile(@NonNls @Nonnull String filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        String path = UsefulTestCase.assertOneElement(filePaths);
        configureByFile(path);
        ExpectedHighlightingData data = new ExpectedHighlightingData(LightPlatformCodeInsightTestCase.myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos,
				LightPlatformCodeInsightTestCase.myFile);
        if (checkSymbolNames) data.checkSymbolNames();

        checkHighlighting(data, composeLocalPath(path));
        return this;
      }
    };
  }

  @Nonnull
  protected List<HighlightInfo> highlightErrors() {
    return doHighlighting(HighlightSeverity.ERROR);
  }

  @Nonnull
  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(LightPlatformTestCase.getProject()).commitAllDocuments();

    IntList toIgnoreList = IntLists.newArrayList();
    if (!doFolding()) {
      toIgnoreList.add(Pass.UPDATE_FOLDING);
    }
    if (!doInspections()) {
      toIgnoreList.add(Pass.LOCAL_INSPECTIONS);
    }
    int[] toIgnore = toIgnoreList.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : toIgnoreList.toArray();
    Editor editor = LightPlatformCodeInsightTestCase.getEditor();
    PsiFile file = LightPlatformCodeInsightTestCase.getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, false);
  }

  protected List<HighlightInfo> doHighlighting(HighlightSeverity minSeverity) {
    return DaemonAnalyzerTestCase.filter(doHighlighting(), minSeverity);
  }

  protected boolean doFolding() {
    return false;
  }

  protected boolean doInspections() {
    return true;
  }
}
