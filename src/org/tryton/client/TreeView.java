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
package org.tryton.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.DataLoader;
import org.tryton.client.data.ViewCache;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;
import org.tryton.client.views.TreeFullAdapter;
import org.tryton.client.views.TreeSummaryAdapter;
import org.tryton.client.views.TreeSummaryItem;

/** Main tree view. Used for top level listing. Other trees are shown with
 * PickOne or ToManyEditor. 
 * TreeView must be unique in order for the dirty check to work. */
public class TreeView extends Activity
    implements Handler.Callback, ListView.OnItemClickListener,
               ExpandableListView.OnChildClickListener,
               DialogInterface.OnCancelListener {

    /** Use a static initializer to pass data to the activity on start.
        Set the menu that triggers the view to load the views. */
    public static void setup(MenuEntry origin) {
        entryInitializer = origin;
    }
    private static MenuEntry entryInitializer;

    private static final int MODE_SUMMARY = 1;
    private static final int MODE_EXTENDED = 2;

    static final int PAGING_SUMMARY = 40; // package scope, used by PickOne
    private static final int PAGING_EXTENDED = 10;
    
    private static boolean dirty;

    private MenuEntry origin;
    private ModelViewTypes viewTypes;
    private int totalDataCount = -1;
    private int dataOffset;
    private List<RelField> relFields;
    private List<Model> data;
    private int mode;
    private int callCountId; // Id for parallel count call
    private int callDataId; // Id for the other call chain
    private int currentLoadingMsg;
    private boolean refreshing;

    private TextView pagination;
    private ImageButton nextPage, previousPage;
    private ProgressDialog loadingDialog;
    private ListView tree;
    private ExpandableListView sumtree;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        AlertBuilder.updateRelogHandler(new Handler(this), this);
        // Init data
        if (state != null) {
            this.origin = (MenuEntry) state.getSerializable("origin");
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
            this.callCountId = state.getInt("callCountId");
            this.callDataId = state.getInt("callDataId");
            this.currentLoadingMsg = state.getInt("currentLoadingMsg");
            this.refreshing = state.getBoolean("refreshing");
            if (this.callCountId != 0) {
                DataLoader.update(this.callCountId, new Handler(this));
                this.showLoadingDialog(this.currentLoadingMsg);
            }
            if (this.callDataId != 0) {
                DataLoader.update(this.callDataId, new Handler(this));
                this.showLoadingDialog(this.currentLoadingMsg);
            }
            this.totalDataCount = state.getInt("totalDataCount");
            if (state.containsKey("data_count")) {
                int count = state.getInt("data_count");
                this.data = new ArrayList<Model>();
                for (int i = 0; i < count; i++) {
                    this.data.add((Model)state.getSerializable("data_" + i));
                }
            }
            if (state.containsKey("rel_count")) {
                int count = state.getInt("rel_count");
                this.relFields = new ArrayList<RelField>();
                for (int i = 0; i < count; i++) {
                    this.relFields.add((RelField)state.getSerializable("rel_" + i));
                }
            }
            this.mode = state.getInt("mode");
        } else if (entryInitializer != null) {
            this.origin = entryInitializer;
            entryInitializer = null;
            this.mode = MODE_SUMMARY;
        }
        // Init view
        this.setContentView(R.layout.tree);
        this.tree = (ListView) this.findViewById(R.id.tree_list);
        this.tree.setOnItemClickListener(this);
        this.sumtree = (ExpandableListView) this.findViewById(R.id.tree_sum_list);
        this.sumtree.setOnChildClickListener(this);
        this.pagination = (TextView) this.findViewById(R.id.tree_pagination);
        this.nextPage = (ImageButton) this.findViewById(R.id.tree_next_btn);
        this.previousPage = (ImageButton) this.findViewById(R.id.tree_prev_btn);
    }

    public void onResume() {
        super.onResume();
        // Load data if there isn't anyone or setup the list
        // or update existing data
        if (this.data == null && this.viewTypes == null) {
            this.loadViewsAndData();
        } else if (this.data == null || dirty) {
            this.loadDataAndMeta(this.refreshing);
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("origin", this.origin);
        outState.putSerializable("viewTypes", this.viewTypes);
        outState.putInt("totalDataCount", this.totalDataCount);
        outState.putBoolean("refreshing", this.refreshing);
        if (this.data != null) {
            outState.putSerializable("data_count", this.data.size());
            for (int i = 0; i < this.data.size(); i++) {
                outState.putSerializable("data_" + i, this.data.get(i));
            }
        }
        if (this.relFields != null) {
            outState.putSerializable("rel_count", this.relFields.size());
            for (int i = 0; i < this.relFields.size(); i++) {
                outState.putSerializable("rel_" + i, this.relFields.get(i));
            }
        }
        outState.putInt("mode", this.mode);
        outState.putInt("callCountId", this.callCountId);
        outState.putInt("callDataId", this.callDataId);
        outState.putInt("currentLoadingMsg", this.currentLoadingMsg);
    }

    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }

    public static void setDirty() {
        dirty = true;
    }

    /** Update the display list and header with loaded data.
     * A call to hideLoadingDialog should be done around it as it means
     * the data are all there. */
    private void updateList() {
        // Update paging display
        String format = this.getString(R.string.tree_pagination);
        int start = 0;
        if (this.data.size() > 0) {
            start = this.dataOffset + 1;
        }
        this.pagination.setText(String.format(format,
                                              start,
                                              this.dataOffset + this.data.size(),
                                              this.totalDataCount));
        if (this.dataOffset == 0) {
            this.previousPage.setVisibility(View.INVISIBLE);
        } else {
            this.previousPage.setVisibility(View.VISIBLE);
        }
        if (this.dataOffset + this.data.size() < this.totalDataCount) {
            this.nextPage.setVisibility(View.VISIBLE);
        } else {
            this.nextPage.setVisibility(View.INVISIBLE);
        }
        // Update data
        ModelView view = this.viewTypes.getView("tree");
        switch (this.mode) {
        case MODE_EXTENDED:
            TreeFullAdapter adapt = new TreeFullAdapter(view,
                                                        this.data);
            this.tree.setAdapter(adapt);
            this.sumtree.setVisibility(View.GONE);
            this.tree.setVisibility(View.VISIBLE);
            break;
        case MODE_SUMMARY:
            TreeSummaryAdapter sumadapt = new TreeSummaryAdapter(view,
                                                                 this.data);
            this.sumtree.setAdapter(sumadapt);
            this.sumtree.setVisibility(View.VISIBLE);
            this.tree.setVisibility(View.GONE);
            break;
        }
    }

    public void prevPage(View button) {
        switch (this.mode) {
        case MODE_EXTENDED:
             this.dataOffset -= PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            this.dataOffset -= PAGING_SUMMARY;
        }
        if (this.dataOffset < 0) {
            this.dataOffset = 0;
        }
        this.loadData(false);
    }

    public void nextPage(View button) {
        int maxOffset = this.totalDataCount;
        switch (this.mode) {
        case MODE_EXTENDED:
            this.dataOffset += PAGING_EXTENDED;
            maxOffset -= PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            this.dataOffset += PAGING_SUMMARY;
            maxOffset -= PAGING_SUMMARY;
        }
        this.dataOffset = Math.min(this.dataOffset, maxOffset);
        this.loadData(false);
    }

    public void onItemClick(AdapterView<?> adapt, View v,
                            int position, long id) {
        Model clickedData = this.data.get(position);
        ModelView formView = this.viewTypes.getView("form");
        if (formView != null) {
            FormView.setup(formView);
        } else {
            FormView.setup(this.viewTypes.getViewId("form"));
        }
        Session.current.editModel(clickedData);
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
    }
    public boolean onChildClick(ExpandableListView parent, View v, int groupPos,
                                int childPos, long id) {
        Model clickedData = this.data.get(groupPos);
        ModelView formView = this.viewTypes.getView("form");
        if (formView != null) {
            FormView.setup(formView);
        } else {
            FormView.setup(this.viewTypes.getViewId("form"));
        }
        Session.current.editModel(clickedData);
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
        return true;
    }

    private static final int LOADING_VIEWS = 0;
    private static final int LOADING_DATA = 1;
    public void showLoadingDialog(int type) {
        if (this.loadingDialog == null) {
            this.currentLoadingMsg = type;
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            String message;
            switch (type) {
            case LOADING_VIEWS:
                message = this.getString(R.string.view_loading);
                break;
            default:
                message = this.getString(R.string.data_loading);
                break;
            }
            this.loadingDialog.setMessage(message);
            this.loadingDialog.setOnCancelListener(this);
            this.loadingDialog.show();
        }
    }

    public void onCancel(DialogInterface dialog) {
        DataLoader.cancel(this.callCountId);
        DataLoader.cancel(this.callDataId);
        this.callDataId = 0;
        this.callCountId = 0;
        this.refreshing = false;
        this.loadingDialog = null;
        this.finish();
    }

    /** Hide the loading dialog if shown. */
    public void hideLoadingDialog() {
        if (this.loadingDialog != null) {
            this.loadingDialog.dismiss();
            this.loadingDialog = null;
        }
    }

    /** Load views and all data when done (by cascading the calls in handler) */
    private void loadViewsAndData() {
        if (this.callDataId == 0) {
            this.showLoadingDialog(LOADING_VIEWS);
            this.callDataId = DataLoader.loadViews(this, this.origin,
                                                   new Handler(this), false);
        }
    }
    
    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta(boolean refresh) {
        String className = this.viewTypes.getModelName();
        if (this.callCountId == 0) {
            this.totalDataCount = -1;
            this.showLoadingDialog(LOADING_DATA);
            this.callCountId = DataLoader.loadDataCount(this, className,
                                                        new Handler(this),
                                                        refresh);
        }
        if (this.callDataId == 0) {
            this.relFields = null;
            this.showLoadingDialog(LOADING_DATA);
            this.callDataId = DataLoader.loadRelFields(this, className,
                                                       new Handler(this),
                                                       refresh);
        }
    }

    /** Load data. Requires that views and meta are loaded. */
    private void loadData(boolean refresh) {
        if (this.callDataId != 0) {
            // A call is already pending, wait for its result
            return;
        }
        int count = 10;
        switch (this.mode) {
        case MODE_EXTENDED:
            count = PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            count = PAGING_SUMMARY;
            break;
        }
        int expectedSize = Math.min(this.totalDataCount - this.dataOffset,
                                    count);
        String className = this.viewTypes.getModelName();
        this.showLoadingDialog(LOADING_DATA);
        ModelView view = this.viewTypes.getView("tree");
        this.callDataId = DataLoader.loadData(this, className, this.dataOffset,
                                              count, expectedSize,
                                              this.relFields, view,
                                              new Handler(this),
                                              refresh);
    }

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case DataLoader.VIEWS_OK:
            // Close the loading dialog if present
            this.hideLoadingDialog();
            this.callDataId = 0;
            @SuppressWarnings("unchecked")
            Object[] ret = (Object[]) msg.obj;
            this.viewTypes = (ModelViewTypes) ret[1];
            // Check if a tree view is available
            if (this.viewTypes.hasView("tree")) {
                this.loadDataAndMeta(this.refreshing);
            } else if (this.viewTypes.hasView("graph")) {
                this.startGraphActivity();
                this.finish();
            } else {
                // No tree view defined, show error and quit
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.error);
                b.setMessage(R.string.general_no_tree_view);
                b.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            TreeView.this.finish();
                        }
                    });
                b.show();
            }
            break;
        case DataLoader.VIEWS_NOK:
        case DataLoader.DATA_NOK:
        case DataLoader.DATACOUNT_NOK:
            // Close the loading dialog if present
            this.hideLoadingDialog();
            this.refreshing = false;
            if (msg.what == DataLoader.DATACOUNT_NOK) {
                this.callCountId = 0;
            } else {
                this.callDataId = 0;
            }
            // Show error popup
            Exception e = (Exception) msg.obj;
            if (!AlertBuilder.showUserError(e, this)
                && !AlertBuilder.showUserError(e, this)) {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.error);
                b.setMessage(R.string.network_error);
                b.show();
                ((Exception)msg.obj).printStackTrace();
            }
            break;
        case DataLoader.DATACOUNT_OK:
            this.callCountId = 0;
            ret = (Object[]) msg.obj;
            int count = (Integer) ret[1];
            this.totalDataCount = count;
            if (this.relFields == null) {
                // Wait for relfields callback
            } else {
                // Load data
                this.loadData(this.refreshing);
            }
            break;
        case DataLoader.RELFIELDS_OK:
            this.callDataId = 0;
            ret = (Object[]) msg.obj;
            this.relFields = (List<RelField>) ret[1];
            if (this.totalDataCount == -1) {
                // Wait for data count callback
            } else {
                // Load data
                this.loadData(this.refreshing);
            }
            break;
        case DataLoader.DATA_OK:
            this.callDataId = 0;
            this.refreshing = false;
            ret = (Object[]) msg.obj;
            List<Model> data = (List<Model>) ret[1];
            this.data = data;
            dirty = false;
            this.hideLoadingDialog();
            this.updateList();
            break;
        case TrytonCall.NOT_LOGGED:
            this.callDataId = 0;
            this.callCountId = 0;
            // Ask for relog
            this.hideLoadingDialog();
            AlertBuilder.showRelog(this, new Handler(this));
            break;
        case AlertBuilder.RELOG_CANCEL:
            this.finish();
            break;
        case AlertBuilder.RELOG_OK:
            this.loadViewsAndData();
            break;
        }
        return true;
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_NEW_ID = 1;
    private static final int MENU_GRAPH_ID = 2;
    private static final int MENU_MODE_ID = 3;
    private static final int MENU_REFRESH_ID = 4;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add logout entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(R.drawable.tryton_log_out);
        // Set form entry (new data)
        MenuItem add = menu.add(android.view.Menu.NONE, MENU_NEW_ID, 1,
                                this.getString(R.string.general_new_record));
        add.setIcon(R.drawable.tryton_new);
        // Set view mode switch
        MenuItem mode = menu.add(android.view.Menu.NONE, MENU_MODE_ID, 10,
                                 this.getString(R.string.tree_switch_mode_summary));
        mode.setIcon(R.drawable.tryton_fullscreen);
        // Set refresh
        MenuItem refresh = menu.add(android.view.Menu.NONE, MENU_REFRESH_ID, 30,
                                 this.getString(R.string.general_reload));
        refresh.setIcon(R.drawable.tryton_refresh);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Add graph entry if there is a graph view
        if (this.viewTypes.getView("graph") != null
            && menu.findItem(MENU_GRAPH_ID) == null) {
            // Set graph entry
            MenuItem graph = menu.add(android.view.Menu.NONE, MENU_GRAPH_ID, 2,
                                      this.getString(R.string.general_graph));
            graph.setIcon(R.drawable.tryton_chart);
        }
        // Set mode label
        MenuItem mode = menu.findItem(MENU_MODE_ID);
        if (mode != null) {
            switch (this.mode) {
            case MODE_SUMMARY:
                mode.setTitle(R.string.tree_switch_mode_extended);
                break;
            case MODE_EXTENDED:
                mode.setTitle(R.string.tree_switch_mode_summary);
            }
        }
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGOUT_ID:
            Start.logout(this);
            break;
        case MENU_MODE_ID:
            if (this.mode == MODE_SUMMARY) {
                this.mode = MODE_EXTENDED;
            } else {
                this.mode = MODE_SUMMARY;
            }
            this.loadData(false);
            break;
        case MENU_NEW_ID:
            Session.current.editNewModel(this.viewTypes.getModelName());
            ModelView formView = this.viewTypes.getView("form");
            if (formView != null) {
                FormView.setup(formView);
            } else {
                FormView.setup(this.viewTypes.getViewId("form"));
            }
            Intent i = new Intent(this, FormView.class);
            this.startActivity(i);
            break;
        case MENU_REFRESH_ID:
            this.refreshing = true;
            this.loadDataAndMeta(this.refreshing);
            break;
        case MENU_GRAPH_ID:
            this.startGraphActivity();
            break;
        }
        return true;
    }

    /** Start a graph activity */
    private void startGraphActivity() {
        ModelView graphView = this.viewTypes.getView("graph");
        if (graphView != null) {
            GraphView.setup(graphView);
        } else {
            GraphView.setup(this.viewTypes.getViewId("graph"),
                            this.viewTypes.getModelName());
        }
        Intent i = new Intent(this, GraphView.class);
        this.startActivity(i);
    }
}
