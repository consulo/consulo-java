package com.intellij.coverage;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.consulo.compiler.ModuleCompilerPathsManager;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.TestContentFolderTypeProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.util.classFinder.ClassFinder;
import com.intellij.rt.coverage.util.classFinder.ClassPathEntry;
import com.intellij.util.lang.UrlClassLoader;

/**
* @author anna
*/
class IdeaClassFinder extends ClassFinder {
  private static final Logger LOG = Logger.getInstance("#" + IdeaClassFinder.class.getName());

  private final Project myProject;
  private final CoverageSuitesBundle myCurrentSuite;

  public IdeaClassFinder(Project project, CoverageSuitesBundle currentSuite) {
    super(obtainPatternsFromSuite(currentSuite), new ArrayList());
    myProject = project;
    myCurrentSuite = currentSuite;
  }

  private static List<Pattern> obtainPatternsFromSuite(CoverageSuitesBundle currentSuiteBundle) {
    final List<Pattern> includePatterns = new ArrayList<Pattern>();
    for (CoverageSuite currentSuite : currentSuiteBundle.getSuites()) {
      for (String pattern : ((JavaCoverageSuite)currentSuite).getFilteredPackageNames()) {
        includePatterns.add(Pattern.compile(pattern + ".*"));
      }

      for (String pattern : ((JavaCoverageSuite)currentSuite).getFilteredClassNames()) {
        includePatterns.add(Pattern.compile(pattern));
      }
    }
    return includePatterns;
  }

  @Override
  protected Collection getClassPathEntries() {
    final Collection entries = super.getClassPathEntries();
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleCompilerPathsManager extension = ModuleCompilerPathsManager.getInstance(module);
      if (extension != null) {
        final VirtualFile outputFile = extension.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
        try {
          if (outputFile != null) {
            final URL outputURL = VfsUtilCore.virtualToIoFile(outputFile).toURI().toURL();
            entries.add(new ClassPathEntry(outputFile.getPath(), UrlClassLoader.build().urls(outputURL).get()));
          }
          if (myCurrentSuite.isTrackTestFolders()) {
            final VirtualFile testOutput = extension.getCompilerOutput(TestContentFolderTypeProvider.getInstance());
            if (testOutput != null) {
              final URL testOutputURL = VfsUtilCore.virtualToIoFile(testOutput).toURI().toURL();
              entries.add(new ClassPathEntry(testOutput.getPath(), UrlClassLoader.build().urls(testOutputURL).get()));
            }
          }
        }
        catch (MalformedURLException e1) {
          LOG.error(e1);
        }
      }
    }
    return entries;
  }
}
