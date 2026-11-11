package app.nightrecord.twa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Simple WebView shell. Avoids TWA/Chrome Custom Tabs so the launcher icon
 * always opens the site, including on devices where TWA silently fails.
 */
public class MainActivity extends Activity {
  private static final String START_URL = "https://nightrecord.app/";
  private static final int FILE_CHOOSER = 1001;

  private WebView webView;
  private ValueCallback<Uri[]> fileCallback;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    webView = new WebView(this);
    webView.setBackgroundColor(Color.parseColor("#0A0A0C"));
    setContentView(webView);

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setLoadWithOverviewMode(true);
    settings.setUseWideViewPort(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        String host = uri.getHost();
        if (host != null && (
            host.equals("nightrecord.app")
                || host.equals("www.nightrecord.app")
                || host.equals("dev.nightrecord.app")
        )) {
          return false;
        }
        try {
          startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
          // no handler
        }
        return true;
      }
    });

    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onShowFileChooser(
          WebView view,
          ValueCallback<Uri[]> filePathCallback,
          FileChooserParams fileChooserParams
      ) {
        if (fileCallback != null) {
          fileCallback.onReceiveValue(null);
        }
        fileCallback = filePathCallback;
        Intent intent = fileChooserParams.createIntent();
        try {
          startActivityForResult(intent, FILE_CHOOSER);
        } catch (Exception e) {
          fileCallback = null;
          return false;
        }
        return true;
      }
    });

    String url = START_URL;
    Intent intent = getIntent();
    if (intent != null && intent.getData() != null) {
      String data = intent.getData().toString();
      if (data.startsWith("https://nightrecord.app")
          || data.startsWith("https://www.nightrecord.app")
          || data.startsWith("https://dev.nightrecord.app")) {
        url = data;
      }
    }
    webView.loadUrl(url);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == FILE_CHOOSER) {
      Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
      if (fileCallback != null) {
        fileCallback.onReceiveValue(result);
        fileCallback = null;
      }
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onBackPressed() {
    if (webView != null && webView.canGoBack()) {
      webView.goBack();
      return;
    }
    super.onBackPressed();
  }
}
