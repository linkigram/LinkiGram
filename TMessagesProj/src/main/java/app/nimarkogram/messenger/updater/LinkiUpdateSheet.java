package app.nimarkogram.messenger.updater;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import app.nimarkogram.messenger.appearance.ClientTheme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update sheet in the style of exteraless' updater: logo + version header, a
 * status card with icon/subtitle/release notes, a progress line, one action
 * button, an auto-update toggle row and a releases link.
 */
public class LinkiUpdateSheet extends BottomSheet implements NimarkoUpdater.DownloadUiOwner {

    private static final String RELEASES_URL = "https://github.com/timaa130704/LinkiGram/releases";

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    private static final int STATE_CHECKING = 0;
    private static final int STATE_LATEST = 1;
    private static final int STATE_AVAILABLE = 2;
    private static final int STATE_FAILED = 3;

    private final BaseFragment fragment;
    private final int accent;
    private final LinearLayout content;
    private final ImageView statusImage;
    private final RadialProgressView statusProgress;
    private final TextView statusTitle;
    private final TextView statusSubtitle;
    private final TextView versionTransition;
    private final TextView heroPill;
    private final TextView notesView;
    private final ScrollView notesScroll;
    private final LinearLayout notesCard;
    private final LineProgressView progressView;
    private final ButtonWithCounterView actionButton;
    private final TextView channelInfo;
    private final org.telegram.ui.Cells.TextCheckCell betaToggle;
    private final TextView[] channelSegments = new TextView[2];

    private int state = -1;
    private boolean downloadFinished;
    private boolean downloadClicked;
    private boolean dismissed;
    private long downloadBindingToken;
    private NimarkoUpdater.Update release;

    public LinkiUpdateSheet(BaseFragment fragment) {
        this(fragment, null);
    }

