package com.android.example.diconexcalorimeterforwaterloads;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;



/**
 * @author LI Ziyao
 *
 * This class is an activity of the application called DICONEX CALORIMETER FOR WATER LOADS
 * It only gives details on datas printed in the MainActivity
 */
public class HelpActivity extends AppCompatActivity {
    private Button button;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.help_activity);
    }
}

