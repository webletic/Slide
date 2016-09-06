package me.ccrama.redditslide.Activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import me.ccrama.redditslide.Adapters.SubmissionAdapter;
import me.ccrama.redditslide.ColorPreferences;
import me.ccrama.redditslide.ContentType;
import me.ccrama.redditslide.Fragments.SubmissionsView;
import me.ccrama.redditslide.OpenRedditLink;
import me.ccrama.redditslide.PostMatch;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.Views.NestedWebView;
import me.ccrama.redditslide.Visuals.Palette;
import me.ccrama.redditslide.util.AdBlocker;
import me.ccrama.redditslide.util.LogUtil;

public class Website extends BaseActivityAnim {

    public static final String EXTRA_URL        = "url";
    public static final String EXTRA_COLOR      = "color";
    public static final String ADAPTER_POSITION = "adapter_position";

    WebView              v;
    String               url;
    int                  subredditColor;
    MyWebViewClient      client;
    AdBlockWebViewClient webClient;
    ProgressBar          p;

    public static String getDomainName(String url) {
        URI uri;
        try {
            uri = new URI(url);

            String domain = uri.getHost();
            return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return url;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_website, menu);

        //   if (mShowInfoButton) menu.findItem(R.id.action_info).setVisible(true);
        //   else menu.findItem(R.id.action_info).setVisible(false);
        MenuItem item = menu.findItem(R.id.store_cookies);
        item.setChecked(SettingValues.cookies);

        if (getIntent().hasExtra(ADAPTER_POSITION)) {
            final int commentUrl = getIntent().getExtras().getInt(ADAPTER_POSITION);
            menu.findItem(R.id.comments).getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                    SubmissionsView.datachanged(commentUrl);
                }
            });
        } else {
            menu.findItem(R.id.comments).setVisible(false);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (v.canGoBack()) {
            v.goBack();
        } else if (!isFinishing()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                finish();
                return true;
            case R.id.refresh:
                v.reload();
                return true;
            case R.id.back:
                v.goBack();
                return true;
            case R.id.external:
                Intent inte = new Intent(this, MakeExternal.class);
                inte.putExtra("url", url);
                startActivity(inte);
                return true;
            case R.id.store_cookies:
                SettingValues.prefs.edit()
                        .putBoolean(SettingValues.PREF_COOKIES, !SettingValues.cookies)
                        .apply();
                SettingValues.cookies = !SettingValues.cookies;
                finish();
                overridePendingTransition(0, 0);
                startActivity(getIntent());
                overridePendingTransition(0, 0);
                return true;
            case R.id.read:
                v.evaluateJavascript(
                        "(function(){return \"<html>\" + document.documentElement.innerHTML + \"</html>\";})();",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String html) {
                                Intent i = new Intent(Website.this, ReaderMode.class);

                                if (html != null && !html.isEmpty()) {
                                    ReaderMode.html = html;
                                    LogUtil.v(html);
                                } else {
                                    ReaderMode.html = "";
                                    i.putExtra("url", v.getUrl());
                                }
                                startActivity(i);

                            }
                        });
                return true;
            case R.id.chrome:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(v.getUrl()));
                startActivity(Intent.createChooser(browserIntent, getDomainName(v.getUrl())));
                return true;
            case R.id.share:
                Reddit.defaultShareText(v.getTitle(), v.getUrl(), Website.this);

                return true;

        }
        return false;
    }

    //Stop audio
    @Override
    public void finish() {
        super.finish();
        v.loadUrl("about:blank");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        applyColorTheme("");
        setContentView(R.layout.activity_web);
        url = getIntent().getExtras().getString(EXTRA_URL, "");
        subredditColor = getIntent().getExtras().getInt(EXTRA_COLOR, Palette.getDefaultColor());

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        setupAppBar(R.id.toolbar, "", true, subredditColor, R.id.appbar);
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        p = (ProgressBar) findViewById(R.id.progress);
        v = (NestedWebView) findViewById(R.id.web);

        client = new MyWebViewClient();
        webClient = new AdBlockWebViewClient();

        if (!SettingValues.cookies) {
            CookieSyncManager.createInstance(this);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            CookieManager.getInstance().flush();
            cookieManager.setAcceptCookie(false);

            WebSettings ws = v.getSettings();
            ws.setSaveFormData(false);
            ws.setSavePassword(false);
        }

        /* todo in the future, drag left and right to go back and forward in history

        IOverScrollDecor decor = new HorizontalOverScrollBounceEffectDecorator(new WebViewOverScrollDecoratorAdapter(v));

        decor.setOverScrollStateListener(new IOverScrollStateListener() {
                                             @Override
                                             public void onOverScrollStateChange(IOverScrollDecor decor, int oldState, int newState) {
                                                 switch (newState) {
                                                     case IOverScrollState.STATE_IDLE:
                                                         // No over-scroll is in effect.
                                                         break;
                                                     case IOverScrollState.STATE_DRAG_START_SIDE:
                                                         break;
                                                     case IOverScrollState.STATE_DRAG_END_SIDE:
                                                         break;
                                                     case IOverScrollState.STATE_BOUNCE_BACK:
                                                         if (oldState == IOverScrollState.STATE_DRAG_START_SIDE) {
                                                             if(v.canGoBack())
                                                                 v.goBack();
                                                         } else { // i.e. (oldState == STATE_DRAG_END_SIDE)
                                                             if(v.canGoForward())
                                                                 v.goForward();
                                                         }
                                                         break;
                                                 }
                                             }
                                         });
                                         */
        v.setWebChromeClient(client);
        v.setWebViewClient(webClient);
        v.getSettings().setBuiltInZoomControls(true);
        v.getSettings().setDisplayZoomControls(false);
        v.getSettings().setJavaScriptEnabled(true);
        v.getSettings().setLoadWithOverviewMode(true);
        v.getSettings().setUseWideViewPort(true);
        v.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimetype, long contentLength) {
                //Downloads using download manager on default browser
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        v.loadUrl(url);
    }

    public void setValue(int newProgress) {
        p.setProgress(newProgress);
        if (newProgress == 100) {
            p.setVisibility(View.GONE);
        } else if (p.getVisibility() == View.GONE) {
            p.setVisibility(View.VISIBLE);
        }
    }

    private class MyWebViewClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            Website.this.setValue(newProgress);
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            try {
                super.onReceivedTitle(view, title);
                if (getSupportActionBar() != null) {

                    if (!title.isEmpty()) {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(title);

                            setShareUrl(url);

                            if (url.contains("/")) {
                                getSupportActionBar().setSubtitle(getDomainName(url));
                            }
                        }
                    } else {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(getDomainName(url));
                        }

                    }
                }
            } catch (Exception ignored) {

            }
        }

    }

    public static ArrayList<String> triedURLS;


    //Method adapted from http://www.hidroh.com/2016/05/19/hacking-up-ad-blocker-android/
    public class AdBlockWebViewClient extends WebViewClient {
        private Map<String, Boolean> loadedUrls = new HashMap<>();

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url, Website.this);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }
            return ad && SettingValues.tabletUI ? AdBlocker.createEmptyResource()
                    : super.shouldInterceptRequest(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            ContentType.Type type = ContentType.getContentType(url);

            if (triedURLS == null) {
                triedURLS = new ArrayList<>();
            }
            if ((!PostMatch.openExternal(url) || type == ContentType.Type.VIDEO)
                    && !triedURLS.contains(url)) {
                switch (type) {
                    case DEVIANTART:
                    case IMGUR:
                        if (SettingValues.image) {
                            Intent intent2 = new Intent(view.getContext(), MediaView.class);
                            intent2.putExtra(MediaView.EXTRA_URL, url);
                            view.getContext().startActivity(intent2);
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case REDDIT:
                        boolean opened = OpenRedditLink.openUrl(view.getContext(), url, false);
                        if (!opened) {
                            return super.shouldOverrideUrlLoading(view, url);
                        }
                        return true;
                    case STREAMABLE:
                    case VID_ME:
                        if (SettingValues.video) {
                            Intent myIntent = new Intent(view.getContext(), MediaView.class);
                            myIntent.putExtra(MediaView.EXTRA_URL, url);
                            view.getContext().startActivity(myIntent);
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case ALBUM:
                        if (SettingValues.album) {
                            if (SettingValues.albumSwipe) {
                                Intent i = new Intent(view.getContext(), AlbumPager.class);
                                i.putExtra(Album.EXTRA_URL, url);
                                view.getContext().startActivity(i);
                            } else {
                                Intent i = new Intent(view.getContext(), Album.class);
                                i.putExtra(Album.EXTRA_URL, url);
                                view.getContext().startActivity(i);
                            }
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case IMAGE:
                        if (SettingValues.image) {
                            Intent myIntent = new Intent(view.getContext(), MediaView.class);
                            myIntent.putExtra(MediaView.EXTRA_URL, url);
                            view.getContext().startActivity(myIntent);
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case GIF:
                        if (SettingValues.gif) {
                            Intent myIntent = new Intent(view.getContext(), MediaView.class);
                            myIntent.putExtra(MediaView.EXTRA_URL, url);
                            view.getContext().startActivity(myIntent);
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case VIDEO:
                        if (Reddit.videoPlugin) {
                            try {
                                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                                sharingIntent.setClassName("ccrama.me.slideyoutubeplugin",
                                        "ccrama.me.slideyoutubeplugin.YouTubeView");
                                sharingIntent.putExtra("url", url);
                                view.getContext().startActivity(sharingIntent);
                            } catch (Exception e) {
                                return super.shouldOverrideUrlLoading(view, url);
                            }
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, url);
                    case EXTERNAL:
                    default:
                        return super.shouldOverrideUrlLoading(view, url);
                }
            } else {
                return super.shouldOverrideUrlLoading(view, url);
            }
        }
    }
}
