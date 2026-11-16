
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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

    private final int cream = Color.rgb(250,247,244);
    private final int plum = Color.rgb(105,72,125);
    private final int rose = Color.rgb(220,145,166);
    private final int green = Color.rgb(65,145,105);
    private final int textColor = Color.rgb(65,55,70);
    private final int muted = Color.rgb(135,125,140);
    private final int white = Color.WHITE;

    private LinearLayout root;
    private LinearLayout content;
    private TextView timerText;
    private TextView instructionText;
    private TextView stepText;

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean soundEnabled = true;
    private boolean trainingRunning = false;

    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int secondsLeft = 0;
    private int currentStep = 0;

    private final Handler handler = new Handler();
    private ArrayList<TrainingStep> trainingSteps =
            new ArrayList<>();

    private class TrainingStep {
        String title;
        String description;
        int seconds;

        TrainingStep(String t, String d, int s) {
            title = t;
            description = d;
            seconds = s;
        }
    }

    private final String[] weekTitles = {
        "شروع آرام",
        "آشنایی با عضلات",
        "کنترل بهتر",
        "افزایش استقامت",
        "تقویت تدریجی",
        "کنترل و هماهنگی",
        "تمرین پیشرفته",
        "استقامت بیشتر",
        "هماهنگی عضلات",
        "کنترل پایدار",
        "تثبیت توانایی",
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
        "تنفس آرام",
        "انقباض ملایم",
        "انقباض و رهاسازی",
        "تمرین سریع",
        "تمرین استقامتی",
        "آرام‌سازی"
    };

    private final String[] exerciseDescriptions = {
        "آرام نفس بکشید و بدن را رها کنید.",
        "عضلات کف لگن را به‌آرامی منقبض کنید.",
        "پس از هر انقباض، عضلات را کاملاً رها کنید.",
        "انقباض کوتاه و رهاسازی کامل را تمرین کنید.",
        "انقباض کنترل‌شده را برای مدت کوتاه نگه دارید.",
        "در پایان، عضلات را کاملاً آزاد کنید."
    };

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!trainingRunning) return;

            secondsLeft--;

            if (secondsLeft <= 0) {
                currentStep++;
                if (currentStep >= trainingSteps.size()) {
                    finishTraining();
                    return;
                }
                loadCurrentStep();
            } else {
                updateTimer();
                handler.postDelayed(this,1000);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("KegelYarData",
                MODE_PRIVATE);

        soundEnabled = prefs.getBoolean("sound",true);
        gender = prefs.getString("gender","");

        initTTS();

        if (gender.isEmpty()) {
            showGender();
        } else {
            showHome();
        }
    }

    private String gender = "";

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale fa = Locale.forLanguageTag("fa-IR");
                int result = tts.setLanguage(fa);

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
                                if (name.contains("fa")
                                        && (name.contains("female")
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

    private int dp(float value) {
        return (int)(value * getResources()
                .getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView makeText(String value,int size,int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8),dp(7),dp(8),dp(7));
        t.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);
        return t;
    }

    private void setup(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cream);

        TextView header = makeText(title,22,white);
        header.setTypeface(null,Typeface.BOLD);
        header.setBackground(shape(plum,0));
        header.setPadding(dp(10),dp(17),dp(10),dp(17));
        root.addView(header,new LinearLayout.LayoutParams(
                -1,-2));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15),dp(12),dp(15),dp(20));

        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(
                -1,0,1));

        setContentView(root);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13),dp(12),dp(13),dp(12));
        card.setBackground(shape(white,18));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(8));
        content.addView(card,lp);
        return card;
    }

    private void addText(LinearLayout parent,
                         String value,int size,int color) {
        TextView t = makeText(value,size,color);
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        parent.addView(t,new LinearLayout.LayoutParams(
                -1,-2));
    }

    private void addButton(String title,Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(white);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackground(shape(plum,15));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(6));
        content.addView(b,lp);

        b.setOnClickListener(v -> action.run());
    }

    private void addCardButton(LinearLayout parent,
                               String title,Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(white);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(shape(rose,13));
        parent.addView(b,new LinearLayout.LayoutParams(
                -1,-2));
        b.setOnClickListener(v -> action.run());
    }

    private void speak(String message) {
        if (soundEnabled && ttsReady && tts != null) {
            tts.speak(message,TextToSpeech.QUEUE_FLUSH,
                    null,"kegelyar_voice");
        }
    }

    private String dayKey(int week,int day) {
        return "week_"+week+"_day_"+day;
    }

    private boolean isDayDone(int week,int day) {
        return prefs.getBoolean(dayKey(week,day),false);
    }

    private int getWeekCompleted(int week) {
        int count = 0;
        for (int d=1;d<=7;d++) {
            if (isDayDone(week,d)) count++;
        }
        return count;
    }

    private boolean isWeekUnlocked(int week) {
        if (week == 1) return true;
        return getWeekCompleted(week-1) == 7;
    }

    private int getTotalCompleted() {
        int total = 0;
        for (int w=1;w<=12;w++) {
            total += getWeekCompleted(w);
        }
        return total;
    }

    private int getFirstAvailableWeek() {
        for (int w=1;w<=12;w++) {
            if (isWeekUnlocked(w)
                    && getWeekCompleted(w)<7) {
                return w;
            }
        }
        return 12;
    }

    private int getFirstIncompleteDay(int week) {
        for (int d=1;d<=7;d++) {
            if (!isDayDone(week,d)) return d;
        }
        return 7;
    }

    private void showGender() {
        setup("کِگِل‌یار");

        LinearLayout card = makeCard();
        addText(card,"به کگل‌یار خوش آمدید 🌸",22,plum);
        addText(card,
                "برنامه تمرینی خود را انتخاب کنید.",
                15,textColor);

        addButton("برنامه آقایان",() -> {
            gender = "آقایان";
            prefs.edit().putString("gender",gender).apply();
            showHome();
        });

        addButton("برنامه بانوان",() -> {
            gender = "بانوان";
            prefs.edit().putString("gender",gender).apply();
            showHome();
        });
    }

    private void showHome() {
        setup("کِگِل‌یار");

        LinearLayout card = makeCard();
        addText(card,"سلام، خوش آمدید 🌷",22,plum);
        addText(card,"برنامه انتخابی: "+gender,15,textColor);
        addText(card,"مسیر تمرینی ۱۲ هفته‌ای شما",15,textColor);
        addText(card,
                "پیشرفت: "+getTotalCompleted()+" از ۸۴ جلسه",
                18,green);

        addButton("❤️ شروع تمرین روزانه",() -> {
            if (getTotalCompleted() >= 84) {
                showProgress();
                return;
            }
            selectedWeek = getFirstAvailableWeek();
            selectedDay = getFirstIncompleteDay(selectedWeek);
            showTraining();
        });

        addButton("📅 برنامه ۱۲ هفته‌ای",() -> showWeeks());
        addButton("🏆 پیشرفت من",() -> showProgress());
        addButton("🏃 ورزش‌های مکمل",() -> showExercises());
        addButton("📖 آموزش کگل",() -> showGuide());
        addButton("🛡 نکات ایمنی",() -> showSafety());
        addButton("🔊 تنظیمات صدا",() -> showSettings());
        addButton("تغییر گروه تمرینی",() -> showGender());
    }

    private void showWeeks() {
        setup("📅 برنامه ۱۲ هفته‌ای");

        LinearLayout intro = makeCard();
        addText(intro,
                "هر هفته ۷ جلسه دارد. هفته بعد فقط پس از تکمیل ۷ روز هفته قبل باز می‌شود.",
                14,textColor);

        for (int i=0;i<12;i++) {
            final int week = i+1;
            boolean unlocked = isWeekUnlocked(week);
            int done = getWeekCompleted(week);

            LinearLayout card = makeCard();
            addText(card,
                    "هفته "+week+" | "+weekTitles[i],
                    18,plum);
            addText(card,weekDescriptions[i],14,textColor);
            addText(card,
                    "پیشرفت: "+done+" از ۷ روز",
                    15,green);
            addText(card,
                    unlocked ? "🔓 باز" : "🔒 قفل",
                    14,unlocked ? green : muted);

            if (unlocked) {
                addCardButton(card,"مشاهده روزهای هفته",() -> {
                    selectedWeek = week;
                    showDays();
                });
            }
        }

        addButton("بازگشت به خانه",() -> showHome());
    }

    private void showDays() {
        if (!isWeekUnlocked(selectedWeek)) {
            Toast.makeText(this,
                    "ابتدا هفته قبل را کامل کنید.",
                    Toast.LENGTH_LONG).show();
            showWeeks();
            return;
        }

        setup("هفته "+selectedWeek);

        LinearLayout card = makeCard();
        addText(card,weekTitles[selectedWeek-1],20,plum);
        addText(card,
                "جلسه‌های انجام‌شده: "
                        +getWeekCompleted(selectedWeek)+" از ۷",
                15,green);

        for (int d=1;d<=7;d++) {
            final int day = d;
            boolean done = isDayDone(selectedWeek,d);

            LinearLayout dayCard = makeCard();
            addText(dayCard,
                    "روز "+d+(done ? "   ✅" : "   ○"),
                    18,plum);
            addText(dayCard,
                    done ? "این جلسه تکمیل شده است."
                            : (d==7
                            ? "جلسه سبک بازیابی"
                            : "جلسه تمرینی روزانه"),
                    14,textColor);

            if (!done) {
                addCardButton(dayCard,"شروع این جلسه",() -> {
                    selectedDay = day;
                    showTraining();
                });
            }
        }

        addButton("بازگشت به هفته‌ها",() -> showWeeks());
        addButton("خانه",() -> showHome());
    }

    private void showTraining() {
        trainingRunning = false;
        handler.removeCallbacks(timerRunnable);
        buildTrainingSteps();

        currentStep = 0;

        setup("تمرین روز "+selectedDay);

        LinearLayout card = makeCard();
        addText(card,
                "هفته "+selectedWeek+" - روز "+selectedDay,
                18,plum);
        addText(card,
                "آرام باشید و بدون فشار تمرین کنید.",
                14,textColor);

        stepText = makeText("",18,plum);
        stepText.setTypeface(null,Typeface.BOLD);
        card.addView(stepText);

        instructionText = makeText("",15,textColor);
        card.addView(instructionText);

        timerText = makeText("",36,plum);
        timerText.setTypeface(null,Typeface.BOLD);
        card.addView(timerText);

        addButton("▶ شروع تمرین",() -> startTraining());
        addButton("⏸ توقف تمرین",() -> pauseTraining());
        addButton("ادامه تمرین",() -> resumeTraining());
        addButton("مرحله بعد",() -> nextStep());
        addButton("بازگشت به خانه",() -> {
            pauseTraining();
            showHome();
        });

        loadCurrentStep();
    }

    private void buildTrainingSteps() {
        trainingSteps.clear();

        if (selectedDay == 7) {
            trainingSteps.add(new TrainingStep(
                    "تنفس و آرام‌سازی",
                    "به‌آرامی نفس بکشید و عضلات کف لگن را رها کنید.",
                    90));
            trainingSteps.add(new TrainingStep(
                    "رهاسازی عضلات",
                    "بدون انقباض، عضلات را کاملاً آزاد نگه دارید.",
                    60));
            trainingSteps.add(new TrainingStep(
                    "پایان جلسه بازیابی",
                    "چند نفس آرام بکشید و به بدن استراحت دهید.",
                    60));
            return;
        }

        trainingSteps.add(new TrainingStep(
                "آماده‌سازی و تنفس",
                "در وضعیت راحت قرار بگیرید و آرام نفس بکشید.",
                60));

        int hold = Math.min(8,3+(selectedWeek-1)/2);
        int rest = hold + 2;
        int repetitions = selectedWeek < 4 ? 4 : 5;

        for (int i=1;i<=repetitions;i++) {
            trainingSteps.add(new TrainingStep(
                    "انقباض ملایم "+i,
                    "عضلات کف لگن را ملایم منقبض کنید. نفس را حبس نکنید.",
                    hold));
            trainingSteps.add(new TrainingStep(
                    "رهاسازی کامل "+i,
                    "انقباض را کاملاً رها کنید و استراحت کنید.",
                    rest));
        }

        int quickCount = selectedWeek < 4 ? 3 : 5;

        for (int i=1;i<=quickCount;i++) {
            trainingSteps.add(new TrainingStep(
                    "انقباض کوتاه "+i,
                    "یک انقباض کوتاه و ملایم انجام دهید.",
                    2));
            trainingSteps.add(new TrainingStep(
                    "رهاسازی کوتاه "+i,
                    "عضلات را کاملاً رها کنید.",
                    4));
        }

        trainingSteps.add(new TrainingStep(
                "آرام‌سازی پایانی",
                "عضلات کف لگن را کاملاً آزاد کنید و چند نفس آرام بکشید.",
                60));
    }

    private void loadCurrentStep() {
        if (currentStep >= trainingSteps.size()) {
            finishTraining();
            return;
        }

        TrainingStep step = trainingSteps.get(currentStep);
        secondsLeft = step.seconds;

        if (stepText != null) {
            stepText.setText(
                    "مرحله "+(currentStep+1)+" از "
                            +trainingSteps.size()+"\n"+step.title);
        }

        if (instructionText != null) {
            instructionText.setText(step.description);
        }

        updateTimer();
        speak(step.title+". "+step.description);
    }

    private void updateTimer() {
        if (timerText != null) {
            timerText.setText(secondsLeft+" ثانیه");
        }
    }

    private void startTraining() {
        if (trainingRunning) return;
        trainingRunning = true;
        speak("تمرین را شروع می‌کنیم. آرام و بدون فشار.");
        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable,1000);
    }

    private void pauseTraining() {
        trainingRunning = false;
        handler.removeCallbacks(timerRunnable);
        if (tts != null) tts.stop();
    }

    private void resumeTraining() {
        if (currentStep >= trainingSteps.size()) return;
        startTraining();
    }

    private void nextStep() {
        pauseTraining();
        currentStep++;

        if (currentStep >= trainingSteps.size()) {
            finishTraining();
        } else {
            loadCurrentStep();
        }
    }

    private void finishTraining() {
        pauseTraining();

        prefs.edit()
                .putBoolean(dayKey(selectedWeek,selectedDay),true)
                .apply();

        setup("آفرین! جلسه کامل شد 🌷");

        LinearLayout card = makeCard();
        addText(card,"تبریک می‌گوییم ❤️",24,plum);
        addText(card,
                "جلسه روز "+selectedDay+" از هفته "
                        +selectedWeek+" با موفقیت ثبت شد.",
                16,textColor);
        addText(card,
                "پیشرفت کل: "+getTotalCompleted()+" از ۸۴ جلسه",
                18,green);

        if (getWeekCompleted(selectedWeek)==7
                && selectedWeek<12) {
            addText(card,
                    "هفته کامل شد! هفته بعد باز شد. 🎉",
                    16,green);
        }

        addButton("مشاهده روزهای هفته",() -> showDays());
        addButton("بازگشت به خانه",() -> showHome());
        addButton("پیشرفت من",() -> showProgress());
    }

    private void showProgress() {
        setup("🏆 پیشرفت من");

        int total = getTotalCompleted();
        int percent = (int)(total*100.0/84.0);

        LinearLayout card = makeCard();
        addText(card,"مسیر پیشرفت شما",22,plum);
        addText(card,total+" از ۸۴ جلسه تکمیل شده",18,green);
        addText(card,"درصد پیشرفت: "+percent+"٪",20,plum);

        ProgressBar bar = new ProgressBar(this,
                null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(total);
        card.addView(bar,new LinearLayout.LayoutParams(-1,dp(18)));

        for (int w=1;w<=12;w++) {
            addText(card,
                    "هفته "+w+": "+getWeekCompleted(w)+" از ۷ روز",
                    14,textColor);
        }

        addButton("بازگشت به خانه",() -> showHome());
    }

    private void showExercises() {
        setup("🏃 ورزش‌های مکمل");

        for (int i=0;i<exerciseNames.length;i++) {
            LinearLayout card = makeCard();
            addText(card,exerciseNames[i],18,plum);
            addText(card,exerciseDescriptions[i],14,textColor);
        }

        addText(content,
                "این حرکات مکمل اختیاری هستند. در صورت درد یا ناراحتی انجامشان ندهید.",
                13,muted);

        addButton("بازگشت",() -> showHome());
    }

    private void showGuide() {
        setup("📖 آموزش کگل");

        LinearLayout card = makeCard();
        addText(card,"کگل چیست؟",20,plum);
        addText(card,
                "تمرین کگل شامل انقباض و رهاسازی آگاهانه عضلات کف لگن است.",
                15,textColor);

        addText(card,"روش صحیح تمرین",19,plum);
        addText(card,
                "۱. در وضعیت راحت قرار بگیرید.\n"
                +"۲. عضلات کف لگن را به‌آرامی منقبض کنید.\n"
                +"۳. نفس خود را حبس نکنید.\n"
                +"۴. سپس عضلات را کاملاً رها کنید.\n"
                +"۵. بین انقباض‌ها استراحت کافی داشته باشید.",
                15,textColor);

        addText(card,"نکته مهم",18,plum);
        addText(card,
                "برای پیدا کردن عضلات می‌توان جریان ادرار را فقط یک‌بار به‌صورت آزمایشی شناخت؛ اما تمرین کگل را هنگام ادرار کردن انجام ندهید.",
                14,textColor);

        addButton("بازگشت",() -> showHome());
    }

    private void showSafety() {
        setup("🛡 نکات ایمنی");

        LinearLayout card = makeCard();
        addText(card,"سلامت شما مهم‌تر از تعداد تمرین‌هاست.",19,plum);

        addText(card,
                "• تمرین نباید باعث درد شود.\n"
                +"• از انقباض شدید و فشار آوردن خودداری کنید.\n"
                +"• نفس خود را حبس نکنید.\n"
                +"• بین انقباض‌ها عضلات را کاملاً رها کنید.\n"
                +"• کگل را هنگام ادرار کردن انجام ندهید.\n"
                +"• در صورت درد، گرفتگی یا فشار، تمرین را متوقف کنید.\n"
                +"• اگر درد لگن یا مشکل ادراری دارید، پیش از ادامه با پزشک یا فیزیوتراپیست کف لگن مشورت کنید.",
                15,textColor);

        addText(card,
                "توجه: بعضی افراد به آرام‌سازی کف لگن نیاز دارند، نه تقویت بیشتر.",
                15,plum);

        addButton("بازگشت",() -> showHome());
    }

    private void showSettings() {
        setup("🔊 تنظیمات");

        LinearLayout card = makeCard();
        addText(card,"راهنمای صوتی فارسی",19,plum);

        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("فعال بودن صدای راهنما");
        soundSwitch.setTextColor(textColor);
        soundSwitch.setChecked(soundEnabled);
        card.addView(soundSwitch);

        soundSwitch.setOnCheckedChangeListener((button,checked) -> {
            soundEnabled = checked;
            prefs.edit().putBoolean("sound",checked).apply();

            if (!checked && tts != null) {
                tts.stop();
            }
        });

        addCardButton(card,"آزمایش صدای راهنما",() -> {
            speak("سلام. به برنامه کگل‌یار خوش آمدید. آرام و با حوصله تمرین کنید.");
            if (!ttsReady) {
                Toast.makeText(this,
                        "صدای فارسی در موتور گفتار گوشی در دسترس نیست.",
                        Toast.LENGTH_LONG).show();
            }
        });

        addText(card,
                "نوع صدای زنانه به صداهای فارسی نصب‌شده روی گوشی وابسته است.",
                13,muted);

        addButton("بازگشت",() -> showHome());
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseTraining();
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
        pauseTraining();
        showHome();
    }
}
