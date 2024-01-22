package consulo.java.execution.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ui.console.ConsoleFoldingContributor;
import consulo.execution.ui.console.ConsoleFoldingRegistrator;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class JavaConsoleFoldingContributor implements ConsoleFoldingContributor {
  @Override
  public void register(@Nonnull ConsoleFoldingRegistrator registrator) {
    registrator.addFolding("at java.awt.EventDispatchThread");
    registrator.addFolding("at java.awt.Window.dispatchEventImpl(");
    registrator.addFolding("at java.awt.Container.dispatchEventImpl(");
    registrator.addFolding("at java.awt.LightweightDispatcher.");
    registrator.addFolding("at java.awt.Component.dispatchEvent(");
    registrator.addFolding("at java.awt.event.InvocationEvent.dispatch(");
    registrator.addFolding("at java.awt.EventQueue");
    registrator.addFolding("at java.awt.Component.dispatchEventImpl(");
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
    registrator.addFolding("at java.security.AccessControlContext$1.doIntersectionPrivilege(");
    registrator.addFolding("at java.awt.EventQueue.dispatchEventImpl(");
    registrator.addFolding("at java.security.AccessController.doPrivileged(Native Method)");
    registrator.addFolding("at java.security.ProtectionDomain$1.doIntersectionPrivilege(");
    registrator.addFolding("at java.security.ProtectionDomain$JavaSecurityAccessImpl.doIntersectionPrivilege(");
    registrator.addFolding("at com.jgoodies.binding.beans.ExtendedPropertyChangeSupport.firePropertyChange0(");
    registrator.addFolding("at javax.swing.plaf.basic.BasicComboPopup$Handler.mouseReleased(");
    registrator.addFolding("at java.util.ArrayList$ArrayListSpliterator");
    registrator.addFolding("at java.util.stream.ReferencePipeline");
    registrator.addFolding("at java.util.Spliterators$");
    registrator.addFolding("at java.util.stream.AbstractPipeline.evaluate(");
    registrator.addFolding("at java.util.stream.AbstractPipeline.copyInto(");
    registrator.addFolding("at java.util.stream.AbstractPipeline.wrapAndCopyInto(");
    registrator.addFolding("at java.util.stream.DistinctOps");
    registrator.addFolding("at java.util.stream.FindOps");
    registrator.addFolding("at java.util.stream.ForEachOps");
    registrator.addFolding("at java.util.stream.MatchOps");
    registrator.addFolding("at java.util.stream.ReduceOps");
    registrator.addFolding("at java.util.stream.SliceOps");
    registrator.addFolding("at java.util.stream.WhileOps");

    registrator.addFolding("at java.util.concurrent.Executors$");
    registrator.addFolding("at java.util.concurrent.ThreadPoolExecutor"); /*methods and inner classes*/
    registrator.addFolding("at java.util.concurrent.FutureTask.");
    registrator.addFolding("at java.util.concurrent.CompletableFuture$AsyncSupply.run(");

    registrator.addFolding("java.lang.Thread.run(");

    registrator.addFolding("java.lang.reflect.Method.invoke(");
    registrator.addFolding("java.lang.reflect.Constructor.newInstance(");
    registrator.addFolding("at java.base/jdk.internal");
    registrator.addFolding("at java.security.AccessController.doPrivileged(");
    registrator.addFolding("at sun.reflect.");
    registrator.addFolding("at java.rmi.");
    registrator.addFolding("at sun.rmi.");
    registrator.addFolding("at com.sun.proxy.$Proxy");
    registrator.addFolding("at com.intellij.rt.execution.");
  }
}
