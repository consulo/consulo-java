/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl;

import consulo.application.ui.FrameStateManager;
import consulo.application.ui.event.FrameStateListener;
import consulo.component.util.Weighted;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.setting.XDebuggerSettingsManager;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.ui.ex.IdeGlassPane;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.update.UiNotifyConnector;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awt.util.IdeGlassPaneUtil;
import consulo.ui.ex.update.Activatable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;

/**
 * @author lex
 */
public class TipManager implements Disposable, PopupMenuListener
{
	private volatile boolean myIsDisposed = false;
	private boolean myPopupShown;
	private MyAwtPreprocessor myHideCanceller;

	private MouseEvent myLastMouseEvent;

	public interface TipFactory
	{
		JComponent createToolTip(MouseEvent e);

		MouseEvent createTooltipEvent(MouseEvent candidateEvent);

		boolean isFocusOwner();
	}


	private boolean isOverTip(MouseEvent e)
	{
		if(myCurrentTooltip != null)
		{
			if(!myCurrentTooltip.isShowing())
			{
				hideTooltip(true);
				return false;
			}
			final Component eventOriginator = e.getComponent();
			if(eventOriginator == null)
			{
				return false;
			}
			final Point point = e.getPoint();
			SwingUtilities.convertPointToScreen(point, eventOriginator);

			final Rectangle bounds = myCurrentTooltip.getBounds();
			final Point tooltipLocationOnScreen = myCurrentTooltip.getLocationOnScreen();
			bounds.setLocation(tooltipLocationOnScreen.x, tooltipLocationOnScreen.y);

			return bounds.contains(point);
		}
		return false;
	}

	boolean myInsideComponent;

	private class MyMouseListener extends MouseAdapter implements Weighted
	{
		@Override
		public void mouseExited(final MouseEvent e)
		{
			myInsideComponent = false;
		}

		@Override
		public void mousePressed(final MouseEvent e)
		{
			if(myInsideComponent)
			{
				hideTooltip(true);
			}
		}

		@Override
		public double getWeight()
		{
			return 0;
		}

		@Override
		public void mouseEntered(final MouseEvent e)
		{
			myInsideComponent = true;
		}
	}

	private class MyFrameStateListener extends FrameStateListener.Adapter
	{
		@Override
		public void onFrameDeactivated()
		{
			hideTooltip(true);
		}
	}

	public JPopupMenu registerPopup(JPopupMenu menu)
	{
		menu.addPopupMenuListener(this);
		return menu;
	}

	@Override
	public void popupMenuWillBecomeVisible(final PopupMenuEvent e)
	{
		myPopupShown = true;
	}

	@Override
	public void popupMenuWillBecomeInvisible(final PopupMenuEvent e)
	{
		onPopupClosed(e);
	}

	@Override
	public void popupMenuCanceled(final PopupMenuEvent e)
	{
		onPopupClosed(e);
	}

	private void onPopupClosed(final PopupMenuEvent e)
	{
		myPopupShown = false;
		if(e.getSource() instanceof JPopupMenu)
		{
			((JPopupMenu) e.getSource()).removePopupMenuListener(this);
		}
	}

	private class MyMouseMotionListener extends MouseMotionAdapter implements Weighted
	{
		@Override
		public void mouseMoved(final MouseEvent e)
		{
			myLastMouseEvent = e;

			if(!myComponent.isShowing())
			{
				return;
			}

			myInsideComponent = true;

			if(myCurrentTooltip == null)
			{
				if(isInsideComponent(e))
				{
					tryTooltip(e, true);
				}
			}
			else
			{
				if(!isOverTip(e))
				{
					tryTooltip(e, true);
				}
			}
		}

		@Override
		public double getWeight()
		{
			return 0;
		}
	}

	private boolean isInsideComponent(final MouseEvent e)
	{
		final Rectangle compBounds = myComponent.getVisibleRect();
		final Point compPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myComponent);

