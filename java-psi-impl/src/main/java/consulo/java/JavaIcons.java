package consulo.java;

import com.intellij.openapi.util.IconLoader;
import consulo.ui.image.Image;

// Generated Consulo DevKit plugin 
public interface JavaIcons
{
	interface FileTypes
	{
		Image Java = IconLoader.getIcon("/icons/fileTypes/java.png");  // 16x16
		Image JavaClass = IconLoader.getIcon("/icons/fileTypes/javaClass.png");  // 16x16
		Image JavaOutsideSource = IconLoader.getIcon("/icons/fileTypes/javaOutsideSource.png");  // 16x16
	}

	interface Gutter
	{
		Image EventMethod = IconLoader.getIcon("/icons/gutter/eventMethod.png");  // 12x12
		Image ExtAnnotation = IconLoader.getIcon("/icons/gutter/extAnnotation.png");  // 12x12
	}

	interface Nodes
	{
		Image JavaModule = IconLoader.getIcon("/icons/nodes/javaModule.png");  // 16x16
		Image JavaModuleRoot = IconLoader.getIcon("/icons/nodes/javaModuleRoot.png");  // 16x16
		Image NativeLibrariesFolder = IconLoader.getIcon("/icons/nodes/nativeLibrariesFolder.png");  // 16x16
	}

	Image Java = IconLoader.getIcon("/icons/java.svg");  // 16x16
}