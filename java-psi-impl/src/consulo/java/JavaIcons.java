package consulo.java;

import javax.swing.Icon;

import com.intellij.openapi.util.IconLoader;

// Generated Consulo DevKit plugin 
public interface JavaIcons
{
	interface FileTypes
	{
		Icon Java = IconLoader.getIcon("/icons/fileTypes/java.png");  // 16x16
		Icon JavaClass = IconLoader.getIcon("/icons/fileTypes/javaClass.png");  // 16x16
		Icon JavaOutsideSource = IconLoader.getIcon("/icons/fileTypes/javaOutsideSource.png");  // 16x16
	}

	interface Gutter
	{
		Icon ExtAnnotation = IconLoader.getIcon("/icons/gutter/extAnnotation.png");  // 12x12
	}

	interface Nodes
	{
		Icon JavaModule = IconLoader.getIcon("/icons/nodes/javaModule.png");  // 16x16
		Icon JavaModuleRoot = IconLoader.getIcon("/icons/nodes/javaModuleRoot.png");  // 16x16
	}

	Icon Java = IconLoader.getIcon("/icons/java.png");  // 16x16
}