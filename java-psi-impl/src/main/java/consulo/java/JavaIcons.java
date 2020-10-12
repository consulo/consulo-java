package consulo.java;

import consulo.annotation.DeprecationInfo;
import consulo.java.psi.impl.icon.JavaPsiImplIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use JavaPsiImplIconGroup")
public interface JavaIcons
{
	interface FileTypes
	{
		Image Java = JavaPsiImplIconGroup.fileTypesJava();
		Image JavaClass = JavaPsiImplIconGroup.fileTypesJavaClass();
		Image JavaOutsideSource = JavaPsiImplIconGroup.fileTypesJavaOutsideSource();
	}

	interface Gutter
	{
		Image EventMethod = JavaPsiImplIconGroup.gutterEventMethod();
		Image ExtAnnotation = JavaPsiImplIconGroup.gutterExtAnnotation();
	}

	interface Nodes
	{
		Image JavaModule = JavaPsiImplIconGroup.nodesJavaModule();
		Image NativeLibrariesFolder = JavaPsiImplIconGroup.nodesNativeLibrariesFolder();
	}

	Image Java = JavaPsiImplIconGroup.java();
}