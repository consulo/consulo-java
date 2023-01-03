package consulo.java.execution.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ui.console.ConsoleFoldingContributor;
import consulo.execution.ui.console.ConsoleFoldingRegistrator;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class JavaConsoleFoldingContributor implements ConsoleFoldingContributor {
  @Override
  public void register(@Nonnull ConsoleFoldingRegistrator registrator) {
    registrator.addFolding("at java.awt.EventDispatchThread.pumpEventsForFilter(");
    registrator.addFolding("at java.awt.EventDispatchThread.pumpEventsForHierarchy(");
    registrator.addFolding("at java.awt.EventDispatchThread.pumpEvents(");
    registrator.addFolding("at java.awt.Window.dispatchEventImpl(");
    registrator.addFolding("at java.awt.Container.dispatchEventImpl(");
    registrator.addFolding("at java.awt.LightweightDispatcher.");
    registrator.addFolding("at java.awt.Component.dispatchEvent(");
    registrator.addFolding("at java.awt.EventQueue.dispatchEvent(");
    registrator.addFolding("at java.awt.Component.dispatchEventImpl(");
    registrator.addFolding("at java.awt.EventDispatchThread.pumpOneEventForFilters(");
    registrator.addFolding("at java.awt.Container.processEvent(");
    registrator.addFolding("at javax.swing.JComponent.processMouseEvent(");
    registrator.addFolding("at javax.swing.plaf.basic.BasicMenuItemUI");
    registrator.addFolding("at java.awt.Component.processMouseEvent(");
    registrator.addFolding("at javax.swing.AbstractButton.doClick(");
    registrator.addFolding("at java.awt.Component.processEvent(");
    registrator.addFolding("at java.awt.Container.dispatchEventImpl(");
    registrator.addFolding("at javax.swing.DefaultButtonModel.fireActionPerformed(");
    registrator.addFolding("at javax.swing.DefaultButtonModel.setPressed(");
    registrator.addFolding("at javax.swing.AbstractButton.fireActionPerformed(");
    registrator.addFolding("at javax.swing.AbstractButton$Handler.actionPerformed(");
    registrator.addFolding("at java.awt.EventQueue$1.run(");
    registrator.addFolding("at java.security.AccessControlContext$1.doIntersectionPrivilege(");
    registrator.addFolding("at java.awt.EventQueue.dispatchEventImpl(");
    registrator.addFolding("at java.security.AccessController.doPrivileged(Native Method)");
    registrator.addFolding("at java.awt.EventQueue$2.run(");
    registrator.addFolding("at java.awt.EventQueue.access$000(");
    registrator.addFolding("at com.jgoodies.binding.beans.ExtendedPropertyChangeSupport.firePropertyChange0(");
    registrator.addFolding("at javax.swing.plaf.basic.BasicComboPopup$Handler.mouseReleased(");

    registrator.addFolding("at java.lang.reflect.Method.invoke(");
    registrator.addFolding("at java.lang.reflect.Constructor.newInstance(");
    registrator.addFolding("at sun.reflect.");
    registrator.addFolding("at java.rmi.");
    registrator.addFolding("at sun.rmi.");
    registrator.addFolding("at com.intellij.rt.execution.");
  }
}
