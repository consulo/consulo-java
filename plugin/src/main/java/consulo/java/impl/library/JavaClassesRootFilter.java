package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.FileTypeBasedRootFilter;

public class JavaClassesRootFilter extends FileTypeBasedRootFilter
{
	public JavaClassesRootFilter()
	{
		super(OrderRootType.CLASSES, false, JavaClassFileType.INSTANCE, "java classes");
	}
}
