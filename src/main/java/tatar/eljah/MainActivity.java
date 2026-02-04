package tatar.eljah;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import tatar.eljah.demo.ToneDemoActivity;
import tatar.eljah.practice.TonePracticeActivity;
import tatar.eljah.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View demoButton = findViewById(R.id.btn_demo);
        View practiceButton = findViewById(R.id.btn_practice);

        demoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ToneDemoActivity.class);
                startActivity(intent);
            }
        });

        practiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TonePracticeActivity.class);
                startActivity(intent);
            }
        });
    }
}
