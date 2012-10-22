/*
    Tryton Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.tryton.client.models;

import android.graphics.drawable.Drawable;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.Serializable;

/** A menu entry */
public class MenuEntry implements Serializable {

    private int id;
    private String label;
    private String actionType;
    private int actionId;
    // icon is declared transient as Drawables are not serializable
    private transient Drawable icon;
    private String iconSource;
    private int sequence;
    private List<MenuEntry> children;

    public MenuEntry(int id, String label, int sequence,
                     String actionType, int actionId) {
        this.id = id;
        this.label = label;
        this.sequence = sequence;
        this.children = new ArrayList<MenuEntry>();
        this.actionType = actionType;
        this.actionId = actionId;
    }

    public int getId() {
        return this.id;
    }

    public String getLabel() {
        return this.label;
    }

    public String getActionType() {
        return this.actionType;
    }
    
    public int getActionId() {
        return this.actionId;
    }

    public List<MenuEntry> getChildren() {
        return this.children;
    }

    public void addChild(MenuEntry child) {
        this.children.add(child);
    }

    public Drawable getIcon() {
        if (this.icon == null && this.iconSource != null) {
            System.out.println("Creating svg for " + label);
            SVG svg = SVGParser.getSVGFromString(this.iconSource);
            this.icon = svg.createPictureDrawable();
        }
        return icon;
    }

    public void setIconSource(String source) {
        this.iconSource = source;
    }
   
    public static class SequenceSorter implements Comparator<MenuEntry> {
        
        private static void sortChildren(MenuEntry entry,
                                         SequenceSorter sorter) {
             for (MenuEntry child : entry.getChildren()) {
                sortChildren(child, sorter);
            }
            Collections.sort(entry.children, sorter);
        }

        public static void recursiveSort(List<MenuEntry> entries) {
            SequenceSorter sorter = new SequenceSorter();
            Collections.sort(entries, sorter);
            for (MenuEntry entry : entries) {
                sortChildren(entry, sorter);
            }
        }

        public int compare(MenuEntry o1, MenuEntry o2) {
            return o1.sequence - o2.sequence;
        }
    }
}
