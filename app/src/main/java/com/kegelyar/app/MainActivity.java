
package com.kegelyar.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(243,239,251);
    private final int CARD = Color.WHITE;
    private final int PURPLE = Color.rgb(125,82,190);
    private final int DARK = Color.rgb(78,53,119);
    private final int PINK = Color.rgb(231,143,165);
    private final int TEAL = Color.rgb(114,199,190);
    private final int GREEN = Color.rgb(75,158,128);
    private final int TEXT = Color.rgb(57,46,75);
    private final int MUTED = Color.rgb(135,125,151);
    private final int LINE = Color.rgb(226,218,239);

    private SharedPreferences prefs;
    private LinearLayout root, content;
    private TextToSpeech tts;
    private Vibrator vibrator;
    private boolean ttsReady = false;

    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;
    private boolean morningEnabled = true;
    private boolean noonEnabled = false;
    private boolean nightEnabled = false;

    private String gender = "";
    private String selectedSession = "morning";

    private int selectedWeek = 1;
    private int selectedDay = 1;
    private int currentStage = 0;
    private int secondsLeft = 0;
    private int repetition = 1;
    private int stageDuration = 1;

    private boolean running = false;
    private boolean paused = false;
    private boolean finished = false;
    private boolean contraction = true;

    private final Handler handler = new Handler();
    private final ArrayList<Stage> stages = new ArrayList<>();
    private CircleTimer timerView;
    private TextView phaseLabel, stageLabel, instructionLabel;

    private static class Stage {
        String title;
        String description;
        int mode;
        int duration;
        int reps;
        int hold;
        int release;

        Stage(String title, String description, int duration) {
            this.title = title;
            this.description = description;
            this.duration = duration;
            this.mode = 0;
            this.reps = 1;
            this.hold = duration;
            this.release = 0;
        }

        Stage(String title, String description,
              int reps, int hold, int release) {
            this.title = title;
            this.description = description;
            this.mode = 1;
            this.duration = hold;
            this.reps = reps;
            this.hold = hold;
            this.release = release;
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || finished) return;

            secondsLeft--;

            if (secondsLeft <= 0) {
                Stage s = stages.get(currentStage);

                if (s.mode == 1) {
                    if (contraction) {
                        contraction = false;
                        secondsLeft = s.release;
                        stageDuration = s.release;
                        speak("Relax");
                        vibrate(2);
                        updateTrainingScreen();
                        handler.postDelayed(this, 1000);
                        return;
                    }

                    if (repetition < s.reps) {
                        repetition++;
                        contraction = true;
                        secondsLeft = s.hold;
                        stageDuration = s.hold;
                        speak("Hold");
                        vibrate(1);
                        updateTrainingScreen();
                        handler.postDelayed(this, 1000);
                        return;
                    }
                }

                running = false;
                handler.removeCallbacks(this);
                stageCompleted();
                return;
            }

            updateTrainingScreen();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("KegelYarData", MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        soundEnabled = prefs.getBoolean("sound", true);
        vibrationEnabled = prefs.getBoolean("vibration", true);
        morningEnabled = prefs.getBoolean("session_morning", true);
        noonEnabled = prefs.getBoolean("session_noon", false);
        nightEnabled = prefs.getBoolean("session_night", false);
        gender = prefs.getString("gender", "");

        markRestDays();
        initTTS();

        if (gender.isEmpty()) showGender();
        else showHome();
    }

    private void initTTS() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int result = tts.setLanguage(Locale.US);
                    ttsReady = result >= TextToSpeech.LANG_AVAILABLE;
                    tts.setSpeechRate(0.85f);
                }
            });
        } catch (Exception e) {
            ttsReady = false;
        }
    }

    private void speak(String word) {
        if (!soundEnabled || !ttsReady || tts == null) return;
        try {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "kegel_voice");
        } catch (Exception ignored) { }
    }

    private void vibrate(int type) {
        if (!vibrationEnabled || vibrator == null ||
                !vibrator.hasVibrator()) return;

        long[] pattern = type == 1
                ? new long[]{0,100}
                : new long[]{0,60,90,60};

        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) { }
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable outlined(int color, int radius) {
        GradientDrawable d = shape(color, radius);
        d.setStroke(dp(1), LINE);
        return d;
    }

    private GradientDrawable gradient() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{PURPLE,DARK});
        d.setCornerRadius(dp(22));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6),dp(6),dp(6),dp(6));
        return t;
    }

    private void setup(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),dp(7),dp(12),dp(7));

        TextView home = text("⌂",26,PURPLE);
        home.setBackground(shape(CARD,14));
        header.addView(home,new LinearLayout.LayoutParams(dp(46),dp(44)));
        home.setOnClickListener(v -> showHome());

        TextView heading = text(title,19,DARK);
        heading.setTypeface(null,Typeface.BOLD);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));

        TextView settings = text("⚙",23,PURPLE);
        settings.setBackground(shape(CARD,14));
        header.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(44)));
        settings.setOnClickListener(v -> showSettings());

        root.addView(header);

        View line = new View(this);
        line.setBackgroundColor(LINE);
        root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(10),dp(14),dp(24));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(13),dp(12),dp(13),dp(12));
        c.setBackground(outlined(CARD,19));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(7));
        content.addView(c,lp);
        return c;
    }

    private void addText(LinearLayout parent,String value,
                         int size,int color) {
        TextView t = text(value,size,color);
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        parent.addView(t);
    }

    private void button(String title,Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(shape(PURPLE,15));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(5));
        content.addView(b,lp);
        b.setOnClickListener(v -> action.run());
    }

    private void smallButton(LinearLayout parent,String title,
                             Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setTextColor(DARK);
        b.setAllCaps(false);
        b.setBackground(shape(BG,13));
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v -> action.run());
    }

    private void tileRow(String a,String b,Runnable actionA,
                         String c,String d,Runnable actionB) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        tile(row,a,b,actionA);
        tile(row,c,d,actionB);
    }

    private void tile(LinearLayout row,String title,String sub,
                      Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5),dp(10),dp(5),dp(10));
        box.setBackground(outlined(CARD,17));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0,dp(100),1);
        lp.setMargins(dp(4),dp(4),dp(4),dp(4));
        row.addView(box,lp);

        TextView t = text(title,15,DARK);
        t.setTypeface(null,Typeface.BOLD);
        box.addView(t);
        box.addView(text(sub,11,MUTED));
        box.setOnClickListener(v -> action.run());
    }

    private String dayKey(int w,int d) {
        return "week_" + w + "_day_" + d;
    }

    private String sessionKey(int w,int d,String session) {
        return dayKey(w,d) + "_session_" + session;
    }

    private boolean sessionDone(int w,int d,String s) {
        return prefs.getBoolean(sessionKey(w,d,s),false);
    }

    private boolean sessionEnabled(String s) {
        if (s.equals("morning")) return morningEnabled;
        if (s.equals("noon")) return noonEnabled;
        return nightEnabled;
    }

    private int enabledCount() {
        return (morningEnabled?1:0) +
                (noonEnabled?1:0) + (nightEnabled?1:0);
    }

    private void markRestDays() {
        SharedPreferences.Editor e = prefs.edit();
        for (int w=1;w<=12;w++) {
            e.putBoolean(dayKey(w,7),true);
        }
        e.apply();
    }

    private boolean dayDone(int w,int d) {
        if (d == 7) return true;
        if (prefs.getBoolean(dayKey(w,d),false)) return true;
        if (enabledCount() == 0) return false;

        if (morningEnabled && !sessionDone(w,d,"morning")) return false;
        if (noonEnabled && !sessionDone(w,d,"noon")) return false;
        if (nightEnabled && !sessionDone(w,d,"night")) return false;
        return true;
    }

    private int weekDone(int w) {
        int n=0;
        for (int d=1;d<=7;d++) if (dayDone(w,d)) n++;
        return n;
    }

    private boolean weekUnlocked(int w) {
        return w==1 || weekDone(w-1)==7;
    }

    private int totalDone() {
        int n=0;
        for (int w=1;w<=12;w++) n+=weekDone(w);
        return n;
    }

    private int firstWeek() {
        for (int w=1;w<=12;w++) {
            if (weekUnlocked(w) && weekDone(w)<7) return w;
        }
        return 12;
    }

    private int firstDay(int w) {
        for (int d=1;d<=6;d++) {
            if (!dayDone(w,d)) return d;
        }
        return 7;
    }

    private String sessionName(String s) {
        if (s.equals("morning")) return "صبح";
        if (s.equals("noon")) return "ظهر";
        return "شب";
    }

    private void showGender() {
        setup("کگل‌یار");
        LinearLayout c=card();
        addText(c,"به کگل‌یار خوش آمدید",23,DARK);
        addText(c,"گروه تمرینی خود را انتخاب کنید",15,MUTED);
        button("♂ آقایان",() -> {
            gender="آقایان";
            prefs.edit().putString("gender",gender).apply();
            showHome();
        });
        button("♀ بانوان",() -> {
            gender="بانوان";
            prefs.edit().putString("gender",gender).apply();
            showHome();
        });
    }

    private void showHome() {
        if (enabledCount()==0) {
            morningEnabled=true;
            prefs.edit().putBoolean("session_morning",true).apply();
        }

        selectedWeek=firstWeek();
        selectedDay=firstDay(selectedWeek);

        setup("کگل‌یار");

        LinearLayout p=card();
        p.setBackground(gradient());
        addText(p,"پیشرفت برنامه",18,Color.WHITE);
        addText(p,totalDone()+" از ۸۴ روز",25,Color.WHITE);

        ProgressBar bar=new ProgressBar(this,null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(84);
        bar.setProgress(totalDone());
        p.addView(bar,new LinearLayout.LayoutParams(-1,dp(12)));
        addText(p,"گروه: "+gender,13,Color.WHITE);

        LinearLayout today=card();
        addText(today,"جلسه امروز",19,DARK);
        addText(today,"هفته "+selectedWeek+"  •  روز "+selectedDay,
                14,MUTED);

        if (selectedDay==7) {
            addText(today,"امروز روز استراحت است 🌿",17,GREEN);
        } else {
            if (morningEnabled) homeSession(today,"☀ تمرین صبح","morning");
            if (noonEnabled) homeSession(today,"◉ تمرین ظهر","noon");
            if (nightEnabled) homeSession(today,"☾ تمرین شب","night");
        }

        tileRow("☀ صبح",morningEnabled?"فعال":"غیرفعال",
                () -> launchSession("morning"),
                "◉ ظهر",noonEnabled?"فعال":"غیرفعال",
                () -> launchSession("noon"));

        tileRow("☾ شب",nightEnabled?"فعال":"غیرفعال",
                () -> launchSession("night"),
                "▦ برنامه ۱۲ هفته","مشاهده روزها",
                () -> showWeeks());

        tileRow("◉ پیشرفت","گزارش تمرین",
                () -> showProgress(),
                "♡ مکمل","تمرین‌های کمکی",
                () -> showExercises());

        tileRow("📖 آموزش","روش صحیح",
                () -> showGuide(),
                "⚙ تنظیمات","نوبت‌ها و صدا",
                () -> showSettings());

        button("نکات ایمنی",() -> showSafety());
        button("تغییر گروه تمرینی",() -> showGender());
    }

    private void homeSession(LinearLayout parent,String label,String s) {
        boolean done=sessionDone(selectedWeek,selectedDay,s);
        smallButton(parent,label+(done?" ✓":"  ▶ شروع"),
                () -> launchSession(s));
    }

    private void launchSession(String s) {
        if (!sessionEnabled(s)) {
            Toast.makeText(this,"این نوبت غیرفعال است.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        selectedWeek=firstWeek();
        selectedDay=firstDay(selectedWeek);

        if (selectedDay==7) {
            showHome();
            return;
        }

        if (sessionDone(selectedWeek,selectedDay,s)) {
            Toast.makeText(this,"این نوبت قبلاً انجام شده است.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        selectedSession=s;
        showTraining();
    }

    private void showWeeks() {
        setup("برنامه ۱۲ هفته‌ای");

        LinearLayout intro=card();
        addText(intro,"۱۲ هفته و ۸۴ روز تقویمی",19,DARK);
        addText(intro,"هر هفته ۶ روز تمرین و یک روز استراحت دارد.",
                14,MUTED);

        for (int i=1;i<=12;i++) {
            final int w=i;
            LinearLayout c=card();
            addText(c,"هفته "+w,18,DARK);
            addText(c,weekDone(w)+" از ۷ روز تکمیل شده",13,GREEN);

            ProgressBar pb=new ProgressBar(this,null,
                    android.R.attr.progressBarStyleHorizontal);
            pb.setMax(7);
            pb.setProgress(weekDone(w));
            c.addView(pb);

            if (weekUnlocked(w)) {
                smallButton(c,"مشاهده روزهای هفته",
                        () -> {
                            selectedWeek=w;
                            showDays();
                        });
            } else {
                addText(c,"🔒 ابتدا هفته قبلی را کامل کنید.",13,MUTED);
            }
        }
        button("بازگشت به خانه",() -> showHome());
    }

    private void showDays() {
        if (!weekUnlocked(selectedWeek)) {
            showWeeks();
            return;
        }

        setup("هفته "+selectedWeek);

        for (int d=1;d<=7;d++) {
            final int day=d;
            LinearLayout c=card();

            if (d==7) {
                addText(c,"روز هفتم  •  استراحت 🌿",17,GREEN);
                addText(c,"امروز تمرین ندارید.",13,MUTED);
                continue;
            }

            boolean done=dayDone(selectedWeek,d);
            addText(c,"روز "+d+(done?"  ✓":"  ○"),17,DARK);

            if (done) {
                addText(c,"جلسه‌های فعال تکمیل شده‌اند.",13,GREEN);
            } else if (d==firstDay(selectedWeek)) {
                if (morningEnabled)
                    dayButton(c,day,"صبح","morning");
                if (noonEnabled)
                    dayButton(c,day,"ظهر","noon");
                if (nightEnabled)
                    dayButton(c,day,"شب","night");
            } else {
                addText(c,"پس از تکمیل روز قبلی فعال می‌شود.",
                        13,MUTED);
            }
        }

        button("بازگشت به هفته‌ها",() -> showWeeks());
        button("خانه",() -> showHome());
    }

    private void dayButton(LinearLayout c,int day,
                           String label,String session) {
        boolean done=sessionDone(selectedWeek,day,session);
        smallButton(c,label+(done?" ✓ انجام شده":"  ▶ شروع"),
                () -> {
                    if (done) return;
                    selectedDay=day;
                    selectedSession=session;
                    showTraining();
                });
    }

    private void buildStages() {
        stages.clear();

        int offset=selectedSession.equals("morning")?0:
                selectedSession.equals("noon")?1:2;

        stages.add(new Stage("آماده‌سازی",
                "آرام نفس بکشید و بدن را رها کنید.",
                30+offset*5));

        int hold=Math.min(8,3+(selectedWeek-1)/2);
        int release=hold+2;
        int reps=selectedWeek<4?4:5;

        if (offset>0) reps=Math.max(3,reps-1);

        stages.add(new Stage("انقباض و رهاسازی",
                "به‌آرامی منقبض کنید و سپس کاملاً رها کنید.",
                reps,hold,release));

        int quick=selectedWeek<4?3:5;

        stages.add(new Stage("انقباض‌های کوتاه",
                "انقباض کوتاه و رهاسازی کامل.",
                quick,1,3));

        stages.add(new Stage("آرام‌سازی پایانی",
                "عضلات را کاملاً آزاد کنید و آرام نفس بکشید.",
                35+offset*5));
    }

    private void showTraining() {
        stopTimer();
        finished=false;
        currentStage=0;
        buildStages();
        prepareStage();
    }

    private void prepareStage() {
        running=false;
        paused=false;
        handler.removeCallbacks(timerRunnable);

        if (currentStage>=stages.size()) {
            finishTraining();
            return;
        }

        Stage s=stages.get(currentStage);
        repetition=1;
        contraction=true;
        secondsLeft=s.mode==1?s.hold:s.duration;
        stageDuration=secondsLeft;

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),dp(7),dp(12),dp(7));

        TextView home=text("⌂",26,PURPLE);
        home.setBackground(shape(CARD,14));
        header.addView(home,new LinearLayout.LayoutParams(dp(46),dp(44)));
        home.setOnClickListener(v -> {
            stopTimer();
            showHome();
        });

        TextView session=text("تمرین "+sessionName(selectedSession),
                17,DARK);
        session.setTypeface(null,Typeface.BOLD);
        header.addView(session,new LinearLayout.LayoutParams(0,-2,1));

        TextView settings=text("⚙",23,PURPLE);
        settings.setBackground(shape(CARD,14));
        header.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(44)));
        settings.setOnClickListener(v -> {
            stopTimer();
            showSettings();
        });

        root.addView(header);

        root.addView(text("هفته "+selectedWeek+"  •  روز "+selectedDay,
                14,MUTED));

        stageLabel=text("",16,DARK);
        stageLabel.setTypeface(null,Typeface.BOLD);
        root.addView(stageLabel);

        phaseLabel=text("",17,PURPLE);
        root.addView(phaseLabel);

        instructionLabel=text("",13,MUTED);
        root.addView(instructionLabel);

        timerView=new CircleTimer(this);
        LinearLayout.LayoutParams timerLp =
                new LinearLayout.LayoutParams(dp(245),dp(245));
        timerLp.gravity=Gravity.CENTER;
        timerLp.setMargins(0,dp(18),0,dp(15));
        root.addView(timerView,timerLp);

        TextView rep=text("",13,MUTED);
        rep.setTag("rep");
        root.addView(rep);

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8),dp(12),dp(8),dp(12));

        TextView play=text("▶",31,Color.WHITE);
        play.setBackground(shape(PURPLE,60));
        controls.addView(play,new LinearLayout.LayoutParams(dp(70),dp(70)));
        play.setOnClickListener(v -> startOrResume());

        TextView pause=text("Ⅱ",27,Color.WHITE);
        pause.setBackground(shape(DARK,60));
        LinearLayout.LayoutParams pauseLp =
                new LinearLayout.LayoutParams(dp(70),dp(70));
        pauseLp.setMargins(dp(25),0,0,0);
        controls.addView(pause,pauseLp);
        pause.setOnClickListener(v -> pauseStage());

        root.addView(controls);

        TextView hint=text(
                "برای شروع هر مرحله یک‌بار ▶ را بزنید.",
                12,MUTED);
        root.addView(hint);

        Button next=new Button(this);
        next.setText("خانه");
        next.setAllCaps(false);
        next.setTextColor(DARK);
        next.setBackground(shape(CARD,15));
        root.addView(next,new LinearLayout.LayoutParams(-1,-2));
        next.setOnClickListener(v -> {
            stopTimer();
            showHome();
        });

        setContentView(root);
        updateTrainingScreen();
    }

    private void startOrResume() {
        if (finished || running) return;

        if (paused) {
            paused=false;
            running=true;
            handler.removeCallbacks(timerRunnable);
            handler.postDelayed(timerRunnable,1000);
            return;
        }

        running=true;

        Stage s=stages.get(currentStage);
        if (s.mode==1) {
            contraction=true;
            speak("Hold");
            vibrate(1);
        }

        handler.removeCallbacks(timerRunnable);
        handler.postDelayed(timerRunnable,1000);
        updateTrainingScreen();
    }

    private void pauseStage() {
        if (!running) return;
        running=false;
        paused=true;
        handler.removeCallbacks(timerRunnable);
        updateTrainingScreen();
    }

    private void stopTimer() {
        running=false;
        paused=false;
        handler.removeCallbacks(timerRunnable);
    }

    private void updateTrainingScreen() {
        if (currentStage>=stages.size()) return;

        Stage s=stages.get(currentStage);

        if (stageLabel!=null) {
            stageLabel.setText("مرحله "+(currentStage+1)+
                    " از "+stages.size()+"  •  "+s.title);
        }

        if (instructionLabel!=null)
            instructionLabel.setText(s.description);

        if (phaseLabel!=null) {
            if (s.mode==1) {
                phaseLabel.setText(contraction?
                        "انقباض  •  Hold":"رهاسازی  •  Relax");
                phaseLabel.setTextColor(contraction?PINK:TEAL);
            } else {
                phaseLabel.setText(running?"در حال انجام":"آماده شروع");
                phaseLabel.setTextColor(PURPLE);
            }
        }

        if (timerView!=null) timerView.invalidate();

        TextView rep=(TextView)root.findViewWithTag("rep");
        if (rep!=null && s.mode==1) {
            rep.setText("تکرار "+repetition+" از "+s.reps);
        } else if (rep!=null) {
            rep.setText("زمان مرحله");
        }
    }

    private void stageCompleted() {
        running=false;
        paused=false;
        handler.removeCallbacks(timerRunnable);
        currentStage++;

        if (currentStage>=stages.size()) {
            finishTraining();
            return;
        }

        prepareStage();
        Toast.makeText(this,
                "مرحله تمام شد. برای مرحله بعد ▶ را بزنید.",
                Toast.LENGTH_LONG).show();
    }

    private void finishTraining() {
        stopTimer();
        finished=true;

        prefs.edit().putBoolean(
                sessionKey(selectedWeek,selectedDay,selectedSession),
                true).apply();

        if (enabledCount()==1) {
            prefs.edit().putBoolean(
                    dayKey(selectedWeek,selectedDay),true).apply();
        } else if (allEnabledSessionsDone()) {
            prefs.edit().putBoolean(
                    dayKey(selectedWeek,selectedDay),true).apply();
        }

        vibrate(3);
        setup("پایان تمرین");

        LinearLayout c=card();
        addText(c,"آفرین! تمرین تمام شد 🌷",22,GREEN);
        addText(c,"نوبت "+sessionName(selectedSession)+
                " با موفقیت ثبت شد.",15,DARK);
        addText(c,"برای ادامه، عضلات را آزاد نگه دارید.",
                13,MUTED);

        button("بازگشت به خانه",() -> showHome());
        button("مشاهده برنامه",() -> showWeeks());
    }

    private boolean allEnabledSessionsDone() {
        if (morningEnabled &&
                !sessionDone(selectedWeek,selectedDay,"morning"))
            return false;
        if (noonEnabled &&
                !sessionDone(selectedWeek,selectedDay,"noon"))
            return false;
        if (nightEnabled &&
                !sessionDone(selectedWeek,selectedDay,"night"))
            return false;
        return true;
    }

    private class CircleTimer extends View {
        private final Paint paint=new Paint(3);

        CircleTimer(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx=getWidth()/2f;
            float cy=getHeight()/2f;
            float radius=Math.min(cx,cy)-dp(17);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(13));
            paint.setColor(LINE);
            canvas.drawCircle(cx,cy,radius,paint);

            int color=PURPLE;
            if (currentStage<stages.size() &&
                    stages.get(currentStage).mode==1) {
                color=contraction?PINK:TEAL;
            }

            float fraction=stageDuration<=0?0:
                    Math.max(0,Math.min(1,
                            secondsLeft/(float)stageDuration));

            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,
                    -90,360*fraction,false,paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(TEXT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(
                    Typeface.DEFAULT,Typeface.BOLD));
            paint.setTextSize(dp(55));

            canvas.drawText(String.valueOf(secondsLeft),
                    cx,cy+dp(18),paint);

            paint.setTextSize(dp(13));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(MUTED);

            String label=running?"ثانیه باقی‌مانده":
                    paused?"متوقف شده":"آماده شروع";

            canvas.drawText(label,cx,cy+dp(48),paint);
        }
    }

    private void showProgress() {
        setup("گزارش پیشرفت");

        LinearLayout c=card();
        addText(c,"مجموع روزهای تکمیل‌شده",18,DARK);
        addText(c,totalDone()+" از ۸۴ روز",24,GREEN);

        ProgressBar pb=new ProgressBar(this,null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(84);
        pb.setProgress(totalDone());
        c.addView(pb);

        for (int w=1;w<=12;w++) {
            LinearLayout item=card();
            addText(item,"هفته "+w,15,DARK);
            addText(item,weekDone(w)+" از ۷ روز",13,MUTED);
        }

        button("بازگشت",() -> showHome());
    }

    private void showExercises() {
        setup("تمرین‌های مکمل");

        String[] names={
                "تنفس دیافراگمی",
                "پل باسن",
                "کشش ملایم لگن",
                "حرکت گربه و گاو",
                "راه رفتن آرام",
                "کشش عضلات ران",
                "آرام‌سازی شکم",
                "کشش ملایم کمر",
                "تنفس آرام در حالت خوابیده",
                "ریلکس کردن کف لگن"
        };

        String[] details={
                "به پشت دراز بکشید. هنگام دم شکم را آرام بالا بیاورید.",
                "زانوها خم باشد. لگن را آرام بالا ببرید و پایین بیاورید.",
                "بدون فشار، لگن را در دامنه راحت حرکت دهید.",
                "در حالت چهار دست‌وپا کمر را آرام گرد و صاف کنید.",
                "چند دقیقه با سرعت راحت راه بروید.",
                "کشش را ملایم انجام دهید و درد ایجاد نکنید.",
                "شکم را آزاد بگذارید و آرام نفس بکشید.",
                "کمر را بدون فشار در وضعیت راحت قرار دهید.",
                "تنفس آرام و بدون حبس نفس انجام دهید.",
                "به‌جای انقباض، روی رهاسازی کامل تمرکز کنید."
        };

        for (int i=0;i<names.length;i++) {
            LinearLayout c=card();
            addText(c,(i+1)+". "+names[i],16,DARK);
            addText(c,details[i],13,MUTED);
        }

        button("بازگشت",() -> showHome());
    }

    private void showGuide() {
        setup("آموزش تمرین کگل");

        LinearLayout c=card();
        addText(c,"روش صحیح تمرین",20,DARK);
        addText(c,
                "۱. عضلات کف لگن را به‌آرامی منقبض کنید.\n\n"+
                "۲. هنگام انقباض نفس را حبس نکنید.\n\n"+
                "۳. پس از هر انقباض، عضلات را کاملاً رها کنید.\n\n"+
                "۴. شکم، باسن و ران‌ها را تا حد امکان شل نگه دارید.\n\n"+
                "۵. کیفیت حرکت مهم‌تر از تعداد تکرارهاست.\n\n"+
                "۶. تمرین را هنگام ادرار کردن انجام ندهید.",
                14,TEXT);

        button("بازگشت",() -> showHome());
    }

    private void showSafety() {
        setup("نکات ایمنی");

        LinearLayout c=card();
        addText(c,"سلامت شما مهم‌تر از تمرین است",19,DARK);
        addText(c,
                "• تمرین نباید درد ایجاد کند.\n\n"+
                "• از فشار بیش از حد خودداری کنید.\n\n"+
                "• بین انقباض‌ها رهاسازی کامل داشته باشید.\n\n"+
                "• نفس خود را حبس نکنید.\n\n"+
                "• تمرین کگل را هنگام ادرار کردن انجام ندهید.\n\n"+
                "• اگر درد لگن، سوزش، مشکل ادرار یا بدتر شدن علائم دارید، تمرین را متوقف کنید و با پزشک یا فیزیوتراپیست کف لگن مشورت کنید.\n\n"+
                "این برنامه عمومی است و جایگزین ارزیابی تخصصی نیست.",
                14,TEXT);

        button("بازگشت",() -> showHome());
    }

    private void showSettings() {
        setup("تنظیمات");

        LinearLayout c=card();
        addText(c,"انتخاب نوبت‌های تمرین",18,DARK);
        addText(c,"هر نوبت جداگانه ثبت می‌شود.",13,MUTED);

        CheckBox morning=new CheckBox(this);
        morning.setText("تمرین صبح");
        morning.setTextColor(TEXT);
        morning.setChecked(morningEnabled);
        c.addView(morning);

        CheckBox noon=new CheckBox(this);
        noon.setText("تمرین ظهر");
        noon.setTextColor(TEXT);
        noon.setChecked(noonEnabled);
        c.addView(noon);

        CheckBox night=new CheckBox(this);
        night.setText("تمرین شب");
        night.setTextColor(TEXT);
        night.setChecked(nightEnabled);
        c.addView(night);

        CheckBox sound=new CheckBox(this);
        sound.setText("صدای راهنمای انگلیسی Hold و Relax");
        sound.setTextColor(TEXT);
        sound.setChecked(soundEnabled);
        c.addView(sound);

        CheckBox vibration=new CheckBox(this);
        vibration.setText("لرزش هنگام تغییر مرحله");
        vibration.setTextColor(TEXT);
        vibration.setChecked(vibrationEnabled);
        c.addView(vibration);

        button("ذخیره تنظیمات",() -> {
            if (!morning.isChecked() &&
                    !noon.isChecked() && !night.isChecked()) {
                Toast.makeText(this,
                        "حداقل یک نوبت باید فعال باشد.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            morningEnabled=morning.isChecked();
            noonEnabled=noon.isChecked();
            nightEnabled=night.isChecked();
            soundEnabled=sound.isChecked();
            vibrationEnabled=vibration.isChecked();

            prefs.edit()
                    .putBoolean("session_morning",morningEnabled)
                    .putBoolean("session_noon",noonEnabled)
                    .putBoolean("session_night",nightEnabled)
                    .putBoolean("sound",soundEnabled)
                    .putBoolean("vibration",vibrationEnabled)
                    .apply();

            Toast.makeText(this,"تنظیمات ذخیره شد.",
                    Toast.LENGTH_SHORT).show();
            showHome();
        });

        button("تغییر گروه تمرینی",() -> showGender());
        button("بازگشت به خانه",() -> showHome());
    }

    @Override
    public void onBackPressed() {
        if (running) pauseStage();
        showHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) pauseStage();
    }

    @Override
    protected void onDestroy() {
        stopTimer();

        if (tts!=null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) { }
        }

        super.onDestroy();
    }
}
