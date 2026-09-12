package tatar.eljah.recorder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AudiverisCompatRecognitionEngineTest {
    public static void main(String[] args) throws Exception {
        new AudiverisCompatRecognitionEngineTest().rejectsLongSingleDurationRecognitionAsLowConfidence();
    }

    public void rejectsLongSingleDurationRecognitionAsLowConfidence() throws Exception {
        AudiverisCompatRecognitionEngine engine = new AudiverisCompatRecognitionEngine(null);
        Method isPlausible = AudiverisCompatRecognitionEngine.class.getDeclaredMethod(
                "isPlausible", List.class, List.class);
        isPlausible.setAccessible(true);

        assertLowConfidence(engine, isPlausible, 73);
        assertLowConfidence(engine, isPlausible, 47);
    }

    private void assertLowConfidence(AudiverisCompatRecognitionEngine engine, Method isPlausible, int noteCount) throws Exception {
        List<NoteEvent> notes = new ArrayList<NoteEvent>();
        for (int i = 0; i < noteCount; i++) {
            notes.add(new NoteEvent("F", 4, "quarter", 1 + i / 4, i * 10f, 100f));
        }
        if ((Boolean) isPlausible.invoke(engine, notes, threeStaves())) {
            throw new AssertionError("Expected " + noteCount + " quarter-only notes to be rejected");
        }
    }

    private List<Object> threeStaves() throws Exception {
        List<Object> staves = new ArrayList<Object>();
        staves.add(staff(100f, 148f, 12f));
        staves.add(staff(300f, 348f, 12f));
        staves.add(staff(500f, 548f, 12f));
        return staves;
    }

    private Object staff(float top, float bottom, float spacing) throws Exception {
        Class<?> staffClass = Class.forName(
                "tatar.eljah.recorder.AudiverisCompatRecognitionEngine$StaffModel");
        java.lang.reflect.Constructor<?> constructor = staffClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object staff = constructor.newInstance();
        set(staffClass, staff, "top", top);
        set(staffClass, staff, "bottom", bottom);
        set(staffClass, staff, "spacing", spacing);
        set(staffClass, staff, "center", (top + bottom) * 0.5f);
        return staff;
    }

    private void set(Class<?> owner, Object target, String name, float value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }
}
