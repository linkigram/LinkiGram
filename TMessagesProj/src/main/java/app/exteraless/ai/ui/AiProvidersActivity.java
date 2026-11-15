package app.exteraless.ai.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import app.exteraless.ai.AiConfig;
import app.exteraless.ai.AiController;
import app.exteraless.ai.data.Service;

/**
 * LinkiGram: a dedicated screen for managing the saved AI providers (endpoint + model
 * pairs). Tap a row to make it active and open its editor ({@link AiSettingsActivity});
 * long-press for edit/delete; the last row adds a new provider to configure.
 */
public class AiProvidersActivity extends BaseFragment {

    private static final int ROW_HEADER = 0;
    private static final int ROW_SERVICE = 1;
    private static final int ROW_ADD = 2;

    private RecyclerListView listView;
    private ListAdapter adapter;

    private final ArrayList<Service> services = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        reload();
        return true;
    }

    private void reload() {
        services.clear();
        ArrayList<Service> stored = AiConfig.getServices();
        if (stored.isEmpty()) {
            // First run: the default service is what the assistant already uses, make it real.
            AiController.saveService(AiConfig.DEFAULT_SERVICE);
            stored = AiConfig.getServices();
        }
        services.addAll(stored);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private static String describe(Service service) {
        String host = AndroidUtilities.getHostAuthority(service.getUrl());
        if (TextUtils.isEmpty(host)) {
            host = service.getUrl();
        }
        String model = service.getShortModel();
        if (TextUtils.isEmpty(host)) {
            return model;
        }
        return TextUtils.isEmpty(model) ? host : host + " \u00b7 " + model;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(LocaleController.getString(R.string.NM_AiProviders_Title));
        actionBar.setAllowOverlayTitle(false);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (adapter.getItemViewType(position) == ROW_ADD) {
                addProvider();
            } else {
                int index = position - 1;
                if (index >= 0 && index < services.size()) {
                    Service service = services.get(index);
                    AiConfig.setSelectedServices(service);
                    presentFragment(new AiSettingsActivity());
                }
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            int index = position - 1;
            if (adapter.getItemViewType(position) != ROW_SERVICE || index < 0 || index >= services.size()) {
                return false;
            }
            showServiceOptions(view, services.get(index));
            return true;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void showServiceOptions(View view, Service service) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, 1);
        } catch (Throwable ignored) {}
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions.makeOptions(this, view)
                .add(R.drawable.msg_edit, LocaleController.getString(R.string.Edit), () -> {
                    AiConfig.setSelectedServices(service);
                    presentFragment(new AiSettingsActivity());
                })
                .add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> confirmDelete(service))
                .setScrimViewBackground(listView.getClipBackground(view))
                .show();
    }

    private void confirmDelete(Service service) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.NM_AiProviders_Delete));
        builder.setMessage(LocaleController.formatString(R.string.NM_AiProviders_DeleteConfirm, service.getDisplayName()));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            AiController.removeService(service);
            reload();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void addProvider() {
        if (getParentActivity() == null) {
            return;
        }
        Service service = new Service("", "", null, false);
        AiController.saveService(service);
        reload();
        presentFragment(new AiSettingsActivity());
        BulletinFactory.of(this).createSimpleBulletin(R.drawable.ai_chat,
                LocaleController.getString(R.string.NM_AiProviders_AddHint)).show();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;

        ListAdapter(Context context) {
            this.ctx = context;
        }

        @Override
        public int getItemCount() {
            return 1 + services.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return ROW_HEADER;
            }
            if (position == getItemCount() - 1) {
                return ROW_ADD;
            }
            return ROW_SERVICE;
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == ROW_HEADER) {
                view = new HeaderCell(ctx, getResourceProvider());
            } else {
                TextSettingsCell cell = new TextSettingsCell(ctx, getResourceProvider());
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
                view = cell;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            if (type == ROW_HEADER) {
                ((HeaderCell) holder.itemView).setText(LocaleController.getString(R.string.NM_AiProviders_Header));
            } else if (type == ROW_ADD) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setTextAndValue(LocaleController.getString(R.string.NM_AiProviders_Add),
                        LocaleController.getString(R.string.NM_AiProviders_AddDesc), false);
            } else {
                Service service = services.get(position - 1);
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                String prefix = service.isSelected() ? "\u2713 " : "";
                cell.setTextAndValue(prefix + service.getDisplayName(), describe(service),
                        position < services.size());
            }
        }
    }
}
