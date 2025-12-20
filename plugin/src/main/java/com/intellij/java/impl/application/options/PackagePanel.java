/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.application.options;

import consulo.application.ApplicationBundle;
import consulo.language.codeStyle.PackageEntry;
import consulo.language.codeStyle.PackageEntryTable;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.TableUtil;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

/**
 * @author Max Medvedev
 */
public class PackagePanel {
    private static void addPackageToPackages(JBTable table, PackageEntryTable list) {
        int selected = table.getSelectedRow() + 1;
        if (selected < 0) {
            selected = list.getEntryCount();
        }
        PackageEntry entry = new PackageEntry(false, "", true);
        list.insertEntryAt(entry, selected);
        ImportLayoutPanel.refreshTableModel(selected, table);
    }

    private static void removeEntryFromPackages(JBTable table, PackageEntryTable list) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return;
        }
        TableUtil.stopEditing(table);
        list.removeEntryAt(selected);
        AbstractTableModel model = (AbstractTableModel)table.getModel();
        model.fireTableRowsDeleted(selected, selected);
        if (selected >= list.getEntryCount()) {
            selected--;
        }
        if (selected >= 0) {
            table.setRowSelectionInterval(selected, selected);
        }
    }

    public static JPanel createPackagesPanel(JBTable packageTable, PackageEntryTable packageList) {
        JPanel panel = ToolbarDecorator.createDecorator(packageTable)
            .setAddAction(button -> addPackageToPackages(packageTable, packageList))
            .setRemoveAction(button -> removeEntryFromPackages(packageTable, packageList))
            .disableUpDownActions()
            .setPreferredSize(new Dimension(-1, 100))
            .createPanel();

        UIUtil.addBorder(panel, IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.packages.to.use.import.with"), false));

        return panel;
    }
}
