package com.intellij.java.language.internal;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;

/**
 * @author VISTALL
 * @since 02.06.2024
 */
public abstract class DefaultJavaSdkType extends JavaSdkType {
    protected DefaultJavaSdkType(String id, LocalizeValue displayName, Image icon) {
        super(id, displayName, icon);
    }
}
