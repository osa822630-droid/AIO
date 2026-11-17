package userInterface.mainScreen;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.nextgen.R;
import com.nextgen.databinding.ActivityMain1Binding;
import com.nextgen.databinding.ActivityMain1Tab1Binding;

import org.jetbrains.annotations.NotNull;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseFragment;
import coreUtils.library.process.FragNavigator;
import coreUtils.library.process.LoggerUtils;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.service.ServiceBridge;
import userInterface.fragmentsUIs.downloads.DownloadsFragment;
import userInterface.fragmentsUIs.homepage.HomepageFragment;
import userInterface.fragmentsUIs.localSpeed.LocalSpeedFragment;

/**
 * Main hub activity hosting the Speed-Downloader Studio (HomepageFragment),
 * Local Media Speed Converter (LocalSpeedFragment), and Downloads Manager (DownloadsFragment).
 */
public final class MainActivity extends BaseActivity<ActivityMain1Binding> {

    public static final String EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB";

    private final LoggerUtils logger = LoggerUtils.from(getClass());
    private FragNavigator fragNavigator;
    private MainViewModel mainViewModel;
    private boolean isBottomNavVisible = true;
    private int scrollDistance = 0;
    private static final int SCROLL_THRESHOLD = 20;
    private int navigationBarHeight = 0;

    @Override
    protected boolean shouldLockOrientation() {
        return true;
    }

    @Override
    protected ActivityMain1Binding inflateBinding(LayoutInflater inflater) {
        return ActivityMain1Binding.inflate(inflater);
    }

    @Override
    protected void onLoadedLayout() {
        adjustWindowPadding();
        initMainViewModel();
        initFragmentNavigator();
        initBottomTabButtons();
        setupMiniPlayerControls();

        String requestedTab = getIntent() != null ? getIntent().getStringExtra(EXTRA_OPEN_TAB) : null;
        if ("DOWNLOADS".equalsIgnoreCase(requestedTab)) {
            openDownloadsTab();
        } else if ("LOCAL_SPEED".equalsIgnoreCase(requestedTab)) {
            openLocalSpeedTab();
        } else {
            loadHomepage();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String requestedTab = intent != null ? intent.getStringExtra(EXTRA_OPEN_TAB) : null;
        if ("DOWNLOADS".equalsIgnoreCase(requestedTab)) {
            openDownloadsTab();
        } else if ("LOCAL_SPEED".equalsIgnoreCase(requestedTab)) {
            openLocalSpeedTab();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        scrollDistance = 0;
    }

    private void adjustWindowPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.bottomNavContainer, (view, insets) -> {
                    int navigationBars = WindowInsetsCompat.Type.navigationBars();
                    navigationBarHeight = insets.getInsets(navigationBars).bottom;
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                    params.bottomMargin = navigationBarHeight;
                    view.setLayoutParams(params);
                    return insets;
                });
    }

