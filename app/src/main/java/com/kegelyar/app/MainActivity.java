
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
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

    private final int BG = Color.rgb(24, 14, 48);
    private final int CARD = Color.rgb(53, 34, 83);
    private final int PURPLE = Color.rgb(145, 65, 235);
    private final int PINK = Color.rgb(230, 105, 205);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(205, 190, 225);
    private final int GREEN = Color.rgb(75, 210, 155);

    private LinearLayout root, content;
    private TextView stepTitle, stepDescription, timerLabel;
    private CircleTimer circleTimer;
    private SharedPreferences prefs;
    private TextToSpeech tts;

    private boolean ttsReady = false;
    private boolean soundEnabled = true;
    private boolean running = false;
    private boolean sessionFinished = false;

    private String gender = "";
    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int currentStep = 0;
    private int secondsLeft = 0;
    private int stageDuration = 1;

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

    private static class TrainingStep {
        String title;
        String description;
        int seconds;
        boolean contraction;

        TrainingStep(String t, String d, int s, boolean c) {
            title = t;
            description = d;
            seconds = s;
            contraction = c;
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || sessionFinished) return;

            secondsLeft--;

            if (secondsLeft <= 0) {
                running = false;
                handler.removeCallbacks(this);

                if (currentStep == steps.size() - 1) {
                    finishTraining();
                } else {
                    currentStep++;
                    showWaitingForPermission();
                }
                return;
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

        if (gender.isEmpty()) showGender();
        else showHome();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.forLanguageTag("fa-IR"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;

                if (ttsReady) {
                    tts.setSpeechRate(0.85f);
                    try {
                        Set<Voice> voices = tts.getVoices();
                        if (voices != null) {
                            for (Voice voice : voices) {
                                String n = voice.getName().toLowerCase(Locale.ROOT);
                                if (n.contains("fa") &&
                                    (n.contains("female") || n.contains("woman"))) {
                                    tts.setVoice(voice);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
        });
    }

    private void speak(String s) {
        if (soundEnabled && ttsReady && tts != null) {
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "kegelyar_voice");
        }
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable gradient(int radius) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{PURPLE, Color.rgb(70, 30, 135)});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView makeText(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
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

        TextView header = makeText("✦   " + title, 21, WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setBackground(gradient(0));
        header.setPadding(dp(12), dp(17), dp(12), dp(17));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(22));
        scroll.addView(content);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private LinearLayout makeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(13), dp(13), dp(13), dp(13));
        c.setBackground(shape(CARD, 20));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(8));
        content.addView(c, lp);
        return c;
    }

    private void addText(LinearLayout p, String s, int size, int color) {
        TextView t = makeText(s, size, color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        p.addView(t);
    }

    private void addButton(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackground(gradient(16));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        content.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }

    private void addCardButton(LinearLayout p, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE, 14));
        p.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
    }

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
        return week == 1 || getWeekCompleted(week - 1) == 7;
    }

    private int getTotalCompleted() {
        int total = 0;
        for (int w = 1; w <= 12; w++) total += getWeekCompleted(w);
        return total;
    }

    private int getFirstAvailableWeek() {
        for (int w = 1; w <= 12; w++) {
            if (isWeekUnlocked(w) && getWeekCompleted(w) < 7) return w;
        }
        return 12;
    }

    private int getFirstIncompleteDay(int week) {
        for (int d = 1; d <= 7; d++) {
            if (!isDayDone(week, d)) return d;
        }
        return 7;
    }

    private void showGender() {
        setup("کگل‌یار");
        LinearLayout c = makeCard();
        addText(c, "به کگل‌یار خوش آمدید", 24, WHITE);
        addText(c, "گروه تمرینی خود را انتخاب کنید", 16, MUTED);

        addButton("♂ برنامه آقایان", () -> chooseGender("آقایان"));
        addButton("♀ برنامه بانوان", () -> chooseGender("بانوان"));
    }

    private void chooseGender(String g) {
        gender = g;
        prefs.edit().putString("gender", g).apply();
        showHome();
    }

    private void showHome() {
        setup("کگل‌یار");

        LinearLayout hero = makeCard();
        hero.setBackground(gradient(24));
        addText(hero, "سلام، خوش آمدید ✨", 24, WHITE);
        addText(hero, "سلامت و آرامش با تمرین روزانه", 15, MUTED);
        addText(hero, "برنامه انتخابی: " + gender, 15, WHITE);

        LinearLayout progress = makeCard();
        addText(progress, "پیشرفت کلی شما", 18, WHITE);
        addText(progress, getTotalCompleted() + " از ۸۴ جلسه", 23, PINK);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(getTotalCompleted());
        progress.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));

        addButton("▶ شروع تمرین روزانه", () -> {
            if (getTotalCompleted() >= 84) {
                showProgress();
                return;
            }
            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);
            showTraining();
        });

        addButton("▦ برنامه ۱۲ هفته‌ای", () -> showWeeks());
        addButton("◉ پیشرفت من", () -> showProgress());
        addButton("♡ ورزش‌های مکمل", () -> showExercises());
        addButton("📖 آموزش کگل", () -> showGuide());
        addButton("🛡 نکات ایمنی", () -> showSafety());
        addButton("⚙ تنظیمات صدا", () -> showSettings());
        addButton("تغییر گروه تمرینی", () -> showGender());
    }

    private void showWeeks() {
        setup("برنامه ۱۲ هفته‌ای");

        LinearLayout intro = makeCard();
        addText(intro, "هر هفته ۷ جلسه دارد", 18, WHITE);
        addText(intro, "هفته بعد پس از تکمیل تمام ۷ روز باز می‌شود.", 14, MUTED);

        for (int i = 0; i < 12; i++) {
            final int week = i + 1;
            LinearLayout c = makeCard();

            addText(c, "هفته " + week + "  ✦  " + weekTitles[i], 17, WHITE);
            addText(c, weekDescriptions[i], 14, MUTED);
            addText(c, getWeekCompleted(week) + " از ۷ روز تکمیل شده",
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

    private void showDays() {
        if (!isWeekUnlocked(selectedWeek)) {
            showWeeks();
            return;
        }

        setup("هفته " + selectedWeek);

        LinearLayout intro = makeCard();
        addText(intro, weekTitles[selectedWeek - 1], 20, WHITE);
        addText(intro, getWeekCompleted(selectedWeek) + " از ۷ جلسه تکمیل شده",
                15, GREEN);

        for (int d = 1; d <= 7; d++) {
            final int day = d;
            LinearLayout c = makeCard();

            boolean done = isDayDone(selectedWeek, day);
            addText(c, "روز " + day + (done ? "   ✓" : "   ○"), 18, WHITE);
            addText(c, done ? "جلسه تکمیل شده" : "جلسه تمرینی", 14, MUTED);

            if (!done) {
                addCardButton(c, "شروع این جلسه", () -> {
                    selectedDay = day;
                    showTraining();
                });
            }
        }

        addButton("بازگشت به هفته‌ها", () -> showWeeks());
        addButton("خانه", () -> showHome());
    }

    private void buildTrainingSteps() {
        steps.clear();

        if (selectedDay == 7) {
            steps.add(new TrainingStep("تنفس آرام",
                    "به‌آرامی نفس بکشید و عضلات را رها کنید.", 60, false));
            steps.add(new TrainingStep("رهاسازی عضلات",
                    "بدون انقباض، عضلات را کاملاً آزاد نگه دارید.", 60, false));
            steps.add(new TrainingStep("آرام‌سازی پایانی",
                    "چند نفس آرام بکشید و استراحت کنید.", 60, false));
            return;
        }

        steps.add(new TrainingStep("آماده‌سازی و تنفس",
                "در وضعیت راحت قرار بگیرید و آرام نفس بکشید.", 45, false));

        int hold = Math.min(8, 3 + (selectedWeek - 1) / 2);
        int rest = hold + 2;
        int reps = selectedWeek < 4 ? 4 : 5;

        for (int i = 1; i <= reps; i++) {
            steps.add(new TrainingStep("انقباض ملایم " + i,
                    "عضلات کف لگن را ملایم منقبض کنید. نفس را حبس نکنید.",
                    hold, true));
            steps.add(new TrainingStep("رهاسازی کامل " + i,
                    "انقباض را کاملاً رها کنید و استراحت کنید.",
                    rest, false));
        }

        int quick = selectedWeek < 4 ? 3 : 5;
        for (int i = 1; i <= quick; i++) {
            steps.add(new TrainingStep("انقباض کوتاه " + i,
                    "یک انقباض کوتاه و ملایم انجام دهید.", 2, true));
            steps.add(new TrainingStep("رهاسازی کوتاه " + i,
                    "عضلات را کاملاً رها کنید.", 4, false));
        }

        steps.add(new TrainingStep("آرام‌سازی پایانی",
                "عضلات کف لگن را کاملاً آزاد کنید.", 45, false));
    }

    private void showTraining() {
        running = false;
        sessionFinished = false;
        handler.removeCallbacks(timerRunnable);
        buildTrainingSteps();
        currentStep = 0;
        prepareStage();
    }

    private void prepareStage() {
        running = false;
        handler.removeCallbacks(timerRunnable);

        if (currentStep >= steps.size()) {
            finishTraining();
            return;
        }

        TrainingStep s = steps.get(currentStep);
        secondsLeft = s.seconds;
        stageDuration = s.seconds;

        setup("تمرین روز " + selectedDay);

        LinearLayout info = makeCard();
        addText(info, "هفته " + selectedWeek + "  •  روز " + selectedDay,
                15, MUTED);
        addText(info, "مرحله " + (currentStep + 1) + " از " + steps.size(),
                17, WHITE);

        ProgressBar stageBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        stageBar.setMax(steps.size());
        stageBar.setProgress(currentStep);
        info.addView(stageBar);

        LinearLayout visual = makeCard();
        exerciseArt = new ExerciseArt(s.contraction);
        visual.addView(exerciseArt, new LinearLayout.LayoutParams(-1, dp(205)));

        stepTitle = makeText(s.title, 22, WHITE);
        stepTitle.setTypeface(null, Typeface.BOLD);
        visual.addView(stepTitle);

        stepDescription = makeText(s.description, 15, MUTED);
        visual.addView(stepDescription);

        timerLabel = makeText("", 15, PINK);
        visual.addView(timerLabel);

        circleTimer = new CircleTimer();
        circleTimer.setValue(secondsLeft, stageDuration);
        visual.addView(circleTimer, new LinearLayout.LayoutParams(
                dp(210), dp(210)));

        updateTimer();

        if (currentStep == 0) {
            addButton("▶ شروع مرحله اول با اجازه من", () -> startStage());
        } else {
            addButton("▶ شروع مرحله بعد با اجازه من", () -> startStage());
        }

        addButton("⏸ توقف تمرین", () -> pauseStage());
        addButton("بازگشت به خانه", () -> {
            pauseStage();
            showHome();
        });

        speak(s.title + ". " + s.description);
    }

    private void showWaitingForPermission() {
        prepareStage();
        Toast.makeText(this,
                "مرحله قبلی تمام شد. منتظر اجازه شما هستیم.",
                Toast.LENGTH_LONG).show();
    }

    private void startStage() {
        if (running || sessionFinished) return;

        running = true;
        speak(steps.get(currentStep).title);
        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable, 1000);
    }

    private void pauseStage() {
        running = false;
        handler.removeCallbacks(timerRunnable);
        if (tts != null) tts.stop();
    }

    private void updateTimer() {
        if (timerLabel != null) {
            timerLabel.setText("زمان باقی‌مانده: " + secondsLeft + " ثانیه");
        }
        if (circleTimer != null) {
            circleTimer.setValue(secondsLeft, stageDuration);
        }
    }

    private void finishTraining() {
        if (sessionFinished) return;

        sessionFinished = true;
        pauseStage();

        prefs.edit()
                .putBoolean(dayKey(selectedWeek, selectedDay), true)
                .apply();

        setup("آفرین! جلسه کامل شد");

        LinearLayout c = makeCard();
        addText(c, "🏆 تبریک می‌گوییم!", 24, PINK);
        addText(c, "تمام مراحل این جلسه با موفقیت انجام شد.", 16, WHITE);
        addText(c, "پیشرفت کل: " + getTotalCompleted() + " از ۸۴ جلسه",
                18, GREEN);

        if (getWeekCompleted(selectedWeek) == 7 && selectedWeek < 12) {
            addText(c, "هفته کامل شد؛ هفته بعد باز شد!", 17, GREEN);
        }

        addButton("مشاهده روزهای هفته", () -> showDays());
        addButton("بازگشت به خانه", () -> showHome());
        addButton("مشاهده پیشرفت", () -> showProgress());
    }

    private class CircleTimer extends View {
        private final Paint p = new Paint(3);
        private int value = 0;
        private int max = 1;

        CircleTimer() {
            super(MainActivity.this);
        }

        void setValue(int v, int m) {
            value = v;
            max = Math.max(1, m);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - dp(17);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(13));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(83, 62, 112));
            canvas.drawCircle(cx, cy, r, p);

            p.setColor(PINK);
            canvas.drawArc(cx-r, cy-r, cx+r, cy+r,
                    -90, 360f * value / max, false, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(dp(31));
            String time = String.format(Locale.US, "%02d:%02d",
                    value / 60, value % 60);
            canvas.drawText(time, cx, cy + dp(10), p);
        }
    }

    private class ExerciseArt extends View {
        private final Paint p = new Paint(3);
        private final boolean contraction;
        private float phase = 0;

        ExerciseArt(boolean c) {
            super(MainActivity.this);
            contraction = c;
        }

        private void line(Canvas c, float x1, float y1, float x2,
                          float y2, int color, float width) {
            p.setColor(color);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            canvas.drawColor(Color.rgb(42, 27, 69));

            phase += 0.07f;
            float lift = (float)Math.sin(phase) * dp(3);

            p.setColor(Color.rgb(91, 64, 125));
            canvas.drawRoundRect(dp(12), h*.78f, w-dp(12), h*.86f,
                    dp(15), dp(15), p);

            p.setColor(Color.rgb(180, 135, 215));
            canvas.drawRoundRect(w*.08f, h*.66f, w*.29f, h*.78f,
                    dp(14), dp(14), p);

            p.setColor(Color.rgb(230, 155, 190));
            canvas.drawRoundRect(w*.23f, h*.57f+lift,
                    w*.65f, h*.73f+lift, dp(35), dp(35), p);

            p.setColor(Color.rgb(245, 190, 160));
            canvas.drawCircle(w*.20f, h*.55f+lift, dp(23), p);

            p.setColor(Color.rgb(75, 40, 35));
            canvas.drawCircle(w*.18f, h*.53f+lift, dp(24), p);

            line(canvas, w*.60f, h*.66f+lift, w*.75f, h*.48f,
                    Color.rgb(230, 155, 190), dp(20));
            line(canvas, w*.75f, h*.48f, w*.90f, h*.70f,
                    Color.rgb(230, 155, 190), dp(20));
            line(canvas, w*.55f, h*.67f+lift, w*.76f, h*.74f,
                    Color.rgb(230, 155, 190), dp(19));

            p.setColor(PINK);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(37));
            canvas.drawText(contraction ? "↑" : "↓",
                    w*.51f, h*.46f + lift, p);

            p.setTextSize(dp(12));
            p.setColor(MUTED);
            canvas.drawText(contraction ? "انقباض ملایم" : "رهاسازی و آرامش",
                    w/2, h-dp(5), p);

            postInvalidateDelayed(90);
        }
    }

    private void showProgress() {
        setup("پیشرفت من");

        int total = getTotalCompleted();
        int percent = total * 100 / 84;

        LinearLayout c = makeCard();
        addText(c, "مسیر پیشرفت شما", 22, WHITE);
        addText(c, total + " از ۸۴ جلسه", 20, GREEN);
        addText(c, percent + " درصد تکمیل شده", 18, PINK);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(total);
        c.addView(bar);

        for (int w = 1; w <= 12; w++) {
            addText(c, "هفته " + w + ": " +
                    getWeekCompleted(w) + " از ۷ روز", 14, MUTED);
        }

        addButton("بازگشت به خانه", () -> showHome());
    }

    private void showExercises() {
        setup("ورزش‌های مکمل");
        for (int i = 0; i < exerciseNames.length; i++) {
            LinearLayout c = makeCard();
            addText(c, exerciseNames[i], 18, WHITE);
            addText(c, exerciseDescriptions[i], 14, MUTED);
        }
        addButton("بازگشت", () -> showHome());
    }

    private void showGuide() {
        setup("آموزش کگل");
        LinearLayout c = makeCard();
        addText(c, "روش صحیح تمرین", 21, PINK);
        addText(c,
                "۱. در وضعیت راحت قرار بگیرید.\n" +
                "۲. عضلات کف لگن را به‌آرامی منقبض کنید.\n" +
                "۳. نفس خود را حبس نکنید.\n" +
                "۴. عضلات را کاملاً رها کنید.\n" +
                "۵. بین انقباض‌ها استراحت کافی داشته باشید.",
                15, WHITE);
        addText(c,
                "تمرین کگل را هنگام ادرار کردن انجام ندهید.",
                14, MUTED);
        addButton("بازگشت", () -> showHome());
    }

    private void showSafety() {
        setup("نکات ایمنی");
        LinearLayout c = makeCard();
        addText(c, "سلامت شما مهم‌تر از تعداد تمرین‌هاست.", 19, PINK);
        addText(c,
                "• تمرین نباید باعث درد شود.\n" +
                "• از فشار آوردن خودداری کنید.\n" +
                "• نفس خود را حبس نکنید.\n" +
                "• بین انقباض‌ها عضلات را کاملاً رها کنید.\n" +
                "• کگل را هنگام ادرار کردن انجام ندهید.\n" +
                "• در صورت درد یا گرفتگی تمرین را متوقف کنید.\n" +
                "• در صورت درد لگن یا مشکل ادراری با پزشک مشورت کنید.",
                15, WHITE);
        addButton("بازگشت", () -> showHome());
    }

    private void showSettings() {
        setup("تنظیمات صدا");
        LinearLayout c = makeCard();

        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("فعال بودن صدای راهنما");
        soundSwitch.setTextColor(WHITE);
        soundSwitch.setChecked(soundEnabled);
        c.addView(soundSwitch);

        soundSwitch.setOnCheckedChangeListener((button, checked) -> {
            soundEnabled = checked;
            prefs.edit().putBoolean("sound", checked).apply();
            if (!checked && tts != null) tts.stop();
        });

        addCardButton(c, "آزمایش صدای راهنما", () -> {
            speak("سلام. به برنامه کگل‌یار خوش آمدید.");
            if (!ttsReady) {
                Toast.makeText(this,
                        "صدای فارسی روی موتور گفتار گوشی در دسترس نیست.",
                        Toast.LENGTH_LONG).show();
            }
        });

        addText(c, "صدای زنانه به صداهای فارسی نصب‌شده روی گوشی وابسته است.",
                13, MUTED);
        addButton("بازگشت", () -> showHome());
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseStage();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        pauseStage();
        showHome();
    }
}