		return compBounds.contains(compPoint);
	}


	private void tryTooltip(final InputEvent e, final boolean auto)
	{
		myShowAlarm.cancelAllRequests();
		myHideAlarm.cancelAllRequests();
		myShowAlarm.addRequest(new Runnable()
		{
			@Override
			public void run()
			{
				if(!myIsDisposed && !myPopupShown)
				{
					showTooltip(e, auto);
				}
			}
		}, auto ? XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay() : 10);
	}

	private void showTooltip(InputEvent e, boolean auto)
	{
		if(auto && !XDebuggerSettingsManager.getInstance().getDataViewSettings().isValueTooltipAutoShow())
		{
			return;
		}

		MouseEvent sourceEvent = null;
		JComponent newTip = null;

		if(e instanceof MouseEvent)
		{
			sourceEvent = (MouseEvent) e;
		}
		else if(e instanceof KeyEvent)
		{
			sourceEvent = myTipFactory.createTooltipEvent(myLastMouseEvent);
		}


		MouseEvent convertedEvent = null;
		if(sourceEvent != null)
		{
			convertedEvent = SwingUtilities.convertMouseEvent(sourceEvent.getComponent(), sourceEvent, myComponent);
			newTip = myTipFactory.createToolTip(convertedEvent);
		}

		if(newTip == null || (auto && !myTipFactory.isFocusOwner()))
		{
			hideTooltip(false);
			return;
		}

		if(newTip == myCurrentTooltip)
		{
			if(!auto)
			{
				hideTooltip(true);
				return;
			}
			return;
		}

		hideTooltip(true);

		if(myComponent.isShowing())
		{
			PopupFactory popupFactory = PopupFactory.getSharedInstance();
			final Point location = convertedEvent.getPoint();
			final Component sourceComponent = convertedEvent.getComponent();
			if(sourceComponent != null)
			{
				SwingUtilities.convertPointToScreen(location, sourceComponent);
			}

			myTipPopup = popupFactory.getPopup(myComponent, newTip, location.x, location.y);
			myInsideComponent = false;
			myTipPopup.show();
			myCurrentTooltip = newTip;
		}
	}

	public void hideTooltip()
	{
		hideTooltip(true);
	}

	public void hideTooltip(boolean now)
	{
		if(myTipPopup == null)
		{
			return;
		}

		if(now)
		{
			myHideAlarm.cancelAllRequests();
			myTipPopup.hide();
			myTipPopup = null;
			myCurrentTooltip = null;
		}
		else
		{
			myHideAlarm.addRequest(new Runnable()
			{
				@Override
				public void run()
				{
					if(myInsideComponent)
					{
						hideTooltip(true);
					}
				}
			}, 100);
		}
	}

	private JComponent myCurrentTooltip;
	private Popup myTipPopup;
	private final TipFactory myTipFactory;
	private final JComponent myComponent;
	private MouseListener myMouseListener = new MyMouseListener();
	private MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();
	private FrameStateListener myFrameStateListener = new MyFrameStateListener();

	private final Alarm myShowAlarm = new Alarm();
	private final Alarm myHideAlarm = new Alarm();


	private IdeGlassPane myGP;

	public TipManager(final JComponent component, TipFactory factory)
	{
		myTipFactory = factory;
		myComponent = component;

		new UiNotifyConnector.Once(component, new Activatable()
		{
			@Override
			public void showNotify()
			{
				installListeners();
			}

			@Override
			public void hideNotify()
			{
			}
		});

		final HideTooltipAction hide = new HideTooltipAction();
		hide.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), myComponent);
		Disposer.register(this, new Disposable()
		{
			@Override
			public void dispose()
			{
				hide.unregisterCustomShortcutSet(myComponent);
			}
		});
	}


	private class HideTooltipAction extends AnAction
	{
		@Override
		public void actionPerformed(AnActionEvent e)
		{
			hideTooltip(true);
		}

		@Override
		public void update(AnActionEvent e)
		{
			e.getPresentation().setEnabled(myTipPopup != null);
		}
	}

	private void installListeners()
	{
		if(myIsDisposed)
		{
			return;
		}

		myGP = IdeGlassPaneUtil.find(myComponent);
		assert myGP != null;

		myGP.addMousePreprocessor(myMouseListener, this);
		myGP.addMouseMotionPreprocessor(myMouseMotionListener, this);

		myHideCanceller = new MyAwtPreprocessor();
		Toolkit.getDefaultToolkit().addAWTEventListener(myHideCanceller, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent
				.MOUSE_EVENT_MASK);
		FrameStateManager.getInstance().addListener(myFrameStateListener);
	}

	@Override
	public void dispose()
	{
		Disposer.dispose(this);

		hideTooltip(true);

		Toolkit.getDefaultToolkit().removeAWTEventListener(myHideCanceller);

		myIsDisposed = true;
		myShowAlarm.cancelAllRequests();
		myMouseListener = null;
		myMouseMotionListener = null;
		FrameStateManager.getInstance().removeListener(myFrameStateListener);
		myFrameStateListener = null;
	}

	private class MyAwtPreprocessor implements AWTEventListener
	{

		@Override
		public void eventDispatched(AWTEvent event)
		{
			if(event.getID() == MouseEvent.MOUSE_MOVED)
			{
				preventFromHideIfInsideTooltip(event);
			}
			else if(event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED)
			{
				hideTooltipIfCloseClick((MouseEvent) event);
			}
			else if(event instanceof KeyEvent)
			{
				tryToShowTooltipIfRequested((KeyEvent) event);
			}
		}

		private void hideTooltipIfCloseClick(MouseEvent me)
		{
			if(myCurrentTooltip == null)
			{
				return;
			}

			if(isInsideTooltip(me) && UIUtil.isCloseClick(me))
			{
				hideTooltip(true);
			}
		}

		private void tryToShowTooltipIfRequested(KeyEvent event)
		{
			if(KeymapUtil.isTooltipRequest(event))
			{
				tryTooltip(event, false);
			}
			else
			{
				if(event.getID() == KeyEvent.KEY_PRESSED)
				{
					myLastMouseEvent = null;
				}
			}
		}

		private void preventFromHideIfInsideTooltip(AWTEvent event)
		{
			if(myCurrentTooltip == null)
			{
				return;
			}

			if(event.getID() == MouseEvent.MOUSE_MOVED)
			{
				final MouseEvent me = (MouseEvent) event;
				if(isInsideTooltip(me))
				{
					myHideAlarm.cancelAllRequests();
				}
			}
		}

		private boolean isInsideTooltip(MouseEvent me)
		{
			return myCurrentTooltip == me.getComponent() || SwingUtilities.isDescendingFrom(me.getComponent(), myCurrentTooltip);
		}
	}
}
