package consulo.java.impl.library;

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.content.OrderRootType;
import consulo.content.library.ui.FileTypeBasedRootFilter;

public class JavaJarRootFilter extends FileTypeBasedRootFilter
{
	public JavaJarRootFilter()
	{
		super(OrderRootType.CLASSES, true, JavaClassFileType.INSTANCE, "jar directory");
	}
}
