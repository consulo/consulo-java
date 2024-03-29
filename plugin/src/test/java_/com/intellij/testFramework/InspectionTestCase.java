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
package com.intellij.testFramework;

import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.inspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationPresentation;
import com.intellij.codeInspection.ex.*;
import consulo.ide.impl.idea.codeInspection.reference.EntryPoint;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.component.extension.ExtensionPoint;
import consulo.content.bundle.Sdk;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiUtilCore;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import consulo.util.collection.ArrayUtil;
import consulo.java.analysis.codeInspection.JavaExtensionPoints;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.io.File;

import static org.junit.Assert.assertNotNull;

/**
 * @author max
 * @since Apr 11, 2002
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class InspectionTestCase extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance(InspectionTestCase.class);
  private EntryPoint myUnusedCodeExtension;
  private VirtualFile ext_src;

  protected static GlobalInspectionToolWrapper getUnusedDeclarationWrapper() {
    InspectionEP ep = new InspectionEP();
    ep.presentation = UnusedDeclarationPresentation.class.getName();
    ep.implementationClass = UnusedDeclarationInspection.class.getName();
    ep.shortName = UnusedDeclarationInspection.SHORT_NAME;
    GlobalInspectionToolWrapper wrapper = new GlobalInspectionToolWrapper(ep);
    return wrapper;
  }

  public InspectionManagerEx getManager() {
    return (InspectionManagerEx)InspectionManager.getInstance(myProject);
  }

  public void doTest(@NonNls String folderName, LocalInspectionTool tool) {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }

  public void doTest(@NonNls String folderName, GlobalInspectionTool tool) {
    doTest(folderName, new GlobalInspectionToolWrapper(tool));
  }

  public void doTest(@NonNls String folderName, GlobalInspectionTool tool, boolean checkRange) {
    doTest(folderName, new GlobalInspectionToolWrapper(tool), checkRange);
  }

  public void doTest(@NonNls String folderName, GlobalInspectionTool tool, boolean checkRange, boolean runDeadCodeFirst) {
    doTest(folderName, new GlobalInspectionToolWrapper(tool), "java 1.4", checkRange, runDeadCodeFirst);
  }

  public void doTest(@NonNls String folderName, InspectionToolWrapper tool) {
    doTest(folderName, tool, "java 1.4");
  }

  public void doTest(@NonNls String folderName, InspectionToolWrapper tool, final boolean checkRange) {
    doTest(folderName, tool, "java 1.4", checkRange);
  }

  public void doTest(@NonNls String folderName, LocalInspectionTool tool, @NonNls final String jdkName) {
    doTest(folderName, new LocalInspectionToolWrapper(tool), jdkName);
  }

  public void doTest(@NonNls String folderName, InspectionToolWrapper tool, @NonNls final String jdkName) {
    doTest(folderName, tool, jdkName, false);
  }

  public void doTest(@NonNls String folderName, InspectionToolWrapper tool, @NonNls final String jdkName, boolean checkRange) {
    doTest(folderName, tool, jdkName, checkRange, false);
  }

  public void doTest(@NonNls String folderName,
                     InspectionToolWrapper toolWrapper,
                     @NonNls final String jdkName,
                     boolean checkRange,
                     boolean runDeadCodeFirst,
                     InspectionToolWrapper... additional) {
    final String testDir = getTestDataPath() + "/" + folderName;
    GlobalInspectionContextImpl context = runTool(testDir, jdkName, runDeadCodeFirst, toolWrapper, additional);

    InspectionTestUtil.compareToolResults(context, toolWrapper, checkRange, testDir);
  }

  protected void runTool(@NonNls final String testDir, @NonNls final String jdkName, final InspectionToolWrapper tool) {
    runTool(testDir, jdkName, false, tool);
  }

  protected GlobalInspectionContextImpl runTool(final String testDir,
                                                final String jdkName,
                                                boolean runDeadCodeFirst,
                                                @Nonnull InspectionToolWrapper toolWrapper,
                                                @Nonnull InspectionToolWrapper... additional) {
    final VirtualFile[] sourceDir = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          setupRootModel(testDir, sourceDir, jdkName);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
    AnalysisScope scope = createAnalysisScope(sourceDir[0].getParent());

    InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(getProject());
    InspectionToolWrapper[] toolWrappers = runDeadCodeFirst ? new InspectionToolWrapper []{getUnusedDeclarationWrapper(), toolWrapper} : new InspectionToolWrapper []{toolWrapper};
    toolWrappers = ArrayUtil.mergeArrays(toolWrappers, additional);
    final GlobalInspectionContextImpl globalContext =
      CodeInsightTestFixtureImpl.createGlobalContextForTool(scope, getProject(), inspectionManager, toolWrappers);

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext, inspectionManager);
    return globalContext;
  }

  @Nonnull
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    return new AnalysisScope(psiManager.findDirectory(sourceDir));
  }

  protected void setupRootModel(final String testDir, final VirtualFile[] sourceDir, final String sdkName) {
    VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir));
    assertNotNull("could not find project dir " + testDir, projectDir);
    sourceDir[0] = projectDir.findChild("src");
    if (sourceDir[0] == null) {
      sourceDir[0] = projectDir;
    }
    // IMPORTANT! The jdk must be obtained in a way it is obtained in the normal program!
    //ProjectJdkEx jdk = ProjectJdkTable.getInstance().getInternalJdk();
    PsiTestUtil.removeAllRoots(myModule, getTestProjectSdk());
    PsiTestUtil.addContentRoot(myModule, projectDir);
    PsiTestUtil.addSourceRoot(myModule, sourceDir[0]);
    ext_src = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir + "/ext_src"));
    if (ext_src != null) {
      PsiTestUtil.addSourceRoot(myModule, ext_src);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPoint<EntryPoint> point = Application.get().getExtensionPoint(JavaExtensionPoints.DEAD_CODE_EP_NAME);
    myUnusedCodeExtension = new EntryPoint() {
      @Nonnull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(PsiElement psiElement) {
        return ext_src != null && VfsUtilCore.isAncestor(ext_src, PsiUtilCore.getVirtualFile(psiElement), false);
      }

      @Override
      public boolean isSelected() {
        return false;
      }

      @Override
      public void setSelected(boolean selected) {

      }

      @Override
      public void readExternal(Element element) {

      }

      @Override
      public void writeExternal(Element element) {

      }
    };

    //point.registerExtension(myUnusedCodeExtension);
  }

  @Override
  protected void tearDown() throws Exception {
    ExtensionPoint<EntryPoint> point = Application.get().getExtensionPoint(JavaExtensionPoints.DEAD_CODE_EP_NAME);
   // point.unregisterExtension(myUnusedCodeExtension);
    myUnusedCodeExtension = null;
    ext_src = null;
    super.tearDown();
  }

  @Override
  protected void setUpJdk() {
  }

  protected Sdk getTestProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk17();
    //LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    return sdk;
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return "/inspection/";
  }
}
