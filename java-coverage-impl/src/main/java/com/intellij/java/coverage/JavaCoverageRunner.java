package com.intellij.java.coverage;

import consulo.application.Application;
import consulo.application.util.TempFileService;
import consulo.container.boot.ContainerPathManager;
import consulo.execution.coverage.CoverageEngine;
import consulo.execution.coverage.CoverageRunner;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  private static final Logger LOG = Logger.getInstance(JavaCoverageRunner.class);
  private static final String COVERAGE_AGENT_PATH = "coverage.lib.path";

  public boolean isJdk7Compatible() {
    return true;
  }

  @Override
  public boolean acceptsCoverageEngine(@Nonnull CoverageEngine engine) {
    return engine instanceof JavaCoverageEngine;
  }

  public abstract void appendCoverageArgument(
    final String sessionDataFilePath,
    @Nullable final String[] patterns,
    final OwnJavaParameters parameters,
    final boolean collectLineInfo,
    final boolean isSampling
  );

  @Nonnull
  protected static String handleSpacesInPath(@Nonnull File parent) {
    String agentPath;
    final String userDefined = Platform.current().jvm().getRuntimeProperty(COVERAGE_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      agentPath = userDefined;
    } else {
      agentPath = parent.getParent();
    }
    if (!Platform.current().os().isWindows() && agentPath.contains(" ")) {
      File dir = new File(ContainerPathManager.get().getSystemPath(), "coverageJars");
      if (dir.getAbsolutePath().contains(" ")) {
        try {
          TempFileService tempFileService = Application.get().getInstance(TempFileService.class);
          dir = tempFileService.createTempDirectory("coverage", "jars").toFile();
          if (dir.getAbsolutePath().contains(" ")) {
            LOG.info("Coverage agent not used since the agent path contains spaces: " + agentPath + "\n" +
                "One can move the agent libraries to a directory with no spaces in path and specify its path in idea.properties as "
                + COVERAGE_AGENT_PATH + "=<path>");
            return agentPath;
          }
        } catch (IOException e) {
          LOG.info(e);
          return agentPath;
        }
      }

      try {
        LOG.info("Coverage jars were copied to " + dir.getPath());
        FileUtil.copyDir(new File(agentPath), dir, FilePermissionCopier.BY_NIO2);
        return dir.getPath();
      } catch (IOException e) {
        LOG.info(e);
      }
    }
    return agentPath;
  }

  protected static void write2file(File tempFile, String arg) throws IOException {
    FileUtil.writeToFile(tempFile, (arg + "\n").getBytes("UTF-8"), true);
  }

  protected static File createTempFile() throws IOException {
    File tempFile = FileUtil.createTempFile("coverage", "args");
    if (!Platform.current().os().isWindows() && tempFile.getAbsolutePath().contains(" ")) {
      tempFile = FileUtil.createTempFile(
        new File(ContainerPathManager.get().getSystemPath(), "coverage"),
        "coverage",
        "args",
        true
      );
      if (tempFile.getAbsolutePath().contains(" ")) {
        final String userDefined = Platform.current().jvm().getRuntimeProperty(COVERAGE_AGENT_PATH);
        if (userDefined != null && new File(userDefined).isDirectory()) {
          tempFile = FileUtil.createTempFile(new File(userDefined), "coverage", "args", true);
        }
      }
    }
    return tempFile;
  }
}
