package com.intellij.java.debugger.impl.memory.ui;

import com.intellij.java.debugger.impl.memory.action.EnableBackgroundTrackingAction;
import com.intellij.java.debugger.impl.memory.action.ShowClassesWithDiffAction;
import com.intellij.java.debugger.impl.memory.action.ShowClassesWithInstanceAction;
import com.intellij.java.debugger.impl.memory.action.ShowTrackedAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author VISTALL
 * @since 2024-12-01
 */
@ActionImpl(id = MemoryViewSettingsPopupActionGroup.ID, children = {
    @ActionRef(type = ShowClassesWithInstanceAction.class),
    @ActionRef(type = ShowClassesWithDiffAction.class),
    @ActionRef(type = ShowTrackedAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = EnableBackgroundTrackingAction.class)
})
public class MemoryViewSettingsPopupActionGroup extends DefaultActionGroup {
    public static final String ID = "MemoryView.SettingsPopupActionGroup";

    public MemoryViewSettingsPopupActionGroup() {
        super(LocalizeValue.localizeTODO("Memory View Settings"), LocalizeValue.of(), PlatformIconGroup.generalGearplain());
    }

    @Override
    public boolean isPopup() {
        return true;
    }

    @Override
    public boolean showBelowArrow() {
        return false;
    }
}
