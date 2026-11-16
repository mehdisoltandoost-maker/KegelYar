
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    // رنگ‌بندی روشن کگل‌یار
    private final int BG = Color.rgb(248, 245, 253);
    private final int CARD = Color.WHITE;
    private final int PURPLE = Color.rgb(143, 94, 205);
    private final int DARK_PURPLE = Color.rgb(91, 55, 151);
    private final int PINK = Color.rgb(222, 126, 184);
    private final int TEXT = Color.rgb(62, 47, 81);
    private final int MUTED = Color.rgb(137, 124, 151);
    private final int GREEN = Color.rgb(62, 169, 121);
    private final int TRACK = Color.rgb(231, 222, 243);

    private LinearLayout root, content;
    private TextView stepTitle, stepDescription, timerLabel;
    private CircleTimer circleTimer;
    private ExerciseArt exerciseArt;

    private SharedPreferences prefs;
    private TextToSpeech tts;

    private boolean ttsReady = false;
    private boolean soundEnabled = true;
    private boolean running = false;
    private boolean sessionFinished = false;

    // آیا کاربر قبلاً اجازه شروع این مرحله را داده است؟
    private boolean stageStarted = false;

    private boolean contractionPhase = true;

    private String gender = "";

    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int currentStep = 0;

    private int secondsLeft = 0;
    private int stageDuration = 1;
    private int stageRemaining = 0;
    private int phaseSecondsLeft = 0;
    private int currentRep = 1;

    private final Handler handler = new Handler();
    private final ArrayList<TrainingStep> steps = new ArrayList<>();

    private final String[] weekTitles = {
        "شروع آرام", "شناخت عضلات", "کنترل بهتر",
        "افزایش استقامت", "تقویت تدریجی",
        "کنترل و هماهنگی", "تمرین پیشرفته",
        "استقامت بیشتر", "هماهنگی عضلات",
        "کنترل پایدار", "تثبیت توانایی",
        "حفظ و نگهداری"
    };

    private final String[] weekDescriptions = {
        "آشنایی با انقباض و رهاسازی صحیح",
        "تمرین آرام و شناخت عضلات کف لگن",
        "افزایش تدریجی کنترل عضلات",
        "تمرین استقامت با استراحت کافی",
        "تقویت تدریجی و کنترل‌شده",
        "هماهنگی تنفس و انقباض",
        "تمرین کنترل و استقامت",
        "توجه به کیفیت انقباض",
        "هماهنگی و آرام‌سازی",
        "کنترل بهتر در تمرین روزانه",
        "تثبیت الگوی صحیح تمرین",
        "مرور و حفظ عادت صحیح"
    };

    private final String[] exerciseNames = {
        "تنفس آرام", "انقباض ملایم",
        "انقباض و رهاسازی", "تمرین سریع",
        "تمرین استقامتی", "آرام‌سازی"
    };

    private final String[] exerciseDescriptions = {
        "آرام نفس بکشید و بدن را رها کنید.",
        "عضلات کف لگن را به‌آرامی منقبض کنید.",
        "پس از هر انقباض، عضلات را کاملاً رها کنید.",
        "انقباض کوتاه و رهاسازی کامل را تمرین کنید.",
        "انقباض کنترل‌شده را برای مدت کوتاه نگه دارید.",
        "در پایان، عضلات را کاملاً آزاد کنید."
    };

    /*
     * هر TrainingStep یک مرحله مستقل است.
     * تکرارها درون همان مرحله و به صورت خودکار اجرا می‌شوند.
     */
    private static class TrainingStep {
        String title;
        String description;

        int seconds;
        boolean contraction;
        boolean repeated;

        int repetitions;
        int holdSeconds;
        int restSeconds;

        // مرحله معمولی
        TrainingStep(String t, String d, int s, boolean c) {
            title = t;
            description = d;
            seconds = s;
            contraction = c;
            repeated = false;
            repetitions = 1;
            holdSeconds = s;
            restSeconds = 0;
        }

        // مرحله دارای چند تکرار خودکار
        TrainingStep(String t, String d, int reps,
                     int hold, int rest) {
            title = t;
            description = d;
            repetitions = reps;
            holdSeconds = hold;
            restSeconds = rest;
            seconds = reps * (hold + rest);
            contraction = true;
            repeated = true;
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {

            if (!running || sessionFinished) return;

            stageRemaining--;

            if (steps.get(currentStep).repeated) {
                phaseSecondsLeft--;
            }

            if (stageRemaining <= 0) {
                completeStage();
                return;
            }

            if (steps.get(currentStep).repeated
                    && phaseSecondsLeft <= 0) {
                changeExercisePhase();
            }

            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("KegelYarData", MODE_PRIVATE);

        soundEnabled = prefs.getBoolean("sound", true);
        gender = prefs.getString("gender", "");

        initTTS();

        if (gender.isEmpty()) {
            showGender();
        } else {
            showHome();
        }
    }

    // صدای راهنمای فارسی
    private void initTTS() {
        tts = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {

                int result = tts.setLanguage(
                        Locale.forLanguageTag("fa-IR"));

                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;

                if (ttsReady) {
                    tts.setSpeechRate(0.85f);

                    try {
                        Set<Voice> voices = tts.getVoices();

                        if (voices != null) {
                            for (Voice voice : voices) {

                                String name = voice.getName()
                                        .toLowerCase(Locale.ROOT);

                                if (name.contains("fa") &&
                                    (name.contains("female")
                                    || name.contains("woman"))) {

                                    tts.setVoice(voice);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void speak(String message) {
        if (soundEnabled && ttsReady && tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH,
                    null, "kegelyar_voice");
        }
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), TRACK);
        return d;
    }

    private GradientDrawable gradient(int radius) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{PURPLE, DARK_PURPLE});

        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView makeText(String text, int size, int color) {
        TextView t = new TextView(this);

        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(7), dp(7), dp(7), dp(7));

        return t;
    }

    private void setup(String title) {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        TextView header = makeText(
                "✦   " + title, 21, Color.WHITE);

        header.setTypeface(null, Typeface.BOLD);
        header.setBackground(gradient(0));
        header.setPadding(dp(12), dp(17), dp(12), dp(17));

        root.addView(header);

        ScrollView scroll = new ScrollView(this);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(22));

        scroll.addView(content);

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private LinearLayout makeCard() {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(shape(CARD, 20));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);

        lp.setMargins(0, dp(6), 0, dp(8));

        content.addView(card, lp);

        return card;
    }

    private void addText(LinearLayout parent, String text,
                         int size, int color) {

        TextView t = makeText(text, size, color);

        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        parent.addView(t);
    }

    private void addButton(String title, Runnable action) {

        Button b = new Button(this);

        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackground(gradient(16));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);

        lp.setMargins(0, dp(6), 0, dp(6));

        content.addView(b, lp);

        b.setOnClickListener(v -> action.run());
    }

    private void addCardButton(LinearLayout parent,
                               String title, Runnable action) {

        Button b = new Button(this);

        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE, 14));

        parent.addView(b,
                new LinearLayout.LayoutParams(-1, -2));

        b.setOnClickListener(v -> action.run());
    }

    // ذخیره وضعیت روزها
    private String dayKey(int week, int day) {
        return "week_" + week + "_day_" + day;
    }

    private boolean isDayDone(int week, int day) {
        return prefs.getBoolean(dayKey(week, day), false);
    }

    private int getWeekCompleted(int week) {

        int count = 0;

        for (int d = 1; d <= 7; d++) {
            if (isDayDone(week, d)) count++;
        }

        return count;
    }

    private boolean isWeekUnlocked(int week) {
        return week == 1
                || getWeekCompleted(week - 1) == 7;
    }

    private int getTotalCompleted() {

        int total = 0;

        for (int w = 1; w <= 12; w++) {
            total += getWeekCompleted(w);
        }

        return total;
    }

    private int getFirstAvailableWeek() {

        for (int w = 1; w <= 12; w++) {

            if (isWeekUnlocked(w)
                    && getWeekCompleted(w) < 7) {
                return w;
            }
        }

        return 12;
    }

    private int getFirstIncompleteDay(int week) {

        for (int d = 1; d <= 7; d++) {
            if (!isDayDone(week, d)) return d;
        }

        return 7;
    }

    // انتخاب گروه تمرینی
    private void showGender() {

        setup("کگل‌یار");

        LinearLayout c = makeCard();

        addText(c, "به کگل‌یار خوش آمدید", 24, TEXT);
        addText(c, "گروه تمرینی خود را انتخاب کنید",
                16, MUTED);

        addButton("♂ برنامه آقایان",
                () -> chooseGender("آقایان"));

        addButton("♀ برنامه بانوان",
                () -> chooseGender("بانوان"));
    }

    private void chooseGender(String g) {

        gender = g;

        prefs.edit().putString("gender", g).apply();

        showHome();
    }

    // صفحه اصلی
    private void showHome() {

        setup("کگل‌یار");

        LinearLayout hero = makeCard();
        hero.setBackground(gradient(24));

        addText(hero, "سلام، خوش آمدید ✨",
                24, Color.WHITE);

        addText(hero, "سلامت و آرامش با تمرین روزانه",
                15, Color.WHITE);

        addText(hero, "برنامه انتخابی: " + gender,
                15, Color.WHITE);

        LinearLayout progress = makeCard();

        addText(progress, "پیشرفت کلی شما", 18, TEXT);

        addText(progress,
                getTotalCompleted() + " از ۸۴ جلسه",
                23, PURPLE);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);

        bar.setMax(84);
        bar.setProgress(getTotalCompleted());

        progress.addView(bar,
                new LinearLayout.LayoutParams(-1, dp(12)));

        addButton("▶ شروع تمرین روزانه", () -> {

            if (getTotalCompleted() >= 84) {
                showProgress();
                return;
            }

            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);

            showTraining();
        });

        addButton("▦ برنامه ۱۲ هفته‌ای",
                () -> showWeeks());

        addButton("◉ پیشرفت من",
                () -> showProgress());

        addButton("♡ ورزش‌های مکمل",
                () -> showExercises());

        addButton("📖 آموزش کگل",
                () -> showGuide());

        addButton("🛡 نکات ایمنی",
                () -> showSafety());

        addButton("⚙ تنظیمات صدا",
                () -> showSettings());

        addButton("تغییر گروه تمرینی",
                () -> showGender());
    }

    // هفته‌ها
    private void showWeeks() {

        setup("برنامه ۱۲ هفته‌ای");

        LinearLayout intro = makeCard();

        addText(intro, "هر هفته ۷ جلسه دارد",
                18, TEXT);

        addText(intro,
                "هفته بعد پس از تکمیل تمام ۷ روز باز می‌شود.",
                14, MUTED);

        for (int i = 0; i < 12; i++) {

            final int week = i + 1;

            LinearLayout c = makeCard();

            addText(c,
                    "هفته " + week + "  ✦  " + weekTitles[i],
                    17, TEXT);

            addText(c, weekDescriptions[i], 14, MUTED);

            addText(c,
                    getWeekCompleted(week) + " از ۷ روز تکمیل شده",
                    15, GREEN);

            ProgressBar pb = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);

            pb.setMax(7);
            pb.setProgress(getWeekCompleted(week));

            c.addView(pb);

            if (isWeekUnlocked(week)) {

                addCardButton(c, "مشاهده روزهای هفته", () -> {
                    selectedWeek = week;
                    showDays();
                });

            } else {

                addText(c, "🔒 هفته قفل است", 14, MUTED);
            }
        }

        addButton("بازگشت به خانه", () -> showHome());
    }

    // روزهای هفته
    private void showDays() {

        if (!isWeekUnlocked(selectedWeek)) {
            showWeeks();
            return;
        }

        setup("هفته " + selectedWeek);

        LinearLayout intro = makeCard();

        addText(intro,
                weekTitles[selectedWeek - 1],
                
