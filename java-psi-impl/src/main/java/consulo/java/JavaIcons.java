package consulo.java;

import com.intellij.openapi.util.IconLoader;
import consulo.ui.migration.SwingImageRef;

// Generated Consulo DevKit plugin 
public interface JavaIcons
{
	interface FileTypes
	{
		SwingImageRef Java = IconLoader.getIcon("/icons/fileTypes/java.png");  // 16x16
		SwingImageRef JavaClass = IconLoader.getIcon("/icons/fileTypes/javaClass.png");  // 16x16
		SwingImageRef JavaOutsideSource = IconLoader.getIcon("/icons/fileTypes/javaOutsideSource.png");  // 16x16
	}

	interface Gutter
	{
		SwingImageRef EventMethod = IconLoader.getIcon("/icons/gutter/eventMethod.png");  // 12x12
		SwingImageRef ExtAnnotation = IconLoader.getIcon("/icons/gutter/extAnnotation.png");  // 12x12
	}

	interface Nodes
	{
		SwingImageRef JavaModule = IconLoader.getIcon("/icons/nodes/javaModule.png");  // 16x16
		SwingImageRef JavaModuleRoot = IconLoader.getIcon("/icons/nodes/javaModuleRoot.png");  // 16x16
		SwingImageRef NativeLibrariesFolder = IconLoader.getIcon("/icons/nodes/nativeLibrariesFolder.png");  // 16x16
	}

	SwingImageRef Java = IconLoader.getIcon("/icons/java.png");  // 16x16
}