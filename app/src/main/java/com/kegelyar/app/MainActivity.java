
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {

    final int cream = Color.rgb(249,245,238);
    final int cardColor = Color.WHITE;
    final int plum = Color.rgb(91,48,72);
    final int rose = Color.rgb(207,157,157);
    final int textColor = Color.rgb(65,53,57);
    final int muted = Color.rgb(135,119,119);
    final int darkBg = Color.rgb(55,36,49);
    final int white = Color.WHITE;

    LinearLayout root;
    Handler handler = new Handler();

    TextView timerText, phaseText, roundText;
    Button startButton;

    boolean running = false;
    boolean contraction = true;
    boolean audioEnabled = false;
    boolean voiceReady = false;

    int seconds = 10;
    int round = 1;
    int totalRounds = 5;
    int selectedWeek = 1;

    String gender = "آقایان";

    TextToSpeech tts;

    final String[] exerciseNames = {
        "تنفس دیافراگمی",
        "چرخش لگن در حالت خوابیده",
        "پل باسن",
        "دست و پای مخالف",
        "اسکوات روی صندلی",
        "حرکت گربه و گاو",
        "حالت کودک",
        "زانو به سینه",
        "خم شدن از مفصل ران",
        "کشش پروانه‌ای"
    };

    final String[] exerciseDescriptions = {
        "به پشت بخوابید. یک دست را روی شکم قرار دهید. آرام از بینی نفس بکشید و با بازدم شکم را رها کنید.",
        "به پشت بخوابید و زانوها را خم کنید. لگن را به‌آرامی به جلو و عقب بچرخانید.",
        "به پشت بخوابید و زانوها را خم کنید. لگن را کمی بالا بیاورید و آرام پایین ببرید.",
        "در حالت چهار دست‌وپا، دست و پای مخالف را آرام بکشید و سپس سمت دیگر را انجام دهید.",
        "روی صندلی محکم بنشینید. با کنترل بلند شوید و دوباره آرام بنشینید.",
        "در حالت چهار دست‌وپا، پشت را آرام گرد کنید و سپس به حالت طبیعی برگردانید.",
        "روی زانوها قرار بگیرید و بدن را به‌آرامی به جلو و پایین ببرید.",
        "به پشت بخوابید و یک زانو را آرام به سمت سینه بیاورید. سپس پا را عوض کنید.",
        "پاها را به اندازه عرض لگن باز کنید. از مفصل ران کمی به جلو خم شوید و صاف بایستید.",
        "بنشینید و کف پاها را مقابل هم قرار دهید. زانوها را بدون فشار رها کنید."
    };

    final String[] weekTitles = {
        "آشنایی و شروع","آشنایی و شروع",
        "پایه‌سازی","پایه‌سازی",
        "تقویت و استقامت","تقویت و استقامت",
        "تقویت و استقامت","تقویت و استقامت",
        "تثبیت و ارزیابی","تثبیت و ارزیابی",
        "تثبیت و ارزیابی","تثبیت و ارزیابی"
    };

    final String[] weekDescriptions = {
        "آشنایی با عضلات کف لگن و یادگیری انقباض و رهاسازی.",
        "تمرین آرام و شناخت تفاوت انقباض و رهاسازی.",
        "ساختن عادت تمرین منظم با تمرکز بر تکنیک صحیح.",
        "تمرکز بر تنفس طبیعی و جلوگیری از انقباض عضلات اضافی.",
        "بهبود کنترل عضلات با تمرین متناسب با توان فرد.",
        "توجه به کیفیت انقباض و رهاسازی.",
        "تمرین منظم بدون فشار و خستگی بیش از حد.",
        "ادامه تمرین متناسب با شرایط بدن.",
        "بررسی راحتی و کیفیت اجرای تمرین.",
        "حفظ تکنیک صحیح و نظم تمرین.",
        "ارزیابی تغییرات و توجه به علائم بدن.",
        "جمع‌بندی دوره و تصمیم درباره ادامه تمرین."
    };

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            seconds--;

            if (seconds <= 0) {
                if (contraction) {
                    contraction = false;
                    seconds = 10;
                    speak("رهاسازی کامل");
                } else {
                    contraction = true;
                    seconds = 10;
                    round++;

                    if (round > totalRounds) {
                        finishTraining();
                        return;
                    }

                    speak("انقباض ملایم");
                }
            }

            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("fa"));
                voiceReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        showGender();
    }

    int dp(float value) {
        return (int)(value *
            getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        t.setPadding(dp(8),dp(8),dp(8),dp(8));
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    void addText(LinearLayout parent, String value,
                 int size, int color) {
        parent.addView(makeText(value,size,color));
    }

    LinearLayout makeCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15),dp(13),dp(15),dp(13));
        box.setBackground(shape(cardColor,20));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(7),0,dp(7));

        root.addView(box,p);
        return box;
    }

    void addButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(white);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(shape(plum,16));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(0,dp(6),0,dp(6));

        root.addView(b,p);
        b.setOnClickListener(v -> action.run());
    }

    void addCardButton(LinearLayout parent, String label,
                       Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(plum);
        b.setAllCaps(false);
        b.setBackground(shape(cream,13));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,dp(48));
        p.setMargins(0,dp(8),0,0);

        parent.addView(b,p);
        b.setOnClickListener(v -> action.run());
    }

    void setup(String title) {
        running = false;
        handler.removeCallbacks(timerRunnable);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(cream);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(20),dp(20),dp(25));
        root.setBackgroundColor(cream);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        scroll.addView(root);
        setContentView(scroll);

        TextView heading = makeText(title,25,plum);
        heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(heading);

        addText(root,"آرام، منظم و بدون فشار",13,muted);
    }

    void speak(String message) {
        if (audioEnabled && voiceReady && tts != null) {
            tts.speak(message,TextToSpeech.QUEUE_FLUSH,
                null,"kegelyar_voice");
        }
    }

    void showGender() {
        setup("کِگِل‌یار");

        LinearLayout card = makeCard();
        addText(card,"به کگل‌یار خوش آمدید 🌸",22,plum);
        addText(card,
            "برای شروع، برنامه متناسب با خود را انتخاب کنید.",
            15,textColor);

        addButton("برنامه آقایان",() -> {
            gender = "آقایان";
            showHome();
        });

        addButton("برنامه بانوان",() -> {
            gender = "بانوان";
            showHome();
        });

        LinearLayout note = makeCard();
        addText(note,
            "تمرین آگاهانه، آرام و منظم عضلات کف لگن",
            14,muted);
    }

    void showHome() {
        setup("کگل‌یار");

        LinearLayout welcome = makeCard();
        addText(welcome,"سلام، خوش آمدید 🌷",21,plum);
        addText(welcome,"برنامه انتخابی: "+gender,15,textColor);
        addText(welcome,"مسیر تمرینی ۱۲ هفته‌ای شما",14,muted);

        addButton("شروع تمرین",() -> showTraining());
        addButton("برنامه ۱۲ هفته‌ای",() -> showWeeks());
        addButton("ورزش‌های مکمل",() -> showExercises());
        addButton("آموزش کگل",() -> showGuide());
        addButton("نکات ایمنی",() -> showSafety());
        addButton("تنظیمات و راهنمای صوتی",() -> showSettings());
        addButton("تغییر گروه تمرینی",() -> showGender());
    }

    void showWeeks() {
        setup("برنامه ۱۲ هفته‌ای");

        LinearLayout intro = makeCard();
        addText(intro,
            "این برنامه یک مسیر پیشنهادی است؛ شدت تمرین باید با شرایط بدن هماهنگ شود.",
            14,textColor);

        for (int i=0;i<12;i++) {
            final int week = i+1;

            LinearLayout card = makeCard();
            addText(card,"هفته "+week+" | "+weekTitles[i],
                18,plum);
            addText(card,weekDescriptions[i],14,textColor);

            addCardButton(card,"مشاهده و شروع تمرین",
                () -> {
                    selectedWeek = week;
                    showTraining();
                });
        }

        addButton("بازگشت به خانه",() -> showHome());
    }

    void showExercises() {
        setup("ورزش‌های مکمل");

        LinearLayout intro = makeCard();
        addText(intro,"۱۰ حرکت مکمل کف لگن",20,plum);
        addText(intro,
            "این حرکات جایگزین تمرین کگل نیستند. هر حرکت را فقط در محدوده راحتی بدن انجام دهید.",
            14,textColor);

        for (int i=0;i<exerciseNames.length;i++) {
            final int index = i;

            LinearLayout card = makeCard();
            addText(card,(i+1)+" . "+exerciseNames[i],
                17,plum);
            addText(card,exerciseDescriptions[i],
                14,textColor);

            addCardButton(card,"مشاهده آموزش",
                () -> showExerciseDetail(index));
        }

        addButton("بازگشت",() -> showHome());
    }

    void showExerciseDetail(int index) {
        setup(exerciseNames[index]);

        LinearLayout card = makeCard();
        addText(card,exerciseNames[index],21,plum);
        addText(card,exerciseDescriptions[index],16,textColor);

        addText(card,
            "پیشنهاد: ابتدا حرکت را آرام و با تعداد کم انجام دهید. اگر درد یا فشار ایجاد شد، متوقف شوید.",
            14,muted);

        addButton("بازگشت به فهرست حرکات",
            () -> showExercises());
    }

    void showGuide() {
        setup("آموزش کگل");

        LinearLayout card = makeCard();

        addText(card,"روش صحیح تمرین",20,plum);

        addText(card,
            "۱. عضلات کف لگن را به‌آرامی منقبض کنید.\n\n"+
            "۲. نفس خود را حبس نکنید.\n\n"+
            "۳. شکم، ران و باسن را تا حد امکان آزاد نگه دارید.\n\n"+
            "۴. سپس عضلات کف لگن را کاملاً رها کنید.\n\n"+
            "۵. کیفیت حرکت از تعداد تکرار مهم‌تر است.",
            16,textColor);

        addButton("بازگشت",() -> showHome());
    }

    void showSafety() {
        setup("نکات ایمنی");

        LinearLayout card = makeCard();

        addText(card,"سلامت شما اولویت دارد",20,plum);

        addText(card,
            "• تمرین نباید دردناک باشد.\n\n"+
            "• هنگام ادرار کردن تمرین کگل انجام ندهید.\n\n"+
            "• رهاسازی کامل به اندازه انقباض اهمیت دارد.\n\n"+
            "• اگر درد، گرفتگی یا احساس فشار دارید، تمرین را متوقف کنید.\n\n"+
            "• برخی افراد به‌جای تقویت، به آرام‌سازی عضلات کف لگن نیاز دارند.\n\n"+
            "• در صورت تداوم علائم با پزشک یا فیزیوتراپیست کف لگن مشورت کنید.",
            15,textColor);

        addButton("بازگشت",() -> showHome());
    }

    void showSettings() {
        setup("تنظیمات");

        LinearLayout card = makeCard();

        addText(card,"راهنمای صوتی فارسی",19,plum);

        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("فعال‌سازی راهنمای صوتی");
        soundSwitch.setTextColor(textColor);
        soundSwitch.setTextSize(15);
        soundSwitch.setChecked(audioEnabled);

        card.addView(soundSwitch);

        soundSwitch.setOnCheckedChangeListener((button,checked) -> {
            audioEnabled = checked;

            if (checked) {
                if (voiceReady) {
                    speak("راهنمای صوتی فعال شد");
                } else {
                    Toast.makeText(this,
                        "صدای فارسی روی موتور گفتار گوشی در دسترس نیست.",
                        Toast.LENGTH_LONG).show();
                }
            } else if (tts != null) {
                tts.stop();
            }
        });

        addText(card,
            "نوع صدای فارسی به امکانات گفتار نصب‌شده روی گوشی وابسته است.",
            13,muted);

        addButton("بازگشت",() -> showHome());
    }

    void showTraining() {
        running = false;
        handler.removeCallbacks(timerRunnable);

        contraction = true;
        seconds = 10;
        round = 1;
        totalRounds = 5;

        setup("جلسه تمرین");

        root.setBackgroundColor(darkBg);

        TextView heading = makeText("کگل‌یار | تمرین",23,white);
        root.addView(heading);

        phaseText = makeText("آماده شروع",21,rose);
        timerText = makeText("۱۰",65,white);
        roundText = makeText("دور ۱ از ۵",16,white);

        root.addView(phaseText);
        root.addView(timerText);
        root.addView(roundText);

        addText(root,"هر مرحله ۱۰ ثانیه",14,rose);

        startButton = new Button(this);
        startButton.setText("شروع تمرین");
        startButton.setTextColor(white);
        startButton.setTextSize(16);
        startButton.setAllCaps(false);
        startButton.setBackground(shape(plumLight,16));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,dp(55));
        p.setMargins(0,dp(15),0,dp(10));

        root.addView(startButton,p);

        startButton.setOnClickListener(v -> {
            if (!running) {
                if (round > totalRounds) {
                    showHome();
                    return;
                }

                running = true;
                phaseText.setText(
                    contraction ? "انقباض ملایم" : "رهاسازی کامل");
                startButton.setText("توقف");
                speak(contraction ?
                    "انقباض ملایم" : "رهاسازی کامل");
                handler.postDelayed(timerRunnable,1000);

            } else {
                running = false;
                handler.removeCallbacks(timerRunnable);
                startButton.setText("ادامه");
                phaseText.setText("تمرین متوقف شد");
                if (tts != null) tts.stop();
            }
        });

        Button back = new Button(this);
        back.setText("پایان و بازگشت");
        back.setTextColor(plum);
        back.setAllCaps(false);
        back.setBackground(shape(cream,16));
        root.addView(back,new LinearLayout.LayoutParams(-1,dp(52)));

        back.setOnClickListener(v -> {
            running = false;
            handler.removeCallbacks(timerRunnable);
            showHome();
        });
    }

    void updateTimer() {
        timerText.setText(String.valueOf(seconds));
        roundText.setText("دور "+round+" از "+totalRounds);
        phaseText.setText(
            contraction ? "انقباض ملایم" : "رهاسازی کامل");
    }

    void finishTraining() {
        running = false;
        handler.removeCallbacks(timerRunnable);

        if (tts != null) tts.stop();

        phaseText.setText("تمرین تمام شد 🌷");
        timerText.setText("آفرین!");
        roundText.setText("۵ دور تمرین به پایان رسید");

        startButton.setText("بازگشت به خانه");
        startButton.setOnClickListener(v -> showHome());
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(timerRunnable);

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