    public LinkiUpdateSheet(BaseFragment fragment, NimarkoUpdater.Update update) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;
        this.release = update;
        fixNavigationBar();
        Context context = getContext();
        accent = ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.PRIMARY)
                : getThemedColor(Theme.key_featuredStickers_addButton);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(20));

        View handle = new View(context);
        handle.setBackground(Theme.createRoundRectDrawable(dp(2),
                ColorUtils.setAlphaComponent(getThemedColor(Theme.key_dialogTextGray2), 0x66)));
        content.addView(handle, LayoutHelper.createLinear(32, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        HeroBanner hero = createHero(context);
        LinearLayout heroInner = new LinearLayout(context);
        heroInner.setOrientation(LinearLayout.VERTICAL);
        heroInner.setGravity(Gravity.CENTER_HORIZONTAL);
        heroInner.setPadding(dp(20), dp(18), dp(20), dp(16));

        ImageView heroLogo = createLogo(context, 46);
        heroInner.addView(heroLogo, LayoutHelper.createLinear(46, 46, Gravity.CENTER_HORIZONTAL));

        TextView heroTitle = new TextView(context);
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 27);
        heroTitle.setTypeface(AndroidUtilities.bold());
        heroTitle.setTextColor(0xFFFFFFFF);
        heroTitle.setGravity(Gravity.CENTER);
        heroTitle.setText(getString(R.string.AppName));
        heroTitle.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
        heroInner.addView(heroTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        TextView heroVersion = new TextView(context);
        heroVersion.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        heroVersion.setTextColor(0xCCFFFFFF);
        heroVersion.setGravity(Gravity.CENTER);
        heroVersion.setText(LocaleController.formatString(R.string.LUS_CurrentVersionShort,
                NimarkoUpdater.getCurrentVersionName()));
        heroInner.addView(heroVersion, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        heroInner.addView(new View(context), LayoutHelper.createLinear(1, 0, 1f));

        heroPill = new TextView(context);
        heroPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroPill.setTypeface(AndroidUtilities.bold());
        heroPill.setTextColor(0xFFFFFFFF);
        heroPill.setGravity(Gravity.CENTER);
        heroPill.setSingleLine(true);
        heroPill.setEllipsize(TextUtils.TruncateAt.END);
        heroPill.setPadding(dp(16), dp(7), dp(16), dp(7));
        heroPill.setBackground(Theme.createRoundRectDrawable(dp(18), 0x30FFFFFF));
        heroInner.addView(heroPill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        hero.addView(heroInner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 220, 0, 4, 0, 0));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(22));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout statusIcon = new FrameLayout(context);
        statusImage = new ImageView(context);
        statusImage.setScaleType(ImageView.ScaleType.CENTER);
        statusIcon.addView(statusImage, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        statusProgress = new RadialProgressView(context, resourcesProvider);
        statusProgress.setSize(dp(22));
        statusProgress.setProgressColor(accent);
        statusIcon.addView(statusProgress, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
        row.addView(statusIcon, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        statusTitle = new TextView(context);
        statusTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        statusTitle.setTypeface(AndroidUtilities.bold());
        statusTitle.setTextColor(textPrimary());
        texts.addView(statusTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        statusSubtitle = new TextView(context);
        statusSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusSubtitle.setTextColor(textSecondary());
        texts.addView(statusSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        versionTransition = new TextView(context);
        versionTransition.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        versionTransition.setTypeface(AndroidUtilities.bold());
        versionTransition.setTextColor(accent);
        versionTransition.setGravity(Gravity.CENTER);
        versionTransition.setSingleLine(true);
        versionTransition.setEllipsize(TextUtils.TruncateAt.END);
        versionTransition.setBackground(Theme.createRoundRectDrawable(dp(11),
                ColorUtils.setAlphaComponent(accent, 0x1F)));
        versionTransition.setPadding(dp(12), dp(5), dp(12), dp(5));
        versionTransition.setVisibility(View.GONE);
        card.addView(versionTransition, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        notesView = new TextView(context);
        notesView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        notesView.setLineSpacing(dp(3), 1f);
        notesView.setTextColor(textPrimary());
        notesView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        notesView.setMovementMethod(LinkMovementMethod.getInstance());
        notesScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        (int) (AndroidUtilities.displaySize.y * 0.30f), MeasureSpec.AT_MOST));
            }
        };
        notesScroll.setVerticalScrollBarEnabled(false);
        notesScroll.addView(notesView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        notesCard = new LinearLayout(context);
        notesCard.setOrientation(LinearLayout.VERTICAL);
        notesCard.setBackground(cardBackground(22));
        notesCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView notesHeader = new TextView(context);
        notesHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        notesHeader.setTypeface(AndroidUtilities.bold());
        notesHeader.setTextColor(accent);
        notesHeader.setText(getString(R.string.LUS_WhatsNew));
        notesCard.addView(notesHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));
        notesCard.addView(notesScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        notesCard.setVisibility(View.GONE);

        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));
        content.addView(notesCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        progressView = new LineProgressView(context);
        progressView.setProgressColor(accent);
        progressView.setBackColor(ColorUtils.setAlphaComponent(accent, 0x33));
        progressView.setVisibility(View.GONE);
        content.addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6, 2, 14, 2, 0));

        actionButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        actionButton.setFilled(true);
        actionButton.setOnClickListener(v -> onAction());
        content.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 14, 0, 0));

        LinearLayout autoCard = new LinearLayout(context);
        autoCard.setOrientation(LinearLayout.VERTICAL);
        autoCard.setBackground(cardBackground(22));
        autoCard.setPadding(dp(16), dp(14), dp(16), dp(6));

        TextView channelHeader = new TextView(context);
        channelHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        channelHeader.setTypeface(AndroidUtilities.bold());
        channelHeader.setTextColor(accent);
        channelHeader.setText(getString(R.string.LUS_AutoHeader));
        autoCard.addView(channelHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable segmentsBg = new GradientDrawable();
        segmentsBg.setCornerRadius(dp(22));
        segmentsBg.setColor(ColorUtils.setAlphaComponent(accent, 0x14));
        segments.setBackground(segmentsBg);
        for (int i = 0; i < channelSegments.length; i++) {
            final int index = i;
            TextView segment = new TextView(context);
            segment.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setEllipsize(TextUtils.TruncateAt.END);
            segment.setText(getString(index == 0 ? R.string.LUS_Off : R.string.LUS_On));
            segment.setOnClickListener(v -> selectAutoUpdate(index == 1));
            channelSegments[i] = segment;
            segments.addView(segment, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        }
        autoCard.addView(segments, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 10, 0, 0));

        channelInfo = new TextView(context);
        channelInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        channelInfo.setTextColor(textSecondary());
        autoCard.addView(channelInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 2, 8, 2, 0));

        betaToggle = new org.telegram.ui.Cells.TextCheckCell(context, 4, true, resourcesProvider);
        betaToggle.setTextAndValueAndCheck(getString(R.string.LUS_Beta),
                getString(R.string.LUS_BetaInfo), NimarkoUpdateConfig.getBetaUpdates(), true, false);
        betaToggle.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        betaToggle.setOnClickListener(v -> {
            NimarkoUpdater.DownloadUiState download = NimarkoUpdater.getDownloadUiState();
            if (state == STATE_CHECKING || download.downloading || download.paused) return;
            boolean enabled = !NimarkoUpdateConfig.getBetaUpdates();
            NimarkoUpdateConfig.setBetaUpdates(enabled);
            NimarkoUpdater.lastUpdate = null;
            betaToggle.setChecked(enabled);
            downloadFinished = false;
            downloadClicked = false;
            check();
        });
        autoCard.addView(betaToggle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        content.addView(autoCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        content.addView(createLink(context, R.drawable.msg_link, getString(R.string.LUS_AllReleases), () ->
                Browser.openUrl(getContext(), RELEASES_URL)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 16, 0, 0));

        setCustomView(content);
        setOnDismissListener(() -> {
            dismissed = true;
            long token = downloadBindingToken;
            downloadBindingToken = 0L;
            if (token != 0L) {
                NimarkoUpdater.unbindDownloadUi(token);
            }
        });

        downloadBindingToken = NimarkoUpdater.bindDownloadUi(actionButton, this);
        NimarkoUpdater.DownloadUiState downloadState = NimarkoUpdater.getDownloadUiState();
        if (downloadState.finished) {
            downloadFinished = true;
        } else if (downloadState.downloading || downloadState.paused) {
            downloadClicked = true;
        }

        updateAutoUpdate();
        if (release != null) {
            setState(STATE_AVAILABLE);
        } else {
            check();
        }
    }

    private int textPrimary() {
        return ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.ON_SURFACE)
                : getThemedColor(Theme.key_dialogTextBlack);
    }

    private int textSecondary() {
        return ClientTheme.isEnabled()
                ? ClientTheme.color(ClientTheme.SECONDARY)
                : ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextBlack),
                        getThemedColor(Theme.key_windowBackgroundWhite), 0.45f);
    }

    private int surface() {
        if (ClientTheme.glassEnabled()) return ClientTheme.glassColor(ClientTheme.SURFACE);
        if (ClientTheme.isEnabled()) return ClientTheme.color(ClientTheme.SURFACE_HIGH);
        return getThemedColor(Theme.key_graySection);
    }

    private GradientDrawable cardBackground(int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(radiusDp));
        background.setColor(surface());
        background.setStroke(dp(1), ColorUtils.setAlphaComponent(textPrimary(), 0x14));
        return background;
    }

    private ImageView createLogo(Context context, int sizeDp) {
        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(context.getPackageName());
            logo.setImageDrawable(icon);
        } catch (Exception e) {
            FileLog.e(e);
        }
        final float radius = dp(sizeDp / 4f);
        logo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        logo.setClipToOutline(true);
        logo.setElevation(dp(2));
        return logo;
    }

    private HeroBanner createHero(Context context) {
        final float radius = dp(28);
        HeroBanner hero = new HeroBanner(context, accent);
        hero.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        hero.setClipToOutline(true);
        hero.setElevation(dp(1));
        return hero;
    }

    /**
     * Big rounded banner at the top of the sheet, like the "About phone" cover
     * in ColorOS: an accent gradient with soft light sweeps, the app name and
     * version, and a translucent pill with the current update state.
     */
    private static class HeroBanner extends FrameLayout {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int colorTop;
        private final int colorBottom;

        HeroBanner(Context context, int accentColor) {
            super(context);
            colorTop = ColorUtils.blendARGB(accentColor, 0xFFFFFFFF, 0.18f);
            colorBottom = ColorUtils.blendARGB(accentColor, 0xFF000000, 0.40f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, w * 0.85f, h,
                    colorTop, colorBottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setColor(0x24FFFFFF);
            canvas.save();
            canvas.rotate(-22, w * 0.5f, h * 0.5f);
            canvas.drawOval(w * 0.20f, -h * 0.42f, w * 1.12f, h * 0.58f, paint);
            canvas.restore();

            paint.setColor(0x16FFFFFF);
            canvas.save();
            canvas.rotate(19, w * 0.5f, h * 0.5f);
            canvas.drawOval(-w * 0.24f, h * 0.36f, w * 0.86f, h * 1.42f, paint);
            canvas.restore();

            paint.setColor(0x0DFFFFFF);
            canvas.drawCircle(w * 0.86f, h * 0.12f, h * 0.55f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x26FFFFFF);
            canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f),
                    dp(28), dp(28), paint);
            paint.setStyle(Paint.Style.FILL);

            super.onDraw(canvas);
        }
    }

    private TextView createLink(Context context, int icon, CharSequence text, Runnable onClick) {
        TextView link = new TextView(context);
        link.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        link.setTypeface(AndroidUtilities.bold());
        link.setTextColor(accent);
        link.setGravity(Gravity.CENTER);
        link.setSingleLine(true);
        link.setEllipsize(TextUtils.TruncateAt.END);
        link.setPadding(dp(12), 0, dp(12), 0);
        link.setText(text);
        Drawable drawable = ContextCompat.getDrawable(context, icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            drawable.setBounds(0, 0, dp(20), dp(20));
            link.setCompoundDrawables(drawable, null, null, null);
            link.setCompoundDrawablePadding(dp(6));
        }
        link.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x33)),
                cardBackground(15), null));
        link.setOnClickListener(v -> onClick.run());
        return link;
    }

    private void selectAutoUpdate(boolean enabled) {
        if (NimarkoUpdateConfig.getAutoOTA() == enabled) {
            return;
        }
        NimarkoUpdateConfig.setAutoOTA(enabled);
        animateLayout();
        updateAutoUpdate();
    }

    private void updateAutoUpdate() {
        boolean on = NimarkoUpdateConfig.getAutoOTA();
        for (int i = 0; i < channelSegments.length; i++) {
            TextView segment = channelSegments[i];
            boolean selected = (i == 1) == on;
            segment.setTypeface(selected ? AndroidUtilities.bold() : null);
            segment.setTextColor(selected ? accent : getThemedColor(Theme.key_dialogTextBlack));
            segment.setBackground(selected
                    ? Theme.AdaptiveRipple.filledRect(ColorUtils.setAlphaComponent(accent, 0x26), 19)
                    : Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        }
        channelInfo.setText(getString(on ? R.string.LUS_AutoInfoOn : R.string.LUS_AutoInfoOff));
    }

    private void check() {
        release = null;
        setState(STATE_CHECKING);
        NimarkoUpdater.checkUpdates(fragment, true,
                () -> setState(STATE_LATEST),
                () -> {
                    release = NimarkoUpdater.lastUpdate;
                    if (release == null || !release.isNew()) {
                        release = NimarkoUpdater.getOrRestoreLastUpdate();
                    }
                    setState(STATE_AVAILABLE);
                },
                () -> setState(STATE_FAILED));
    }

    private void animateLayout() {
        if (content.isAttachedToWindow()) {
            TransitionManager.beginDelayedTransition(content, new AutoTransition().setDuration(220));
        }
    }

    private void setState(int value) {
        animateLayout();
        state = value;
        boolean checking = value == STATE_CHECKING;
        betaToggle.setEnabled(!checking);
        betaToggle.setAlpha(checking ? 0.5f : 1f);
        statusProgress.setVisibility(checking ? View.VISIBLE : View.GONE);
        statusImage.setVisibility(checking ? View.GONE : View.VISIBLE);
        int iconColor = accent;
        int iconBackground = ColorUtils.setAlphaComponent(accent, 0x26);
        CharSequence subtitle = null;
        switch (value) {
            case STATE_CHECKING:
                statusTitle.setText(getString(R.string.LUS_Checking));
                heroPill.setText(getString(R.string.LUS_HeroChecking));
                subtitle = checkedText();
                break;
            case STATE_LATEST:
                statusImage.setImageResource(R.drawable.msg_check_s);
                statusTitle.setText(getString(R.string.LUS_Latest));
                heroPill.setText(getString(R.string.LUS_HeroLatest));
                subtitle = checkedText();
                break;
            case STATE_AVAILABLE:
                statusImage.setImageResource(R.drawable.msg_download);
                iconColor = ClientTheme.isEnabled()
                        ? ClientTheme.color(ClientTheme.ON_PRIMARY)
                        : getThemedColor(Theme.key_featuredStickers_buttonText);
                iconBackground = accent;
                statusTitle.setText(LocaleController.formatString(R.string.LUS_Available,
                        release != null ? release.version : ""));
                heroPill.setText(LocaleController.formatString(R.string.LUS_HeroAvailable,
                        release != null ? release.version : ""));
                subtitle = releaseDetails();
                break;
            case STATE_FAILED:
                int red = getThemedColor(Theme.key_text_RedRegular);
                statusImage.setImageResource(R.drawable.msg_warning);
                iconColor = red;
                iconBackground = ColorUtils.setAlphaComponent(red, 0x26);
                statusTitle.setText(getString(R.string.LUS_CheckFailed));
                heroPill.setText(getString(R.string.LUS_HeroFailed));
                subtitle = getString(R.string.LUS_FailedInfo);
                break;
        }
        statusImage.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        ((View) statusImage.getParent()).setBackground(Theme.createCircleDrawable(dp(44), iconBackground));
        statusSubtitle.setText(subtitle);
        statusSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        boolean hasRelease = !checking && value == STATE_AVAILABLE && release != null;
        versionTransition.setVisibility(hasRelease ? View.VISIBLE : View.GONE);
        if (hasRelease) {
            versionTransition.setText(LocaleController.formatString(R.string.LUS_VersionArrow,
                    NimarkoUpdater.getCurrentVersionName(), release.version));
        }
        if (!checking) {
            boolean hasNotes = hasRelease && !TextUtils.isEmpty(release.changelog);
            if (hasNotes) {
                notesView.setText(Emoji.replaceEmoji(format(release.changelog),
                        notesView.getPaint().getFontMetricsInt(), false));
                notesScroll.scrollTo(0, 0);
            }
            notesCard.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        } else {
            notesCard.setVisibility(View.GONE);
        }
        updateAction();
    }

    private CharSequence checkedText() {
        long last = NimarkoUpdateConfig.getLastUpdateCheckTime();
        if (last <= 0) {
            return null;
        }
        if (Math.abs(System.currentTimeMillis() - last) < 60_000) {
            return getString(R.string.LUS_CheckedJustNow);
        }
        return LocaleController.formatString(R.string.LUS_CheckedAt,
                LocaleController.formatDateAudio(last / 1000, true));
    }

    private CharSequence releaseDetails() {
        if (release == null) {
            return null;
        }
        StringBuilder details = new StringBuilder();
        if (!TextUtils.isEmpty(release.size)) {
            details.append(release.size);
        }
        String date = formatUploadDate(release.uploadDate);
        if (date != null) {
            if (details.length() > 0) {
                details.append(" · ");
            }
            details.append(date);
        }
        return details.length() > 0 ? details : null;
    }

    private static String formatUploadDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            Matcher m = ISO_DATE.matcher(raw);
            if (m.find()) {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(m.group());
                if (date != null) {
                    return new SimpleDateFormat("d MMMM", LocaleController.getInstance().getCurrentLocale())
                            .format(date);
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private void updateAction() {
        NimarkoUpdater.DownloadUiState downloadState = NimarkoUpdater.getDownloadUiState();
        boolean busy = downloadState.downloading || downloadState.paused;
        progressView.setVisibility(state == STATE_AVAILABLE && busy ? View.VISIBLE : View.GONE);
        actionButton.setEnabled(state != STATE_CHECKING);
        actionButton.setAlpha(state == STATE_CHECKING ? 0.5f : 1f);
        if (state == STATE_AVAILABLE) {
            if (downloadFinished || downloadState.finished) {
                actionButton.setText(getString(R.string.UP_Install), false);
            } else if (downloadState.paused) {
                actionButton.setText(getString(R.string.LUS_Resume), false);
            } else if (downloadState.downloading) {
                actionButton.setText(LocaleController.formatString(R.string.AppUpdateDownloading,
                        downloadState.progress), false);
            } else {
                actionButton.setText(release != null && !TextUtils.isEmpty(release.size)
                        ? LocaleController.formatString(R.string.LUS_DownloadSize, release.size)
                        : getString(R.string.LUS_Download), false);
            }
        } else if (state == STATE_FAILED) {
            actionButton.setText(getString(R.string.TryAgain), false);
        } else {
            actionButton.setText(getString(R.string.LUS_CheckAgain), false);
        }
    }

    private void onAction() {
        if (state == STATE_AVAILABLE) {
            NimarkoUpdater.DownloadUiState downloadState = NimarkoUpdater.getDownloadUiState();
            if (downloadFinished || downloadState.finished) {
                if (NimarkoUpdater.apkFile != null) {
                    NimarkoUpdater.installApk(getContext(), NimarkoUpdater.apkFile.getAbsolutePath());
                }
                return;
            }
            if (downloadState.paused) {
                downloadClicked = true;
                NimarkoUpdater.resumeDownload(getContext().getApplicationContext());
                return;
            }
            if (downloadState.downloading) {
                NimarkoUpdater.cancelDownload(getContext(), NimarkoUpdater.id);
                return;
            }
            if (!downloadClicked && release != null) {
                downloadClicked = true;
                NimarkoUpdater.downloadApk(getContext(), release.downloadURL,
                        "LinkiGram " + release.version, downloadBindingToken);
            }
            return;
        }
        if (state == STATE_LATEST || state == STATE_FAILED) {
            check();
        }
    }

    @Override
    public void onDownloadComplete() {
        if (dismissed || downloadBindingToken == 0L) {
            return;
        }
        downloadFinished = true;
        actionButton.setText(getString(R.string.UP_Install), true);
    }

    @Override
    public void onDownloadError() {
        if (dismissed || downloadBindingToken == 0L) {
            return;
        }
        downloadClicked = false;
        updateAction();
    }

    @Override
    public void dismiss() {
        dismissed = true;
        super.dismiss();
    }

    static CharSequence format(String text) {
        String prepared = (text == null ? "" : text).replace("\r", "").trim()
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*(.+)$", "**$1**")
                .replaceAll("(?m)^\\s{0,3}[*-]\\s+", "• ");
        SpannableStringBuilder builder = new SpannableStringBuilder(prepared);
        Matcher link = LINK.matcher(builder);
        int shift = 0;
        while (link.find()) {
            int start = link.start() - shift;
            int end = link.end() - shift;
            String label = link.group(1);
            String url = link.group(2);
            builder.replace(start, end, label);
            builder.setSpan(new URLSpanNoUnderline(url), start, start + label.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            shift += (end - start) - label.length();
        }
        applyMarker(builder, "`", new android.text.style.TypefaceSpan("monospace"));
        applyMarker(builder, "**", null);
        return builder;
    }

    private static void applyMarker(SpannableStringBuilder builder, String marker, Object span) {
        int from = 0;
        while (true) {
            String current = builder.toString();
            int start = current.indexOf(marker, from);
            if (start < 0) {
                return;
            }
            int end = current.indexOf(marker, start + marker.length());
            if (end < 0) {
                return;
            }
            builder.delete(end, end + marker.length());
            builder.delete(start, start + marker.length());
            int spanEnd = end - marker.length();
            if (spanEnd > start) {
                builder.setSpan(span != null ? span : new StyleSpan(Typeface.BOLD), start, spanEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            from = spanEnd;
            if (span != null) {
                span = new android.text.style.TypefaceSpan("monospace");
            }
        }
    }

    public static void show(BaseFragment fragment) {
        show(fragment, null);
    }

    public static void show(BaseFragment fragment, NimarkoUpdater.Update update) {
        if (fragment == null || fragment.getParentActivity() == null || fragment.getContext() == null) {
            return;
        }
        LinkiUpdateSheet sheet = new LinkiUpdateSheet(fragment, update);
        fragment.showDialog(sheet);
    }
}
