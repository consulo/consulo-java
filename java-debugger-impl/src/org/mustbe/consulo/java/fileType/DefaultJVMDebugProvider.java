package org.mustbe.consulo.java.fileType;

import com.intellij.debugger.engine.JVMDebugProvider;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiFile;

/**
 * @author VISTALL
 * @since 22.03.14
 */
public class DefaultJVMDebugProvider implements JVMDebugProvider
{
	@Override
	public boolean supportsJVMDebugging(PsiFile file)
	{
		return file.getFileType() == JavaFileType.INSTANCE;
	}
}
