package com.intellij.java.language.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiFile;


/**
 * @author Eugene.Kudelevsky
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ImportFilter {
    public static final ExtensionPointName<ImportFilter> EP_NAME = ExtensionPointName.create(ImportFilter.class);

    public abstract boolean shouldUseFullyQualifiedName(
        PsiFile targetFile,
        String classQualifiedName
    );

    public static boolean shouldImport(PsiFile targetFile, String classQualifiedName) {
        for (ImportFilter filter : EP_NAME.getExtensionList()) {
            if (filter.shouldUseFullyQualifiedName(targetFile, classQualifiedName)) {
                return false;
            }
        }
        return true;
    }
}
