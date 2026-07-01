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

        findViewById(R.id.open_tashi_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, TashiStockBinDebugActivity.class)));

        findViewById(R.id.open_yuejiang_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, YuejiangRobotDebugActivity.class)));

        findViewById(R.id.open_drum_pot_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, DrumPotModbusDebugActivity.class)));

        findViewById(R.id.open_lebai_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, LebaiDebugActivity.class)));

        findViewById(R.id.open_order_subscribe_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, OrderSubscribeDebugActivity.class)));
    }
}
