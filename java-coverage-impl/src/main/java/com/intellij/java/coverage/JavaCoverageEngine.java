package com.intellij.java.coverage;

import com.intellij.java.analysis.impl.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.java.coverage.view.JavaCoverageViewExtension;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.application.ApplicationConfiguration;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.java.language.psi.*;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.compiler.CompileContext;
import consulo.compiler.CompileStatusNotification;
import consulo.compiler.CompilerManager;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.component.extension.Extensions;
import consulo.content.ContentFolderTypeProvider;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.*;
import consulo.execution.coverage.view.CoverageViewExtension;
import consulo.execution.test.AbstractTestProxy;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * @author Roman.Chernyatchik
 */
@ExtensionImpl
public class JavaCoverageEngine extends CoverageEngine {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class);

  @Override
  public boolean isApplicableTo(@Nullable final RunConfigurationBase conf) {
    if (conf instanceof CommonJavaRunConfigurationParameters) {
      return true;
    }
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
      if (extension.isApplicableTo(conf)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase conf) {
    return !(conf instanceof ApplicationConfiguration) && conf instanceof CommonJavaRunConfigurationParameters;
  }

  @Nonnull
  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(@Nullable final RunConfigurationBase conf) {
    return new JavaCoverageEnabledConfiguration(conf, this);
  }

  @Nullable
  @Override
  public CoverageSuite createCoverageSuite(@Nonnull final CoverageRunner covRunner,
                                           @Nonnull final String name,
                                           @Nonnull final CoverageFileProvider coverageDataFileProvider,
                                           String[] filters,
                                           long lastCoverageTimeStamp,
                                           String suiteToMerge,
                                           boolean coverageByTestEnabled,
                                           boolean tracingEnabled,
                                           boolean trackTestFolders, Project project) {

    return createSuite(covRunner, name, coverageDataFileProvider, filters, lastCoverageTimeStamp, coverageByTestEnabled,
        tracingEnabled, trackTestFolders, project);
  }

  @Override
  public CoverageSuite createCoverageSuite(@Nonnull final CoverageRunner covRunner,
                                           @Nonnull final String name,
                                           @Nonnull final CoverageFileProvider coverageDataFileProvider,
                                           @Nonnull final CoverageEnabledConfiguration config) {
    if (config instanceof JavaCoverageEnabledConfiguration) {
      final JavaCoverageEnabledConfiguration javaConfig = (JavaCoverageEnabledConfiguration) config;
      return createSuite(covRunner, name, coverageDataFileProvider,
          javaConfig.getPatterns(),
          new Date().getTime(),
          javaConfig.isTrackPerTestCoverage() && !javaConfig.isSampling(),
          !javaConfig.isSampling(),
          javaConfig.isTrackTestFolders(), config.getConfiguration().getProject());
    }
    return null;
  }

  @Nullable
  @Override
  public CoverageSuite createEmptyCoverageSuite(@Nonnull CoverageRunner coverageRunner) {
    return new JavaCoverageSuite(this);
  }

  @Nonnull
  @Override
  public CoverageAnnotator getCoverageAnnotator(Project project) {
    return JavaCoverageAnnotator.getInstance(project);
  }

  /**
   * Determines if coverage information should be displayed for given file
   *
   * @param psiFile
   * @return
   */
  public boolean coverageEditorHighlightingApplicableTo(@Nonnull final PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return false;
    }
    // let's show coverage only for module files
    final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      public Module compute() {
        return ModuleUtilCore.findModuleForPsiElement(psiFile);
      }
    });
    return module != null;
  }

  public boolean acceptedByFilters(@Nonnull final PsiFile psiFile, @Nonnull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    final Project project = psiFile.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (!suite.isTrackTestFolders() && fileIndex.isInTestSourceContent(virtualFile)) {
      return false;
    }

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite) coverageSuite;

      final List<PsiJavaPackage> packages = javaSuite.getCurrentSuitePackages(project);
      if (isUnderFilteredPackages((PsiClassOwner) psiFile, packages)) {
        return true;
      } else {
        final List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(project);
        for (PsiClass aClass : classes) {
          final PsiFile containingFile = aClass.getContainingFile();
          if (psiFile.equals(containingFile)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean recompileProjectAndRerunAction(@Nonnull final Module module, @Nonnull final CoverageSuitesBundle suite,
                                                @Nonnull final Runnable chooseSuiteAction) {
    final VirtualFile outputpath = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(ProductionContentFolderTypeProvider.getInstance
        ());
    final VirtualFile testOutputpath = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(TestContentFolderTypeProvider.getInstance());

    if ((outputpath == null && isModuleOutputNeeded(module, ProductionContentFolderTypeProvider.getInstance()))
        || (suite.isTrackTestFolders() && testOutputpath == null && isModuleOutputNeeded(module, TestContentFolderTypeProvider.getInstance()))) {
      final Project project = module.getProject();
      if (suite.isModuleChecked(module)) return false;
      suite.checkModule(module);
      final Runnable runnable = new Runnable() {
        public void run() {
          if (Messages.showOkCancelDialog(
              "Project class files are out of date. Would you like to recompile? The refusal to do it will result in incomplete coverage information",
              "Project is out of date", Messages.getWarningIcon()) == Messages.OK) {
            final CompilerManager compilerManager = CompilerManager.getInstance(project);
            compilerManager.make(compilerManager.createProjectCompileScope(), new CompileStatusNotification() {
              public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
                if (aborted || errors != 0) return;
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (project.isDisposed()) return;
                    CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
                  }
                });
              }
            });
          } else if (!project.isDisposed()) {
            CoverageDataManager.getInstance(project).chooseSuitesBundle(null);
          }
        }
      };
      ApplicationManager.getApplication().invokeLater(runnable);
      return true;
    }
    return false;
  }

  private static boolean isModuleOutputNeeded(Module module, final ContentFolderTypeProvider rootType) {
    return ModuleRootManager.getInstance(module).getContentFolderFiles(it -> it.equals(rootType)).length != 0;
  }

  public static boolean isUnderFilteredPackages(final PsiClassOwner javaFile, final List<PsiJavaPackage> packages) {
    final String hisPackageName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return javaFile.getPackageName();
      }
    });
    PsiPackage hisPackage = JavaPsiFacade.getInstance(javaFile.getProject()).findPackage(hisPackageName);
    if (hisPackage == null) return false;
    for (PsiPackage aPackage : packages) {
      if (PsiTreeUtil.isAncestor(aPackage, hisPackage, false)) return true;
    }
    return false;
  }

  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@Nonnull final File classFile, @Nonnull final CoverageSuitesBundle suite) {
    final List<Integer> uncoveredLines = new ArrayList<Integer>();

    final byte[] content;
    try {
      content = Files.readAllBytes(classFile.toPath());
    } catch (IOException e) {
      return null;
    }

    try {
      SourceLineCounterUtil.collectSrcLinesForUntouchedFiles(uncoveredLines, content, suite.isTracingEnabled());
    } catch (Exception e) {
      LOG.error("Fail to process class from: " + classFile.getPath(), e);
    }
    return uncoveredLines;
  }

  public boolean includeUntouchedFileInCoverage(@Nonnull final String qualifiedName,
                                                @Nonnull final File outputFile,
                                                @Nonnull final PsiFile sourceFile, @Nonnull CoverageSuitesBundle suite) {
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite) coverageSuite;
      if (javaSuite.isClassFiltered(qualifiedName) || javaSuite.isPackageFiltered(getPackageName(sourceFile)))
        return true;
    }
    return false;
  }


  public String getQualifiedName(@Nonnull final File outputFile, @Nonnull final PsiFile sourceFile) {
    final String packageFQName = getPackageName(sourceFile);
    return StringUtil.getQualifiedName(packageFQName, FileUtil.getNameWithoutExtension(outputFile));
  }

  @Nonnull
  @Override
  public Set<String> getQualifiedNames(@Nonnull final PsiFile sourceFile) {
    final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
      public PsiClass[] compute() {
        return ((PsiClassOwner) sourceFile).getClasses();
      }
    });
    final Set<String> qNames = new HashSet<String>();
    for (final JavaCoverageEngineExtension nameExtension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return nameExtension.suggestQualifiedName(sourceFile, classes, qNames);
        }
      })) {
        return qNames;
      }
    }
    for (final PsiClass aClass : classes) {
      final String qName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        public String compute() {
          return aClass.getQualifiedName();
        }
      });
      if (qName == null) continue;
      qNames.add(qName);
    }
    return qNames;
  }

  @Nonnull
  public Set<File> getCorrespondingOutputFiles(@Nonnull final PsiFile srcFile,
                                               @Nullable final Module module,
                                               @Nonnull final CoverageSuitesBundle suite) {
    if (module == null) {
      return Collections.emptySet();
    }
    final Set<File> classFiles = new HashSet<File>();
    final VirtualFile outputpath = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
    final VirtualFile testOutputpath = ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(TestContentFolderTypeProvider.getInstance());

    for (JavaCoverageEngineExtension extension : Extensions.getExtensions(JavaCoverageEngineExtension.EP_NAME)) {
      if (extension.collectOutputFiles(srcFile, outputpath, testOutputpath, suite, classFiles)) return classFiles;
    }

    final String packageFQName = getPackageName(srcFile);
    final String packageVmName = packageFQName.replace('.', '/');

    final List<File> children = new ArrayList<File>();
    final File vDir =
        outputpath == null
            ? null : packageVmName.length() > 0
            ? new File(outputpath.getPath() + File.separator + packageVmName) : VirtualFileUtil.virtualToIoFile(outputpath);
    if (vDir != null && vDir.exists()) {
      Collections.addAll(children, vDir.listFiles());
    }

    if (suite.isTrackTestFolders()) {
      final File testDir =
          testOutputpath == null
              ? null : packageVmName.length() > 0
              ? new File(testOutputpath.getPath() + File.separator + packageVmName) : VirtualFileUtil.virtualToIoFile(testOutputpath);
      if (testDir != null && testDir.exists()) {
        Collections.addAll(children, testDir.listFiles());
      }
    }

    final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
      public PsiClass[] compute() {
        return ((PsiClassOwner) srcFile).getClasses();
      }
    });
    for (final PsiClass psiClass : classes) {
      final String className = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return psiClass.getName();
        }
      });
      for (File child : children) {
        if (FileUtil.extensionEquals(child.getName(), JavaClassFileType.INSTANCE.getDefaultExtension())) {
          final String childName = FileUtil.getNameWithoutExtension(child);
          if (childName.equals(className) ||  //class or inner
              childName.startsWith(className) && childName.charAt(className.length()) == '$') {
            classFiles.add(child);
          }
        }
      }
    }
    return classFiles;
  }

  public String generateBriefReport(@Nonnull Editor editor,
                                    @Nonnull PsiFile psiFile,
                                    int lineNumber,
                                    int startOffset,
                                    int endOffset,
                                    @Nullable LineData lineData) {

    final StringBuffer buf = new StringBuffer();
    buf.append("Hits: ");
    if (lineData == null) {
      buf.append(0);
      return buf.toString();
    }
    buf.append(lineData.getHits()).append("\n");

    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();

    final Project project = editor.getProject();
    for (int offset = startOffset; offset < endOffset; offset++) {
      PsiElement parent = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiStatement.class);
      PsiElement condition = null;
      if (parent instanceof PsiIfStatement) {
        condition = ((PsiIfStatement) parent).getCondition();
      } else if (parent instanceof PsiSwitchStatement) {
        condition = ((PsiSwitchStatement) parent).getExpression();
      } else if (parent instanceof PsiDoWhileStatement) {
        condition = ((PsiDoWhileStatement) parent).getCondition();
      } else if (parent instanceof PsiForStatement) {
        condition = ((PsiForStatement) parent).getCondition();
      } else if (parent instanceof PsiWhileStatement) {
        condition = ((PsiWhileStatement) parent).getCondition();
      } else if (parent instanceof PsiForeachStatement) {
        condition = ((PsiForeachStatement) parent).getIteratedValue();
      } else if (parent instanceof PsiAssertStatement) {
        condition = ((PsiAssertStatement) parent).getAssertCondition();
      }
      if (condition != null && PsiTreeUtil.isAncestor(condition, psiFile.findElementAt(offset), false)) {
        try {
          final ControlFlow controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(
              parent, AllVariablesControlFlowPolicy.getInstance());
          for (Instruction instruction : controlFlow.getInstructions()) {
            if (instruction instanceof ConditionalBranchingInstruction) {
              final PsiExpression expression = ((ConditionalBranchingInstruction) instruction).expression;
              if (!expressions.contains(expression)) {
                expressions.add(expression);
              }
            }
          }
        } catch (AnalysisCanceledException e) {
          return buf.toString();
        }
      }
    }

    final String indent = "    ";
    try {
      int idx = 0;
      int hits = 0;
      if (lineData.getJumps() != null) {
        for (Object o : lineData.getJumps()) {
          final JumpData jumpData = (JumpData) o;
          if (jumpData.getTrueHits() + jumpData.getFalseHits() > 0) {
            final PsiExpression expression = expressions.get(idx++);
            final PsiElement parentExpression = expression.getParent();
            boolean reverse = parentExpression instanceof PsiPolyadicExpression && ((PsiPolyadicExpression) parentExpression).getOperationTokenType() == JavaTokenType.OROR
                || parentExpression instanceof PsiDoWhileStatement || parentExpression instanceof PsiAssertStatement;
            buf.append(indent).append(expression.getText()).append("\n");
            buf.append(indent).append(indent).append("true hits: ").append(reverse ? jumpData.getFalseHits() : jumpData.getTrueHits()).append("\n");
            buf.append(indent).append(indent).append("false hits: ").append(reverse ? jumpData.getTrueHits() : jumpData.getFalseHits()).append("\n");
            hits += jumpData.getTrueHits() + jumpData.getFalseHits();
          }
        }
      }

      if (lineData.getSwitches() != null) {
        for (Object o : lineData.getSwitches()) {
          final SwitchData switchData = (SwitchData) o;
          final PsiExpression conditionExpression = expressions.get(idx++);
          buf.append(indent).append(conditionExpression.getText()).append("\n");
          int i = 0;
          for (int key : switchData.getKeys()) {
            final int switchHits = switchData.getHits()[i++];
            buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchHits).append("\n");
            hits += switchHits;
          }
          int defaultHits = switchData.getDefaultHits();
          final boolean hasDefaultLabel = hasDefaultLabel(conditionExpression);
          if (hasDefaultLabel || defaultHits > 0) {
            if (!hasDefaultLabel) {
              defaultHits -= hits;
            }

            if (hasDefaultLabel || defaultHits > 0) {
              buf.append(indent).append(indent).append("default: ").append(defaultHits).append("\n");
              hits += defaultHits;
            }
          }
        }
      }
      if (lineData.getHits() > hits && hits > 0) {
        buf.append("Unknown outcome: ").append(lineData.getHits() - hits);
      }
    } catch (Exception e) {
      LOG.info(e);
      return "Hits: " + lineData.getHits();
    }
    return buf.toString();
  }

  @Nullable
  public String getTestMethodName(@Nonnull final PsiElement element,
                                  @Nonnull final AbstractTestProxy testProxy) {
    return testProxy.toString();
  }


  @Nonnull
  public List<PsiElement> findTestsByNames(@Nonnull String[] testNames, @Nonnull Project project) {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    for (String testName : testNames) {
      PsiClass psiClass =
          facade.findClass(StringUtil.getPackageName(testName, '_').replaceAll("\\_", "\\."), projectScope);
      int lastIdx = testName.lastIndexOf("_");
      if (psiClass != null) {
        collectTestsByName(elements, testName, psiClass, lastIdx);
      } else {
        String className = testName;
        while (lastIdx > 0) {
          className = className.substring(0, lastIdx - 1);
          psiClass = facade.findClass(StringUtil.getPackageName(className, '_').replaceAll("\\_", "\\."), projectScope);
          lastIdx = className.lastIndexOf("_");
          if (psiClass != null) {
            collectTestsByName(elements, testName, psiClass, lastIdx);
            break;
          }
        }
      }
    }
    return elements;
  }

  private static void collectTestsByName(List<PsiElement> elements, String testName, PsiClass psiClass, int lastIdx) {
    final PsiMethod[] testsByName = psiClass.findMethodsByName(testName.substring(lastIdx + 1), true);
    if (testsByName.length == 1) {
      elements.add(testsByName[0]);
    }
  }


  private static boolean hasDefaultLabel(final PsiElement conditionExpression) {
    boolean hasDefault = false;
    final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(conditionExpression, PsiSwitchStatement.class);
    final PsiCodeBlock body = ((PsiSwitchStatementImpl) conditionExpression.getParent()).getBody();
    if (body != null) {
      final PsiElement bodyElement = body.getFirstBodyElement();
      if (bodyElement != null) {
        PsiSwitchLabelStatement label = PsiTreeUtil.getNextSiblingOfType(bodyElement, PsiSwitchLabelStatement.class);
        while (label != null) {
          if (label.getEnclosingSwitchStatement() == switchStatement) {
            hasDefault |= label.isDefaultCase();
          }
          label = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatement.class);
        }
      }
    }
    return hasDefault;
  }

  protected JavaCoverageSuite createSuite(CoverageRunner acceptedCovRunner,
                                          String name, CoverageFileProvider coverageDataFileProvider,
                                          String[] filters,
                                          long lastCoverageTimeStamp,
                                          boolean coverageByTestEnabled,
                                          boolean tracingEnabled,
                                          boolean trackTestFolders, Project project) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, lastCoverageTimeStamp, coverageByTestEnabled, tracingEnabled,
        trackTestFolders, acceptedCovRunner, this, project);
  }

  @Nonnull
  protected static String getPackageName(final PsiFile sourceFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return ((PsiClassOwner) sourceFile).getPackageName();
      }
    });
  }

  @Override
  public String getPresentableText() {
    return "Java Coverage";
  }

  @Override
  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle,
                                                           CoverageViewManager.StateBean stateBean) {
    return new JavaCoverageViewExtension((JavaCoverageAnnotator) getCoverageAnnotator(project), project, suiteBundle, stateBean);
  }
}
