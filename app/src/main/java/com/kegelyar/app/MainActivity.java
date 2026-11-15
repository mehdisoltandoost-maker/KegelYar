
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {

    int navy = Color.rgb(12, 27, 45);
    int panel = Color.rgb(25, 43, 63);
    int mint = Color.rgb(74, 222, 184);
    int white = Color.WHITE;
    int muted = Color.rgb(185, 199, 214);

    LinearLayout root;
    Handler handler = new Handler();
    TextView timerText, phaseText;
    Button startButton;
    boolean running = false;
    boolean contract = true;
    int seconds = 10;
    int round = 1;
    int totalRounds = 5;
    String gender = "آقایان";

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            seconds--;
            if (seconds <= 0) {
                if (contract) {
                    contract = false;
                    seconds = 10;
                } else {
                    contract = true;
                    seconds = 10;
                    round++;
                }

                if (round > totalRounds) {
                    running = false;
                    phaseText.setText("تمرین تمام شد");
                    timerText.setText("آفرین!");
                    startButton.setText("بازگشت");
                    return;
                }
            }

            phaseText.setText(contract ? "انقباض ملایم" : "رهاسازی کامل");
            timerText.setText(String.valueOf(seconds));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showGender();
    }

    TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(12, 12, 12, 12);
        return t;
    }

    Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(navy);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(mint)
        );
        return b;
    }

    void setup(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(22, 24, 22, 24);
        root.setBackgroundColor(navy);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        TextView heading = text(title, 25, mint);
        root.addView(heading);

        setContentView(scroll);
    }

    void addButton(String label, Runnable action) {
        Button b = button(label);
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 10, 0, 10);
        root.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    void showGender() {
        setup("کِگِل‌یار");
        root.addView(text("سلام، خوش آمدید", 19, white));
        root.addView(text("برنامه تمرینی مناسب خود را انتخاب کنید", 15, muted));

        addButton("آقایان", () -> {
            gender = "آقایان";
            showHome();
        });

        addButton("بانوان", () -> {
            gender = "بانوان";
            showHome();
        });

        root.addView(text(
            "تمرین آرام، منظم و بدون درد",
            14, muted
        ));
    }

    void showHome() {
        setup("کگل‌یار | " + gender);
        root.addView(text("برنامه تمرینی شما", 19, white));

        addButton("شروع تمرین", () -> showTraining());
        addButton("آموزش و راهنما", () -> showGuide());
        addButton("نکات ایمنی", () -> showSafety());
        addButton("تغییر گروه", () -> showGender());
    }

    void showGuide() {
        setup("آموزش کگل");
        root.addView(text(
            "۱. عضلات کف لگن را به آرامی منقبض کنید.\n\n" +
            "۲. از حبس نفس خودداری کنید.\n\n" +
            "۳. سپس عضلات را کاملاً رها کنید.\n\n" +
            "۴. شکم، ران و باسن را تا حد امکان منقبض نکنید.",
            17, white
        ));
        addButton("بازگشت", () -> showHome());
    }

    void showSafety() {
        setup("نکات ایمنی");
        root.addView(text(
            "تمرین نباید دردناک باشد.\n\n" +
            "هنگام ادرار کردن تمرین کگل انجام ندهید.\n\n" +
            "اگر درد، گرفتگی یا احساس فشار دارید، تمرین را متوقف کنید.\n\n" +
            "در صورت تداوم علائم با پزشک یا فیزیوتراپیست کف لگن مشورت کنید.",
            16, white
        ));
        addButton("بازگشت", () -> showHome());
    }

    void showTraining() {
        running = false;
        contract = true;
        seconds = 10;
        round = 1;

        setup("جلسه تمرین");
        phaseText = text("آماده شروع", 21, mint);
        timerText = text("۱۰", 55, white);

        root.addView(phaseText);
        root.addView(timerText);
        root.addView(text("۵ دور تمرین؛ هر مرحله ۱۰ ثانیه", 14, muted));

        startButton = button("شروع");
        root.addView(startButton);

        startButton.setOnClickListener(v -> {
            if (!running) {
                if (round > totalRounds) {
                    showHome();
                    return;
                }
                running = true;
                phaseText.setText("انقباض ملایم");
                startButton.setText("توقف");
                handler.postDelayed(timerRunnable, 1000);
            } else {
                running = false;
                handler.removeCallbacks(timerRunnable);
                startButton.setText("ادامه");
            }
        });

        addButton("پایان و بازگشت", () -> {
            running = false;
            handler.removeCallbacks(timerRunnable);
            showHome();
        });
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }
}
