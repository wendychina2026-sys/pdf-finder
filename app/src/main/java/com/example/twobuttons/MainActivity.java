package com.example.twobuttons;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView label = new TextView(this);
        label.setText("Count: 0");
        label.setTextSize(28);
        label.setGravity(Gravity.CENTER);

        Button addButton = new Button(this);
        addButton.setText("Add 1");
        addButton.setOnClickListener(v -> {
            count++;
            label.setText("Count: " + count);
        });

        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setOnClickListener(v -> {
            count = 0;
            label.setText("Count: 0");
        });

        root.addView(label);
        root.addView(addButton);
        root.addView(resetButton);
        setContentView(root);
    }
}
