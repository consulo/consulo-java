package com.intellij.java.execution.impl.testframework;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;

/**
 * @author VISTALL
 * @since 2025-01-05
 */
@ExtensionImpl
public class TestRunLineMarkerProvider extends BaseTestRunLineMarkerProvider {
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }
}
