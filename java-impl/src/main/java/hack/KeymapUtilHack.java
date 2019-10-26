package hack;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2019-10-26
 */
public class KeymapUtilHack
{
	@Nonnull
	public static String getPreferredShortcutText(@Nonnull Shortcut[] shortcuts)
	{
		KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
		return shortcut != null ? KeymapUtil.getShortcutText(shortcut) :
				shortcuts.length > 0 ? KeymapUtil.getShortcutText(shortcuts[0]) : "";
	}
}
