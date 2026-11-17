package app.nimarkogram.messenger.preferences;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import app.nimarkogram.messenger.appearance.ClientTheme;
import app.nimarkogram.messenger.components.LinkiHeroBanner;
import app.nimarkogram.messenger.updater.LinkiUpdateSheet;
import app.nimarkogram.messenger.updater.NimarkoUpdater;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/** Application information and the existing OTA entry point. */
public class LinkiAboutActivity extends NimarkoUniversalPreferencesActivity {
    private static final int UPDATES = 1, SOURCE = 2, CHANNEL = 3;

    private View heroView;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.LinkiAboutTitle);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (heroView == null) {
            heroView = createHero(getContext());
        }
        items.add(UItem.asFullyCustom(heroView).setTransparent(true));
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

    /**
     * Same cover as the update sheet: accent gradient, app icon, name, current
     * version and a translucent pill that opens the update screen.
     */
    private View createHero(Context context) {
        final int accent = ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.PRIMARY)
                : Theme.getColor(Theme.key_featuredStickers_addButton);

        final FrameLayout holder = new FrameLayout(context);
        holder.setPadding(dp(12), dp(8), dp(12), dp(4));

        final LinkiHeroBanner hero = new LinkiHeroBanner(context, accent);

        final LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        inner.setPadding(dp(20), dp(18), dp(20), dp(16));

        final ImageView logo = LinkiHeroBanner.createLogo(context, 46);
        inner.addView(logo, LayoutHelper.createLinear(46, 46, Gravity.CENTER_HORIZONTAL));

        final TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 27);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setText(LocaleController.getString(R.string.AppName));
        title.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
        inner.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        final TextView version = new TextView(context);
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        version.setTextColor(0xCCFFFFFF);
        version.setGravity(Gravity.CENTER);
        version.setText(LocaleController.formatString(R.string.LUS_CurrentVersionShort,
                NimarkoUpdater.getCurrentVersionName()));
        inner.addView(version, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        inner.addView(new View(context), LayoutHelper.createLinear(1, 0, 1f));

        final TextView pill = new TextView(context);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pill.setTypeface(AndroidUtilities.bold());
        pill.setTextColor(0xFFFFFFFF);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setPadding(dp(16), dp(7), dp(16), dp(7));
        pill.setBackground(Theme.createRoundRectDrawable(dp(18), 0x30FFFFFF));
        pill.setText(LocaleController.getString(R.string.LUS_HeroCheck));
        pill.setOnClickListener(v -> LinkiUpdateSheet.show(this));
        inner.addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        hero.addView(inner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        hero.setOnClickListener(v -> LinkiUpdateSheet.show(this));
        holder.addView(hero, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return holder;
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