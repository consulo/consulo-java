/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Class DebuggerTreeBase
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.impl;

import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.language.impl.JavaFileType;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.project.Project;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.dnd.DnDAwareTree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awt.util.GeometryUtil;
import consulo.ui.ex.awt.util.ScreenUtil;
import consulo.util.jdom.JDOMUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DebuggerTreeBase extends DnDAwareTree implements Disposable
{
  private final Project myProject;
  private DebuggerTreeNodeImpl myCurrentTooltipNode;

  private JComponent myCurrentTooltip;

  protected final TipManager myTipManager;

  public DebuggerTreeBase(TreeModel model, Project project) {
    super(model);
    myProject = project;

    myTipManager = new TipManager(this, new TipManager.TipFactory() {
      @Override
      public JComponent createToolTip(MouseEvent e) {
        return DebuggerTreeBase.this.createToolTip(e);
      }

      @Override
      public MouseEvent createTooltipEvent(MouseEvent candidateEvent) {
        return DebuggerTreeBase.this.createTooltipEvent(candidateEvent);
      }

      @Override
      public boolean isFocusOwner() {
        return DebuggerTreeBase.this.isFocusOwner();
      }
    });

    Disposer.register(this, myTipManager);

    UIUtil.setLineStyleAngled(this);
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new DebuggerTreeRenderer());
    updateUI();
    TreeUtil.installActions(this);
  }

  private JComponent createTipContent(String tipText, DebuggerTreeNodeImpl node) {
    final JToolTip tooltip = new JToolTip();

    if (tipText == null) {
      tooltip.setTipText(tipText);
    }
    else {
      Dimension rootSize = getVisibleRect().getSize();
      Insets borderInsets = tooltip.getBorder().getBorderInsets(tooltip);
      rootSize.width -= (borderInsets.left + borderInsets.right) * 2;
      rootSize.height -= (borderInsets.top + borderInsets.bottom) * 2;

      @NonNls StringBuilder tipBuilder = new StringBuilder();
      final String markupText = node.getMarkupTooltipText();
      if (markupText != null) {
        tipBuilder.append(markupText);
      }

      if (!tipText.isEmpty()) {
        final StringTokenizer tokenizer = new StringTokenizer(tipText, "\n ", true);

        while (tokenizer.hasMoreElements()) {
          final String each = tokenizer.nextElement();
          if ("\n".equals(each)) {
            tipBuilder.append("<br>");
          }
          else if (" ".equals(each)) {
            tipBuilder.append("&nbsp ");
          }
          else {
            tipBuilder.append(JDOMUtil.legalizeText(each));
          }
        }
      }

      tooltip.setTipText(UIUtil.toHtml(tipBuilder.toString(), 0));
    }

    tooltip.setBorder(null);

    return tooltip;
  }

  public MouseEvent createTooltipEvent(MouseEvent candidate) {
    TreePath path = null;

    if (candidate != null) {
      final Point treePoint = SwingUtilities.convertPoint(candidate.getComponent(), candidate.getPoint(), this);
      if (GeometryUtil.isWithin(new Rectangle(0, 0, getWidth(), getHeight()), treePoint)) {
        path = getPathForLocation(treePoint.x, treePoint.y);
      }
    }

    if (path == null) {
      if (isFocusOwner()) {
        path = getSelectionPath();
      }
    }

    if (path == null) return null;

    final int row = getRowForPath(path);
    if (row == -1) return null;

    final Rectangle bounds = getRowBounds(row);

    return new MouseEvent(this, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, bounds.x,
                          bounds.y + bounds.height - bounds.height / 4, 0, false);
  }

  @Nullable
  public JComponent createToolTip(MouseEvent e) {
    final DebuggerTreeNodeImpl node = getNodeToShowTip(e);
    if (node == null) {
      return null;
    }

    if (myCurrentTooltip != null && myCurrentTooltip.isShowing() && myCurrentTooltipNode == node) {
      return myCurrentTooltip;
    }

    myCurrentTooltipNode = node;

    final String toolTipText = getTipText(node);
    if (toolTipText == null) {
      return null;
    }

    final JComponent tipContent = createTipContent(toolTipText, node);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(tipContent);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    final Point point = e.getPoint();
    SwingUtilities.convertPointToScreen(point, e.getComponent());
    Rectangle tipRectangle = new Rectangle(point, tipContent.getPreferredSize());

    final Rectangle screen = ScreenUtil.getScreenRectangle(point.x, point.y);

    final JToolTip toolTip = new JToolTip();

    tipContent.addMouseListener(new HideTooltip(toolTip));

    final Border tooltipBorder = toolTip.getBorder();
    if (tooltipBorder != null) {
      final Insets borderInsets = tooltipBorder.getBorderInsets(this);
      tipRectangle
        .setSize(tipRectangle.width + borderInsets.left + borderInsets.right, tipRectangle.height + borderInsets.top + borderInsets.bottom);
    }

    toolTip.setLayout(new BorderLayout());
    toolTip.add(scrollPane, BorderLayout.CENTER);


    tipRectangle.height += scrollPane.getHorizontalScrollBar().getPreferredSize().height;
    tipRectangle.width += scrollPane.getVerticalScrollBar().getPreferredSize().width;


    final int maxWidth = (int)(screen.width - screen.width * .25);
    if (tipRectangle.width > maxWidth) {
      tipRectangle.width = maxWidth;
    }

    final Dimension prefSize = tipRectangle.getSize();

    ScreenUtil.cropRectangleToFitTheScreen(tipRectangle);

    if (prefSize.width > tipRectangle.width) {
      final int delta = prefSize.width - tipRectangle.width;
      tipRectangle.x -= delta;
      if (tipRectangle.x < screen.x) {
        tipRectangle.x = screen.x + maxWidth / 2;
        tipRectangle.width = screen.width - maxWidth / 2;
      }
      else {
        tipRectangle.width += delta;
      }
    }

    toolTip.setPreferredSize(tipRectangle.getSize());

    myCurrentTooltip = toolTip;

    return myCurrentTooltip;
  }

  @Nullable
  private String getTipText(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor instanceof ValueDescriptorImpl) {
      String text = ((ValueDescriptorImpl)descriptor).getValueLabel();
      if (text != null) {
        if (StringUtil.startsWithChar(text, '{') && text.indexOf('}') > 0) {
          int idx = text.indexOf('}');
          if (idx != text.length() - 1) {
            text = text.substring(idx + 1);
          }
        }

        if (StringUtil.startsWithChar(text, '\"') && StringUtil.endsWithChar(text, '\"')) {
          text = text.substring(1, text.length() - 1);
        }

        final String tipText = prepareToolTipText(text);
        if (!tipText.isEmpty() &&
            (tipText.indexOf('\n') >= 0 || !getVisibleRect().contains(getRowBounds(getRowForPath(new TreePath(node.getPath())))))) {
          return tipText;
        }
      }
    }
    return node.getMarkupTooltipText() != null? "" : null;
  }

  @Nullable
  private DebuggerTreeNodeImpl getNodeToShowTip(MouseEvent event) {
    TreePath path = getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      Object last = path.getLastPathComponent();
      if (last instanceof DebuggerTreeNodeImpl) {
        return (DebuggerTreeNodeImpl)last;
      }
    }

    return null;
  }

  private String prepareToolTipText(String text) {
    int tabSize = CodeStyleSettingsManager.getSettings(myProject).getTabSize(JavaFileType.INSTANCE);
    if (tabSize < 0) {
      tabSize = 0;
    }
    final StringBuilder buf = new StringBuilder();
    boolean special = false;
    for (int idx = 0; idx < text.length(); idx++) {
      char c = text.charAt(idx);
      if (special) {
        if (c == 't') { // convert tabs to spaces
          for (int i = 0; i < tabSize; i++) {
            buf.append(' ');
          }
        }
        else if (c == 'r') { // remove occurrences of '\r'
        }
        else if (c == 'n') {
          buf.append('\n');
        }
        else {
          buf.append('\\');
          buf.append(c);
        }
        special = false;
      }
      else {
        if (c == '\\') {
          special = true;
        }
        else {
          buf.append(c);
        }
      }
    }
    return buf.toString();
  }

  @Override
  public void dispose() {
    final JComponent tooltip = myCurrentTooltip;
    if (tooltip != null) {
      tooltip.setVisible(false);
    }
    myCurrentTooltip = null;
    myCurrentTooltipNode = null;
  }

  public Project getProject() {
    return myProject;
  }

  private static class HideTooltip extends MouseAdapter {
    private final JToolTip myToolTip;

    public HideTooltip(JToolTip toolTip) {
      myToolTip = toolTip;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (UIUtil.isActionClick(e)) {
        final Window wnd = SwingUtilities.getWindowAncestor(myToolTip);
        if (wnd instanceof JWindow) {
          wnd.setVisible(false);
        }
      }
    }
  }
}
