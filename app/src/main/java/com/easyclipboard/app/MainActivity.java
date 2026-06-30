package com.easyclipboard.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.easyclipboard.app.ui.AboutFragment;
import com.easyclipboard.app.ui.ClipboardFragment;
import com.easyclipboard.app.ui.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Single activity that hosts the three app pages as Fragments behind a
 * Material {@link BottomNavigationView}.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_clipboard) {
                show(new ClipboardFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                show(new SettingsFragment());
                return true;
            } else if (id == R.id.nav_about) {
                show(new AboutFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_clipboard);
        }
    }

    private void show(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
