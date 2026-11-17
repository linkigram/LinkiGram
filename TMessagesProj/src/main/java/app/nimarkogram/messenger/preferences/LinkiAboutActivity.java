package app.nimarkogram.messenger.preferences;

import android.view.View;

import java.util.ArrayList;

import app.nimarkogram.messenger.updater.LinkiUpdateSheet;
import app.nimarkogram.messenger.updater.NimarkoUpdater;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/** Application information and the existing OTA entry point. */
public class LinkiAboutActivity extends NimarkoUniversalPreferencesActivity {
    private static final int UPDATES = 1, SOURCE = 2, CHANNEL = 3;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.LinkiAboutTitle);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("LinkiGram"));
        items.add(UItem.asButton(0, LocaleController.getString(R.string.LinkiAboutVersion),
                NimarkoUpdater.getCurrentVersionName()));
        items.add(UItem.asButton(0, LocaleController.getString(R.string.LinkiAboutBuild),
                String.valueOf(NimarkoUpdater.getCurrentVersionCode())));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiAboutDescription)));
        items.add(UItem.asButton(UPDATES, R.drawable.msg_retry_solar,
                LocaleController.getString(R.string.UP_CheckForUpdates)));
        items.add(UItem.asButton(SOURCE, R.drawable.msg_link_2_solar,
                LocaleController.getString(R.string.NM_HUB_SourceCode)));
        items.add(UItem.asButton(CHANNEL, R.drawable.msg_message_solar,
                LocaleController.getString(R.string.LinkiAboutChannel)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.LinkiAboutLicense)));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == UPDATES) {
            LinkiUpdateSheet.show(this);
        } else if (item.id == SOURCE) {
            Browser.openUrl(getParentActivity(), "https://github.com/linkigram/LinkiGram");
        } else if (item.id == CHANNEL) {
            Browser.openUrl(getParentActivity(), "https://t.me/serostgram");
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
