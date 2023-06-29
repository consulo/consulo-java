/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.impl.memory.ui;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessListener;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import com.intellij.java.debugger.impl.memory.component.MemoryViewDebugProcessData;
import com.intellij.java.debugger.impl.memory.component.MemoryViewManager;
import com.intellij.java.debugger.impl.memory.component.MemoryViewManagerState;
import com.intellij.java.debugger.impl.memory.event.InstancesTrackerListener;
import com.intellij.java.debugger.impl.memory.event.MemoryViewManagerListener;
import com.intellij.java.debugger.impl.memory.tracking.ConstructorInstancesTracker;
import com.intellij.java.debugger.impl.memory.tracking.TrackerForNewInstances;
import com.intellij.java.debugger.impl.memory.tracking.TrackingType;
import com.intellij.java.debugger.impl.memory.utils.AndroidUtil;
import com.intellij.java.debugger.impl.memory.utils.KeyboardUtils;
import com.intellij.java.debugger.impl.memory.utils.LowestPriorityCommand;
import com.intellij.java.debugger.impl.memory.utils.SingleAlarmWithMutableDelay;
import com.intellij.java.debugger.requests.ClassPrepareRequestor;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.ui.wm.ApplicationIdeFocusManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.Size;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.event.DoubleClickListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.java.debugger.impl.memory.ui.ClassesTable.DiffViewTableModel.CLASSNAME_COLUMN_INDEX;
import static com.intellij.java.debugger.impl.memory.ui.ClassesTable.DiffViewTableModel.DIFF_COLUMN_INDEX;

