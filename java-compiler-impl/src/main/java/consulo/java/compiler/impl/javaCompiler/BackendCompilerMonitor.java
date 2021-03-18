package consulo.java.compiler.impl.javaCompiler;

import com.intellij.execution.process.ProcessHandler;
import consulo.disposer.Disposable;

/**
 * @author VISTALL
 * @since 13/03/2021
 */
public interface BackendCompilerMonitor extends Disposable
{
	default void handleProcessStart(ProcessHandler process)
	{
	}
}
