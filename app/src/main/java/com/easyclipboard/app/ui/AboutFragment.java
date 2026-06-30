package com.easyclipboard.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.easyclipboard.app.R;

/** About page: a WebView loading the bundled about.html asset. */
public class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        WebView web = (WebView) inflater.inflate(R.layout.fragment_about, container, false);
        web.loadUrl("file:///android_asset/about.html");
        return web;
    }
}
