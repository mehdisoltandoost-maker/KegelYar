package com.kegelyar.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.Calendar;
import java.util.Map;

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
    private TextView instructionText;
    private Button playPauseButton;
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
    private int courseNumber = 1;
    private boolean assessmentDone = false;
    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int currentStage = 0;
    private int secondsLeft = 0;
    private int stageDuration = 1;
    private int repetition = 1;
    // نوبت‌های مستقل روزانه: صبح، ظهر و شب
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

    /*
     * هر TrainingStage یک اجازه شروع می‌گیرد.
     * تکرارهای داخل مرحله به‌صورت خودکار اجرا می‌شوند.
     * mode: صفر = زمان ثابت، یک = چرخه انقباض/رهاسازی.
     */
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
                        // پایان انقباض؛ ورود خودکار به رهاسازی
                        phaseIsContraction = false;
                        secondsLeft = stage.releaseSeconds;
                        stageDuration = stage.releaseSeconds;
                        speakCue(false);
                        vibrate(2);
                        updateStageText();
                        updateTimer();
                        handler.postDelayed(this, 1000);
                        return;
                    } else {
                        // پایان رهاسازی؛ تکرار بعدی بدون اجازه مجدد
                        if (repetition < stage.repetitions) {
                            repetition++;
                            phaseIsContraction = true;
                            secondsLeft = stage.contractionSeconds;
                            stageDuration = stage.contractionSeconds;
                            speakCue(true);
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

        SharedPreferences base = getSharedPreferences("KegelYarData", MODE_PRIVATE);
        gender = base.getString("gender", "");
        if (!gender.isEmpty()) loadProfile(gender, true);
        else prefs = base;
        loadPreferences();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        initTTS();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 701);
        }
        String reminderGender = getIntent().getStringExtra("reminder_gender");
        if (reminderGender != null && !reminderGender.equals(gender)) loadProfile(reminderGender, true);
        String reminderSession = getIntent().getStringExtra("reminder_session");
        if (reminderSession != null) selectedSession = reminderSession;
        if (gender.isEmpty()) showGender();
        else if (!assessmentDone) showAssessment();
        else showHome();
    }

    private void loadProfile(String g, boolean migrateLegacy) {
        SharedPreferences base = getSharedPreferences("KegelYarData", MODE_PRIVATE);
        String profileName = "KegelYarData_" + ("آقایان".equals(g) ? "men" : "women");
        SharedPreferences profile = getSharedPreferences(profileName, MODE_PRIVATE);
        if (migrateLegacy && !base.getBoolean("legacy_migrated_to_profile", false)
                && !profile.getBoolean("profile_initialized", false)) {
            SharedPreferences.Editor editor = profile.edit();
            for (Map.Entry<String, ?> entry : base.getAll().entrySet()) {
                String key = entry.getKey();
                if ("gender".equals(key)) continue;
                Object value = entry.getValue();
                if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof String) editor.putString(key, (String) value);
            }
            editor.putBoolean("profile_initialized", true).apply();
            base.edit().putBoolean("legacy_migrated_to_profile", true).apply();
        }
        prefs = profile;
        gender = g;
        prefs.edit().putString("gender", g).apply();
        base.edit().putString("gender", g).apply();
        loadPreferences();
        migrateLegacyDayProgress();
    }

    private void loadPreferences() {
        if (prefs == null) return;
        soundEnabled = prefs.getBoolean("sound", true);
        vibrationEnabled = prefs.getBoolean("vibration", true);
        morningEnabled = prefs.getBoolean("session_morning", true);
        noonEnabled = prefs.getBoolean("session_noon", false);
        nightEnabled = prefs.getBoolean("session_night", false);
        courseNumber = Math.max(1, prefs.getInt("course_number", 1));
        assessmentDone = prefs.getBoolean("assessment_done", false);
    }

    /*
     * مهاجرت یک‌باره سوابق نسخه‌های قدیمی که فقط کلید تکمیل روز داشتند.
     * اگر برای یک روز حتی یک کلید نوبت وجود داشته باشد، آن روز متعلق به
     * ساختار جدید است و نباید نوبت‌های تازه‌فعال‌شده به‌اشتباه کامل شوند.
     */
    private void migrateLegacyDayProgress() {
        if (prefs == null || prefs.getBoolean("session_progress_migrated_v1", false)) return;

        SharedPreferences.Editor editor = prefs.edit();
        for (int course = 1; course <= courseNumber; course++) {
            String prefix = course <= 1 ? "" : "course_" + course + "_";
            for (int week = 1; week <= 12; week++) {
                for (int day = 1; day <= 7; day++) {
                    String base = prefix + "week_" + week + "_day_" + day;
                    if (!prefs.getBoolean(base, false)) continue;

                    boolean hasSessionRecord = prefs.contains(base + "_session_morning")
                            || prefs.contains(base + "_session_noon")
                            || prefs.contains(base + "_session_night");
                    if (hasSessionRecord) continue;

                    if (morningEnabled) editor.putBoolean(base + "_session_morning", true);
                    if (noonEnabled) editor.putBoolean(base + "_session_noon", true);
                    if (nightEnabled) editor.putBoolean(base + "_session_night", true);
                }
            }
        }
        editor.putBoolean("session_progress_migrated_v1", true).apply();
    }

    private String coursePrefix() {
        return courseNumber <= 1 ? "" : "course_" + courseNumber + "_";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("kegelyar_reminders",
                    "یادآوری تمرین کگل‌یار", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("یادآوری نوبت‌های تمرین صبح، ظهر و شب");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private static int reminderId(String g, String session) {
        return ("آقایان".equals(g) ? 100 : 200) +
                ("morning".equals(session) ? 1 : ("noon".equals(session) ? 2 : 3));
    }

    private static void scheduleReminder(Context context, String g, String session,
                                         int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("gender", g);
        intent.putExtra("session", session);
        int id = reminderId(g, session);
        PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, hour);
        when.set(Calendar.MINUTE, minute);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis()) when.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager alarm = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (alarm != null) alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                when.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static void cancelReminder(Context context, String g, String session) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        int id = reminderId(g, session);
        PendingIntent pi = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pi);
        pi.cancel();
    }

    public static class ReminderReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String g = intent.getStringExtra("gender");
            String session = intent.getStringExtra("session");
            if (g == null || session == null) return;
            if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            String profile = "KegelYarData_" + ("آقایان".equals(g) ? "men" : "women");
            SharedPreferences pref = context.getSharedPreferences(profile, MODE_PRIVATE);
            String title = "morning".equals(session) ? "تمرین صبح" :
                    ("noon".equals(session) ? "تمرین ظهر" : "تمرین شب");
            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("reminder_gender", g);
            open.putExtra("reminder_session", session);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, reminderId(g, session), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, "kegelyar_reminders")
                    : new Notification.Builder(context);
            Notification notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("کگل‌یار · " + title)
                    .setContentText("اگر زمان مناسبی است، تمرین امروزتان را شروع کنید.")
                    .setContentIntent(content).setAutoCancel(true).build();
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(reminderId(g, session), notification);
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String[] genders = {"آقایان", "بانوان"};
            String[] sessions = {"morning", "noon", "night"};
            for (String g : genders) {
                String profile = "KegelYarData_" + ("آقایان".equals(g) ? "men" : "women");
                SharedPreferences p = context.getSharedPreferences(profile, MODE_PRIVATE);
                for (String session : sessions) {
                    if (p.getBoolean("reminder_" + session, false)) {
                        String time = p.getString("reminder_time_" + session,
                                "morning".equals(session) ? "08:00" : ("noon".equals(session) ? "13:00" : "20:00"));
                        try {
                            String[] parts = time.split(":");
                            scheduleReminder(context, g, session,
                                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                        } catch (Exception ignored) { }
                    }
                }
            }
        }
    }

    private void initTTS() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    ttsReady = false;
                    return;
                }
                try {
                    int result = tts.setLanguage(Locale.forLanguageTag("fa-IR"));
                    if (result < TextToSpeech.LANG_AVAILABLE) {
                        result = tts.setLanguage(new Locale("fa"));
                    }
                    ttsReady = result >= TextToSpeech.LANG_AVAILABLE;
                    if (!ttsReady) return;
                    tts.setSpeechRate(0.85f);
                    try {
                        Set<Voice> voices = tts.getVoices();
                        if (voices != null) {
                            for (Voice voice : voices) {
                                String n = voice.getName().toLowerCase(Locale.ROOT);
                                String lang = voice.getLocale() == null ? "" :
                                        voice.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
                                if ((lang.startsWith("fa") || n.contains("fa")) &&
                                        (n.contains("female") || n.contains("woman") || n.contains("fem"))) {
                                    tts.setVoice(voice);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) { }
                        @Override public void onDone(String utteranceId) {
                            if ("kegelyar_english_cue".equals(utteranceId)) {
                                handler.post(() -> restorePersianTts());
                            }
                        }
                        @Override public void onError(String utteranceId) {
                            if ("kegelyar_english_cue".equals(utteranceId)) {
                                handler.post(() -> restorePersianTts());
                            }
                        }
                    });
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
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "kegelyar_voice");
        } catch (Exception ignored) { }
    }

    // الگوهای لرزش: ۱ انقباض، ۲ رهایی، ۳ پایان مرحله، ۴ پایان جلسه
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

    private void speak(String s) {
        if (!soundEnabled || s == null || s.trim().isEmpty()) return;
        if (ttsReady && tts != null) speakNow(s);
        else pendingSpeech = s;
    }

    // فرمان‌های کوتاه تمرین با تلفظ انگلیسی: Hold و Relax.
    private void speakCue(boolean contraction) {
        if (!soundEnabled || tts == null || !ttsReady) return;
        try {
            int result = tts.setLanguage(Locale.US);
            if (result < TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(Locale.forLanguageTag("en-US"));
            }
            tts.setSpeechRate(0.90f);
            int spoken = tts.speak(contraction ? "Hold" : "Relax",
                    TextToSpeech.QUEUE_FLUSH, null, "kegelyar_english_cue");
            if (spoken == TextToSpeech.ERROR) restorePersianTts();
        } catch (Exception ignored) {
            restorePersianTts();
        }
    }

    private void restorePersianTts() {
        if (tts == null || !ttsReady) return;
        try {
            tts.setLanguage(Locale.forLanguageTag("fa-IR"));
            tts.setSpeechRate(0.85f);
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    String name = voice.getName().toLowerCase(Locale.ROOT);
                    String language = voice.getLocale() == null ? "" :
                            voice.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
                    if ((language.startsWith("fa") || name.contains("fa")) &&
                            (name.contains("female") || name.contains("woman") || name.contains("fem"))) {
                        tts.setVoice(voice);
                        break;
                    }
                }
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
        return coursePrefix() + "week_" + week + "_day_" + day;
    }

    private String sessionKey(int week, int day, String session) {
        return coursePrefix() + "week_" + week + "_day_" + day + "_session_" + session;
    }

    private boolean isSessionEnabled(String session) {
        if ("morning".equals(session)) return morningEnabled;
        if ("noon".equals(session)) return noonEnabled;
        return nightEnabled;
    }

    private int enabledSessionCount() {
        return (morningEnabled ? 1 : 0) + (noonEnabled ? 1 : 0)
                + (nightEnabled ? 1 : 0);
    }

    private boolean isSessionDone(int week, int day, String session) {
        return prefs.getBoolean(sessionKey(week, day, session), false);
    }

    private boolean isDayDone(int week, int day) {
        // تکمیل روز فقط بر اساس نوبت‌هایی محاسبه می‌شود که اکنون فعال‌اند.
        // کلید قدیمی روز صرفاً در مهاجرت یک‌باره به کلیدهای نوبت تبدیل می‌شود.
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
        loadProfile(g, true);
        if (!assessmentDone) showAssessment(); else showHome();
    }

    private void showAssessment() {
        setup("ارزیابی اولیه");
        LinearLayout card = makeCard();
        addText(card, "برای شروع، سطح تمرین را مشخص کنیم", 20, WHITE);
        addText(card, "این پرسش‌ها برای تنظیم ملایم‌تر برنامه هستند و جای ارزیابی پزشکی را نمی‌گیرند.", 13, MUTED);
        addText(card, "تجربه تمرین کگل", 15, PINK);
        Spinner experience = new Spinner(this);
        String[] exp = {"تازه‌کارم", "قبلاً تمرین کرده‌ام"};
        experience.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, exp));
        card.addView(experience);
        addText(card, "هدف اصلی", 15, PINK);
        Spinner goal = new Spinner(this);
        String[] goals = {"یادگیری و کنترل صحیح", "افزایش استقامت", "حفظ تمرین منظم"};
        goal.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, goals));
        card.addView(goal);
        addText(card, "اگر درد لگن یا درد هنگام تمرین دارید، قبل از شروع با متخصص مشورت کنید.", 13, MUTED);
        addButton("ثبت و شروع برنامه", () -> {
            prefs.edit().putBoolean("assessment_done", true)
                    .putString("assessment_experience", exp[experience.getSelectedItemPosition()])
                    .putString("assessment_goal", goals[goal.getSelectedItemPosition()]).apply();
            assessmentDone = true;
            showHome();
        });
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
        addText(progress, "دوره " + courseNumber + "  ·  " + getTotalCompleted() + " از ۸۴ روز", 21, PINK);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(getTotalCompleted());
        progress.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));

        LinearLayout daily = makeCard();
        addText(daily, "تمرین‌های امروز", 19, WHITE);
        addText(daily, "هر نوبت را جداگانه شروع کنید؛ برنامه هر نوبت متفاوت است.", 13, MUTED);
        String stars = "صبح " + (morningEnabled ? "★" : "☆") + "     ظهر " +
                (noonEnabled ? "★" : "☆") + "     شب " + (nightEnabled ? "★" : "☆");
        addText(daily, stars, 15, PINK);
        if (morningEnabled) addSessionLaunch(daily, "☀ تمرین صبح", "morning");
        if (noonEnabled) addSessionLaunch(daily, "◉ تمرین ظهر", "noon");
        if (nightEnabled) addSessionLaunch(daily, "☾ تمرین شب", "night");
        if (enabledSessionCount() == 0) {
            addText(daily, "برای شروع، دست‌کم یک نوبت را در تنظیمات فعال کنید.", 14, PINK);
        }
        addButton("تنظیم نوبت‌های صبح، ظهر و شب", () -> showSettings());

        addButton("▦ برنامه ۱۲ هفته‌ای", () -> showWeeks());
        addButton("◉ پیشرفت من", () -> showProgress());
        if (getTotalCompleted() >= 84) {
            addButton("شروع دوره جدید با حفظ سوابق", () -> startNewCourse());
        }
        addButton("♡ ورزش‌های مکمل", () -> showExercises());
        addButton("📖 آموزش و نکات ایمنی", () -> showGuide());
        addButton("⚙ تنظیمات و یادآوری‌ها", () -> showSettings());
        addButton("پشتیبان‌گیری از سوابق", () -> shareBackup());
        addButton("تغییر گروه تمرینی", () -> showGender());
    }

    private void startNewCourse() {
        if (getTotalCompleted() < 84) return;
        courseNumber++;
        prefs.edit().putInt("course_number", courseNumber).apply();
        selectedWeek = 1;
        selectedDay = 1;
        Toast.makeText(this, "دوره جدید آغاز شد؛ سوابق دوره قبل محفوظ است.", Toast.LENGTH_LONG).show();
        showHome();
    }

    private void shareBackup() {
        StringBuilder backup = new StringBuilder();
        backup.append("پشتیبان کگل‌یار\nگروه: ").append(gender)
                .append("\nدوره فعلی: ").append(courseNumber).append("\n");
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            backup.append(e.getKey()).append(" = ").append(String.valueOf(e.getValue())).append("\n");
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "پشتیبان سوابق کگل‌یار - " + gender);
        send.putExtra(Intent.EXTRA_TEXT, backup.toString());
        startActivity(Intent.createChooser(send, "ذخیره یا ارسال پشتیبان"));
    }

    private void addSessionLaunch(LinearLayout parent, String title, String session) {
        Button b = new Button(this);
        b.setText(title + "  ·  " + (isSessionDone(selectedWeek, selectedDay, session)
                ? "تکمیل‌شده" : "شروع"));
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        parent.addView(b, lp);
        b.setOnClickListener(v -> {
            if (getTotalCompleted() >= 84) {
                Toast.makeText(this, "دوره کامل شده است؛ از خانه دوره جدید را شروع کنید.", Toast.LENGTH_LONG).show();
                return;
            }
            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);
            selectedSession = session;
            if (isSessionDone(selectedWeek, selectedDay, session)) {
                Toast.makeText(this, "این نوبت قبلاً تکمیل شده است.", Toast.LENGTH_SHORT).show();
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

    private void addDaySessionButton(LinearLayout card, int day, String label, String session) {
        boolean completed = isSessionDone(selectedWeek, day, session);
        addCardButton(card, label + (completed ? "  ✓ انجام شده" : "  ▶ شروع"), () -> {
            if (completed) {
                Toast.makeText(this, "این نوبت تکمیل شده است.", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedDay = day;
            selectedSession = session;
            showTraining();
        });
    }

    private void buildTrainingStages() {
        stages.clear();

        // هر نوبت الگوی زمانی مستقل دارد؛ همه تکرارهای هر مرحله خودکارند.
        int sessionOffset = "morning".equals(selectedSession) ? 0
                : ("noon".equals(selectedSession) ? 1 : 2);

        if (selectedDay == 7) {
            int recovery = 45 + sessionOffset * 10;
            stages.add(new TrainingStage("تنفس آرام",
                    "به‌آرامی نفس بکشید و عضلات را رها کنید.", recovery, false));
            stages.add(new TrainingStage("رهاسازی عضلات",
                    "بدون انقباض، عضلات را کاملاً آزاد نگه دارید.", recovery, false));
            stages.add(new TrainingStage("آرام‌سازی پایانی",
                    "چند نفس آرام بکشید و استراحت کنید.", recovery, false));
            return;
        }

        int warmup = 30 + sessionOffset * 10;
        stages.add(new TrainingStage("آماده‌سازی و تنفس",
                "در وضعیت راحت قرار بگیرید و آرام نفس بکشید.", warmup, false));

        int hold = Math.min(8, 3 + (selectedWeek - 1) / 2 + (sessionOffset == 1 ? 1 : 0));
        int rest = hold + (sessionOffset == 2 ? 3 : 2);
        int reps = (selectedWeek < 4 ? 4 : 5) + (sessionOffset == 2 ? 1 : 0);

        stages.add(new TrainingStage("انقباض و رهاسازی کنترل‌شده",
                "با راهنمای صوتی، انقباض ملایم و سپس رهاسازی کامل را تکرار کنید. نفس را حبس نکنید.",
                reps, hold, rest));

        int quick = (selectedWeek < 4 ? 3 : 5) + sessionOffset;
        stages.add(new TrainingStage("انقباض‌های کوتاه",
                "انقباض‌های کوتاه و ملایم را با رهاسازی کامل بین آن‌ها انجام دهید.",
                quick, 2, 4));

        int cooldown = 35 + sessionOffset * 10;
        stages.add(new TrainingStage("آرام‌سازی پایانی",
                "عضلات کف لگن را کاملاً آزاد کنید و آرام نفس بکشید.", cooldown, false));
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

        // صفحه تمرین: تایمر در مرکز واقعی صفحه و کنترل‌ها در پایین
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        String sessionTitle = "morning".equals(selectedSession) ? "صبح"
                : ("noon".equals(selectedSession) ? "ظهر" : "شب");
        TextView header = makeText("✦   تمرین " + sessionTitle + " · روز " + selectedDay, 20, WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setBackground(gradient(0));
        header.setPadding(dp(12), dp(15), dp(12), dp(15));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView weekText = makeText("هفته " + selectedWeek + "  •  روز " + selectedDay
                + "  •  نوبت " + sessionTitle, 14, MUTED);
        info.addView(weekText);
        TextView stageText = makeText("مرحله " + (currentStage + 1) + " از " + stages.size(),
                16, WHITE);
        info.addView(stageText);
        ProgressBar stageBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        stageBar.setMax(stages.size());
        stageBar.setProgress(currentStage);
        info.addView(stageBar, new LinearLayout.LayoutParams(-1, dp(5)));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        // فضای انعطاف‌پذیر باعث می‌شود دایره در مرکز عمودی صفحه قرار بگیرد.
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView stageName = makeText(stage.title, 18, WHITE);
        stageName.setTypeface(null, Typeface.BOLD);
        center.addView(stageName, new LinearLayout.LayoutParams(-1, -2));

        circleTimer = new CircleTimer();
        circleTimer.setValue(secondsLeft, stageDuration);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(
                dp(270), dp(270));
        timerParams.gravity = Gravity.CENTER;
        timerParams.topMargin = dp(12);
        timerParams.bottomMargin = dp(12);
        center.addView(circleTimer, timerParams);

        instructionText = makeText(stage.description, 15, MUTED);
        instructionText.setMaxLines(3);
        center.addView(instructionText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(4), dp(14), dp(12));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        playPauseButton = new Button(this);
        playPauseButton.setText("▶ شروع");
        playPauseButton.setTextColor(WHITE);
        playPauseButton.setTextSize(16);
        playPauseButton.setAllCaps(false);
        playPauseButton.setBackground(gradient(16));
        controls.addView(playPauseButton, new LinearLayout.LayoutParams(-1, -2));
        playPauseButton.setOnClickListener(v -> {
            if (running) pauseStage(); else startStage();
        });

        Button homeButton = new Button(this);
        homeButton.setText("بازگشت به خانه");
        homeButton.setTextColor(WHITE);
        homeButton.setAllCaps(false);
        homeButton.setBackground(shape(CARD, 16));
        controls.addView(homeButton, new LinearLayout.LayoutParams(-1, -2));
        homeButton.setOnClickListener(v -> {
            pauseStage();
            showHome();
        });

        setContentView(root);
        updateStageText();
        updateTimer();
        if (stage.mode == 0 && stage.title.contains("آماده")) {
            if (instructionText != null) instructionText.setText("آماده باشید؛ در وضعیت راحت دراز بکشید یا بنشینید و ریلکس کنید.");
        }
        speak(stage.title + ". " + stage.description);
    }

    private void updateStageText() {
        if (currentStage >= stages.size()) return;

        TrainingStage stage = stages.get(currentStage);

        if (stage.mode == 1) {
            if (phaseIsContraction) {
                if (circleTimer != null) circleTimer.setContraction(true);
                if (instructionText != null) instructionText.setText("انقباض ملایم · " + repetition + " از " + stage.repetitions);
            } else {
                if (circleTimer != null) circleTimer.setContraction(false);
                if (instructionText != null) instructionText.setText("رها کنید و عضلات را کاملاً آزاد بگذارید.");
            }
        } else {
            if (circleTimer != null) circleTimer.setContraction(false);
            if (instructionText != null) instructionText.setText(stage.description);
        }
    }

    private void showWaitingForPermission() {
        prepareStage();
        Toast.makeText(this,
                "مرحله قبلی تمام شد. برای شروع مرحله بعد دکمه شروع را بزنید.",
                Toast.LENGTH_LONG).show();
    }

    private void startStage() {
        if (sessionFinished || currentStage >= stages.size()) return;

        running = true;
        if (playPauseButton != null) playPauseButton.setText("⏸ مکث");
        TrainingStage stage = stages.get(currentStage);

        if (stage.mode == 1) {
            speakCue(phaseIsContraction);
            vibrate(phaseIsContraction ? 1 : 2);
        } else {
            speak(stage.title);
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
        if (playPauseButton != null) playPauseButton.setText("▶ ادامه");
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
                .putBoolean(sessionKey(selectedWeek, selectedDay, selectedSession), true)
                .apply();
        if (isDayDone(selectedWeek, selectedDay)) {
            prefs.edit().putBoolean(dayKey(selectedWeek, selectedDay), true).apply();
        }

        setup("آفرین! جلسه کامل شد");

        LinearLayout c = makeCard();
        addText(c, "🏆 تبریک می‌گوییم!", 24, PINK);
        addText(c, "نوبت " + ("morning".equals(selectedSession) ? "صبح"
                : ("noon".equals(selectedSession) ? "ظهر" : "شب"))
                + " با موفقیت انجام شد.", 16, WHITE);
        addText(c, isDayDone(selectedWeek, selectedDay)
                ? "همه نوبت‌های فعال این روز تکمیل شد."
                : "برای تکمیل روز، نوبت‌های فعال دیگر را هم انجام دهید.", 14, MUTED);
        addText(c, "پیشرفت دوره " + courseNumber + ": " + getTotalCompleted() + " از ۸۴ روز",
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
            canvas.drawArc(cx - radius, cy - radius,
                    cx + radius, cy + radius,
                    -90, sweep, false, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(dp(48));
            canvas.drawText(String.valueOf(value), cx, cy + dp(12), p);


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
        addText(c, "نوبت‌های فعال: " + enabledSessionCount() + " نوبت در روز", 14, MUTED);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(total);
        c.addView(bar);

        LinearLayout mosaic = makeCard();
        addText(mosaic, "نقشه تصویری پیشرفت هفته‌ها", 17, WHITE);
        for (int w = 1; w <= 12; w++) {
            LinearLayout weekRow = new LinearLayout(this);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setGravity(Gravity.CENTER_VERTICAL);
            addText(weekRow, "هفته " + w, 12, MUTED);
            for (int d = 1; d <= 7; d++) {
                TextView tile = makeText(isDayDone(w, d) ? "★" : "☆", 18,
                        isDayDone(w, d) ? GREEN : MUTED);
                weekRow.addView(tile, new LinearLayout.LayoutParams(0, dp(30), 1));
            }
            mosaic.addView(weekRow);
        }

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
        setup("آموزش و نکات ایمنی");
        LinearLayout guide = makeCard();
        addText(guide, "روش صحیح تمرین", 21, PINK);
        addText(guide,
                "۱. در وضعیت راحت قرار بگیرید.\n" +
                "۲. عضلات کف لگن را به‌آرامی منقبض کنید.\n" +
                "۳. نفس خود را حبس نکنید.\n" +
                "۴. پس از هر انقباض، عضلات را کاملاً رها کنید.\n" +
                "۵. تمرین را هنگام ادرار کردن انجام ندهید.", 15, WHITE);
        LinearLayout safety = makeCard();
        addText(safety, "نکات ایمنی", 21, PINK);
        addText(safety,
                "• تمرین نباید باعث درد شود.\n" +
                "• از فشار آوردن و انقباض بیش از حد خودداری کنید.\n" +
                "• بین انقباض‌ها رهاسازی کامل داشته باشید.\n" +
                "• در صورت درد یا گرفتگی تمرین را متوقف کنید.\n" +
                "• در صورت درد لگن یا مشکل ادراری با پزشک یا فیزیوتراپیست کف لگن مشورت کنید.",
                15, WHITE);
        addButton("بازگشت", () -> showHome());
    }

    private void showSafety() { showGuide(); }

    private void addReminderControl(LinearLayout card, String label, String session, String defaultTime) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(WHITE);
        boolean enabled = prefs.getBoolean("reminder_" + session, false);
        sw.setChecked(enabled);
        row.addView(sw, new LinearLayout.LayoutParams(0, -2, 1));
        String saved = prefs.getString("reminder_time_" + session, defaultTime);
        Button time = new Button(this);
        time.setText(saved);
        time.setAllCaps(false);
        time.setTextColor(WHITE);
        time.setBackground(shape(PURPLE, 12));
        row.addView(time, new LinearLayout.LayoutParams(dp(105), -2));
        card.addView(row);
        time.setOnClickListener(v -> {
            String[] parts = prefs.getString("reminder_time_" + session, defaultTime).split(":");
            int h = 8, m = 0;
            try { h = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]); } catch (Exception ignored) { }
            new TimePickerDialog(this, (picker, hour, minute) -> {
                String value = String.format(Locale.US, "%02d:%02d", hour, minute);
                prefs.edit().putString("reminder_time_" + session, value).apply();
                time.setText(value);
                if (prefs.getBoolean("reminder_" + session, false))
                    scheduleReminder(this, gender, session, hour, minute);
            }, h, m, true).show();
        });
        sw.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("reminder_" + session, checked).apply();
            if (checked) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 702);
                }
                String[] parts = prefs.getString("reminder_time_" + session, defaultTime).split(":");
                try { scheduleReminder(this, gender, session, Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
                catch (Exception ignored) { }
            } else cancelReminder(this, gender, session);
        });
    }

    private void showSettings() {
        setup("تنظیمات صدا و ویبره");
        LinearLayout c = makeCard();
        addText(c, "یادآوری زمان تمرین", 18, PINK);
        addText(c, "برای هر نوبت ساعت جداگانه انتخاب کنید. اعلان روزانه در همان ساعت نمایش داده می‌شود.", 13, MUTED);
        addReminderControl(c, "یادآوری صبح", "morning", "08:00");
        addReminderControl(c, "یادآوری ظهر", "noon", "13:00");
        addReminderControl(c, "یادآوری شب", "night", "20:00");
        View reminderSeparator = new View(this);
        c.addView(reminderSeparator, new LinearLayout.LayoutParams(-1, dp(12)));
        addText(c, "انتخاب نوبت‌های روزانه", 18, PINK);
        addText(c, "می‌توانید فقط یک نوبت، دو نوبت یا هر سه نوبت را فعال کنید.", 13, MUTED);

        Switch morningSwitch = new Switch(this);
        morningSwitch.setText("تمرین صبح");
        morningSwitch.setTextColor(WHITE);
        morningSwitch.setChecked(morningEnabled);
        c.addView(morningSwitch);
        morningSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                morningSwitch.setChecked(true);
                Toast.makeText(this, "حداقل یک نوبت باید فعال باشد.", Toast.LENGTH_SHORT).show();
                return;
            }
            morningEnabled = checked;
            prefs.edit().putBoolean("session_morning", checked).apply();
        });

        Switch noonSwitch = new Switch(this);
        noonSwitch.setText("تمرین ظهر");
        noonSwitch.setTextColor(WHITE);
        noonSwitch.setChecked(noonEnabled);
        c.addView(noonSwitch);
        noonSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                noonSwitch.setChecked(true);
                Toast.makeText(this, "حداقل یک نوبت باید فعال باشد.", Toast.LENGTH_SHORT).show();
                return;
            }
            noonEnabled = checked;
            prefs.edit().putBoolean("session_noon", checked).apply();
        });

        Switch nightSwitch = new Switch(this);
        nightSwitch.setText("تمرین شب");
        nightSwitch.setTextColor(WHITE);
        nightSwitch.setChecked(nightEnabled);
        c.addView(nightSwitch);
        nightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!checked && enabledSessionCount() == 1) {
                nightSwitch.setChecked(true);
                Toast.makeText(this, "حداقل یک نوبت باید فعال باشد.", Toast.LENGTH_SHORT).show();
                return;
            }
            nightEnabled = checked;
            prefs.edit().putBoolean("session_night", checked).apply();
        });

        View sessionSeparator = new View(this);
        c.addView(sessionSeparator, new LinearLayout.LayoutParams(-1, dp(12)));
        addText(c, "صدا و لرزش", 18, PINK);

        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("فعال بودن صدای راهنما");
        soundSwitch.setTextColor(WHITE);
        soundSwitch.setChecked(soundEnabled);
        c.addView(soundSwitch);
        soundSwitch.setOnCheckedChangeListener((button, checked) -> {
            soundEnabled = checked;
            prefs.edit().putBoolean("sound", checked).apply();
            if (!checked) {
                pendingSpeech = "";
                if (tts != null) tts.stop();
            }
        });

        View separator = new View(this);
        c.addView(separator, new LinearLayout.LayoutParams(-1, dp(10)));

        Switch vibrationSwitch = new Switch(this);
        vibrationSwitch.setText("فعال بودن ویبره تمرین");
        vibrationSwitch.setTextColor(WHITE);
        vibrationSwitch.setChecked(vibrationEnabled);
        c.addView(vibrationSwitch);
        vibrationSwitch.setOnCheckedChangeListener((button, checked) -> {
            vibrationEnabled = checked;
            prefs.edit().putBoolean("vibration", checked).apply();
            if (checked) vibrate(1);
            else if (vibrator != null) vibrator.cancel();
        });

        addCardButton(c, "آزمایش صدای راهنما", () -> {
            speak("سلام. به برنامه کگل‌یار خوش آمدید.");
            if (!ttsReady) {
                Toast.makeText(this,
                        "صدای فارسی روی موتور گفتار گوشی در دسترس نیست.",
                        Toast.LENGTH_LONG).show();
            }
        });

        addCardButton(c, "آزمایش ویبره", () -> vibrate(3));

        addText(c, "صدای زنانه به صداهای فارسی نصب‌شده روی گوشی وابسته است.",
                13, MUTED);
        addText(c, "برای دریافت لرزش، ویبره گوشی نیز باید از تنظیمات دستگاه روشن باشد.",
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
