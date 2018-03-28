/*
 * User: anna
 * Date: 13-Feb-2008
 */
package com.intellij.coverage;

import java.io.File;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.coverage.info.CoberturaLoaderUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.java.execution.configurations.OwnJavaParameters;

public class CoberturaCoverageRunner extends JavaCoverageRunner {

  public ProjectData loadCoverageData(@Nonnull final File sessionDataFile, @javax.annotation.Nullable final CoverageSuite coverageSuite) {
    return CoberturaLoaderUtil.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final OwnJavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    @NonNls StringBuffer argument = new StringBuffer("-javaagent:");
    argument.append(PathManager.getLibPath()).append(File.separator);
    argument.append("cobertura.jar=");

    if (patterns != null && patterns.length > 0) {
      for (String coveragePattern : patterns) {
        coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
        if (!coveragePattern.endsWith(".*")) { //include inner classes
          coveragePattern += "(\\$.*)*";
        }
        argument.append("--includeClasses ").append(coveragePattern).append(" ");
      }
    }
    if (SystemInfo.isWindows) {
      argument.append("--datafile ").append("\\\"").append(sessionDataFilePath).append("\\\"");
    }
    else {
      argument.append("--datafile ").append(sessionDataFilePath);
    }
    javaParameters.getVMParametersList().add(argument.toString());
    javaParameters.getVMParametersList().defineProperty("net.sourceforge.cobertura.datafile", sessionDataFilePath);
    javaParameters.getClassPath().add(PathManager.getLibPath() + File.separator + "cobertura.jar");
  }

  public String getPresentableName() {
    return "Cobertura";
  }

  @NonNls
  public String getId() {
    return "cobertura";
  }

  @NonNls
  public String getDataFileExtension() {
    return "ser";
  }
}