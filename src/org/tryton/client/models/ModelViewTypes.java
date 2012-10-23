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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** This is just an aggregation of views per type for a model name. */
public class ModelViewTypes implements Serializable {

    private String modelName;
    private Map<String, ModelView> views;

    public ModelViewTypes(String modelName) {
        this.modelName = modelName;
        this.views = new HashMap<String, ModelView>();
    }

    public void putView(String type, ModelView view) {
        this.views.put(type, view);
    }

    public ModelView getView(String type) {
        return this.views.get(type);
    }

    public String getModelName() {
        return this.modelName;
    }
}