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
package com.intellij.codeInsight.daemon;

import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.intention.IntentionManager;
import consulo.ide.impl.idea.concurrency.JobSchedulerImpl;
import consulo.component.extension.ExtensionPoint;
import consulo.language.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import consulo.util.lang.function.ThrowableRunnable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

public abstract class LightAdvHighlightingPerformanceTest extends LightDaemonAnalyzerTestCase {
  private final Disposable my = Disposable.newDisposable();
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    //Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LanguageAnnotators.EP_NAME)));
    //Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME)));
    //Disposer.register(my, BlockExtensions.create(Extensions.getArea(getProject()).getExtensionPoint(MultiHostInjector.EP_NAME)));

    IntentionManager.getInstance().getAvailableIntentionActions();  // hack to avoid slowdowns in PyExtensionFactory
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(my);
    super.tearDown();
  }

  private static class BlockExtensions<T> implements Disposable {
    private final ExtensionPoint<T> myEp;
    private T[] myExtensions;
    public BlockExtensions(ExtensionPoint<T> extensionPoint) {
      myEp = extensionPoint;
      block();
    }
    void block() {
      myExtensions = myEp.getExtensions();
      for (T extension : myExtensions) {
       // myEp.unregisterExtension(extension);
      }
    }

    void unblock() {
      for (T extension : myExtensions) {
        //myEp.registerExtension(extension);
      }
      myExtensions = null;
    }
    @Override
    public void dispose() {
      unblock();
    }
    public static <T> BlockExtensions<T> create(ExtensionPoint<T> extensionPoint) {
      return new BlockExtensions<T>(extensionPoint);
    }
  }

  private String getFilePath(final String suffix) {
    return LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(true) + suffix + ".java";
  }

  private List<HighlightInfo> doTest(final int maxMillis) throws Exception {
    configureByFile(getFilePath(""));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    final List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    PlatformTestUtil.startPerformanceTest(getTestName(false), maxMillis, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        infos.clear();
        DaemonCodeAnalyzer.getInstance(getProject()).restart();
        List<HighlightInfo> h = doHighlighting();
        infos.addAll(h);
      }
    }).cpuBound().usesAllCPUCores().assertTiming();

    return highlightErrors();
  }

  public void testAThinlet() throws Exception {
    List<HighlightInfo> errors = doTest(Math.max(10000, 24000 - JobSchedulerImpl.getCPUCoresCount() * 1000));
    if (1226 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }

  public void testAClassLoader() throws Exception {
    List<HighlightInfo> errors = doTest(Math.max(1000, 10000 - JobSchedulerImpl.getCPUCoresCount() * 1000));
    if (173 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }
}
