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
 * @author anna
 * @since 2011-02-14
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JavaCoverageEngineExtension {
    public static final ExtensionPointName<JavaCoverageEngineExtension> EP_NAME =
        ExtensionPointName.create(JavaCoverageEngineExtension.class);

    public abstract boolean isApplicableTo(@Nullable RunConfigurationBase conf);

    public boolean suggestQualifiedName(@Nonnull PsiFile sourceFile, PsiClass[] classes, Set<String> names) {
        return false;
    }

    public boolean collectOutputFiles(
        @Nonnull PsiFile srcFile,
        @Nullable VirtualFile output,
        @Nullable VirtualFile testoutput,
        @Nonnull CoverageSuitesBundle suite,
        @Nonnull Set<File> classFiles
    ) {
        return false;
    }
}