public class ClassesFilteredView extends BorderLayoutPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  private static final double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  private static final double MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);
  private static final int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final String EMPTY_TABLE_CONTENT_WHEN_RUNNING = "The application is running";
  private static final String EMPTY_TABLE_CONTENT_WHEN_SUSPENDED = "Nothing to show";
  private static final String EMPTY_TABLE_CONTENT_WHEN_STOPPED = "Classes are not available";

  private final Project myProject;
  private final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;
  private final InstancesTracker myInstancesTracker;
  private final Map<ReferenceType, ConstructorInstancesTracker> myConstructorTrackedClasses = new ConcurrentHashMap<>();
  private final MyDebuggerSessionListener myDebugSessionListener;

  // tick on each session paused event
  private final AtomicInteger myTime = new AtomicInteger(0);

  private final AtomicInteger myLastUpdatingTime = new AtomicInteger(Integer.MIN_VALUE);

  /**
   * Indicates that the debug session had been stopped at least once.
   * <p>
   * State: false to true
   */
  private final AtomicBoolean myIsTrackersActivated = new AtomicBoolean(false);

  /**
   * Indicates that view is visible
   */
  private volatile boolean myIsActive;

  public ClassesFilteredView(@Nonnull XDebugSession debugSession,
                             @Nonnull DebugProcessImpl debugProcess,
                             @Nonnull InstancesTracker tracker) {
    myProject = debugSession.getProject();

    final DebuggerManagerThreadImpl managerThread = debugProcess.getManagerThread();
    myInstancesTracker = tracker;
    final InstancesTrackerListener instancesTrackerListener = new InstancesTrackerListener() {
      @Override
      public void classChanged(@Nonnull String name, @Nonnull TrackingType type) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null) {
          final boolean activated = myIsTrackersActivated.get();
          managerThread.schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
              trackClass(debugSession, ref, type, activated);
            }
          });
        }
        myTable.repaint();
      }

      @Override
      public void classRemoved(@Nonnull String name) {
        ReferenceType ref = myTable.getClassByName(name);
        if (ref != null && myConstructorTrackedClasses.containsKey(ref)) {
          ConstructorInstancesTracker removed = myConstructorTrackedClasses.remove(ref);
          Disposer.dispose(removed);
          myTable.getRowSorter().allRowsChanged();
        }
      }
    };

    debugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
        myInstancesTracker.removeTrackerListener(instancesTrackerListener);
      }
    });

    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processAttached(DebugProcess process) {
        debugProcess.removeDebugProcessListener(this);
        managerThread.invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            final boolean activated = myIsTrackersActivated.get();
            final VirtualMachineProxyImpl proxy = debugProcess.getVirtualMachineProxy();
            tracker.getTrackedClasses().forEach((className, type) ->
                                                {
                                                  List<ReferenceType> classes = proxy.classesByName(className);
                                                  if (classes.isEmpty()) {
                                                    trackWhenPrepared(className, debugSession, debugProcess, type);
                                                  }
                                                  else {
                                                    for (ReferenceType ref : classes) {
                                                      trackClass(debugSession, ref, type, activated);
                                                    }
                                                  }
                                                });

            tracker.addTrackerListener(instancesTrackerListener);
          }
        });
      }

      private void trackWhenPrepared(@Nonnull String className,
                                     @Nonnull XDebugSession session,
                                     @Nonnull DebugProcessImpl process,
                                     @Nonnull TrackingType type) {
        final ClassPrepareRequestor request = new ClassPrepareRequestor() {
          @Override
          public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
            process.getRequestsManager().deleteRequest(this);
            trackClass(session, referenceType, type, myIsTrackersActivated.get());
          }
        };

        final ClassPrepareRequest classPrepareRequest = process.getRequestsManager().createClassPrepareRequest(request, className);
        if (classPrepareRequest != null) {
          classPrepareRequest.enable();
        }
        else {
          LOG.warn("Cannot create a 'class prepare' request. Class " + className + " not tracked.");
        }
      }
    });

    final MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = new ClassesTable(tracker,
                               this,
                               memoryViewManagerState.isShowWithDiffOnly,
                               memoryViewManagerState.isShowWithInstancesOnly,
                               memoryViewManagerState.isShowTrackedOnly);
    myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
    Disposer.register(this, myTable);

    myTable.addMouseMotionListener(new MyMouseMotionListener());
    myTable.addMouseListener(new MyOpenNewInstancesListener());
    new MyDoubleClickListener().installOn(myTable);

    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (KeyboardUtils.isEnterKey(keyCode)) {
          handleClassSelection(myTable.getSelectedClass());
        }
        else if (KeyboardUtils.isCharacter(keyCode) || KeyboardUtils.isBackSpace(keyCode)) {
          final String text = myFilterTextField.getText();
          final String newText = KeyboardUtils.isBackSpace(keyCode) ? text.substring(0, text.length() - 1) : text + e.getKeyChar();
          myFilterTextField.setText(newText);
          ApplicationIdeFocusManager.getInstance().getInstanceForProject(myProject).requestFocus(myFilterTextField, false);
        }
      }
    });

    myFilterTextField.addKeyboardListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        dispatch(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        dispatch(e);
      }

      private void dispatch(KeyEvent e) {
        if (KeyboardUtils.isUpDownKey(e.getKeyCode()) || KeyboardUtils.isEnterKey(e.getKeyCode())) {
          myTable.dispatchEvent(e);
        }
      }
    });

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
      }
    });

    final MemoryViewManagerListener memoryViewManagerListener = state ->
    {
      myTable.setFilteringByDiffNonZero(state.isShowWithDiffOnly);
      myTable.setFilteringByInstanceExists(state.isShowWithInstancesOnly);
      myTable.setFilteringByTrackingState(state.isShowTrackedOnly);
    };

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener, this);

    myDebugSessionListener = new MyDebuggerSessionListener();
    debugSession.addSessionListener(myDebugSessionListener, this);

    mySingleAlarm = new SingleAlarmWithMutableDelay(suspendContext ->
                                                    {
                                                      ApplicationManager.getApplication().invokeLater(() -> myTable.setBusy(true));
                                                      suspendContext.getDebugProcess()
                                                                    .getManagerThread()
                                                                    .schedule(new MyUpdateClassesCommand(suspendContext));
                                                    }, this);

    mySingleAlarm.setDelay((int)TimeUnit.MILLISECONDS.toMillis(500));

    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu menu = createContextMenu();
        if (menu != null) {
          menu.getComponent().show(comp, x, y);
        }
      }
    });

    final JScrollPane scroll = ScrollPaneFactory.createScrollPane(myTable, SideBorder.TOP);
    final DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("MemoryView.SettingsPopupActionGroup");
    group.setPopup(true);
    final Presentation actionsPresentation = new Presentation("Memory View Settings");
    actionsPresentation.setIcon(AllIcons.General.GearPlain);

    final ActionButton button = ActionButtonFactory.getInstance().create(group, actionsPresentation, ActionPlaces.UNKNOWN, new Size(25, 25));
    final BorderLayoutPanel topPanel = new BorderLayoutPanel();
    topPanel.addToCenter(myFilterTextField);
    topPanel.addToRight(button.getComponent());
    addToTop(topPanel);
    addToCenter(scroll);
  }

  @Nullable
  TrackerForNewInstances getStrategy(@Nonnull ReferenceType ref) {
    return myConstructorTrackedClasses.getOrDefault(ref, null);
  }

  private void trackClass(@Nonnull XDebugSession session,
                          @Nonnull ReferenceType ref,
                          @Nonnull TrackingType type,
                          boolean isTrackerEnabled) {
    LOG.assertTrue(DebuggerManager.getInstance(myProject).isDebuggerManagerThread());
    if (type == TrackingType.CREATION) {
      final ConstructorInstancesTracker old = myConstructorTrackedClasses.getOrDefault(ref, null);
      if (old != null) {
        Disposer.dispose(old);
      }

      final ConstructorInstancesTracker tracker = new ConstructorInstancesTracker(ref, session, myInstancesTracker);
      tracker.setBackgroundMode(!myIsActive);
      if (isTrackerEnabled) {
        tracker.enable();
      }
      else {
        tracker.disable();
      }

      myConstructorTrackedClasses.put(ref, tracker);
    }
  }

  private void handleClassSelection(@Nullable ReferenceType ref) {
    final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (ref != null && debugSession != null && debugSession.isSuspended()) {
      new InstancesWindow(debugSession, limit ->
      {
        final List<ObjectReference> instances = ref.instances(limit);
        return instances == null ? Collections.emptyList() : instances;
      }, ref.name()).show();
    }
  }

  private void commitAllTrackers() {
    myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::commitTracked);
  }

  private void updateClassesAndCounts() {
    ApplicationManager.getApplication().invokeLater(() ->
                                                    {
                                                      final XDebugSession debugSession =
                                                        XDebuggerManager.getInstance(myProject).getCurrentSession();
                                                      if (debugSession != null) {
                                                        final DebugProcess debugProcess = DebuggerManager.getInstance(myProject)
                                                                                                         .getDebugProcess(debugSession.getDebugProcess()
                                                                                                                                      .getProcessHandler());
                                                        if (debugProcess != null && debugProcess.isAttached() && debugProcess instanceof DebugProcessImpl) {
                                                          final DebugProcessImpl process = (DebugProcessImpl)debugProcess;
                                                          final SuspendContextImpl context =
                                                            process.getDebuggerContext().getSuspendContext();
                                                          if (context != null) {
                                                            mySingleAlarm.cancelAndRequest(context);
                                                          }
                                                        }
                                                      }
                                                    }, myProject::isDisposed);
  }

  private static ActionPopupMenu createContextMenu() {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MemoryView.ClassesPopupActionGroup");
    return ActionManager.getInstance().createActionPopupMenu("MemoryView.ClassesPopupActionGroup", group);
  }

  @Override
  public void dispose() {
    myConstructorTrackedClasses.clear();
  }

  public void setActive(boolean active, @Nonnull DebuggerManagerThreadImpl managerThread) {
    if (myIsActive == active) {
      return;
    }

    myIsActive = active;

    managerThread.schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        if (active) {
          doActivate();
        }
        else {
          doPause();
        }
      }
    });
  }

  private void doActivate() {
    myDebugSessionListener.setActive(true);
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(false));

    if (isNeedUpdateView()) {
      updateClassesAndCounts();
    }
  }

  private void doPause() {
    myDebugSessionListener.setActive(false);
    mySingleAlarm.cancelAllRequests();
    myConstructorTrackedClasses.values().forEach(x -> x.setBackgroundMode(true));
  }

  private boolean isNeedUpdateView() {
    return myLastUpdatingTime.get() != myTime.get();
  }

  private void viewUpdated() {
    myLastUpdatingTime.set(myTime.get());
  }

  private final class MyUpdateClassesCommand extends LowestPriorityCommand {

    MyUpdateClassesCommand(@Nullable SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
      handleTrackers();

      final List<ReferenceType> classes = suspendContext.getDebugProcess().getVirtualMachineProxy().allClasses();

      if (!classes.isEmpty()) {
        final VirtualMachine vm = classes.get(0).virtualMachine();
        if (vm.canGetInstanceInfo()) {
          final Map<ReferenceType, Long> counts = getInstancesCounts(classes, vm);
          ApplicationManager.getApplication().invokeLater(() -> myTable.updateContent(counts));
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> myTable.updateClassesOnly(classes));
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> myTable.setBusy(false));
      viewUpdated();
    }

    private void handleTrackers() {
      if (!myIsTrackersActivated.get()) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::enable);
        myIsTrackersActivated.set(true);
      }
      else {
        commitAllTrackers();
      }
    }

    private Map<ReferenceType, Long> getInstancesCounts(@Nonnull List<ReferenceType> classes, @Nonnull VirtualMachine vm) {
      final int batchSize = AndroidUtil.isAndroidVM(vm) ? AndroidUtil.ANDROID_COUNT_BY_CLASSES_BATCH_SIZE : DEFAULT_BATCH_SIZE;

      final int size = classes.size();
      final Map<ReferenceType, Long> result = new LinkedHashMap<>();

      for (int begin = 0, end = Math.min(batchSize, size); begin != size; begin = end, end = Math.min(end + batchSize, size)) {
        final List<ReferenceType> batch = classes.subList(begin, end);

        final long start = System.nanoTime();
        final long[] counts = vm.instanceCounts(batch);
        final long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        for (int i = 0; i < batch.size(); i++) {
          result.put(batch.get(i), counts[i]);
        }

        final int waitTime = (int)Math.min(DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT * delay, MAX_DELAY_MILLIS);
        mySingleAlarm.setDelay(waitTime);
        LOG.debug(String.format("Instances query time = %d ms. Count of classes = %d", delay, batch.size()));
      }

      return result;
    }
  }

  private static class FilterTextField extends SearchTextField {
    FilterTextField() {
      super(false);
    }

    @Override
    protected void showPopup() {
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
    }
  }

  private class MyOpenNewInstancesListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() != 1 || e.getButton() != MouseEvent.BUTTON1 || !isShowNewInstancesEvent(e)) {
        return;
      }

      final ReferenceType ref = myTable.getSelectedClass();
      final TrackerForNewInstances strategy = ref == null ? null : getStrategy(ref);
      XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
      if (strategy != null && debugSession != null) {
        final DebugProcess debugProcess =
          DebuggerManager.getInstance(myProject).getDebugProcess(debugSession.getDebugProcess().getProcessHandler());
        final MemoryViewDebugProcessData data = debugProcess.getUserData(MemoryViewDebugProcessData.KEY);
        if (data != null) {
          final List<ObjectReference> newInstances = strategy.getNewInstances();
          data.getTrackedStacks().pinStacks(ref);
          final InstancesWindow instancesWindow = new InstancesWindow(debugSession, limit -> newInstances, ref.name());
          Disposer.register(instancesWindow.getDisposable(), () -> data.getTrackedStacks().unpinStacks(ref));
          instancesWindow.show();
        }
        else {
          LOG.warn("MemoryViewDebugProcessData not found in debug session user data");
        }
      }
    }
  }

  private class MyDoubleClickListener extends DoubleClickListener {
    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      if (!isShowNewInstancesEvent(event)) {
        handleClassSelection(myTable.getSelectedClass());
        return true;
      }

      return false;
    }
  }

  private class MyMouseMotionListener implements MouseMotionListener {
    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isShowNewInstancesEvent(e)) {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }
  }

  private boolean isShowNewInstancesEvent(@Nonnull MouseEvent e) {
    final int col = myTable.columnAtPoint(e.getPoint());
    final int row = myTable.rowAtPoint(e.getPoint());
    if (col == -1 || row == -1 || myTable.convertColumnIndexToModel(col) != DIFF_COLUMN_INDEX) {
      return false;
    }

    final int modelRow = myTable.convertRowIndexToModel(row);

    final ReferenceType ref = (ReferenceType)myTable.getModel().getValueAt(modelRow, CLASSNAME_COLUMN_INDEX);
    final ConstructorInstancesTracker tracker = myConstructorTrackedClasses.getOrDefault(ref, null);

    return tracker != null && tracker.isReady() && tracker.getCount() > 0;
  }

  private class MyDebuggerSessionListener implements XDebugSessionListener {
    private volatile boolean myIsActive = false;

    void setActive(boolean value) {
      myIsActive = value;
    }

    @Override
    public void sessionResumed() {
      if (myIsActive) {
        myConstructorTrackedClasses.values().forEach(ConstructorInstancesTracker::obsolete);
        ApplicationManager.getApplication().invokeLater(() ->
                                                        {
                                                          myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
                                                          myTable.hideContent();
                                                        });

        mySingleAlarm.cancelAllRequests();
      }
    }

    @Override
    public void sessionStopped() {
      myConstructorTrackedClasses.values().forEach(Disposer::dispose);
      myConstructorTrackedClasses.clear();
      mySingleAlarm.cancelAllRequests();
      ApplicationManager.getApplication().invokeLater(() ->
                                                      {
                                                        myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_STOPPED);
                                                        myTable.clean();
                                                      });
    }

    @Override
    public void sessionPaused() {
      if (myIsActive) {
        ApplicationManager.getApplication().invokeLater(() -> myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_SUSPENDED));
        updateClassesAndCounts();
      }

      myTime.incrementAndGet();
    }
  }
}
