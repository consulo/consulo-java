package com.intellij.debugger.engine;

import org.mustbe.consulo.DeprecationInfo;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author VISTALL
 * @since 15:29/30.06.13
 */
@Deprecated
@DeprecationInfo(value = "Not supported. Use extension 'org.consulo.java.debugger.jvmDebugProvider'", until = "1.0")
public interface FileTypeWithJvmDebugging extends FileType {
}
