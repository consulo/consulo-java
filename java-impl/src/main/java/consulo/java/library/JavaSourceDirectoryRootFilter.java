package consulo.java.library;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.FileTypeBasedRootFilter;

/**
 * @author VISTALL
 * @since 01.02.14
 */
public class JavaSourceDirectoryRootFilter extends FileTypeBasedRootFilter
{
	public JavaSourceDirectoryRootFilter()
	{
		super(OrderRootType.SOURCES, true, JavaFileType.INSTANCE, "java source archive directory");
	}
}