    private void initMainViewModel() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        mainViewModel = viewModelProvider.get(MainViewModel.class);
    }

    private void initFragmentNavigator() {
        FragmentManager frgManager = getSupportFragmentManager();
        fragNavigator = new FragNavigator(frgManager, R.id.fragmentContainer);
    }

    public void loadHomepage() {
        binding.bottomTabs.btnHomeTab.callOnClick();
    }

    public void openDownloadsTab() {
        binding.bottomTabs.btnFilesTab.callOnClick();
    }

    public void openLocalSpeedTab() {
        binding.bottomTabs.btnMusicTab.callOnClick();
    }

    private void initBottomTabButtons() {
        ActivityMain1Tab1Binding bottomTabs = binding.bottomTabs;

        // Tab 1: Home (Speed Downloader Studio)
        bottomTabs.btnHomeTab.setOnClickListener(view ->
                switchTab(new HomepageFragment(),
                        bottomTabs.btnHomeTab, bottomTabs.imgHomeTab,
                        bottomTabs.tvHomeTab, NavigationTabs.HOME_TAB));

        // Tab 2: Local Speed Changer (Convert existing phone files to 1.5x / 2x)
        bottomTabs.btnMusicTab.setOnClickListener(view ->
                switchTab(new LocalSpeedFragment(),
                        bottomTabs.btnMusicTab, bottomTabs.imgMusicTab,
                        bottomTabs.tvMusicsTab, NavigationTabs.MUSIC_TAB));

// Legacy tabs hidden; only the 3 active tabs (Home, Local Speed, Downloads) are shown.

        // Tab 6: All Files / Downloads Manager
        bottomTabs.btnFilesTab.setOnClickListener(view ->
                switchTab(new DownloadsFragment(),
                        bottomTabs.btnFilesTab, bottomTabs.imgFilesTab,
                        bottomTabs.tvFilesTab, NavigationTabs.DOWNLOADS_TAB));
    }

    private void setupMiniPlayerControls() {
        // Hide mini-player unless background PlaybackService is actively running
        if (!ServiceBridge.getInstance().isInitialized()) {
            binding.bottomMiniPlayer.getRoot().setVisibility(View.GONE);
        }
        binding.bottomMiniPlayer.btnTogglePlayback.setOnClickListener(v -> {
            MediaEngine engine = ServiceBridge.getInstance().getEngine();
            if (engine != null) {
                engine.playPause();
            }
        });
        binding.bottomMiniPlayer.btnCancelPlayer.setOnClickListener(v -> {
            MediaEngine engine = ServiceBridge.getInstance().getEngine();
            if (engine != null) {
                engine.stop();
            }
            binding.bottomMiniPlayer.getRoot().setVisibility(View.GONE);
        });
    }

    private void switchTab(@NonNull BaseFragment<?> fragment,
                           @NonNull View btnTab,
                           @NotNull ImageView imgTab,
                           @NotNull TextView tvTab,
                           @NonNull NavigationTabs activeTabEnum) {
        binding.bottomNavContainer.animate().cancel();
        scrollDistance = 0;
        isBottomNavVisible = true;
        binding.bottomNavContainer.setTranslationY(0);

        fragNavigator.navigateTo(fragment, false);
        mainViewModel.setCurrentTabs(activeTabEnum);
        updateTabUI(btnTab, imgTab, tvTab);
    }

    private void updateTabUI(@NotNull View activeBtnTab,
                             @NotNull ImageView activeImgTab,
                             @NotNull TextView activeTvTab) {
        ActivityMain1Tab1Binding bottomTabs = binding.bottomTabs;
        bottomTabs.btnHomeTab.setSelected(false);
        bottomTabs.btnMusicTab.setSelected(false);
        bottomTabs.btnMoviesTab.setSelected(false);
        bottomTabs.btnGamesTab.setSelected(false);
        bottomTabs.btnBrowserTab.setSelected(false);
        bottomTabs.btnFilesTab.setSelected(false);

        Typeface regularFont = ResourcesCompat.getFont(this, R.font.font_family_regular);
        Typeface semiBoldFont = ResourcesCompat.getFont(this, R.font.font_family_semibold);

        bottomTabs.tvHomeTab.setTypeface(regularFont);
        bottomTabs.tvMusicsTab.setTypeface(regularFont);
        bottomTabs.tvMoviesTab.setTypeface(regularFont);
        bottomTabs.tvGamesTab.setTypeface(regularFont);
        bottomTabs.tvBrowserTab.setTypeface(regularFont);
        bottomTabs.tvFilesTab.setTypeface(regularFont);

        activeTvTab.setTypeface(semiBoldFont);
        activeBtnTab.setSelected(true);
    }

    public void setupNestedScrollListener(@NonNull NestedScrollView nestedScrollView) {
        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    int deltaY = scrollY - oldScrollY;
                    if ((deltaY > 0 && scrollDistance < 0) || (deltaY < 0 && scrollDistance > 0)) {
                        scrollDistance = 0;
                    }
                    scrollDistance += deltaY;
                    if (Math.abs(scrollDistance) > SCROLL_THRESHOLD) {
                        if (deltaY > 0 && isBottomNavVisible) {
                            hideBottomNav();
                            scrollDistance = 0;
                        } else if (deltaY < 0 && !isBottomNavVisible) {
                            showBottomNav();
                            scrollDistance = 0;
                        }
                    }
                });
    }

    public void applyEdgeToEdgeScrolling(NestedScrollView scrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, insets) -> {
            int statusBars = WindowInsetsCompat.Type.statusBars();
            int navigationBars = WindowInsetsCompat.Type.navigationBars();
            int statusBarHeight = insets.getInsets(statusBars).top;
            int navBarHeight = insets.getInsets(navigationBars).bottom;
            int extraTopPadding = (int) (5 * getResources().getDisplayMetrics().density);
            int extraBottomPadding = (int) (5 * getResources().getDisplayMetrics().density);
            view.setPadding(
                    view.getPaddingLeft(),
                    statusBarHeight + extraTopPadding,
                    view.getPaddingRight(),
                    navBarHeight + extraBottomPadding
            );
            ((NestedScrollView) view).setClipToPadding(false);
            return insets;
        });
    }

    public void cleanupScrollListener(NestedScrollView nestedScrollView) {
        if (nestedScrollView != null) {
            nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
        }
    }

    private void hideBottomNav() {
        if (!isBottomNavVisible) return;
        binding.bottomNavContainer.animate().cancel();
        int navigationTabHeight = binding.bottomNavContainer.getHeight();
        binding.bottomNavContainer.animate()
                .translationY(navigationTabHeight + navigationBarHeight)
                .setDuration(100)
                .withLayer()
                .setInterpolator(new AccelerateInterpolator())
                .start();
        isBottomNavVisible = false;
    }

    private void showBottomNav() {
        if (isBottomNavVisible) return;
        binding.bottomNavContainer.animate().cancel();
        binding.bottomNavContainer.animate()
                .translationY(0)
                .setDuration(100)
                .withLayer()
                .setInterpolator(new DecelerateInterpolator())
                .start();
        isBottomNavVisible = true;
    }
}
