package com.intellij.java.coverage;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

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

    public boolean suggestQualifiedName(PsiFile sourceFile, PsiClass[] classes, Set<String> names) {
        return false;
    }

    public boolean collectOutputFiles(
        PsiFile srcFile,
        @Nullable VirtualFile output,
        @Nullable VirtualFile testoutput,
        CoverageSuitesBundle suite,
        Set<File> classFiles
    ) {
        return false;
    }
}
