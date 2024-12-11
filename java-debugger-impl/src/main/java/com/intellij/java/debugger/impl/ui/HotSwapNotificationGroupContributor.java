package com.intellij.java.debugger.impl.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import jakarta.annotation.Nonnull;

import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2024-12-11
 */
@ExtensionImpl
public class HotSwapNotificationGroupContributor implements NotificationGroupContributor {
    @Override
    public void contribute(@Nonnull Consumer<NotificationGroup> consumer) {
        consumer.accept(HotSwapProgressImpl.NOTIFICATION_GROUP);
    }
}
