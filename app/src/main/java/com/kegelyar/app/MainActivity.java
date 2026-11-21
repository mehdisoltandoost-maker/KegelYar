package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;
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
    private TextView stepTitle, stepDescription;
    private CircleTimer circleTimer;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private Vibrator vibrator;

    private boolean ttsReady = false;
    private String pendingSpeech = "";
    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;
    private boolean running = false;
    private boolean sessionFinished = false;
    private boolean phaseIsContraction = true;

    private String gender = "";
    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int currentStage = 0;
    private int secondsLeft = 0;
    private int stageDuration = 1;
    private int repetition = 1;

    private String selectedSession = "morning";
    private boolean morningEnabled = true;
    private boolean noonEnabled = false;
    private boolean nightEnabled = false;

    private final Handler handler = new Handler();
    private final ArrayList<TrainingStage> stages = new ArrayList<>();

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

    private static class TrainingStage {
        String title;
        String description;
        int mode;
        int duration;
        int repetitions;
        int contractionSeconds;
        int releaseSeconds;
        boolean contractionArt;

        TrainingStage(String title, String description, int duration,
                      boolean contractionArt) {
            this.title = title;
            this.description = description;
            this.mode = 0;
            this.duration = duration;
            this.repetitions = 1;
            this.contractionSeconds = duration;
            this.releaseSeconds = 0;
            this.contractionArt = contractionArt;
        }

        TrainingStage(String title, String description, int repetitions,
                      int contractionSeconds, int releaseSeconds) {
            this.title = title;
            this.description = description;
            this.mode = 1;
            this.duration = contractionSeconds;
            this.repetitions = repetitions;
            this.contractionSeconds = contractionSeconds;
            this.releaseSeconds = releaseSeconds;
            this.contractionArt = true;
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || sessionFinished) return;

            secondsLeft--;

            if (secondsLeft <= 0) {
                TrainingStage stage = stages.get(currentStage);

                if (stage.mode == 1) {
                    if (phaseIsContraction) {
                        phaseIsContraction = false;
                        secondsLeft = stage.releaseSeconds;
                        stageDuration = stage.releaseSeconds;
                        speak("Relax");
                        vibrate(2);
                        updateStageText();
                        updateTimer();
                        handler.postDelayed(this, 1000);
                        return;
                    } else {
                        if (repetition < stage.repetitions) {
                            repetition++;
                            phaseIsContraction = true;
                            secondsLeft = stage.contractionSeconds;
                            stageDuration = stage.contractionSeconds;
                            speak("Gently contract");
                            vibrate(1);
                            updateStageText();
                            updateTimer();
                            handler.postDelayed(this, 1000);
                            return;
                        }
                    }
                }

                running = false;
                handler.removeCallbacks(this);
                completeStageAndWait();
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
        vibrationEnabled = prefs.getBoolean("vibration", true);
        morningEnabled = prefs.getBoolean("session_morning", true);
        noonEnabled = prefs.getBoolean("session_noon", false);
        nightEnabled = prefs.getBoolean("session_night", false);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        gender = prefs.getString("gender", "");

        initTTS();

        if (gender.isEmpty()) showGender();
        else showHome();
    }

    // راهنمای صوتی انگلیسی
    private void initTTS() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    ttsReady = false;
                    return;
                }

                try {
                    int result = tts.setLanguage(Locale.US);

                    if (result < TextToSpeech.LANG_AVAILABLE) {
                        result = tts.setLanguage(Locale.ENGLISH);
                    }

                    ttsReady = result >= TextToSpeech.LANG_AVAILABLE;

                    if (!ttsReady) return;

                    tts.setSpeechRate(0.85f);

                    // انتخاب صدای زنانه انگلیسی در صورت وجود
                    try {
                        Set<Voice> voices = tts.getVoices();

                        if (voices != null) {
                            for (Voice voice : voices) {
                                String n = voice.getName().toLowerCase(Locale.ROOT);

                                String lang = voice.getLocale() == null
                                        ? ""
                                        : voice.getLocale()
                                        .getLanguage()
                                        .toLowerCase(Locale.ROOT);

                                if (lang.equals("en") &&
                                        (n.contains("female")
                                        || n.contains("woman")
                                        || n.contains("fem"))) {
                                    tts.setVoice(voice);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) { }

                    if (!pendingSpeech.isEmpty() && soundEnabled) {
                        String message = pendingSpeech;
                        pendingSpeech = "";
                        speakNow(message);
                    }

                } catch (Exception ignored) {
                    ttsReady = false;
                }
            });

        } catch (Exception e) {
            ttsReady = false;
        }
    }

    private void speakNow(String message) {
        if (tts == null || !ttsReady || !soundEnabled) return;

        try {
            tts.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "kegelyar_voice"
            );
        } catch (Exception ignored) { }
    }

    private void speak(String s) {
        if (!soundEnabled || s == null || s.trim().isEmpty()) return;

        if (ttsReady && tts != null) {
            speakNow(s);
        } else {
            pendingSpeech = s;
        }
    }

    // تبدیل عنوان و توضیح مراحل فارسی به راهنمای صوتی انگلیسی
    private String getStageSpeech(TrainingStage stage) {

        if (stage.title.contains("آماده‌سازی")) {
            return "Get ready. Breathe slowly and relax.";
        }

        if (stage.title.contains("کنترل‌شده")) {
            return "Controlled contractions. Gently contract your pelvic floor muscles, then relax completely.";
        }

        if (stage.title.contains("کوتاه")) {
            return "Short contractions. Gently contract, then fully relax.";
        }

        if (stage.title.contains("تنفس")) {
            return "Breathe slowly. Relax your body.";
        }

        if (stage.title.contains("رهاسازی")) {
            return "Relax your pelvic floor muscles completely.";
        }

        if (stage.title.contains("پایانی")) {
            return "Cool down. Breathe slowly and relax.";
        }

        return "Get ready for the next exercise.";
    }

    // الگوهای لرزش
    private void vibrate(int type) {
        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) return;

        long[] pattern;

        if (type == 1) {
            pattern = new long[]{0, 110};
        } else if (type == 2) {
            pattern = new long[]{0, 65, 100, 65};
        } else if (type == 3) {
            pattern = new long[]{0, 160, 100, 160};
        } else {
            pattern = new long[]{0, 220, 120, 220, 120, 350};
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) { }
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
                new int[]{PURPLE, Color.rgb(70, 30, 135)}
        );
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

    private String sessionKey(int week, int day, String session) {
        return "week_" + week + "_day_" + day + "_session_" + session;
    }

    private boolean isSessionEnabled(String session) {
        if ("morning".equals(session)) return morningEnabled;
        if ("noon".equals(session)) return noonEnabled;
        return nightEnabled;
    }

    private int enabledSessionCount() {
        return (morningEnabled ? 1 : 0)
                + (noonEnabled ? 1 : 0)
                + (nightEnabled ? 1 : 0);
    }

    private boolean isSessionDone(int week, int day, String session) {
        return prefs.getBoolean(sessionKey(week, day, session), false);
    }

    private boolean isDayDone(int week, int day) {
        if (prefs.getBoolean(dayKey(week, day), false)) return true;

        if (enabledSessionCount() == 0) return false;

        if (morningEnabled && !isSessionDone(week, day, "morning")) return false;
        if (noonEnabled && !isSessionDone(week, day, "noon")) return false;
        if (nightEnabled && !isSessionDone(week, day, "night")) return false;

        return true;
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

        for (int w = 1; w <= 12; w++) {
            total += getWeekCompleted(w);
        }

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
        if (getTotalCompleted() < 84) {
            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);
        }

        setup("کگل‌یار");

        LinearLayout hero = makeCard();
        hero.setBackground(gradient(24));

        addText(hero, "سلام، خوش آمدید ✨", 24, WHITE);
        addText(hero, "سلامت و آرامش با تمرین روزانه", 15, MUTED);
        addText(hero, "برنامه انتخابی: " + gender, 15, WHITE);

        LinearLayout progress = makeCard();

        addText(progress, "پیشرفت کلی شما", 18, WHITE);
        addText(progress, getTotalCompleted() + " از ۸۴ روز", 23, PINK);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);

        bar.setMax(84);
        bar.setProgress(getTotalCompleted());

        progress.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));

        LinearLayout daily = makeCard();

        addText(daily, "تمرین‌های امروز", 19, WHITE);
        addText(daily, "هر نوبت را جداگانه شروع کنید؛ برنامه هر نوبت متفاوت است.", 13, MUTED);

        if (morningEnabled) addSessionLaunch(daily, "☀ تمرین صبح", "morning");
        if (noonEnabled) addSessionLaunch(daily, "◉ تمرین ظهر", "noon");
        if (nightEnabled) addSessionLaunch(daily, "☾ تمرین شب", "night");

        if (enabledSessionCount() == 0) {
            addText(daily, "برای شروع، دست‌کم یک نوبت را در تنظیمات فعال کنید.", 14, PINK);
        }

        addButton("تنظیم نوبت‌های صبح، ظهر و شب", () -> showSettings());
        addButton("▦ برنامه ۱۲ هفته‌ای", () -> showWeeks());
        addButton("◉ پیشرفت من", () -> showProgress());
        addButton("♡ ورزش‌های مکمل", () -> showExercises());
        addButton("📖 آموزش کگل", () -> showGuide());
        addButton("🛡 نکات ایمنی", () -> showSafety());
        addButton("⚙ تنظیمات صدا", () -> showSettings());
        addButton("تغییر گروه تمرینی", () -> showGender());
    }

    private void addSessionLaunch(LinearLayout parent, String title, String session) {
        Button b = new Button(this);

        b.setText(title + "  ·  "
                + (isSessionDone(selectedWeek, selectedDay, session)
                ? "تکمیل‌شده" : "شروع"));

        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE, 14));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);

        lp.setMargins(0, dp(4), 0, dp(4));
        parent.addView(b, lp);

        b.setOnClickListener(v -> {
            if (getTotalCompleted() >= 84) {
                showProgress();
                return;
            }

            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);
            selectedSession = session;

            if (isSessionDone(selectedWeek, selectedDay, session)) {
                Toast.makeText(this,
                        "این نوبت قبلاً تکمیل شده است.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            showTraining();
        });
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

            if (!done && day == getFirstIncompleteDay(selectedWeek)) {
                addText(c, "نوبت‌های قابل انتخاب:", 13, MUTED);

                if (morningEnabled) addDaySessionButton(c, day, "☀ صبح", "morning");
                if (noonEnabled) addDaySessionButton(c, day, "◉ ظهر", "noon");
                if (nightEnabled) addDaySessionButton(c, day, "☾ شب", "night");

            } else if (!done) {
                addText(c, "پس از تکمیل روز قبلی فعال می‌شود.", 13, MUTED);
            }
        }

        addButton("بازگشت به هفته‌ها", () -> showWeeks());
        addButton("خانه", () -> showHome());
    }

    private void addDaySessionButton(LinearLayout card, int day,
                                     String label, String session) {

        boolean completed = isSessionDone(selectedWeek, day, session);

        addCardButton(card,
                label + (completed ? "  ✓ انجام شده" : "  ▶ شروع"),
                () -> {

            if (completed) {
                Toast.makeText(this,
                        "این نوبت تکمیل شده است.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            selectedDay = day;
            selectedSession = session;
            showTraining();
        });
    }

    private void buildTrainingStages() {
        stages.clear();

        int sessionOffset = "morning".equals(selectedSession) ? 0
                : ("noon".equals(selectedSession) ? 1 : 2);

        if (selectedDay == 7) {
            int recovery = 45 + sessionOffset * 10;

            stages.add(new TrainingStage(
                    "تنفس آرام",
                    "به‌آرامی نفس بکشید و عضلات را رها کنید.",
                    recovery, false));

            stages.add(new TrainingStage(
                    "رهاسازی عضلات",
                    "بدون انقباض، عضلات را کاملاً آزاد نگه دارید.",
                    recovery, false));

            stages.add(new TrainingStage(
                    "آرام‌سازی پایانی",
                    "چند نفس آرام بکشید و استراحت کنید.",
                    recovery, false));

            return;
        }

        int warmup = 30 + sessionOffset * 10;

        stages.add(new TrainingStage(
                "آماده‌سازی و تنفس",
                "در وضعیت راحت قرار بگیرید و آرام نفس بکشید.",
                warmup, false));

        int hold = Math.min(8,
                3 + (selectedWeek - 1) / 2
                + (sessionOffset == 1 ? 1 : 0));

        int rest = hold + (sessionOffset == 2 ? 3 : 2);

        int reps = (selectedWeek < 4 ? 4 : 5)
                + (sessionOffset == 2 ? 1 : 0);

        stages.add(new TrainingStage(
                "انقباض و رهاسازی کنترل‌شده",
                "با راهنمای صوتی، انقباض ملایم و سپس رهاسازی کامل را تکرار کنید. نفس را حبس نکنید.",
                reps, hold, rest));

        int quick = (selectedWeek < 4 ? 3 : 5) + sessionOffset;

        stages.add(new TrainingStage(
                "انقباض‌های کوتاه",
                "انقباض‌های کوتاه و ملایم را با رهاسازی کامل بین آن‌ها انجام دهید.",
                quick, 2, 4));

        int cooldown = 35 + sessionOffset * 10;

        stages.add(new TrainingStage(
                "آرام‌سازی پایانی",
                "عضلات کف لگن را کاملاً آزاد کنید و آرام نفس بکشید.",
                cooldown, false));
    }

    private void showTraining() {
        running = false;
        sessionFinished = false;

        handler.removeCallbacks(timerRunnable);

        buildTrainingStages();

        currentStage = 0;
        prepareStage();
    }

    private void prepareStage() {
        running = false;
        handler.removeCallbacks(timerRunnable);

        if (currentStage >= stages.size()) {
            finishTraining();
            return;
        }

        TrainingStage stage = stages.get(currentStage);

        repetition = 1;
        phaseIsContraction = true;

        secondsLeft = stage.mode == 1
                ? stage.contractionSeconds : stage.duration;

        stageDuration = secondsLeft;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        String sessionTitle = "morning".equals(selectedSession) ? "صبح"
                : ("noon".equals(selectedSession) ? "ظهر" : "شب");

        TextView header = makeText(
                "✦   تمرین " + sessionTitle + " · روز " + selectedDay,
                20, WHITE);

        header.setTypeface(null, Typeface.BOLD);
        header.setBackground(gradient(0));
        header.setPadding(dp(12), dp(15), dp(12), dp(15));

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(12), dp(8), dp(12), dp(4));

        TextView weekText = makeText(
                "هفته " + selectedWeek + "  •  روز " + selectedDay
                + "  •  نوبت " + sessionTitle,
                14, MUTED);

        info.addView(weekText);

        TextView stageText = makeText(
                "مرحله " + (currentStage + 1) + " از " + stages.size(),
                16, WHITE);

        info.addView(stageText);

        ProgressBar stageBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);

        stageBar.setMax(stages.size());
        stageBar.setProgress(currentStage);

        info.addView(stageBar, new LinearLayout.LayoutParams(-1, dp(5)));

        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);

        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView stageName = makeText(stage.title, 18, WHITE);
        stageName.setTypeface(null, Typeface.BOLD);

        center.addView(stageName,
                new LinearLayout.LayoutParams(-1, -2));

        circleTimer = new CircleTimer();
        circleTimer.setValue(secondsLeft, stageDuration);

        LinearLayout.LayoutParams timerParams =
                new LinearLayout.LayoutParams(dp(270), dp(270));

        timerParams.gravity = Gravity.CENTER;
        timerParams.topMargin = dp(12);
        timerParams.bottomMargin = dp(12);

        center.addView(circleTimer, timerParams);

        TextView instruction = makeText(stage.description, 13, MUTED);
        instruction.setMaxLines(3);

        center.addView(instruction,
                new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(4), dp(14), dp(12));

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        Button startButton = new Button(this);

        startButton.setText(currentStage == 0
                ? "▶ شروع مرحله اول با اجازه من"
                : "▶ شروع مرحله بعد با اجازه من");

        startButton.setTextColor(WHITE);
        startButton.setTextSize(15);
        startButton.setAllCaps(false);
        startButton.setBackground(gradient(16));

        controls.addView(startButton,
                new LinearLayout.LayoutParams(-1, -2));

        startButton.setOnClickListener(v -> startStage());

        Button pauseButton = new Button(this);

        pauseButton.setText("⏸ توقف تمرین");
        pauseButton.setTextColor(WHITE);
        pauseButton.setAllCaps(false);
        pauseButton.setBackground(shape(CARD, 16));

        controls.addView(pauseButton,
                new LinearLayout.LayoutParams(-1, -2));

        pauseButton.setOnClickListener(v -> pauseStage());

        Button homeButton = new Button(this);

        homeButton.setText("بازگشت به خانه");
        homeButton.setTextColor(WHITE);
        homeButton.setAllCaps(false);
        homeButton.setBackground(shape(CARD, 16));

        controls.addView(homeButton,
                new LinearLayout.LayoutParams(-1, -2));

        homeButton.setOnClickListener(v -> {
            pauseStage();
            showHome();
        });

        setContentView(root);

        updateStageText();
        updateTimer();

        speak(getStageSpeech(stage));
    }

    private void updateStageText() {
        if (currentStage >= stages.size()) return;

        TrainingStage stage = stages.get(currentStage);

        if (stage.mode == 1) {
            if (circleTimer != null) {
                circleTimer.setContraction(phaseIsContraction);
            }
        } else {
            if (circleTimer != null) {
                circleTimer.setContraction(false);
            }
        }
    }

    private void showWaitingForPermission() {
        prepareStage();

        Toast.makeText(this,
                "مرحله قبلی تمام شد. برای شروع مرحله بعد دکمه شروع را بزنید.",
                Toast.LENGTH_LONG).show();
    }

    private void startStage() {
        if (running || sessionFinished || currentStage >= stages.size()) return;

        running = true;

        TrainingStage stage = stages.get(currentStage);

        if (stage.mode == 1) {
            speak(phaseIsContraction ? "Gently contract" : "Relax");
            vibrate(phaseIsContraction ? 1 : 2);
        } else {
            speak(getStageSpeech(stage));
            vibrate(1);
        }

        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable, 1000);
    }

    private void completeStageAndWait() {
        pauseStage();
        vibrate(3);

        if (currentStage >= stages.size() - 1) {
            finishTraining();
            return;
        }

        currentStage++;
        prepareStage();

        Toast.makeText(this,
                "مرحله تمام شد؛ برای شروع مرحله بعد اجازه شما لازم است.",
                Toast.LENGTH_LONG).show();
    }

    private void pauseStage() {
        running = false;

        handler.removeCallbacks(timerRunnable);

        if (tts != null) tts.stop();
        if (vibrator != null) vibrator.cancel();
    }

    private void updateTimer() {
        if (circleTimer != null) {
            circleTimer.setValue(secondsLeft, stageDuration);
        }
    }

    private void finishTraining() {
        if (sessionFinished) return;

        sessionFinished = true;

        pauseStage();
        vibrate(4);

        prefs.edit()
                .putBoolean(
                        sessionKey(selectedWeek, selectedDay, selectedSession),
                        true)
                .apply();

        setup("آفرین! جلسه کامل شد");

        LinearLayout c = makeCard();

        addText(c, "🏆 تبریک می‌گوییم!", 24, PINK);

        addText(c,
                "نوبت " + ("morning".equals(selectedSession) ? "صبح"
                : ("noon".equals(selectedSession) ? "ظهر" : "شب"))
                + " با موفقیت انجام شد.",
                16, WHITE);

        addText(c, isDayDone(selectedWeek, selectedDay)
                ? "همه نوبت‌های فعال این روز تکمیل شد."
                : "برای تکمیل روز، نوبت‌های فعال دیگر را هم انجام دهید.",
                14, MUTED);

        addText(c, "پیشرفت کل: " + getTotalCompleted() + " از ۸۴ روز",
                18, GREEN);

        if (getWeekCompleted(selectedWeek) == 7 && selectedWeek < 12) {
            addText(c, "هفته کامل شد؛ هفته بعد باز شد!", 17, GREEN);
        }

        addButton("مشاهده روزهای هفته", () -> showDays());
        addButton("بازگشت به خانه", () -> showHome());
        addButton("مشاهده پیشرفت", () -> showProgress());
    }

    private class CircleTimer extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        private int value = 0;
        private int max = 1;
        private boolean contraction = false;

        CircleTimer() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setContraction(boolean active) {
            contraction = active;
            invalidate();
        }

        void setValue(int v, int m) {
            value = Math.max(0, v);
            max = Math.max(1, m);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            float radius = Math.min(cx, cy) - dp(22);

            int activeColor = contraction
                    ? Color.rgb(245, 65, 75)
                    : Color.rgb(45, 165, 235);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(15));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(76, 59, 101));

            canvas.drawCircle(cx, cy, radius, p);

            p.setColor(activeColor);

            float sweep = 360f * Math.min(value, max) / max;

            canvas.drawArc(
                    cx - radius, cy - radius,
                    cx + radius, cy + radius,
                    -90, sweep, false, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(WHITE);
            p.setTextAlign(Paint.Align.CENTER);

            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(dp(48));

            canvas.drawText(String.valueOf(value),
                    cx, cy + dp(12), p);

            p.setTextSize(dp(15));
            p.setColor(activeColor);
            p.setTypeface(Typeface.DEFAULT_BOLD);

            String phaseLabel;

            if (currentStage < stages.size()
                    && stages.get(currentStage).mode == 1) {

                phaseLabel = (contraction ? "انقباض " : "رهایی ")
                        + repetition + " از "
                        + stages.get(currentStage).repetitions;

            } else if (currentStage < stages.size()
                    && stages.get(currentStage).title.contains("آماده")) {

                phaseLabel = "آماده‌سازی";

            } else {
                phaseLabel = "آرام‌سازی";
            }

            canvas.drawText(phaseLabel,
                    cx, cy + dp(43), p);

            p.setTextSize(dp(11));
            p.setColor(MUTED);
            p.setTypeface(Typeface.DEFAULT);

            canvas.drawText("ثانیه باقی‌مانده",
                    cx, cy + dp(65), p);
        }
    }

    private void showProgress() {
        setup("پیشرفت من");

        int total = getTotalCompleted();
        int percent = total * 100 / 84;

        LinearLayout c = makeCard();

        addText(c, "مسیر پیشرفت شما", 22, WHITE);
        addText(c, total + " از ۸۴ روز", 20, GREEN);
        addText(c, percent + " درصد تکمیل شده", 18, PINK);
        addText(c, "نوبت‌های فعال: " + enabledSessionCount()
                + " نوبت در روز", 14, MUTED);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);

        bar.setMax(84);
        bar.setProgress(total);

        c.addView(bar);

        for (int w = 1; w <= 12; w++) {
            addText(c, "هفته " + w + ": "
                    + getWeekCompleted(w) + " از ۷ روز",
                    14, MUTED);
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
                "۱. در وضعیت راحت قرار بگیرید.\n"
                + "۲. عضلات کف لگن را به‌آرامی منقبض کنید.\n"
                + "۳. نفس خود را حبس نکنید.\n"
                + "۴. عضلات را کاملاً رها کنید.\n"
                + "۵. بین انقباض‌ها استراحت کافی داشته باشید.",
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
                "• تمرین نباید باعث درد شود.\n"
                + "• از فشار آوردن خودداری کنید.\n"
                + "• نفس خود را حبس نکنید.\n"
                + "• بین انقباض‌ها عضلات را کاملاً رها کنید.\n"
                + "• کگل را هنگام ادرار کردن انجام ندهید.\n"
                + "• در صورت درد یا گرفتگی تمرین را متوقف کنید.\n"
                + "• در صورت درد لگن یا مشکل ادراری با پزشک مشورت کنید.",
                15, WHITE);

        addButton("بازگشت", () -> showHome());
    }

    private void showSettings() {
        setup("تنظیمات صدا و ویبره");

        LinearLayout c = makeCard();

        addText(c, "انتخاب نوبت‌های روزانه", 18, PINK);

        addText(c,
                "می‌توانید فقط یک نوبت، دو نوبت یا هر سه نوبت را فعال کنید.",
                13, MUTED);

        Switch morningSwitch = new Switch(this);

        morningSwitch.setText("تمرین صبح");
        morningSwitch.setTextColor(WHITE);
        morningSwitch.setChecked(morningEnabled);

        c.addView(morningSwitch);

        morningSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                morningSwitch.setChecked(true);

                Toast.makeText(this,
                        "حداقل یک نوبت باید فعال باشد.",
                        Toast.LENGTH_SHORT).show();

                return;
            }

            morningEnabled = checked;

            prefs.edit()
                    .putBoolean("session_morning", checked)
                    .apply();
        });

        Switch noonSwitch = new Switch(this);

        noonSwitch.setText("تمرین ظهر");
        noonSwitch.setTextColor(WHITE);
        noonSwitch.setChecked(noonEnabled);

        c.addView(noonSwitch);

        noonSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                noonSwitch.setChecked(true);

                Toast.makeText(this,
                        "حداقل یک نوبت باید فعال باشد.",
                        Toast.LENGTH_SHORT).show();

                return;
            }

            noonEnabled = checked;

            prefs.edit()
                    .putBoolean("session_noon", checked)
                    .apply();
        });

        Switch nightSwitch = new Switch(this);

        nightSwitch.setText("تمرین شب");
        nightSwitch.setTextColor(WHITE);
        nightSwitch.setChecked(nightEnabled);

        c.addView(nightSwitch);

        nightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                nightSwitch.setChecked(true);

                Toast.makeText(this,
                        "حداقل یک نوبت باید فعال باشد.",
                        Toast.LENGTH_SHORT).show();

                return;
            }

            nightEnabled = checked;

            prefs.edit()
                    .putBoolean("session_night", checked)
                    .apply();
        });

        View sessionSeparator = new View(this);

        c.addView(sessionSeparator,
                new LinearLayout.LayoutParams(-1, dp(12)));

        addText(c, "صدا و لرزش", 18, PINK);

        Switch soundSwitch = new Switch(this);

        soundSwitch.setText("فعال بودن صدای راهنما");
        soundSwitch.setTextColor(WHITE);
        soundSwitch.setChecked(soundEnabled);

        c.addView(soundSwitch);

        soundSwitch.setOnCheckedChangeListener((button, checked) -> {
            soundEnabled = checked;

            prefs.edit()
                    .putBoolean("sound", checked)
                    .apply();

            if (!checked) {
                pendingSpeech = "";

                if (tts != null) tts.stop();
            }
        });

        View separator = new View(this);

        c.addView(separator,
                new LinearLayout.LayoutParams(-1, dp(10)));

        Switch vibrationSwitch = new Switch(this);

        vibrationSwitch.setText("فعال بودن ویبره تمرین");
        vibrationSwitch.setTextColor(WHITE);
        vibrationSwitch.setChecked(vibrationEnabled);

        c.addView(vibrationSwitch);

        vibrationSwitch.setOnCheckedChangeListener((button, checked) -> {
            vibrationEnabled = checked;

            prefs.edit()
                    .putBoolean("vibration", checked)
                    .apply();

            if (checked) vibrate(1);
            else if (vibrator != null) vibrator.cancel();
        });

        addCardButton(c, "آزمایش صدای انگلیسی راهنما", () -> {
            if (!ttsReady) {
                Toast.makeText(this,
                        "موتور گفتار یا صدای انگلیسی گوشی آماده نیست. تنظیمات Text-to-speech گوشی را بررسی کنید.",
                        Toast.LENGTH_LONG).show();
            } else {
                speak("Hello. Welcome to KegelYar. Let's begin.");
            }
        });

        addCardButton(c, "آزمایش ویبره", () -> vibrate(3));

        addText(c,
                "صدای زنانه به صداهای انگلیسی نصب‌شده روی گوشی وابسته است.",
                13, MUTED);

        addText(c,
                "برای دریافت لرزش، ویبره گوشی نیز باید از تنظیمات دستگاه روشن باشد.",
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

        if (vibrator != null) vibrator.cancel();

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
