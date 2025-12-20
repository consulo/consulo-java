/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.javadoc;

import com.intellij.java.execution.configurations.JavaCommandLineStateUtil;
import com.intellij.java.language.JavadocBundle;
import com.intellij.java.language.impl.projectRoots.ex.PathUtilEx;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.ServerPageFile;
import consulo.application.progress.ProgressManager;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.configuration.ModuleRunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.RegexpFilter;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.webBrowser.BrowserUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author nik
 */
public class JavadocGeneratorRunProfile implements ModuleRunProfile {
  private static final Logger LOGGER = Logger.getInstance(JavadocConfiguration.class.getName());
  private final Project myProject;
  private final AnalysisScope myGenerationScope;
  private final JavadocConfiguration myConfiguration;

  public JavadocGeneratorRunProfile(Project project, AnalysisScope generationScope, JavadocConfiguration configuration) {
    myProject = project;
    myGenerationScope = generationScope;
    myConfiguration = configuration;
  }

  public static Sdk getSdk(@Nonnull Project project) {
    return PathUtilEx.getAnyJdk(project);
  }

  @Override
  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env) throws ExecutionException {
    return new MyJavaCommandLineState(myConfiguration, myProject, myGenerationScope, env);
  }

  @Override
  public String getName() {
    return JavadocBundle.message("javadoc.settings.title");
  }

  @Override
  public Image getIcon() {
    return null;
  }

  @Override
  @Nonnull
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  private static class MyJavaCommandLineState extends CommandLineState {
    private final AnalysisScope myGenerationOptions;
    private final Project myProject;
    @NonNls
    private static final String INDEX_HTML = "index.html";
    private JavadocConfiguration myConfiguration;

    public MyJavaCommandLineState(JavadocConfiguration configuration, Project project, AnalysisScope generationOptions, ExecutionEnvironment env) {
      super(env);
      myGenerationOptions = generationOptions;
      myProject = project;
      addConsoleFilters(new RegexpFilter(project, "$FILE_PATH$:$LINE$:[^\\^]+\\^"), new RegexpFilter(project, "$FILE_PATH$:$LINE$: warning - .+$"));
      this.myConfiguration = configuration;
    }

    protected GeneralCommandLine createCommandLine() throws ExecutionException {
      GeneralCommandLine cmdLine = new GeneralCommandLine();
      Sdk jdk = getSdk(myProject);
      setupExeParams(jdk, cmdLine);
      setupProgramParameters(jdk, cmdLine);
      return cmdLine;
    }

    private void setupExeParams(Sdk jdk, GeneralCommandLine cmdLine) throws ExecutionException {
      String jdkPath = jdk != null && jdk.getSdkType() instanceof JavaSdkType javaSdkType ? javaSdkType.getBinPath(jdk) : null;
      if (jdkPath == null) {
        throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
      }
      JavaSdkVersion version = JavaSdkTypeUtil.getVersion(jdk);
      if (myConfiguration.HEAP_SIZE != null && myConfiguration.HEAP_SIZE.trim().length() != 0) {
        if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_2)) {
          cmdLine.getParametersList().prepend("-J-Xmx" + myConfiguration.HEAP_SIZE + "m");
        } else {
          cmdLine.getParametersList().prepend("-J-mx" + myConfiguration.HEAP_SIZE + "m");
        }
      }
      cmdLine.setWorkDirectory((File) null);
      @NonNls String javadocExecutableName = File.separator + (Platform.current().os().isWindows() ? "javadoc.exe" : "javadoc");
      @NonNls String exePath = jdkPath.replace('/', File.separatorChar) + javadocExecutableName;
      if (new File(exePath).exists()) {
        cmdLine.setExePath(exePath);
      } else { //try to use wrapper jdk
        exePath = new File(jdkPath).getParent().replace('/', File.separatorChar) + javadocExecutableName;
        if (!new File(exePath).exists()) {
          File parent = new File(Platform.current().jvm().getRuntimeProperty("java.home")).getParentFile(); //try system jre
          exePath = parent.getPath() + File.separator + "bin" + javadocExecutableName;
          if (!new File(exePath).exists()) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.jdk.path"));
          }
        }
        cmdLine.setExePath(exePath);
      }
    }

    private void setupProgramParameters(Sdk jdk, GeneralCommandLine cmdLine) throws CantRunException {
      @NonNls ParametersList parameters = cmdLine.getParametersList();

      if (myConfiguration.LOCALE != null && myConfiguration.LOCALE.length() > 0) {
        parameters.add("-locale");
        parameters.add(myConfiguration.LOCALE);
      }

      if (myConfiguration.OPTION_SCOPE != null) {
        parameters.add("-" + myConfiguration.OPTION_SCOPE);
      }

      if (!myConfiguration.OPTION_HIERARCHY) {
        parameters.add("-notree");
      }

      if (!myConfiguration.OPTION_NAVIGATOR) {
        parameters.add("-nonavbar");
      }

      if (!myConfiguration.OPTION_INDEX) {
        parameters.add("-noindex");
      } else if (myConfiguration.OPTION_SEPARATE_INDEX) {
        parameters.add("-splitindex");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_USE) {
        parameters.add("-use");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_AUTHOR) {
        parameters.add("-author");
      }

      if (myConfiguration.OPTION_DOCUMENT_TAG_VERSION) {
        parameters.add("-version");
      }

      if (!myConfiguration.OPTION_DOCUMENT_TAG_DEPRECATED) {
        parameters.add("-nodeprecated");
      } else if (!myConfiguration.OPTION_DEPRECATED_LIST) {
        parameters.add("-nodeprecatedlist");
      }

      parameters.addParametersString(myConfiguration.OTHER_OPTIONS);

      Set<Module> modules = new LinkedHashSet<>();
      try {
        File sourcePathTempFile = FileUtil.createTempFile("javadoc", "args.txt", true);
        parameters.add("@" + sourcePathTempFile.getCanonicalPath());
        try (PrintWriter writer = new PrintWriter(new FileWriter(sourcePathTempFile))) {
          Collection<String> packages = new HashSet<>();
          Collection<String> sources = new HashSet<>();
          Runnable findRunnable = () ->
          {
            int scopeType = myGenerationOptions.getScopeType();
            boolean usePackageNotation = scopeType == AnalysisScope.MODULE || scopeType == AnalysisScope.MODULES
              || scopeType == AnalysisScope.PROJECT || scopeType == AnalysisScope.DIRECTORY;
            myGenerationOptions.accept(new MyContentIterator(myProject, packages, sources, modules, usePackageNotation));
          };
          if (!ProgressManager.getInstance()
            .runProcessWithProgressSynchronously(findRunnable, "Search for sources to generate javadoc in...", true, myProject)) {
            return;
          }
          if (packages.size() + sources.size() == 0) {
            throw new CantRunException(JavadocBundle.message("javadoc.generate.no.classes.in.selected.packages.error"));
          }
          for (String aPackage : packages) {
            writer.println(aPackage);
          }
          //http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#runningjavadoc
          for (String source : sources) {
            writer.println(StringUtil.wrapWithDoubleQuote(source));
          }
          writer.println("-sourcepath");
          OrderEnumerator enumerator = OrderEnumerator.orderEntries(myProject);
          if (!myConfiguration.OPTION_INCLUDE_LIBS) {
            enumerator = enumerator.withoutSdk().withoutLibraries();
          }
          PathsList pathsList = enumerator.getSourcePathsList();
          List<VirtualFile> files = pathsList.getRootDirs();
          ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          StringBuilder sourcePath = new StringBuilder();
          boolean start = true;
          for (VirtualFile file : files) {
            if (!myGenerationOptions.isIncludeTestSource() && fileIndex.isInTestSourceContent(file)) {
              continue;
            }
            if (start) {
              start = false;
            }
            else {
              sourcePath.append(File.pathSeparator);
            }
            sourcePath.append(file.getPath());
          }
          writer.println(StringUtil.wrapWithDoubleQuote(sourcePath.toString()));
        }
      } catch (IOException e) {
        LOGGER.error(e);
      }

      if (myConfiguration.OPTION_LINK_TO_JDK_DOCS) {
        VirtualFile[] docUrls = jdk.getRootProvider().getFiles(SourcesOrderRootType.getInstance());
        for (VirtualFile docUrl : docUrls) {
          parameters.add("-link");
          parameters.add(VirtualFileUtil.toUri(docUrl).toString());
        }
      }

      PathsList classPath;
      OrderEnumerator orderEnumerator = ProjectRootManager.getInstance(myProject).orderEntries(modules);
      if (jdk.getSdkType() instanceof JavaSdkType) {
        classPath = orderEnumerator.withoutSdk().withoutModuleSourceEntries().getPathsList();
      } else {
        //libraries are included into jdk
        classPath = orderEnumerator.withoutModuleSourceEntries().getPathsList();
      }
      String classPathString = classPath.getPathsString();
      if (classPathString.length() > 0) {
        parameters.add("-classpath");
        parameters.add(classPathString);
      }

      if (myConfiguration.OUTPUT_DIRECTORY != null) {
        parameters.add("-d");
        parameters.add(myConfiguration.OUTPUT_DIRECTORY.replace('/', File.separatorChar));
      }
    }

    @Override
    @Nonnull
    protected ProcessHandler startProcess() throws ExecutionException {
      ProcessHandler handler = JavaCommandLineStateUtil.startProcess(createCommandLine());
      ProcessTerminatedListener.attach(handler, myProject, JavadocBundle.message("javadoc.generate.exited"));
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(ProcessEvent event) {
          if (myConfiguration.OPEN_IN_BROWSER) {
            File url = new File(myConfiguration.OUTPUT_DIRECTORY, INDEX_HTML);
            if (url.exists() && event.getExitCode() == 0) {
              BrowserUtil.browse(url);
            }
          }
        }
      });
      return handler;
    }
  }

  private static class MyContentIterator extends PsiRecursiveElementWalkingVisitor {
    private final PsiManager myPsiManager;
    private final Collection<String> myPackages;
    private final Collection<String> mySourceFiles;
    private final Set<Module> myModules;
    private final boolean myUsePackageNotation;

    public MyContentIterator(Project project, Collection<String> packages, Collection<String> sources, Set<Module> modules, boolean canUsePackageNotation) {
      myModules = modules;
      myUsePackageNotation = canUsePackageNotation;
      myPsiManager = PsiManager.getInstance(project);
      myPackages = packages;
      mySourceFiles = sources;
    }

    @Override
    public void visitFile(PsiFile file) {
      VirtualFile fileOrDir = file.getVirtualFile();
      if (fileOrDir == null) {
        return;
      }
      if (!fileOrDir.isInLocalFileSystem()) {
        return;
      }
      Module module = ModuleUtilCore.findModuleForFile(fileOrDir, myPsiManager.getProject());
      if (module != null) {
        myModules.add(module);
      }
      if (file instanceof PsiJavaFile javaFile) {
        String packageName = javaFile.getPackageName();
        if (containsPackagePrefix(module, packageName) || (packageName.length() == 0 && !(javaFile instanceof ServerPageFile))
          || !myUsePackageNotation) {
          mySourceFiles.add(FileUtil.toSystemIndependentName(fileOrDir.getPath()));
        } else {
          myPackages.add(packageName);
        }
      }
    }

    private static boolean containsPackagePrefix(Module module, String packageFQName) {
      if (module == null) {
        return false;
      }
      /*for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries())
			{
				for (ContentFolder sourceFolder : contentEntry.getFolders(JavaModuleSourceRootTypes.SOURCES))
				{
					final String packagePrefix = sourceFolder.getPackagePrefix();
					final int prefixLength = packagePrefix.length();
					if (prefixLength > 0 && packageFQName.startsWith(packagePrefix))
					{
						return true;
					}
				}
			} */
      return false;
    }
  }
}
