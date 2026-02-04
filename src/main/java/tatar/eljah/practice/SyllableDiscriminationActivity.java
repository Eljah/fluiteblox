package tatar.eljah.practice;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import android.support.v7.app.AppCompatActivity;

import tatar.eljah.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SyllableDiscriminationActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SOUND = "sound";
    public static final String MODE_TONE = "tone";

    private static final String[] CONSONANTS = {"", "m", "n", "l", "b", "d", "g", "h", "k", "ng"};
    private static final String[] VOWELS = {
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y"
    };
    private static final String[] BASE_VOWELS = {"a", "e", "i", "o", "u"};
    private static final String[] TONES = {"ngang", "sắc", "huyền", "hỏi", "ngã", "nặng"};
    private static final Map<String, String[]> TONE_FORMS = new HashMap<>();
    private static final Map<String, String[]> BASE_VOWEL_VARIANTS = new HashMap<>();

    static {
        TONE_FORMS.put("a", new String[]{"a", "á", "à", "ả", "ã", "ạ"});
        TONE_FORMS.put("ă", new String[]{"ă", "ắ", "ằ", "ẳ", "ẵ", "ặ"});
        TONE_FORMS.put("â", new String[]{"â", "ấ", "ầ", "ẩ", "ẫ", "ậ"});
        TONE_FORMS.put("e", new String[]{"e", "é", "è", "ẻ", "ẽ", "ẹ"});
        TONE_FORMS.put("ê", new String[]{"ê", "ế", "ề", "ể", "ễ", "ệ"});
        TONE_FORMS.put("i", new String[]{"i", "í", "ì", "ỉ", "ĩ", "ị"});
        TONE_FORMS.put("o", new String[]{"o", "ó", "ò", "ỏ", "õ", "ọ"});
        TONE_FORMS.put("ô", new String[]{"ô", "ố", "ồ", "ổ", "ỗ", "ộ"});
        TONE_FORMS.put("ơ", new String[]{"ơ", "ớ", "ờ", "ở", "ỡ", "ợ"});
        TONE_FORMS.put("u", new String[]{"u", "ú", "ù", "ủ", "ũ", "ụ"});
        TONE_FORMS.put("ư", new String[]{"ư", "ứ", "ừ", "ử", "ữ", "ự"});
        TONE_FORMS.put("y", new String[]{"y", "ý", "ỳ", "ỷ", "ỹ", "ỵ"});

        BASE_VOWEL_VARIANTS.put("a", new String[]{"a", "ă", "â"});
        BASE_VOWEL_VARIANTS.put("e", new String[]{"e", "ê"});
        BASE_VOWEL_VARIANTS.put("i", new String[]{"i", "y"});
        BASE_VOWEL_VARIANTS.put("o", new String[]{"o", "ô", "ơ"});
        BASE_VOWEL_VARIANTS.put("u", new String[]{"u", "ư"});
    }

    private Spinner consonantSpinner;
    private Spinner baseVowelSpinner;
    private Spinner toneSpinner;
    private Spinner vowelSpinner;
    private Spinner firstChoiceSpinner;
    private Spinner secondChoiceSpinner;
    private LinearLayout baseVowelRow;
    private LinearLayout toneRow;
    private LinearLayout vowelRow;
    private TextView optionsView;
    private TextView scoreView;
    private TextView resultView;
    private Button playPairButton;
    private Button checkAnswerButton;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    private String correctFirst;
    private String correctSecond;
    private final Random random = new Random();
    private int score = 0;
    private String mode = MODE_SOUND;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_syllable_discrimination);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_SOUND;
        }

        consonantSpinner = findViewById(R.id.spinner_discrimination_consonant);
        baseVowelSpinner = findViewById(R.id.spinner_discrimination_base_vowel);
        toneSpinner = findViewById(R.id.spinner_discrimination_tone);
        vowelSpinner = findViewById(R.id.spinner_discrimination_vowel);
        firstChoiceSpinner = findViewById(R.id.spinner_discrimination_first);
        secondChoiceSpinner = findViewById(R.id.spinner_discrimination_second);
        baseVowelRow = findViewById(R.id.row_base_vowel);
        toneRow = findViewById(R.id.row_tone);
        vowelRow = findViewById(R.id.row_vowel);
        optionsView = findViewById(R.id.tv_discrimination_options);
        scoreView = findViewById(R.id.tv_discrimination_score);
        resultView = findViewById(R.id.tv_discrimination_result);
        playPairButton = findViewById(R.id.btn_play_pair);
        checkAnswerButton = findViewById(R.id.btn_check_answer);

        setupSpinners();
        updateModeUi();
        updateScore();

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale vietnameseLocale = Locale.forLanguageTag("vi-VN");
                    int languageStatus = textToSpeech.setLanguage(vietnameseLocale);
                    isTtsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                            && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            }
        }, "com.google.android.tts");

        playPairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPair();
            }
        });

        checkAnswerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAnswer();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> consonantAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CONSONANTS);
        consonantAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        consonantSpinner.setAdapter(consonantAdapter);

        ArrayAdapter<String> baseVowelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BASE_VOWELS);
        baseVowelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baseVowelSpinner.setAdapter(baseVowelAdapter);

        ArrayAdapter<String> toneAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TONES);
        toneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        toneSpinner.setAdapter(toneAdapter);

        ArrayAdapter<String> vowelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, VOWELS);
        vowelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vowelSpinner.setAdapter(vowelAdapter);
    }

    private void updateModeUi() {
        if (MODE_SOUND.equals(mode)) {
            setTitle(R.string.title_sound_mode);
            baseVowelRow.setVisibility(View.VISIBLE);
            toneRow.setVisibility(View.VISIBLE);
            vowelRow.setVisibility(View.GONE);
        } else {
            setTitle(R.string.title_tone_mode);
            baseVowelRow.setVisibility(View.GONE);
            toneRow.setVisibility(View.GONE);
            vowelRow.setVisibility(View.VISIBLE);
        }
    }

    private void playPair() {
        if (!isTtsReady) {
            resultView.setText(R.string.label_tts_not_ready);
            return;
        }

        List<String> options = buildOptions();
        if (options.size() < 2) {
            resultView.setText(R.string.label_not_enough_variants);
            return;
        }

        optionsView.setText(getString(R.string.label_options, options.get(0), options.get(1)));
        ArrayAdapter<String> choiceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        choiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firstChoiceSpinner.setAdapter(choiceAdapter);
        secondChoiceSpinner.setAdapter(choiceAdapter);

        boolean swap = random.nextBoolean();
        correctFirst = swap ? options.get(1) : options.get(0);
        correctSecond = swap ? options.get(0) : options.get(1);

        resultView.setText("");
        textToSpeech.speak(correctFirst, TextToSpeech.QUEUE_FLUSH, null, "first");
        textToSpeech.speak(correctSecond, TextToSpeech.QUEUE_ADD, null, "second");
    }

    private List<String> buildOptions() {
        List<String> options = new ArrayList<>();
        String consonant = String.valueOf(consonantSpinner.getSelectedItem());
        if (MODE_SOUND.equals(mode)) {
            String baseVowel = String.valueOf(baseVowelSpinner.getSelectedItem());
            String tone = String.valueOf(toneSpinner.getSelectedItem());
            String[] variants = BASE_VOWEL_VARIANTS.get(baseVowel);
            if (variants == null || variants.length < 2) {
                return options;
            }
            int firstIndex = random.nextInt(variants.length);
            int secondIndex = pickDifferentIndex(variants.length, firstIndex);
            String firstVowel = variants[firstIndex];
            String secondVowel = variants[secondIndex];
            options.add(buildSyllable(consonant, firstVowel, tone));
            options.add(buildSyllable(consonant, secondVowel, tone));
        } else {
            String vowel = String.valueOf(vowelSpinner.getSelectedItem());
            int firstToneIndex = random.nextInt(TONES.length);
            int secondToneIndex = pickDifferentIndex(TONES.length, firstToneIndex);
            String firstTone = TONES[firstToneIndex];
            String secondTone = TONES[secondToneIndex];
            options.add(buildSyllable(consonant, vowel, firstTone));
            options.add(buildSyllable(consonant, vowel, secondTone));
        }
        return options;
    }

    private int pickDifferentIndex(int size, int firstIndex) {
        int secondIndex = random.nextInt(size);
        while (secondIndex == firstIndex && size > 1) {
            secondIndex = random.nextInt(size);
        }
        return secondIndex;
    }

    private String buildSyllable(String consonant, String vowel, String tone) {
        String tonedVowel = applyTone(vowel, tone);
        return (consonant + tonedVowel).trim();
    }

    private String applyTone(String vowel, String toneLabel) {
        int toneIndex = 0;
        for (int i = 0; i < TONES.length; i++) {
            if (TONES[i].equals(toneLabel)) {
                toneIndex = i;
                break;
            }
        }
        String[] forms = TONE_FORMS.get(vowel);
        if (forms == null || toneIndex >= forms.length) {
            return vowel;
        }
        return forms[toneIndex];
    }

    private void checkAnswer() {
        if (correctFirst == null || correctSecond == null) {
            resultView.setText(R.string.label_no_round);
            return;
        }
        String chosenFirst = String.valueOf(firstChoiceSpinner.getSelectedItem());
        String chosenSecond = String.valueOf(secondChoiceSpinner.getSelectedItem());
        if (correctFirst.equals(chosenFirst) && correctSecond.equals(chosenSecond)) {
            score += 1;
            resultView.setText(R.string.label_correct);
        } else {
            score -= 1;
            resultView.setText(R.string.label_wrong);
        }
        updateScore();
    }

    private void updateScore() {
        scoreView.setText(getString(R.string.label_score, score));
    }
}
