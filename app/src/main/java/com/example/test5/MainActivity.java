package com.example.test5;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialButton openButton = findViewById(R.id.open_voice_clerk_button);
        openButton.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceClerkActivity.class)));
    }
}
