package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.content.OrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

public class JavaClassesRootFilter extends FileTypeBasedRootFilter
{
	public JavaClassesRootFilter()
	{
		super(OrderRootType.CLASSES, false, JavaClassFileType.INSTANCE, "java classes");
	}
}
