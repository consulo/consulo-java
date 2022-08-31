package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.FileTypeBasedRootFilter;

public class JavaJarRootFilter extends FileTypeBasedRootFilter
{
	public JavaJarRootFilter()
	{
		super(OrderRootType.CLASSES, true, JavaClassFileType.INSTANCE, "jar directory");
	}
}
