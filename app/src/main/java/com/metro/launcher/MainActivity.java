package com.metro.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class MainActivity extends Activity implements AppListAdapter.Host {
    private static final int DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private static final int REQ_PERMS = 1, REQ_HOME = 2, REQ_BIND = 3, REQ_CONFIG = 4;
    private static final int WIDGET_HOST_ID = 0x4D45;
    private static final int MONO_TILE = 0xFF1F1F1F;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newFixedThreadPool(2);
    private final Random rnd = new Random();
    private final Map<String, Drawable> iconCache = new ConcurrentHashMap<>();
    private final Map<String, Drawable> listMonoCache = new HashMap<>();
    private final Map<String, String> appLabels = new HashMap<>();

    private Prefs prefs;
    private List<Section> sections;
    private List<String> dock = new ArrayList<>();
    private final List<AppInfo> apps = new ArrayList<>();

    // Views
    private FrameLayout root;
    private ImageView bgA, bgB;
    private boolean bgAFront = true;
    private SwipeHost host;
    private ScrollView startScroll;
    private LinearLayout startContent, bottomRow, appsPage, dockBar;
    private StartLayout start;
    private EditText search;
    private ListView appList;
    private AppListAdapter adapter;
    private int insetBottom;

    // Widgets
    private AppWidgetHost widgetHost;
    private AppWidgetManager awm;
    private int pendingWidgetId = -1;
    private Section pendingSection;

    // Live data
    private MediaWatcher media;
    private final List<String> bingUrls = new ArrayList<>();
    private int bingIndex = 0;
    private long lastBingRotate = System.currentTimeMillis();
    private WeatherClient.Weather weather;
    private String weatherPlace = "", weatherStatus = null;
    private long lastWeather = 0;
    private String[] nextEvent;
    private boolean resumed;
    private BroadcastReceiver pkgReceiver;
    private LocationManager activeLm;
    private LocationListener activeLocListener;
    /** True right after a crash: live data stays off until the crash report is dismissed. */
    private boolean safeStart;
    private boolean crashedAtStart;
    private String crashReport;

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CrashLog.install(this);
        super.onCreate(savedInstanceState);
        // If Metro crashed recently, show the bare report screen instead of Start, so a crash
        // in Start can't prevent the report from being seen.
        if (CrashLog.read(this) != null && CrashLog.ageMs(this) < 10 * 60000L) {
            startActivity(new Intent(this, CrashReportActivity.class));
            finish();
            crashedAtStart = true;
            return;
        }
        prefs = Prefs.load(this);
        crashReport = CrashLog.read(this);
        safeStart = crashReport != null && CrashLog.ageMs(this) < 10 * 60000L;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        awm = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, WIDGET_HOST_ID);
        media = new MediaWatcher(this, this::updateLiveTiles);
        NotifService.setOnChange(() -> {
            if (resumed) media.start();
            updateLiveTiles();
        });

        buildUi();
        setContentView(root);

        sections = LayoutStore.load(this);
        dock = LayoutStore.loadDock(this);
        rebuildStart();
        loadApps(() -> {
            if (sections == null) {
                sections = LayoutStore.defaults(this, apps, 1, palette());
                saveLayout();
                rebuildStart();
                refreshWeather(true);
                refreshCalendar();
            } else if (pruneTiles()) {
                saveLayout();
                rebuildStart();
            } else {
                rebuildDock();
            }
            if (!prefs.setupDone) showWelcome();
            else if (crashReport != null) showCrashReport();
        });

        if (!prefs.bingCache.isEmpty()) {
            bingUrls.addAll(Arrays.asList(prefs.bingCache.split("\n")));
            if (prefs.bing) showBing(0);
        }

        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                iconCache.clear();
                listMonoCache.clear();
                loadApps(() -> {
                    if (pruneTiles()) saveLayout();
                    rebuildStart();
                });
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (crashedAtStart) return;
        try {
            widgetHost.startListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (crashedAtStart) return;
        try {
            widgetHost.stopListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (crashedAtStart) return;
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        NotifService.setOnChange(null);
        media.stop();
        stopLocationUpdates();
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
    }

    private void stopLocationUpdates() {
        if (activeLm != null && activeLocListener != null) {
            try {
                activeLm.removeUpdates(activeLocListener);
            } catch (Exception ignored) {
            }
        }
        activeLm = null;
        activeLocListener = null;
    }

    /** Runs work off the main thread. Errors are recorded instead of crashing the launcher. */
    private void runBg(Runnable r) {
        if (bg.isShutdown()) return;
        try {
            bg.execute(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    CrashLog.note(this, t);
                }
            });
        } catch (Exception ignored) {
            // Executor already shut down (activity closing): nothing to do.
        }
    }

    /** Shows the saved crash report so it can be copied and sent for a fix. */
    private void showCrashReport() {
        final String report = crashReport;
        if (report == null) return;
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Metro closed unexpectedly")
                .setMessage("Copy this report and paste it to Claude so it can be fixed. "
                        + "Weather, calendar and background updates are paused until you dismiss this.")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Copy report", (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Metro crash report", report));
                    }
                    toast("Report copied");
                    dismissCrashReport();
                })
                .setNegativeButton("Dismiss", (d, w) -> dismissCrashReport())
                .show();
    }

    private void dismissCrashReport() {
        CrashLog.clear(this);
        crashReport = null;
        if (safeStart) {
            safeStart = false;
            media.start();
            refreshWeather(true);
            refreshCalendar();
            refreshBing(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (crashedAtStart) return;
        resumed = true;
        if (!safeStart) media.start();
        updateLiveTiles();
        ui.removeCallbacks(clockTick);
        ui.removeCallbacks(flipTick);
        ui.removeCallbacks(slowTick);
        ui.post(clockTick);
        ui.postDelayed(flipTick, 1500);
        ui.postDelayed(slowTick, 15 * 60000L);
        refreshWeather(false);
        refreshCalendar();
        refreshBing(false);
        if (prefs.bing && prefs.bingMins > 0
                && System.currentTimeMillis() - lastBingRotate > prefs.bingMins * 60000L) {
            rotateBing();
        }
        scheduleBing();
        if (host.getPage() == 0) animateTilesIn();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (crashedAtStart) return;
        resumed = false;
        media.stop();
        ui.removeCallbacks(clockTick);
        ui.removeCallbacks(flipTick);
        ui.removeCallbacks(slowTick);
        ui.removeCallbacks(bingTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (crashedAtStart) return;
        // Home pressed while already showing: back to the top of Start.
        hideKeyboard();
        if (search != null) search.setText("");
        if (host.getPage() == 1) host.goTo(0, true);
        else if (startScroll != null) startScroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (crashedAtStart) return;
        if (host.getPage() == 1) {
            hideKeyboard();
            host.goTo(0, true);
        }
        // On Start, Back does nothing: this is the home screen.
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERMS) {
            try {
                refreshWeather(true);
                refreshCalendar();
            } catch (Exception e) {
                CrashLog.note(this, e);
            }
            if (!prefs.askedRole) {
                prefs.askedRole = true;
                prefs.save(this);
                requestHomeRole(true);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BIND) {
            if (resultCode == RESULT_OK) configureOrAddWidget(pendingWidgetId);
            else discardPendingWidget();
        } else if (requestCode == REQ_CONFIG) {
            if (resultCode == RESULT_OK) addWidgetTile(pendingWidgetId);
            else discardPendingWidget();
        }
    }

    // ================================================================ UI construction

    private int dp(float v) {
        return Util.dp(this, v);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        bgA = makeBackground();
        bgB = makeBackground();
        bgB.setAlpha(0f);
        root.addView(bgA, match());
        root.addView(bgB, match());
        View dim = new View(this);
        dim.setBackgroundColor(0x30000000);
        root.addView(dim, match());

        host = new SwipeHost(this);
        root.addView(host, match());

        // ---- Start page: scrolling sections + bottom buttons, with an optional sticky dock.
        FrameLayout startPage = new FrameLayout(this);
        startScroll = new ScrollView(this);
        startScroll.setVerticalScrollBarEnabled(false);
        startScroll.setClipToPadding(false);
        startContent = new LinearLayout(this);
        startContent.setOrientation(LinearLayout.VERTICAL);
        start = new StartLayout(this);
        start.setPadding(dp(12), dp(36), dp(12), dp(8));
        startContent.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(dp(12), dp(8), dp(12), dp(24));
        TextView more = circleButton("•••", 14);
        more.setContentDescription("Settings");
        more.setOnClickListener(v -> showSettings(false));
        bottomRow.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView add = circleButton("+", 24);
        add.setContentDescription("Add to Start");
        add.setOnClickListener(v -> showAddMenu(lastSection()));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
        alp.leftMargin = dp(12);
        bottomRow.addView(add, alp);
        bottomRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView arrow = circleButton("→", 22);
        arrow.setContentDescription("All apps");
        arrow.setOnClickListener(v -> host.goTo(1, true));
        bottomRow.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        startContent.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        startScroll.addView(startContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        startPage.addView(startScroll, match());

        dockBar = new LinearLayout(this);
        dockBar.setOrientation(LinearLayout.HORIZONTAL);
        dockBar.setGravity(Gravity.CENTER);
        dockBar.setBackgroundColor(0x99000000);
        startPage.addView(dockBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        host.addPage(startPage);

        // ---- App list page
        appsPage = new LinearLayout(this);
        appsPage.setOrientation(LinearLayout.VERTICAL);
        appsPage.setBackgroundColor(0xD9000000);
        search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextSize(18);
        search.setTextColor(Color.BLACK);
        search.setHintTextColor(0xFF6E6E6E);
        search.setBackgroundColor(Color.WHITE);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
                appList.setSelection(0);
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            AppInfo first = adapter.firstApp();
            if (adapter.isSearching() && first != null) {
                launchApp(first.pkg, first.cls);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(16), dp(8), dp(16), dp(12));
        appsPage.addView(search, slp);

        adapter = new AppListAdapter(this, this);
        appList = new ListView(this);
        appList.setDivider(null);
        appList.setVerticalScrollBarEnabled(false);
        appList.setClipToPadding(false);
        appList.setAdapter(adapter);
        appList.setOnItemClickListener((parent, view, position, id) -> {
            Object o = adapter.getItem(position);
            if (o instanceof String) showLetterJump();
            else {
                AppInfo a = (AppInfo) o;
                launchApp(a.pkg, a.cls);
            }
        });
        appList.setOnItemLongClickListener((parent, view, position, id) -> {
            Object o = adapter.getItem(position);
            if (o instanceof AppInfo) {
                showAppMenu((AppInfo) o);
                return true;
            }
            return false;
        });
        appsPage.addView(appList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        host.addPage(appsPage);

        host.setListener(new SwipeHost.Listener() {
            @Override
            public void onOffset(float offset) {
                float tx = -offset * root.getWidth() * 0.05f;
                bgA.setTranslationX(tx);
                bgB.setTranslationX(tx);
            }

            @Override
            public void onPage(int page) {
                if (page == 0) {
                    hideKeyboard();
                    if (search.length() > 0) search.setText("");
                }
            }
        });

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            start.setPadding(dp(12), top + dp(28), dp(12), dp(8));
            appsPage.setPadding(0, top + dp(4), 0, insetBottom);
            applyBottomPadding();
            return insets;
        });
    }

    private void applyBottomPadding() {
        boolean dockOn = dockBar.getVisibility() == View.VISIBLE;
        dockBar.setPadding(dp(8), dp(8), dp(8), insetBottom + dp(8));
        bottomRow.setPadding(dp(12), dp(8), dp(12), dockOn ? dp(16) : insetBottom + dp(20));
        startContent.setPadding(0, 0, 0, dockOn ? insetBottom + dp(80) : 0);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private ImageView makeBackground() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Slightly oversized so it can drift (parallax) while swiping to the app list.
        iv.setScaleX(1.12f);
        iv.setScaleY(1.12f);
        return iv;
    }

    private TextView circleButton(String text, int sizeSp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x33000000);
        ring.setStroke(dp(2), Color.WHITE);
        t.setBackground(ring);
        return t;
    }

    // ================================================================ apps & icons

    private void loadApps(Runnable after) {
        runBg(() -> {
            List<AppInfo> list = AppInfo.loadAll(this);
            ui.post(() -> {
                apps.clear();
                apps.addAll(list);
                appLabels.clear();
                for (AppInfo a : list) if (!appLabels.containsKey(a.pkg)) appLabels.put(a.pkg, a.label);
                adapter.setApps(apps);
                if (after != null) after.run();
            });
            // Warm the icon cache so the app list scrolls smoothly.
            for (AppInfo a : list) {
                if (!iconCache.containsKey(a.key())) {
                    try {
                        iconCache.put(a.key(), a.ai.loadIcon(getPackageManager()));
                    } catch (Exception ignored) {
                    }
                }
            }
            ui.post(() -> adapter.notifyDataSetChanged());
        });
    }

    private Drawable originalIcon(String pkg, String cls, AppInfo a) {
        String key = pkg + "/" + cls;
        Drawable d = iconCache.get(key);
        if (d != null) return d;
        PackageManager pm = getPackageManager();
        try {
            d = a != null && a.ai != null ? a.ai.loadIcon(pm) : pm.getActivityIcon(new ComponentName(pkg, cls));
        } catch (Exception e) {
            try {
                d = pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {
            }
        }
        if (d != null) iconCache.put(key, d);
        return d;
    }

    /** A drawable owned by exactly one view (tiles and the dock need their own copies). */
    private Drawable ownedIcon(String pkg, String cls) {
        Drawable d = originalIcon(pkg, cls, null);
        if (d == null) return null;
        return prefs.effectiveMonoIcons() ? Icons.mono(d, getResources()) : Icons.copy(d, getResources());
    }

    @Override
    public Drawable listIconFor(AppInfo a) {
        Drawable d = originalIcon(a.pkg, a.cls, a);
        if (d == null || !prefs.effectiveMonoIcons()) return d;
        Drawable m = listMonoCache.get(a.key());
        if (m == null) {
            m = Icons.mono(d, getResources());
            listMonoCache.put(a.key(), m);
        }
        return m;
    }

    @Override
    public int accent() {
        return prefs.accent;
    }

    @Override
    public boolean compactDrawer() {
        return prefs.compactDrawer;
    }

    @Override
    public boolean monoIcons() {
        return prefs.effectiveMonoIcons();
    }

    private String appLabel(String pkg) {
        String l = appLabels.get(pkg);
        if (l != null) return l;
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // ================================================================ layout model

    private int[] palette() {
        return Util.PALETTES[prefs.palette];
    }

    private int randomColor() {
        int[] p = palette();
        return p[rnd.nextInt(p.length)];
    }

    private int tileColor(TileSpec s) {
        if (prefs.colorMode == Prefs.COLOR_ACCENT) return prefs.accent;
        if (prefs.colorMode == Prefs.COLOR_MONO) return MONO_TILE;
        return s.color;
    }

    private Section lastSection() {
        if (sections == null) sections = new ArrayList<>();
        if (sections.isEmpty()) sections.add(new Section(""));
        return sections.get(sections.size() - 1);
    }

    private Section sectionOf(TileSpec t) {
        if (sections == null) return null;
        for (Section s : sections) if (s.tiles.contains(t)) return s;
        return null;
    }

    private void saveLayout() {
        if (sections != null) LayoutStore.save(this, sections, dock);
    }

    private void saveAndRebuild() {
        saveLayout();
        rebuildStart();
    }

    /** Drops tiles for uninstalled apps; repairs tiles whose app changed its launch activity. */
    private boolean pruneTiles() {
        if (sections == null || apps.isEmpty()) return false;
        Map<String, AppInfo> byKey = new HashMap<>();
        Map<String, AppInfo> byPkg = new HashMap<>();
        for (AppInfo a : apps) {
            byKey.put(a.key(), a);
            if (!byPkg.containsKey(a.pkg)) byPkg.put(a.pkg, a);
        }
        boolean changed = false;
        for (Section s : sections) {
            for (int i = s.tiles.size() - 1; i >= 0; i--) {
                TileSpec t = s.tiles.get(i);
                if (t.type != TileSpec.APP || byKey.containsKey(t.pkg + "/" + t.cls)) continue;
                AppInfo a = byPkg.get(t.pkg);
                if (a == null) {
                    s.tiles.remove(i);
                } else {
                    t.cls = a.cls;
                    t.label = a.label;
                }
                changed = true;
            }
        }
        for (int i = dock.size() - 1; i >= 0; i--) {
            String k = dock.get(i);
            if (!byKey.containsKey(k)) {
                AppInfo a = byPkg.get(k.substring(0, Math.max(0, k.indexOf('/'))));
                if (a == null) dock.remove(i);
                else dock.set(i, a.key());
                changed = true;
            }
        }
        return changed;
    }

    // ================================================================ Start rendering

    private void rebuildStart() {
        if (start == null) return;
        start.removeAllViews();
        start.setScale(prefs.scale);
        if (sections != null) {
            for (Section sec : sections) {
                SectionView sv = new SectionView(this, sec);
                sv.header.setOnLongClickListener(v -> {
                    showSectionMenu(sec);
                    return true;
                });
                for (TileSpec s : sec.tiles) sv.addTile(makeTileView(s));
                start.addView(sv);
            }
        }
        rebuildDock();
        updateLiveTiles();
    }

    private View makeTileView(TileSpec s) {
        if (s.type == TileSpec.WIDGET) {
            AppWidgetProviderInfo info = null;
            try {
                info = awm.getAppWidgetInfo(s.widgetId);
            } catch (Exception ignored) {
            }
            if (info != null) {
                AppWidgetHostView hv = widgetHost.createView(this, s.widgetId, info);
                WidgetTileView wt = new WidgetTileView(this, s, hv);
                wt.setStyle(tileColor(s), prefs.tileAlpha / 3, prefs.shape);
                wt.setOnLongClickListener(v -> {
                    showTileMenu(s);
                    return true;
                });
                return wt;
            }
        }
        TileView tv = new TileView(this, s);
        if (s.type == TileSpec.APP) tv.setIcon(ownedIcon(s.pkg, s.cls));
        if (s.type == TileSpec.WIDGET) {
            tv.setFaces(null, "Widget unavailable", "Press and hold to remove", null, null, null, false);
        }
        tv.setStyle(tileColor(s), prefs.tileAlpha, prefs.shape);
        tv.setOnClickListener(v -> onTileClick(s));
        tv.setOnLongClickListener(v -> {
            showTileMenu(s);
            return true;
        });
        return tv;
    }

    private void forEachTileView(Consumer<TileView> fn) {
        if (start == null) return;
        for (int i = 0; i < start.getChildCount(); i++) {
            View sv = start.getChildAt(i);
            if (!(sv instanceof SectionView)) continue;
            TileGridView g = ((SectionView) sv).grid;
            for (int j = 0; j < g.getChildCount(); j++) {
                View v = g.getChildAt(j);
                if (v instanceof TileView) fn.accept((TileView) v);
            }
        }
    }

    private List<View> allTileViews() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < start.getChildCount(); i++) {
            View sv = start.getChildAt(i);
            if (!(sv instanceof SectionView)) continue;
            TileGridView g = ((SectionView) sv).grid;
            for (int j = 0; j < g.getChildCount(); j++) out.add(g.getChildAt(j));
        }
        return out;
    }

    private void rebuildDock() {
        dockBar.removeAllViews();
        boolean show = prefs.dock && !dock.isEmpty();
        dockBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            for (String key : dock) {
                int slash = key.indexOf('/');
                if (slash <= 0) continue;
                final String pkg = key.substring(0, slash), cls = key.substring(slash + 1);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(ownedIcon(pkg, cls));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setContentDescription(appLabel(pkg));
                int p = dp(10);
                iv.setPadding(p, p, p, p);
                iv.setOnClickListener(v -> launchApp(pkg, cls));
                iv.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this, DIALOG_THEME)
                            .setTitle(appLabel(pkg))
                            .setItems(new String[]{"Remove from dock", "App info"}, (d, w) -> {
                                if (w == 0) {
                                    dock.remove(key);
                                    saveLayout();
                                    rebuildDock();
                                } else openAppInfo(pkg);
                            }).show();
                    return true;
                });
                dockBar.addView(iv, new LinearLayout.LayoutParams(dp(68), dp(60)));
            }
        }
        applyBottomPadding();
    }

    private void animateTilesIn() {
        List<View> views = allTileViews();
        for (int i = 0; i < views.size(); i++) {
            final View v = views.get(i);
            v.setPivotX(0);
            v.setPivotY(v.getHeight() / 2f);
            v.setRotationY(-65f);
            v.setAlpha(0f);
            v.animate().rotationY(0f).alpha(1f)
                    .setStartDelay(Math.min(i, 14) * 22L)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        v.setPivotX(v.getWidth() / 2f);
                        v.animate().setStartDelay(0);
                    })
                    .start();
        }
    }

    // ================================================================ tile actions

    private void onTileClick(TileSpec s) {
        switch (s.type) {
            case TileSpec.APP:
                launchApp(s.pkg, s.cls);
                break;
            case TileSpec.CLOCK:
                if (!startSafe(new Intent(AlarmClock.ACTION_SHOW_ALARMS))) toast("No clock app found");
                break;
            case TileSpec.WEATHER:
                toast("Updating weather…");
                refreshWeather(true);
                break;
            case TileSpec.CALENDAR:
                Intent cal = new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()));
                if (!startSafe(cal)
                        && !startSafe(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))) {
                    toast("No calendar app found");
                }
                break;
            case TileSpec.MEDIA:
                if (!NotifService.isEnabled(this)) showNotifAccessDialog();
                else if (media.hasSession()) media.togglePlay();
                else if (!startSafe(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))) {
                    toast("Nothing playing");
                }
                break;
            case TileSpec.NOTIFS:
                if (!NotifService.isEnabled(this)) showNotifAccessDialog();
                else showNotifFolder();
                break;
            default:
                break;
        }
    }

    private void launchApp(String pkg, String cls) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!startSafe(i)) {
            Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
            if (li == null || !startSafe(li)) toast("Couldn't open that app");
        }
    }

    private boolean startSafe(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void openAppInfo(String pkg) {
        startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private String sectionTitle(Section s, int index) {
        return s.name == null || s.name.trim().isEmpty() ? "Section " + (index + 1) + " (unnamed)" : s.name;
    }

    private void showTileMenu(TileSpec s) {
        final Section sec = sectionOf(s);
        if (sec == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        boolean isWidget = s.type == TileSpec.WIDGET;
        int[] sizes = {TileSpec.SMALL, TileSpec.MEDIUM, TileSpec.WIDE, TileSpec.LARGE};
        String[] sizeNames = {"Small", "Medium", "Wide", "Large"};
        for (int i = 0; i < sizes.length; i++) {
            final int sz = sizes[i];
            if (sz == s.size || (isWidget && sz == TileSpec.SMALL)) continue;
            labels.add("Resize: " + sizeNames[i]);
            actions.add(() -> { s.size = sz; saveAndRebuild(); });
        }
        if (prefs.colorMode == Prefs.COLOR_COLORFUL && !isWidget) {
            labels.add("Change color");
            actions.add(() -> pickColor("Tile color", palette(), c -> { s.color = c; saveAndRebuild(); }));
        }
        final int idx = sec.tiles.indexOf(s);
        if (idx > 0) {
            labels.add("Move earlier");
            actions.add(() -> { sec.tiles.remove(idx); sec.tiles.add(idx - 1, s); saveAndRebuild(); });
            labels.add("Move to top of section");
            actions.add(() -> { sec.tiles.remove(idx); sec.tiles.add(0, s); saveAndRebuild(); });
        }
        if (idx < sec.tiles.size() - 1) {
            labels.add("Move later");
            actions.add(() -> { sec.tiles.remove(idx); sec.tiles.add(idx + 1, s); saveAndRebuild(); });
        }
        if (sections.size() > 1) {
            labels.add("Move to another section");
            actions.add(() -> pickSection("Move to", sec, target -> {
                sec.tiles.remove(s);
                target.tiles.add(s);
                saveAndRebuild();
            }));
        }
        if (s.type == TileSpec.APP) {
            labels.add("App info");
            actions.add(() -> openAppInfo(s.pkg));
        }
        if (s.type == TileSpec.MEDIA && media.packageName() != null) {
            labels.add("Open " + appLabel(media.packageName()));
            actions.add(() -> {
                Intent li = getPackageManager().getLaunchIntentForPackage(media.packageName());
                if (li != null) startSafe(li);
            });
            labels.add("Next track");
            actions.add(media::next);
        }
        if (s.type == TileSpec.MEDIA || s.type == TileSpec.NOTIFS) {
            labels.add("Notification access settings");
            actions.add(this::showNotifAccessDialog);
        }
        labels.add("Unpin from Start");
        actions.add(() -> {
            sec.tiles.remove(s);
            if (isWidget && s.widgetId >= 0) widgetHost.deleteAppWidgetId(s.widgetId);
            saveAndRebuild();
        });

        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(s.label == null || s.label.isEmpty() ? "Tile" : s.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void pickSection(String title, Section exclude, Consumer<Section> cb) {
        List<Section> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            if (s == exclude) continue;
            choices.add(s);
            names.add(sectionTitle(s, i));
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setItems(names.toArray(new String[0]), (d, w) -> cb.accept(choices.get(w)))
                .show();
    }

    private void showSectionMenu(Section sec) {
        final int idx = sections.indexOf(sec);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(sec.name.trim().isEmpty() ? "Name this section" : "Rename section");
        actions.add(() -> askText(sec.name.trim().isEmpty() ? "Name this section" : "Rename section",
                sec.name, "Leave blank for no name", t -> { sec.name = t; saveAndRebuild(); }));
        labels.add("Add to this section…");
        actions.add(() -> showAddMenu(sec));
        labels.add("New section below");
        actions.add(() -> newSection(idx + 1));
        if (idx > 0) {
            labels.add("Move section up");
            actions.add(() -> { sections.remove(idx); sections.add(idx - 1, sec); saveAndRebuild(); });
        }
        if (idx < sections.size() - 1) {
            labels.add("Move section down");
            actions.add(() -> { sections.remove(idx); sections.add(idx + 1, sec); saveAndRebuild(); });
        }
        if (sections.size() > 1) {
            labels.add("Delete section (keeps its tiles)");
            actions.add(() -> {
                Section into = sections.get(idx > 0 ? idx - 1 : 1);
                into.tiles.addAll(sec.tiles);
                sections.remove(sec);
                saveAndRebuild();
            });
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(sectionTitle(sec, idx))
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void newSection(int at) {
        askText("New section", "", "Name (optional)", name -> {
            Section s = new Section(name);
            if (sections == null) sections = new ArrayList<>();
            sections.add(Math.max(0, Math.min(at, sections.size())), s);
            saveAndRebuild();
            toast("Section added. Press and hold its header to add tiles.");
        });
    }

    private void askText(String title, String initial, String hint, Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setText(initial);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(et);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> cb.accept(et.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddMenu(Section target) {
        String[] items = {"App tile", "Android widget", "Live tile", "New section"};
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Add to Start")
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        host.goTo(1, true);
                        toast("Press and hold an app, then choose Pin to Start");
                    } else if (w == 1) {
                        showWidgetPicker(target);
                    } else if (w == 2) {
                        showAddLiveTile(target);
                    } else {
                        newSection(sections == null ? 0 : sections.size());
                    }
                })
                .show();
    }

    private void showAddLiveTile(Section target) {
        final int[] types = {TileSpec.CLOCK, TileSpec.WEATHER, TileSpec.CALENDAR, TileSpec.MEDIA, TileSpec.NOTIFS};
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = TileSpec.liveLabel(types[i]);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Add a live tile")
                .setItems(names, (d, which) -> {
                    int type = types[which];
                    int size = type == TileSpec.CLOCK || type == TileSpec.MEDIA ? TileSpec.WIDE : TileSpec.MEDIUM;
                    target.tiles.add(TileSpec.live(type, names[which], size, randomColor()));
                    saveAndRebuild();
                    if (type == TileSpec.WEATHER) refreshWeather(true);
                    if (type == TileSpec.CALENDAR) refreshCalendar();
                    if ((type == TileSpec.MEDIA || type == TileSpec.NOTIFS) && !NotifService.isEnabled(this)) {
                        showNotifAccessDialog();
                    } else {
                        toast(names[which] + " tile added");
                    }
                })
                .show();
    }

    private void showAppMenu(AppInfo a) {
        final String key = a.key();
        final boolean inDock = dock.contains(key);
        String[] items = {"Pin to Start", inDock ? "Remove from dock" : "Add to dock", "App info", "Uninstall"};
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(a.label)
                .setItems(items, (d, which) -> {
                    if (which == 0) pinApp(a);
                    else if (which == 1) {
                        if (inDock) dock.remove(key);
                        else if (dock.size() >= 6) {
                            toast("The dock holds up to 6 apps");
                            return;
                        } else {
                            dock.add(key);
                            if (!prefs.dock) {
                                prefs.dock = true;
                                prefs.save(this);
                            }
                        }
                        saveLayout();
                        rebuildDock();
                        toast(inDock ? "Removed from dock" : "Added to dock");
                    } else if (which == 2) openAppInfo(a.pkg);
                    else startSafe(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                })
                .show();
    }

    private void pinApp(AppInfo a) {
        Section target = lastSection();
        for (Section s : sections) {
            for (TileSpec t : s.tiles) {
                if (t.type == TileSpec.APP && a.pkg.equals(t.pkg) && a.cls.equals(t.cls)) {
                    toast("Already on Start");
                    return;
                }
            }
        }
        target.tiles.add(TileSpec.app(a.label, a.pkg, a.cls, TileSpec.MEDIUM, randomColor()));
        saveAndRebuild();
        hideKeyboard();
        host.goTo(0, true);
        ui.postDelayed(() -> startScroll.smoothScrollTo(0, startContent.getHeight()), 350);
    }

    // ================================================================ widgets

    private void showWidgetPicker(Section target) {
        final PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> providers;
        try {
            providers = new ArrayList<>(awm.getInstalledProviders());
        } catch (Exception e) {
            toast("Couldn't list widgets");
            return;
        }
        if (providers.isEmpty()) {
            toast("No widgets installed");
            return;
        }
        final Map<AppWidgetProviderInfo, String> names = new HashMap<>();
        for (AppWidgetProviderInfo p : providers) {
            String app = appLabel(p.provider.getPackageName());
            String w = p.loadLabel(pm);
            names.put(p, w == null || w.equals(app) ? app : app + " — " + w);
        }
        final Collator col = Collator.getInstance();
        Collections.sort(providers, (a, b) -> col.compare(names.get(a), names.get(b)));
        final List<AppWidgetProviderInfo> list = providers;
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = names.get(list.get(i));
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Add an Android widget")
                .setItems(labels, (d, w) -> startAddWidget(list.get(w), target))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startAddWidget(AppWidgetProviderInfo info, Section target) {
        pendingSection = target;
        pendingWidgetId = widgetHost.allocateAppWidgetId();
        boolean ok;
        try {
            ok = awm.bindAppWidgetIdIfAllowed(pendingWidgetId, info.getProfile(), info.provider, null);
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            configureOrAddWidget(pendingWidgetId);
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                discardPendingWidget();
                toast("This phone didn't allow adding that widget");
            }
        }
    }

    private void configureOrAddWidget(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            discardPendingWidget();
            return;
        }
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null);
                return;
            } catch (Exception ignored) {
                // Fall through and add it without configuring.
            }
        }
        addWidgetTile(id);
    }

    private void addWidgetTile(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            discardPendingWidget();
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        float wDp = info.minWidth / d, hDp = info.minHeight / d;
        int size = hDp > 200 ? TileSpec.LARGE : (wDp <= 180 && hDp <= 180) ? TileSpec.MEDIUM : TileSpec.WIDE;
        String label = appLabel(info.provider.getPackageName());
        Section target = pendingSection != null && sections.contains(pendingSection) ? pendingSection : lastSection();
        target.tiles.add(TileSpec.widget(id, label, size));
        pendingWidgetId = -1;
        pendingSection = null;
        saveAndRebuild();
        toast("Widget added. Press and hold it to resize or move.");
    }

    private void discardPendingWidget() {
        if (pendingWidgetId >= 0) {
            try {
                widgetHost.deleteAppWidgetId(pendingWidgetId);
            } catch (Exception ignored) {
            }
        }
        pendingWidgetId = -1;
        pendingSection = null;
    }

    // ================================================================ notifications folder

    private void showNotifAccessDialog() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Notification access")
                .setMessage("The Music and Notifications tiles need Notification access for Metro Launcher.\n\n"
                        + "If the switch is greyed out (Android 13 and later, for apps installed from a file): "
                        + "tap App info below, open the ⋮ menu at the top right, choose "
                        + "\"Allow restricted settings\", then come back and try again.")
                .setPositiveButton("Open settings", (d, w) ->
                        startSafe(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNeutralButton("App info", (d, w) -> openAppInfo(getPackageName()))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void showNotifFolder() {
        final List<StatusBarNotification> items = NotifService.items();
        if (items.isEmpty()) {
            toast("No notifications");
            return;
        }
        int n = Math.min(items.size(), 60);
        String[] rows = new String[n];
        for (int i = 0; i < n; i++) {
            StatusBarNotification sbn = items.get(i);
            String title = NotifService.title(sbn), text = NotifService.text(sbn);
            StringBuilder sb = new StringBuilder(appLabel(sbn.getPackageName()));
            if (!title.isEmpty()) sb.append(": ").append(title);
            if (!text.isEmpty()) sb.append("\n").append(text.length() > 140 ? text.substring(0, 140) + "…" : text);
            rows[i] = sb.toString();
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(items.size() + (items.size() == 1 ? " notification" : " notifications"))
                .setItems(rows, (d, w) -> {
                    StatusBarNotification sbn = items.get(w);
                    PendingIntent pi = sbn.getNotification().contentIntent;
                    if (pi != null) sendPending(pi);
                    else {
                        Intent li = getPackageManager().getLaunchIntentForPackage(sbn.getPackageName());
                        if (li != null) startSafe(li);
                    }
                    if ((sbn.getNotification().flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0) {
                        NotifService.dismiss(sbn);
                    }
                })
                .setPositiveButton("Clear all", (d, w) -> NotifService.clearAll())
                .setNegativeButton("Close", null)
                .show();
    }

    private void sendPending(PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions o = ActivityOptions.makeBasic();
                o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(this, 0, null, null, null, null, o.toBundle());
            } else {
                pi.send();
            }
        } catch (Exception e) {
            toast("Couldn't open that notification");
        }
    }

    // ================================================================ letter jump & colors

    /** Windows Phone style letter grid: tap a letter header to jump. */
    private void showLetterJump() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(0xEE000000));
        GridLayout g = new GridLayout(this);
        g.setColumnCount(4);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int m = dp(4);
        int cell = Math.min(dp(96), (screenW - dp(32) - m * 8) / 4);
        g.setPadding(dp(16), dp(48), dp(16), dp(48));
        Set<String> active = adapter.activeLetters();
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        for (int i = 0; i < AppListAdapter.LETTERS.length(); i++) {
            final String L = String.valueOf(AppListAdapter.LETTERS.charAt(i));
            boolean on = active.contains(L);
            TextView t = new TextView(this);
            t.setText(L.toLowerCase(Locale.getDefault()));
            t.setTypeface(light);
            t.setTextSize(30);
            t.setGravity(Gravity.START | Gravity.BOTTOM);
            t.setPadding(dp(8), 0, 0, dp(4));
            t.setTextColor(on ? Color.WHITE : 0xFF555555);
            t.setBackgroundColor(on ? prefs.accent : 0xFF1E1E1E);
            if (on) {
                t.setOnClickListener(v -> {
                    d.dismiss();
                    int p = adapter.positionOf(L);
                    if (p >= 0) appList.setSelection(p);
                });
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            lp.setMargins(m, m, m, m);
            g.addView(t, lp);
        }
        FrameLayout center = new FrameLayout(this);
        center.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        ScrollView sv = new ScrollView(this);
        sv.addView(center);
        center.setOnClickListener(v -> d.dismiss());
        d.setContentView(sv);
        d.show();
    }

    private void pickColor(String title, int[] colors, IntConsumer cb) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(5);
        g.setPadding(dp(16), dp(8), dp(16), dp(8));
        final AlertDialog[] holder = new AlertDialog[1];
        int cell = dp(46);
        int m = dp(4);
        for (final int c : colors) {
            View v = new View(this);
            v.setBackgroundColor(c);
            v.setContentDescription(Util.colorName(c));
            v.setOnClickListener(x -> {
                cb.accept(c);
                if (holder[0] != null) holder[0].dismiss();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            lp.setMargins(m, m, m, m);
            g.addView(v, lp);
        }
        holder[0] = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setView(g)
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================================================ setup & settings

    private void showWelcome() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Welcome to Metro")
                .setMessage("Start with a ready-made Start screen, or make it yours: tile size, shape, "
                        + "colors, sections and dock.\n\nYou can change everything later with the ••• button.")
                .setCancelable(false)
                .setPositiveButton("Use defaults", (d, w) -> finishSetup())
                .setNegativeButton("Customize", (d, w) -> showSettings(true))
                .show();
    }

    private void finishSetup() {
        prefs.setupDone = true;
        prefs.save(this);
        if (prefs.dock && dock.isEmpty()) dock.addAll(LayoutStore.defaultDock(this));
        saveAndRebuild();
        animateTilesIn();
        if (!prefs.askedPerms) {
            prefs.askedPerms = true;
            prefs.save(this);
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CALENDAR}, REQ_PERMS);
        }
    }

    private TextView sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(prefs.accent);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setAlpha(0.75f);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private RadioGroup radios(String[] names, int idBase, int checked) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(idBase + i);
            g.addView(rb);
        }
        g.check(idBase + checked);
        return g;
    }

    private Switch toggle(String text, boolean on) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setChecked(on);
        s.setPadding(0, dp(6), 0, dp(6));
        return s;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void showSettings(final boolean setup) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(8));

        RadioGroup layoutStyle = null;
        if (setup) {
            box.addView(sectionHeader("START LAYOUT"));
            layoutStyle = radios(LayoutStore.STYLE_NAMES, 400, 1);
            box.addView(layoutStyle);
            box.addView(note("Sections can be named or left blank. On wide screens (Fold, tablets) they sit side by side."));
        }

        box.addView(sectionHeader("TILE SIZE"));
        final RadioGroup scale = radios(Prefs.SCALE_NAMES, 100, prefs.scale);
        box.addView(scale);

        box.addView(sectionHeader("TILE SHAPE"));
        final RadioGroup shape = radios(Prefs.SHAPE_NAMES, 200, prefs.shape);
        box.addView(shape);

        box.addView(sectionHeader("COLORS"));
        final RadioGroup colorMode = radios(Prefs.COLOR_MODE_NAMES, 300, prefs.colorMode);
        box.addView(colorMode);
        final int[] paletteSel = {prefs.palette};
        final Button paletteBtn = button("Palette: " + Util.PALETTE_SET_NAMES[paletteSel[0]], null);
        paletteBtn.setOnClickListener(v -> new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Color palette")
                .setItems(Util.PALETTE_SET_NAMES, (d, w) -> {
                    paletteSel[0] = w;
                    paletteBtn.setText("Palette: " + Util.PALETTE_SET_NAMES[w]);
                }).show());
        box.addView(paletteBtn);
        final int[] accent = {prefs.accent};
        final Button accentBtn = button("Accent color: " + Util.colorName(accent[0]), null);
        accentBtn.setOnClickListener(v -> pickColor("Accent color", Util.PALETTES[paletteSel[0]], c -> {
            accent[0] = c;
            accentBtn.setText("Accent color: " + Util.colorName(c));
        }));
        box.addView(accentBtn);
        final Switch mono = toggle("Monochrome icons (white glyphs)", prefs.monoIcons);
        box.addView(mono);
        TextView opLabel = note("Tile opacity (lower lets more background show through)");
        box.addView(opLabel);
        final SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(Math.round((prefs.tileAlpha - 90) * 100f / 165f));
        box.addView(opacity);

        box.addView(sectionHeader("DOCK & APP LIST"));
        final Switch dockSw = toggle("Sticky dock at the bottom of Start", prefs.dock);
        box.addView(dockSw);
        final Switch compact = toggle("Compact app list", prefs.compactDrawer);
        box.addView(compact);

        box.addView(sectionHeader("BACKGROUND"));
        final Switch bingSw = toggle("Bing image of the day", prefs.bing);
        box.addView(bingSw);
        final RadioGroup rot = new RadioGroup(this);
        String[] rotNames = {"Rotate every 15 minutes", "Rotate every hour", "Rotate every 4 hours", "Today's image only"};
        int[] rotIds = {15, 60, 240, 1};
        for (int i = 0; i < rotNames.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(rotNames[i]);
            rb.setId(rotIds[i]);
            rot.addView(rb);
        }
        rot.check(prefs.bingMins == 0 ? 1 : prefs.bingMins);
        box.addView(rot);
        if (!setup) {
            box.addView(button("Next background now", v -> {
                if (!prefs.bing) toast("Turn on the Bing background first");
                else if (bingUrls.size() < 2) refreshBing(true);
                else rotateBing();
            }));
        }

        box.addView(sectionHeader("WEATHER"));
        final Switch celsius = toggle("Show °C instead of °F", prefs.celsius);
        box.addView(celsius);
        final EditText city = new EditText(this);
        city.setHint("City (leave blank to use your location)");
        city.setText(prefs.city);
        city.setSingleLine(true);
        city.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(city);

        if (!setup) {
            box.addView(sectionHeader("MORE"));
            box.addView(button("Add to Start (widget, live tile, section)", v -> showAddMenu(lastSection())));
            box.addView(button("Notification access (Music & Notifications tiles)", v -> showNotifAccessDialog()));
            box.addView(button("Make Metro my home screen", v -> requestHomeRole(false)));
            box.addView(button("Reset Start layout…", v -> confirmReset()));
            box.addView(note("Tips: press and hold a tile to resize, recolor, move or unpin it. "
                    + "Press and hold a section name to rename, move or add to it. "
                    + "Swipe left (or tap →) for all apps; press and hold an app to pin it or add it to the dock."));
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        final RadioGroup styleGroup = layoutStyle;
        AlertDialog.Builder b = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(setup ? "Customize Metro" : "Metro settings")
                .setView(sv)
                .setCancelable(!setup)
                .setPositiveButton(setup ? "Done" : "Save", (d, w) -> {
                    int rid = rot.getCheckedRadioButtonId();
                    int mins = rid == 1 ? 0 : (rid > 0 ? rid : 60);
                    boolean bingToggled = prefs.bing != bingSw.isChecked();
                    boolean rotChanged = mins != prefs.bingMins;
                    String newCity = city.getText().toString().trim();
                    boolean cityChanged = !newCity.equals(prefs.city);
                    boolean unitsChanged = celsius.isChecked() != prefs.celsius;
                    boolean paletteChanged = paletteSel[0] != prefs.palette;

                    prefs.scale = Math.max(0, scale.getCheckedRadioButtonId() - 100);
                    prefs.shape = Math.max(0, shape.getCheckedRadioButtonId() - 200);
                    prefs.colorMode = Math.max(0, colorMode.getCheckedRadioButtonId() - 300);
                    prefs.palette = paletteSel[0];
                    prefs.accent = accent[0];
                    prefs.monoIcons = mono.isChecked();
                    prefs.tileAlpha = 90 + Math.round(opacity.getProgress() * 165f / 100f);
                    prefs.dock = dockSw.isChecked();
                    prefs.compactDrawer = compact.isChecked();
                    prefs.bing = bingSw.isChecked();
                    prefs.bingMins = mins;
                    prefs.celsius = celsius.isChecked();
                    if (cityChanged) {
                        prefs.city = newCity;
                        prefs.cityLat = Double.NaN;
                        prefs.cityLon = Double.NaN;
                        prefs.cityName = "";
                    }
                    prefs.save(this);

                    if (setup && styleGroup != null) {
                        int style = Math.max(0, styleGroup.getCheckedRadioButtonId() - 400);
                        sections = LayoutStore.defaults(this, apps, style, palette());
                    } else if (paletteChanged) {
                        recolorAll();
                    }
                    if (prefs.dock && dock.isEmpty()) dock.addAll(LayoutStore.defaultDock(this));

                    listMonoCache.clear();
                    adapter.notifyDataSetChanged();
                    if (setup) finishSetup();
                    else saveAndRebuild();
                    if (bingToggled) refreshBing(true);
                    if (rotChanged && prefs.bing) {
                        if (mins == 0) {
                            bingIndex = 0;
                            showBing(0);
                        }
                        scheduleBing();
                    }
                    if (cityChanged || unitsChanged) {
                        weather = null;
                        weatherStatus = null;
                        refreshWeather(true);
                    }
                });
        if (setup) b.setNegativeButton("Back", (d, w) -> showWelcome());
        else b.setNegativeButton("Cancel", null);
        b.show();
    }

    /** Spreads the current palette across all tiles. */
    private void recolorAll() {
        if (sections == null) return;
        int[] p = palette();
        int i = 0;
        for (Section s : sections) for (TileSpec t : s.tiles) t.color = p[i++ % p.length];
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Reset Start to which layout?")
                .setItems(LayoutStore.STYLE_NAMES, (d, w) -> {
                    if (sections != null) {
                        for (Section s : sections) {
                            for (TileSpec t : s.tiles) {
                                if (t.type == TileSpec.WIDGET && t.widgetId >= 0) widgetHost.deleteAppWidgetId(t.widgetId);
                            }
                        }
                    }
                    sections = LayoutStore.defaults(this, apps, w, palette());
                    saveAndRebuild();
                    refreshWeather(true);
                    refreshCalendar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestHomeRole(boolean quiet) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    if (!quiet) toast("Metro is already your home screen");
                    return;
                }
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        if (!startSafe(new Intent(Settings.ACTION_HOME_SETTINGS))) startSafe(new Intent(Settings.ACTION_SETTINGS));
    }

    private void hideKeyboard() {
        if (search == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    // ================================================================ live tiles

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateLiveTiles();
            long now = System.currentTimeMillis();
            ui.postDelayed(this, 60000 - (now % 60000) + 150);
        }
    };

    private final Runnable flipTick = new Runnable() {
        @Override
        public void run() {
            flipRandomTile();
            ui.postDelayed(this, 2500 + rnd.nextInt(3000));
        }
    };

    private final Runnable slowTick = new Runnable() {
        @Override
        public void run() {
            refreshCalendar();
            refreshWeather(false);
            refreshBing(false);
            ui.postDelayed(this, 15 * 60000L);
        }
    };

    private final Runnable bingTick = new Runnable() {
        @Override
        public void run() {
            rotateBing();
            scheduleBing();
        }
    };

    private void flipRandomTile() {
        if (!resumed || host.getPage() != 0) return;
        final List<TileView> candidates = new ArrayList<>();
        final Rect r = new Rect();
        forEachTileView(t -> {
            if (t.canFlip() && t.getGlobalVisibleRect(r)) candidates.add(t);
        });
        if (!candidates.isEmpty()) candidates.get(rnd.nextInt(candidates.size())).flip();
    }

    private boolean hasLiveTile(int type) {
        if (sections == null) return false;
        for (Section s : sections) for (TileSpec t : s.tiles) if (t.type == type) return true;
        return false;
    }

    private String temp(double v) {
        return Math.round(v) + "°";
    }

    private void updateLiveTiles() {
        if (start == null) return;
        Date now = new Date();
        Locale loc = Locale.getDefault();
        boolean h24 = DateFormat.is24HourFormat(this);
        final String time = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", loc).format(now);
        final String weekday = new SimpleDateFormat("EEEE", loc).format(now);
        final String monthDay = new SimpleDateFormat("MMMM d", loc).format(now);
        final String dayNum = new SimpleDateFormat("d", loc).format(now);
        final String monthYear = new SimpleDateFormat("MMMM yyyy", loc).format(now);
        final String alarm = nextAlarmText(h24);
        final boolean notifOn = NotifService.isEnabled(this);
        final List<StatusBarNotification> notifs = notifOn
                ? NotifService.items() : new ArrayList<StatusBarNotification>();

        forEachTileView(t -> {
          try {
            switch (t.spec.type) {
                case TileSpec.CLOCK:
                    t.setFaces(time, weekday, monthDay, dayNum, monthYear, alarm, true);
                    break;
                case TileSpec.CALENDAR:
                    if (nextEvent != null) {
                        t.setFaces(dayNum, weekday, null, null, nextEvent[0], nextEvent[1], true);
                    } else if (CalendarReader.allowed(this)) {
                        t.setFaces(dayNum, weekday, null, null, "No upcoming events", "in the next 3 days", true);
                    } else {
                        t.setFaces(dayNum, weekday, null, null, "Calendar access is off", "Allow it in app settings", true);
                    }
                    break;
                case TileSpec.WEATHER:
                    if (weather != null) {
                        t.setFaces(temp(weather.temp), WeatherClient.describe(weather.code), weatherPlace,
                                null,
                                "Today " + temp(weather.hi) + " / " + temp(weather.lo),
                                "Tomorrow " + temp(weather.tHi) + " / " + temp(weather.tLo)
                                        + "  " + WeatherClient.describe(weather.tCode),
                                true);
                    } else {
                        t.setFaces("--°", weatherStatus == null ? "Loading…" : weatherStatus,
                                null, null, null, null, false);
                    }
                    break;
                case TileSpec.MEDIA:
                    if (!notifOn) {
                        t.setArt(null);
                        t.setFaces("♪", "Tap to connect", "to your music apps", null, null, null, false);
                    } else if (media.hasSession()) {
                        t.setArt(media.art());
                        String state = media.isPlaying() ? "Playing" : "Paused";
                        String artist = media.artist();
                        t.setFaces(null, media.title(), artist.isEmpty() ? state : artist + " · " + state,
                                null, null, null, false);
                    } else {
                        t.setArt(null);
                        t.setFaces("♪", "Nothing playing", "Tap to open music", null, null, null, false);
                    }
                    break;
                case TileSpec.NOTIFS:
                    if (!notifOn) {
                        t.setFaces(null, "Tap to turn on", "your notification folder", null, null, null, false);
                    } else if (notifs.isEmpty()) {
                        t.setFaces("0", "All caught up", null, null, null, null, false);
                    } else {
                        StatusBarNotification a = notifs.get(0);
                        String b1 = null, b2 = null;
                        if (notifs.size() > 1) {
                            StatusBarNotification b = notifs.get(1);
                            b1 = appLabel(b.getPackageName()) + ": " + NotifService.title(b);
                            b2 = NotifService.text(b);
                        }
                        t.setFaces(String.valueOf(notifs.size()),
                                appLabel(a.getPackageName()) + ": " + NotifService.title(a),
                                NotifService.text(a), null, b1, b2, notifs.size() > 1);
                    }
                    break;
                default:
                    break;
            }
          } catch (Exception e) {
            CrashLog.note(this, e);
          }
        });
    }

    private String nextAlarmText(boolean h24) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
            if (info == null) return "No alarm set";
            return "Alarm " + new SimpleDateFormat(h24 ? "EEE H:mm" : "EEE h:mm a", Locale.getDefault())
                    .format(new Date(info.getTriggerTime()));
        } catch (Exception e) {
            return "";
        }
    }

    private void refreshCalendar() {
        if (safeStart || !hasLiveTile(TileSpec.CALENDAR)) return;
        runBg(() -> {
            String[] ev = CalendarReader.next(this);
            ui.post(() -> {
                nextEvent = ev;
                updateLiveTiles();
            });
        });
    }

    // ================================================================ weather

    private void refreshWeather(boolean force) {
        if (safeStart) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastWeather < 30 * 60000L) return;
        if (!hasLiveTile(TileSpec.WEATHER)) return;
        lastWeather = now;

        if (!prefs.city.isEmpty()) {
            final String cityQuery = prefs.city;
            runBg(() -> {
                try {
                    if (Double.isNaN(prefs.cityLat)) {
                        WeatherClient.Place p = WeatherClient.geocode(cityQuery);
                        if (p == null) {
                            postWeatherStatus("City not found");
                            return;
                        }
                        prefs.cityLat = p.lat;
                        prefs.cityLon = p.lon;
                        prefs.cityName = p.name;
                        prefs.save(this);
                    }
                    fetchWeatherAt(prefs.cityLat, prefs.cityLon, prefs.cityName);
                } catch (Exception e) {
                    postWeatherStatus("Weather unavailable");
                }
            });
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            weatherStatus = "Set a city in settings";
            updateLiveTiles();
            return;
        }
        final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        List<String> providers;
        try {
            providers = lm.getProviders(true);
        } catch (Exception e) {
            providers = new ArrayList<>();
        }
        for (String p : providers) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {
                // e.g. "gps" when only approximate location was allowed
            }
        }
        if (best != null && now - best.getTime() < 3 * 3600000L) {
            final Location b = best;
            runBg(() -> fetchWeatherAt(b.getLatitude(), b.getLongitude(), null));
            return;
        }
        String provider = null;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) provider = LocationManager.GPS_PROVIDER;
        } catch (Exception ignored) {
        }
        final Location fallback = best;
        if (provider == null) {
            if (fallback != null) {
                runBg(() -> fetchWeatherAt(fallback.getLatitude(), fallback.getLongitude(), null));
            } else {
                weatherStatus = "Turn on location or set a city";
                updateLiveTiles();
            }
            return;
        }
        stopLocationUpdates();
        try {
            final boolean[] done = {false};
            final LocationListener ll = new LocationListener() {
                @Override
                public void onLocationChanged(Location l) {
                    stopLocationUpdates();
                    if (done[0] || l == null) return;
                    done[0] = true;
                    final double lat = l.getLatitude(), lon = l.getLongitude();
                    runBg(() -> fetchWeatherAt(lat, lon, null));
                }

                @Override
                public void onStatusChanged(String p, int s, Bundle e) {
                }

                @Override
                public void onProviderEnabled(String p) {
                }

                @Override
                public void onProviderDisabled(String p) {
                }
            };
            activeLm = lm;
            activeLocListener = ll;
            lm.requestLocationUpdates(provider, 0, 0, ll, Looper.getMainLooper());
            ui.postDelayed(() -> {
                if (activeLocListener == ll) stopLocationUpdates();
                if (done[0]) return;
                done[0] = true;
                if (fallback != null) {
                    runBg(() -> fetchWeatherAt(fallback.getLatitude(), fallback.getLongitude(), null));
                } else {
                    lastWeather = 0;
                    weatherStatus = "Location unavailable";
                    updateLiveTiles();
                }
            }, 30000);
        } catch (Exception e) {
            stopLocationUpdates();
            if (fallback != null) {
                runBg(() -> fetchWeatherAt(fallback.getLatitude(), fallback.getLongitude(), null));
            } else {
                weatherStatus = "Set a city in settings";
                updateLiveTiles();
            }
        }
    }

    private void postWeatherStatus(String s) {
        ui.post(() -> {
            lastWeather = 0;
            weatherStatus = s;
            updateLiveTiles();
        });
    }

    /** Runs on a background thread. */
    private void fetchWeatherAt(double lat, double lon, String placeName) {
        try {
            WeatherClient.Weather w = WeatherClient.fetch(lat, lon, prefs.celsius);
            String place = placeName;
            if (place == null || place.isEmpty()) {
                try {
                    List<Address> a = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
                    if (a != null && !a.isEmpty()) {
                        place = a.get(0).getLocality();
                        if (place == null) place = a.get(0).getSubAdminArea();
                    }
                } catch (Exception ignored) {
                }
            }
            final String pl = place == null ? "" : place;
            ui.post(() -> {
                weather = w;
                weatherPlace = pl;
                weatherStatus = null;
                updateLiveTiles();
            });
        } catch (Exception e) {
            postWeatherStatus("Weather unavailable");
        }
    }

    // ================================================================ Bing background

    private void scheduleBing() {
        ui.removeCallbacks(bingTick);
        if (resumed && prefs.bing && prefs.bingMins > 0) ui.postDelayed(bingTick, prefs.bingMins * 60000L);
    }

    private boolean hasBackground() {
        return bgA.getDrawable() != null || bgB.getDrawable() != null;
    }

    private void refreshBing(boolean force) {
        if (safeStart) return;
        if (!prefs.bing) {
            bgA.setImageDrawable(null);
            bgB.setImageDrawable(null);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && !bingUrls.isEmpty() && now - prefs.bingFetched < 6 * 3600000L) {
            if (!hasBackground()) showBing(bingIndex);
            return;
        }
        runBg(() -> {
            try {
                List<String> urls = BingClient.fetch();
                if (urls.isEmpty()) return;
                BingClient.cleanCache(getCacheDir(), urls);
                ui.post(() -> {
                    boolean changed = !urls.equals(bingUrls);
                    bingUrls.clear();
                    bingUrls.addAll(urls);
                    prefs.bingCache = TextUtils.join("\n", urls);
                    prefs.bingFetched = System.currentTimeMillis();
                    prefs.save(this);
                    if (changed) bingIndex = 0;
                    if (changed || !hasBackground()) showBing(bingIndex);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (!bingUrls.isEmpty() && !hasBackground()) showBing(bingIndex);
                });
            }
        });
    }

    private void rotateBing() {
        lastBingRotate = System.currentTimeMillis();
        if (!prefs.bing || bingUrls.size() < 2) return;
        bingIndex = (bingIndex + 1) % bingUrls.size();
        showBing(bingIndex);
    }

    private void showBing(int index) {
        if (index < 0 || index >= bingUrls.size()) return;
        final String url = bingUrls.get(index);
        final File cacheDir = getCacheDir();
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        runBg(() -> {
            try {
                File f = BingClient.cacheFile(cacheDir, url);
                if (!f.exists()) Util.download(url, f);
                Bitmap bm = Util.decodeSampled(f, dm.widthPixels, dm.heightPixels);
                if (bm != null) ui.post(() -> crossfade(bm));
            } catch (Exception ignored) {
            }
        });
    }

    private void crossfade(Bitmap bm) {
        if (!prefs.bing) return;
        ImageView in = bgAFront ? bgB : bgA;
        ImageView out = bgAFront ? bgA : bgB;
        in.setImageBitmap(bm);
        in.animate().alpha(1f).setDuration(1400).start();
        out.animate().alpha(0f).setDuration(1400).start();
        bgAFront = !bgAFront;
    }
}
