package com.intellij.java.coverage;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Set;

/**
 * User: anna
 * Date: 2/14/11
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JavaCoverageEngineExtension {
  public static final ExtensionPointName<JavaCoverageEngineExtension> EP_NAME =
    ExtensionPointName.create(JavaCoverageEngineExtension.class);

  public abstract boolean isApplicableTo(@jakarta.annotation.Nullable RunConfigurationBase conf);

  public boolean suggestQualifiedName(@jakarta.annotation.Nonnull PsiFile sourceFile, PsiClass[] classes, Set<String> names) {
    return false;
  }

  public boolean collectOutputFiles(@jakarta.annotation.Nonnull final PsiFile srcFile,
                                    @Nullable final VirtualFile output,
                                    @jakarta.annotation.Nullable final VirtualFile testoutput,
                                    @jakarta.annotation.Nonnull final CoverageSuitesBundle suite,
                                    @Nonnull final Set<File> classFiles) {
    return false;
  }
}
